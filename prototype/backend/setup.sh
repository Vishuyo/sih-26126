#!/bin/bash
# Setup script for UGV prototype backend using uv

cd "$(dirname "$0")"

echo "Syncing dependencies with uv..."
uv sync

echo "Pre-downloading YOLOv11 small model..."
uv run python -c "from ultralytics import YOLO; YOLO('yolo11s.pt'); print('✓ YOLOv11-small model ready')"

echo "✓ Setup complete! Run server with: uv run python server.py"
