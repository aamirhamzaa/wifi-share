import json
import logging
from typing import List
from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from config import PIN, PIN_ENABLED

logger = logging.getLogger(__name__)
router = APIRouter()


class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)
        logger.info(f"WebSocket client connected. Total clients: {len(self.active_connections)}")

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)
            logger.info(f"WebSocket client disconnected. Total clients: {len(self.active_connections)}")

    async def broadcast(self, message: dict):
        """Broadcast a message payload to all connected clients."""
        payload = json.dumps(message)
        dead_connections = []
        for connection in self.active_connections:
            try:
                await connection.send_text(payload)
            except Exception:
                dead_connections.append(connection)

        for dead in dead_connections:
            self.disconnect(dead)


ws_manager = ConnectionManager()


@router.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    # Enforce PIN authentication on WebSocket connection
    if PIN_ENABLED:
        provided_pin = (
            websocket.query_params.get("pin")
            or websocket.cookies.get("wifi_share_pin")
        )
        if not provided_pin or str(provided_pin).strip() != str(PIN).strip():
            await websocket.close(code=1008)  # 1008 = Policy Violation / Unauthorized
            return

    await ws_manager.connect(websocket)
    try:
        await websocket.send_json({"event": "connected", "message": "Connected to WiFi Share live stream"})
        while True:
            data = await websocket.receive_text()
            if data.strip().lower() == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        ws_manager.disconnect(websocket)
    except Exception as e:
        logger.debug(f"WebSocket connection error: {e}")
        ws_manager.disconnect(websocket)
