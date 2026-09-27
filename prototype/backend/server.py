import asyncio
import websockets
import cv2
import numpy as np
import json
import time
import signal
import sys
from perception import YOLODetector
from planner import compute_navigation

# Initialize detector on startup
try:
    detector = YOLODetector()
    print("YOLO detector initialized successfully")
except Exception as e:
    print(f"Failed to initialize YOLO detector: {e}")
    sys.exit(1)

async def handle_client(websocket):
    """Handle WebSocket client connections."""
    client_addr = websocket.remote_address
    print(f"Client connected: {client_addr}")

    try:
        async for message in websocket:
            start_time = time.time()

            if isinstance(message, bytes):
                # Decode JPEG frame
                np_arr = np.frombuffer(message, np.uint8)
                frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

                if frame is None:
                    print("Failed to decode frame")
                    continue

                height, width = frame.shape[:2]

                # Run detection
                boxes = detector.detect(frame)

                # Convert boxes to normalized format expected by Android
                # Android expects: {x, y, width, height, label, confidence} in normalized coordinates [0..1]
                normalized_boxes = []
                for box in boxes:
                    normalized_boxes.append({
                        'x': box['x'] / width,
                        'y': box['y'] / height,
                        'width': box['w'] / width,
                        'height': box['h'] / height,
                        'label': box['class'],
                        'confidence': box['confidence']
                    })

                # Run planning
                nav = compute_navigation(boxes, width, height)

                # Build response matching Android NavigationData expectations
                # Use field names that Android's fromJson() expects
                response = {
                    'bounding_boxes': normalized_boxes,
                    'trajectory': [{'x': p[0], 'y': p[1]} for p in nav['trajectory']],
                    'velocity': {
                        'v': nav['v'],
                        'omega': nav['omega']
                    },
                    'timestamp': int(time.time() * 1000)
                }

                # Send response
                await websocket.send(json.dumps(response))

                # Print processing time
                elapsed_ms = (time.time() - start_time) * 1000
                print(f"Processed frame in {elapsed_ms:.2f}ms | {len(normalized_boxes)} detections | v={nav['v']:.2f} ω={nav['omega']:.2f}")

            elif isinstance(message, str):
                # Parse telemetry JSON
                try:
                    telemetry = json.loads(message)
                    print(f"Telemetry: {telemetry}")
                except json.JSONDecodeError as e:
                    print(f"Failed to parse telemetry JSON: {e}")

    except websockets.exceptions.ConnectionClosed:
        print(f"Client disconnected: {client_addr}")
    except Exception as e:
        print(f"Error handling client {client_addr}: {e}")
    finally:
        print(f"Connection closed: {client_addr}")

async def main():
    """Start WebSocket server with graceful shutdown."""
    # Set up signal handlers
    loop = asyncio.get_running_loop()

    def signal_handler():
        print("\nShutting down server...")
        for task in asyncio.all_tasks(loop):
            task.cancel()

    for sig in (signal.SIGTERM, signal.SIGINT):
        try:
            loop.add_signal_handler(sig, signal_handler)
        except NotImplementedError:
            # Windows doesn't support add_signal_handler
            pass

    server = await websockets.serve(handle_client, "0.0.0.0", 8080)
    print("WebSocket server started on ws://0.0.0.0:8080")
    print("Ready for Android UGV Edge Node connections")

    try:
        await asyncio.Future()  # Run forever
    except asyncio.CancelledError:
        print("Server shutdown initiated")
    finally:
        server.close()
        await server.wait_closed()
        print("Server stopped")

if __name__ == "__main__":
    asyncio.run(main())
