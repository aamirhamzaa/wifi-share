import os
from pathlib import Path

# Server network settings
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", 8080))

# Shared directory storage
SHARE_DIR = Path(os.getenv("SHARE_DIR", Path.home() / "wifi-share" / "shared")).expanduser().resolve()
SHARE_DIR.mkdir(parents=True, exist_ok=True)

# Performance & streaming constants
MAX_UPLOAD_SIZE = int(os.getenv("MAX_UPLOAD_SIZE", 10 * 1024 * 1024 * 1024))  # 10 GB limit
CHUNK_SIZE = int(os.getenv("CHUNK_SIZE", 1_048_576))  # 1MB chunk size for non-blocking stream

# Mandatory PIN authentication locked to 2310
PIN_ENABLED = os.getenv("PIN_ENABLED", "true").lower() in ("true", "1", "yes")
PIN = os.getenv("PIN", "2310")

# mDNS discovery
MDNS_SERVICE_TYPE = "_wifishare._tcp.local."
MDNS_SERVICE_NAME = os.getenv("MDNS_SERVICE_NAME", "WiFiShare")
