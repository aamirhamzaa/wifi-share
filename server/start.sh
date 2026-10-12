#!/usr/bin/env bash
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$SCRIPT_DIR"

if [ -f "$SCRIPT_DIR/venv/bin/python" ]; then
    echo "[INFO] Running with virtual environment..."
    exec "$SCRIPT_DIR/venv/bin/python" main.py "$@"
else
    echo "[INFO] Running with system python3..."
    exec python3 main.py "$@"
fi
