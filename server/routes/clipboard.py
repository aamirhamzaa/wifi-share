import asyncio
import logging
import subprocess
import sys
from typing import Optional
from fastapi import APIRouter
from pydantic import BaseModel

from routes.ws import ws_manager

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/clipboard", tags=["clipboard"])

# In-memory shared clipboard buffer
_clipboard_state = {"text": ""}


class ClipboardPayload(BaseModel):
    text: str


def read_system_clipboard() -> Optional[str]:
    """Read current clipboard text from the host desktop environment (Linux, macOS, or Windows)."""
    # 1. Try Windows (PowerShell)
    if sys.platform == "win32":
        try:
            proc = subprocess.run(
                ["powershell", "-NoProfile", "-Command", "Get-Clipboard"],
                capture_output=True,
                text=True,
                timeout=0.8,
            )
            if proc.returncode == 0:
                return proc.stdout.rstrip("\r\n")
        except Exception:
            pass

    # 2. Try pbpaste (macOS native)
    try:
        proc = subprocess.run(
            ["pbpaste"],
            capture_output=True,
            text=True,
            timeout=0.6,
        )
        if proc.returncode == 0:
            return proc.stdout
    except Exception:
        pass

    # 3. Try xclip (Ubuntu X11)
    try:
        proc = subprocess.run(
            ["xclip", "-selection", "clipboard", "-o"],
            capture_output=True,
            text=True,
            timeout=0.6,
        )
        if proc.returncode == 0:
            return proc.stdout
    except Exception:
        pass

    # 4. Try wl-paste (Ubuntu Wayland)
    try:
        proc = subprocess.run(
            ["wl-paste", "-n"],
            capture_output=True,
            text=True,
            timeout=0.6,
        )
        if proc.returncode == 0:
            return proc.stdout
    except Exception:
        pass

    return None


def write_system_clipboard(text: str) -> bool:
    """Inject text into the desktop clipboard (Linux, macOS, or Windows)."""
    # 1. Try Windows (clip command)
    if sys.platform == "win32":
        try:
            proc = subprocess.Popen(["clip"], stdin=subprocess.PIPE, shell=True)
            proc.communicate(input=text.encode("utf-16le"), timeout=0.8)
            if proc.returncode == 0:
                return True
        except Exception:
            pass

    # 2. Try pbcopy (macOS native)
    try:
        proc = subprocess.Popen(["pbcopy"], stdin=subprocess.PIPE)
        proc.communicate(input=text.encode("utf-8"), timeout=0.8)
        if proc.returncode == 0:
            return True
    except Exception:
        pass

    # 3. Try xclip (Ubuntu X11)
    try:
        proc = subprocess.Popen(["xclip", "-selection", "clipboard"], stdin=subprocess.PIPE)
        proc.communicate(input=text.encode("utf-8"), timeout=0.8)
        if proc.returncode == 0:
            return True
    except Exception:
        pass

    # 4. Try wl-copy (Ubuntu Wayland)
    try:
        proc = subprocess.Popen(["wl-copy"], stdin=subprocess.PIPE)
        proc.communicate(input=text.encode("utf-8"), timeout=0.8)
        if proc.returncode == 0:
            return True
    except Exception:
        pass

    return False


@router.get("")
async def get_clipboard():
    """Retrieve the current shared clipboard text, checking host system clipboard first."""
    system_text = read_system_clipboard()
    if system_text is not None and system_text != _clipboard_state["text"]:
        _clipboard_state["text"] = system_text

    return {"text": _clipboard_state["text"]}


@router.post("")
async def set_clipboard(payload: ClipboardPayload):
    """Update shared clipboard from phone, copy to Ubuntu desktop, and broadcast update."""
    _clipboard_state["text"] = payload.text
    write_system_clipboard(payload.text)

    # Broadcast event via WebSocket
    await ws_manager.broadcast({
        "event": "clipboard_updated",
        "text": payload.text,
    })

    return {"ok": True}


async def clipboard_watcher_loop():
    """Background task: periodically check for Ubuntu desktop clipboard changes."""
    while True:
        try:
            await asyncio.sleep(1.2)
            sys_text = read_system_clipboard()
            if sys_text is not None and sys_text != _clipboard_state["text"] and sys_text.strip():
                _clipboard_state["text"] = sys_text
                await ws_manager.broadcast({
                    "event": "clipboard_updated",
                    "text": sys_text,
                })
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.debug(f"Clipboard watcher error: {e}")
            await asyncio.sleep(3)
