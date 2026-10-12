import asyncio
import os
import time
from collections import defaultdict
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Dict, List, Tuple
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from config import HOST, PIN, PIN_ENABLED, PORT
from routes.clipboard import clipboard_watcher_loop, router as clipboard_router
from routes.files import router as files_router
from routes.ws import router as ws_router
from services.discovery import discovery_service
from services.qr import print_server_banner

STATIC_DIR = Path(__file__).parent / "static"

# In-memory IP-based brute force protection
# Tracks: ip -> [(timestamp), ...]
_failed_attempts: Dict[str, List[float]] = defaultdict(list)
_lockout_until: Dict[str, float] = {}

MAX_ATTEMPTS = 5
LOCKOUT_WINDOW_SECONDS = 60
LOCKOUT_DURATION_SECONDS = 60


def is_ip_locked(client_ip: str) -> Tuple[bool, int]:
    """Check if a client IP is currently rate-limited due to brute force attempts."""
    now = time.time()
    locked_until = _lockout_until.get(client_ip, 0)
    if now < locked_until:
        remaining = int(locked_until - now)
        return True, remaining

    # Purge old attempts older than LOCKOUT_WINDOW_SECONDS
    _failed_attempts[client_ip] = [
        t for t in _failed_attempts[client_ip] if now - t < LOCKOUT_WINDOW_SECONDS
    ]
    return False, 0


def record_failed_attempt(client_ip: str):
    now = time.time()
    _failed_attempts[client_ip].append(now)
    if len(_failed_attempts[client_ip]) >= MAX_ATTEMPTS:
        _lockout_until[client_ip] = now + LOCKOUT_DURATION_SECONDS


def clear_failed_attempts(client_ip: str):
    _failed_attempts.pop(client_ip, None)
    _lockout_until.pop(client_ip, None)


class PinVerifyPayload(BaseModel):
    pin: str


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup actions
    print_server_banner(HOST, PORT)
    discovery_service.start(PORT)
    clipboard_task = asyncio.create_task(clipboard_watcher_loop())
    yield
    # Shutdown actions
    clipboard_task.cancel()
    discovery_service.stop()


app = FastAPI(
    title="WiFi Share",
    version="1.3.0",
    description="High-speed local network file sharing",
    lifespan=lifespan,
)

# Enable CORS for local network clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def pin_auth_middleware(request: Request, call_next):
    """
    Verify PIN via header, cookie, or query param for all media/api routes.
    CRITICAL: Always bypass CORS OPTIONS preflight requests!
    """
    # 1. Always permit CORS preflight requests
    if request.method == "OPTIONS":
        return await call_next(request)

    if PIN_ENABLED:
        path = request.url.path
        normalized_path = path.rstrip("/") or "/"
        exempt_paths = ("/", "/health", "/auth/verify")

        if not (normalized_path in exempt_paths or path.startswith("/static")):
            client_ip = request.client.host if request.client else "unknown"

            # Check brute-force lockout
            is_locked, remaining = is_ip_locked(client_ip)
            if is_locked:
                return JSONResponse(
                    status_code=429,
                    content={"detail": f"Too many failed attempts. Locked out for {remaining}s."}
                )

            provided_pin = (
                request.headers.get("x-auth-pin")
                or request.cookies.get("wifi_share_pin")
                or request.query_params.get("pin")
            )

            if not provided_pin or str(provided_pin).strip() != str(PIN).strip():
                return JSONResponse(
                    status_code=401,
                    content={"detail": "Invalid or missing authentication PIN"}
                )

    return await call_next(request)


# PIN Verification & Session Endpoint
@app.post("/auth/verify")
async def verify_pin(payload: PinVerifyPayload, request: Request):
    """Validate 4-digit PIN with rate-limiting and set authentication cookie."""
    client_ip = request.client.host if request.client else "unknown"

    # Check brute-force lockout
    is_locked, remaining = is_ip_locked(client_ip)
    if is_locked:
        return JSONResponse(
            status_code=429,
            content={"ok": False, "detail": f"Too many failed attempts. Try again in {remaining}s."}
        )

    if not PIN_ENABLED or str(payload.pin).strip() == str(PIN).strip():
        clear_failed_attempts(client_ip)
        res = JSONResponse({"ok": True, "pin_enabled": PIN_ENABLED})
        res.set_cookie(
            key="wifi_share_pin",
            value=str(PIN).strip(),
            httponly=False,
            samesite="lax",
            path="/",
        )
        return res

    # Record failed attempt and add artificial delay to prevent automated attacks
    record_failed_attempt(client_ip)
    await asyncio.sleep(0.4)

    is_locked_now, remaining_now = is_ip_locked(client_ip)
    if is_locked_now:
        return JSONResponse(
            status_code=429,
            content={"ok": False, "detail": f"Too many failed attempts. Locked for {remaining_now}s."}
        )

    return JSONResponse(status_code=401, content={"ok": False, "detail": "Incorrect PIN"})


# Include API and WebSocket routes
app.include_router(files_router)
app.include_router(clipboard_router)
app.include_router(ws_router)


# Health check endpoint
@app.get("/health")
async def health_check():
    return {"status": "ok", "version": "1.3.0", "pin_protected": PIN_ENABLED}


# Serve Web UI
@app.get("/")
async def serve_index():
    index_file = STATIC_DIR / "index.html"
    return FileResponse(index_file)


# Mount static directory for any additional assets
if STATIC_DIR.exists():
    app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host=HOST, port=PORT, reload=False)
