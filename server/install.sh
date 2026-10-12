#!/usr/bin/env bash
set -e

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
CURRENT_USER=$(whoami)
SHARED_DIR="$HOME/wifi-share/shared"
VENV_DIR="$SCRIPT_DIR/venv"

echo "=========================================================="
echo "          WiFi Share — Setup & Installation"
echo "=========================================================="
echo "Server Directory: $SCRIPT_DIR"
echo "Shared Folder:    $SHARED_DIR"
echo "Current User:     $CURRENT_USER"
echo "=========================================================="

# 1. Create shared directory
mkdir -p "$SHARED_DIR"
echo "[OK] Verified shared directory at $SHARED_DIR"

# 2. Check Python 3
if ! command -v python3 &> /dev/null; then
    echo "[ERROR] python3 is not installed. Please install Python 3.10+."
    exit 1
fi

# 3. Create start script first so it's always ready
chmod +x "$SCRIPT_DIR/start.sh"

# 4. Check & create Virtual Environment
# If a previous failed venv exists without pip, remove it
if [ -d "$VENV_DIR" ] && [ ! -f "$VENV_DIR/bin/pip" ]; then
    echo "[INFO] Cleaning up incomplete virtual environment..."
    rm -rf "$VENV_DIR"
fi

OS_TYPE=$(uname -s)

if [ ! -d "$VENV_DIR" ]; then
    echo "[INFO] Creating Python virtual environment..."
    if ! python3 -m venv "$VENV_DIR" 2>/dev/null; then
        if [ "$OS_TYPE" = "Darwin" ]; then
            echo "[ERROR] Error creating venv. Please ensure Python 3 is installed via python.org or Homebrew (brew install python)."
        else
            echo ""
            echo "[ERROR] Ubuntu is missing the 'python3-venv' package."
            echo "Please run this command in your terminal:"
            echo ""
            echo "   sudo apt install -y python3-venv python3.10-venv python3-pip"
            echo ""
        fi
        rm -rf "$VENV_DIR"
        exit 1
    fi
fi

# 5. Install Dependencies
echo "[INFO] Installing dependencies from requirements.txt..."
"$VENV_DIR/bin/pip" install --upgrade pip
"$VENV_DIR/bin/pip" install -r "$SCRIPT_DIR/requirements.txt"
echo "[OK] Dependencies installed successfully."

# 6. Service configuration (Linux systemd vs macOS launchd)
if [ "$OS_TYPE" = "Linux" ]; then
    SERVICE_SRC="$SCRIPT_DIR/wifi-share.service"
    TEMP_SERVICE="/tmp/wifi-share.service"

    sed -e "s|%USER%|$CURRENT_USER|g" \
        -e "s|%WORKDIR%|$SCRIPT_DIR|g" \
        -e "s|%VENV%|$VENV_DIR|g" \
        "$SERVICE_SRC" > "$TEMP_SERVICE"

    echo ""
    echo "[INFO] Linux systemd service ready."
    echo "To enable WiFi Share on boot, run:"
    echo "   sudo cp $TEMP_SERVICE /etc/systemd/system/wifi-share.service"
    echo "   sudo systemctl daemon-reload"
    echo "   sudo systemctl enable --now wifi-share.service"
elif [ "$OS_TYPE" = "Darwin" ]; then
    echo ""
    echo "[INFO] macOS detected: Native pbcopy/pbpaste and Bonjour mDNS are ready."
fi

echo ""
echo "To test or run manually right now:"
echo "   $SCRIPT_DIR/start.sh"
echo "=========================================================="
