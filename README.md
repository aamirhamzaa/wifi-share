# WiFi Share

A high-performance, local-network file transfer and clipboard synchronization system designed for cross-device sharing between Linux (Ubuntu), macOS, and Android.

---

## Features

- **Local Network Transfers**: Fully offline and local. Zero cloud dependencies, zero external tracking, maximum Wi-Fi bandwidth utilization.
- **Asynchronous Chunk Streaming**: Non-blocking 1 MB streaming chunks (`aiofiles`) maintain low memory consumption even during multi-gigabyte transfers.
- **Resumable Downloads**: Full support for HTTP `Range` requests allows downloads to resume seamlessly if interrupted.
- **Interactive Web Interface**: Obsidian and Dark Emerald theme with 100% vector SVG icons, zero gradients, and zero emojis.
  - Three customizable layout options: Single-column feed, multi-column grid, and dense list.
  - In-browser lightbox for instant preview of images, videos, audio, and documents.
  - Multi-file batch management: Select all, batch delete, and single-click ZIP archive downloads.
  - System storage telemetry gauge and filter chips by media category.
- **Bidirectional Clipboard Synchronization**: Real-time clipboard sharing supporting Linux (`xclip`, `wl-paste`), macOS (`pbcopy`, `pbpaste`), and Android.
- **Access Control & Security**:
  - Mandatory session PIN gate (`2310`) protecting all API routes, downloads, previews, and WebSockets.
  - In-memory rate limiting and brute-force lockout (5 failed attempts trigger a 60-second cooldown).
  - Explicit per-session verification with instant lock-out capability.
- **Automatic Service Discovery**: Built-in Zeroconf/mDNS advertising (`_wifishare._tcp.local.`) and terminal ASCII QR code generation.
- **Native Android Client**: Jetpack Compose (Material 3) architecture with background WorkManager uploads and Network Service Discovery (NSD).

---

## Architecture Overview

```
                      +-----------------------------+
                      |   WiFi Share Server (Host)  |
                      |   FastAPI / Uvicorn (8080)  |
                      +--------------+--------------+
                                     |
           +-------------------------+-------------------------+
           |                                                   |
           v                                                   v
+-------------------------+                         +-------------------------+
|     Web Browser UI      |                         |   Native Android App    |
| (Mobile / Desktop Client)                         |    (Jetpack Compose)    |
|                         |                         |                         |
| - Layout Modes          |                         | - Network Discovery     |
| - Lightbox Previews     |                         | - WorkManager Transfers |
| - Batch Actions (ZIP)   |                         | - Background Service    |
| - Bi-directional Sync   |                         | - Native Notifications  |
+-------------------------+                         +-------------------------+
```

---

## Quick Start

### 1. Server Installation (Ubuntu / Linux & macOS)

Clone the repository and run the automated installer:

```bash
cd server
chmod +x install.sh start.sh
./install.sh
```

The script configures a dedicated Python virtual environment and installs all dependencies.

### 2. Start the Server

```bash
./start.sh
```

Or manually:

```bash
source venv/bin/activate
python3 main.py
```

Upon launch, the server outputs:
- The local network IP addresses.
- An ASCII QR code for camera scanning on mobile devices.
- Local mDNS advertisement status.

---

## Connecting Devices

### Option A: Web Browser (Zero Installation)
1. Ensure the client device is connected to the same Wi-Fi network as the host.
2. Scan the terminal QR code or navigate to:
   ```
   http://<host-ip>:8080
   ```
3. Enter security PIN `2310` to unlock the session.
4. Upload files via drag-and-drop or file picker, stream media directly, and sync clipboard text.

### Option B: Native Android Client
1. Open the `android/` directory in Android Studio.
2. Build and deploy to your Android device (Android 8.0+ / API 26+).
3. The application automatically discovers the host via Network Service Discovery (NSD) and enables background transfers with persistent system notifications.

---

## Background Service Setup (Linux)

To run the server as a background service on Linux using `systemd`:

```bash
# Copy and enable the service
sudo cp /tmp/wifi-share.service /etc/systemd/system/wifi-share.service
sudo systemctl daemon-reload
sudo systemctl enable --now wifi-share.service
```

To manage the service:

```bash
sudo systemctl status wifi-share.service
journalctl -u wifi-share.service -f
```

---

## Configuration Reference

Settings can be customized via environment variables or modified directly in `server/config.py`:

| Variable | Default | Description |
|---|---|---|
| `HOST` | `0.0.0.0` | Network interface to bind |
| `PORT` | `8080` | Port for HTTP and WebSocket traffic |
| `SHARE_DIR` | `~/wifi-share/shared` | Storage directory for shared files |
| `PIN_ENABLED` | `True` | Enforce PIN protection on all routes |
| `PIN` | `2310` | 4-digit authentication code |
| `CHUNK_SIZE` | `1048576` (1 MB) | Chunk size for non-blocking file streaming |
| `MAX_UPLOAD_SIZE`| `10737418240` (10 GB) | Maximum single file upload limit |

---

## Shared Storage Location

Files transferred to the host are stored at:

- **Linux**: `/home/<user>/wifi-share/shared/`
- **macOS**: `/Users/<user>/wifi-share/shared/`

Files placed directly into this folder on the host are immediately indexed and available for client download.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
