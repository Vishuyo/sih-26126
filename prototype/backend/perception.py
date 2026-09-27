from ultralytics import YOLO
import cv2
import numpy as np
from typing import List, Dict, Any


class YOLODetector:
    def __init__(self, model_path: str = "yolo11s.pt", device: str = "0"):
        """Initialize YOLO detector with model path and device.

        Args:
            model_path: Path to YOLO model weights (default: yolo11s.pt)
            device: Device to run inference on (default: "0" for CUDA GPU)
        """
        try:
            self.model = YOLO(model_path)
            self.device = device
            print(f"Loaded YOLO model from {model_path} on device {device}")
        except Exception as e:
            print(f"Failed to load YOLO model: {e}")
            raise

    def detect(self, frame: np.ndarray) -> List[Dict[str, Any]]:
        """Run YOLO detection on a frame.

        Args:
            frame: OpenCV BGR image frame (numpy array)

        Returns:
            List of detection dictionaries with format:
            {"x": int, "y": int, "w": int, "h": int, "class": str, "confidence": float}
        """
        try:
            results = self.model(
                frame,
                imgsz=640,
                conf=0.25,
                iou=0.45,
                classes=[0, 1, 2, 3, 5, 7],  # COCO: person, bicycle, car, motorcycle, bus, truck
                augment=True,  # test-time augmentation for robustness
                device=self.device,
                verbose=False
            )
        except Exception as e:
            print(f"Inference error: {e}")
            return []

        detections = []
        for result in results:
            boxes = result.boxes
            for box in boxes:
                confidence = float(box.conf[0])

                if confidence > 0.25:
                    xyxy = box.xyxy[0].cpu().numpy()
                    x1, y1, x2, y2 = map(int, xyxy)

                    cls_id = int(box.cls[0])
                    class_name = result.names[cls_id]

                    detections.append({
                        "x": x1,
                        "y": y1,
                        "w": x2 - x1,
                        "h": y2 - y1,
                        "class": class_name,
                        "confidence": confidence
                    })

        return detections
