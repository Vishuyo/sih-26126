"""
Reactive obstacle avoidance planner for navigation.
Divides the bottom half of the frame into 3 zones and computes navigation commands.
"""

from typing import List, Dict, Any

# Temporal smoothing state
prev_omega = 0.0


def compute_navigation(boxes: List[Any], frame_width: int, frame_height: int) -> Dict[str, Any]:
    """
    Compute navigation commands based on detected obstacles.

    Args:
        boxes: List of detections. Accepts two formats:
            1. Dict from YOLODetector: {"x": int, "y": int, "w": int, "h": int, "class": str, "confidence": float}
            2. List from tests: [x1, y1, x2, y2, conf, cls]
        frame_width: Width of the frame in pixels
        frame_height: Height of the frame in pixels

    Returns:
        dict: {
            'v': linear velocity (m/s),
            'omega': angular velocity (rad/s),
            'trajectory': list of [x, y] trajectory points (normalized 0..1)
        }
    """
    # Zone configuration
    half_height = frame_height // 2
    left_boundary = frame_width // 3
    right_boundary = 2 * frame_width // 3

    # Initialize zones as clear
    left_clear = True
    center_clear = True
    right_clear = True

    # Check each detection box
    for box in boxes:
        # Support both dict (from YOLODetector) and list (from tests) formats
        if isinstance(box, dict):
            x = box.get("x", 0)
            y = box.get("y", 0)
            w = box.get("w", 0)
            h = box.get("h", 0)
        elif isinstance(box, (list, tuple)) and len(box) >= 4:
            x1, y1, x2, y2 = box[:4]
            x, y, w, h = x1, y1, x2 - x1, y2 - y1
        else:
            continue

        # Calculate center point
        cx = x + w / 2
        cy = y + h / 2

        # Only consider obstacles in the bottom half
        if cy > half_height:
            # Determine which zone the obstacle is in
            if cx < left_boundary:
                left_clear = False
            elif cx < right_boundary:
                center_clear = False
            else:
                right_clear = False

    # Determine navigation command based on zone status
    if center_clear:
        v = 0.5
        omega = 0.0
    elif left_clear:
        v = 0.3
        omega = 0.3
    elif right_clear:
        v = 0.3
        omega = -0.3
    else:
        v = 0.0
        omega = 0.0

    # Temporal smoothing (low-pass filter)
    global prev_omega
    omega = omega * 0.7 + prev_omega * 0.3
    prev_omega = omega

    # Generate trajectory (5 points ahead, normalized coordinates)
    # Each point is [x, y] where x is lateral offset, y is forward distance
    trajectory = [[i * 0.1, omega * i * 0.1] for i in range(5)]

    return {
        'v': v,
        'omega': omega,
        'trajectory': trajectory
    }


def test_planner():
    """Simple test function to verify the planner works."""
    global prev_omega
    print("Testing reactive obstacle avoidance planner...")

    # Test case 1: No obstacles
    prev_omega = 0.0
    boxes = []
    result = compute_navigation(boxes, 640, 480)
    print(f"\nTest 1 - No obstacles:")
    print(f"  v={result['v']}, omega={result['omega']}")
    print(f"  Expected: v=0.5, omega=0.0 (go straight)")
    assert result['v'] == 0.5 and result['omega'] == 0.0

    # Test case 2: Obstacle in center zone (bottom half)
    prev_omega = 0.0
    boxes = [[280, 300, 360, 380, 0.9, 0]]  # center of frame
    result = compute_navigation(boxes, 640, 480)
    print(f"\nTest 2 - Obstacle in center:")
    print(f"  v={result['v']}, omega={result['omega']}")
    # With temporal smoothing: omega = 0.3 * 0.7 + 0.0 * 0.3 = 0.21
    expected_omega = 0.21
    print(f"  Expected: v=0.3, omega={expected_omega} (turn left with smoothing)")
    assert result['v'] == 0.3 and abs(result['omega'] - expected_omega) < 0.001

    # Test case 3: Obstacles in center and left zones
    prev_omega = 0.0
    boxes = [
        [280, 300, 360, 380, 0.9, 0],  # center
        [50, 300, 150, 380, 0.8, 0]     # left
    ]
    result = compute_navigation(boxes, 640, 480)
    print(f"\nTest 3 - Obstacles in center and left:")
    print(f"  v={result['v']}, omega={result['omega']}")
    # With temporal smoothing: omega = -0.3 * 0.7 + 0.0 * 0.3 = -0.21
    expected_omega = -0.21
    print(f"  Expected: v=0.3, omega={expected_omega} (turn right with smoothing)")
    assert result['v'] == 0.3 and abs(result['omega'] - expected_omega) < 0.001

    # Test case 4: All zones blocked
    prev_omega = 0.0
    boxes = [
        [50, 300, 150, 380, 0.9, 0],   # left
        [280, 300, 360, 380, 0.9, 0],  # center
        [500, 300, 600, 380, 0.9, 0]   # right
    ]
    result = compute_navigation(boxes, 640, 480)
    print(f"\nTest 4 - All zones blocked:")
    print(f"  v={result['v']}, omega={result['omega']}")
    print(f"  Expected: v=0.0, omega=0.0 (stop)")
    assert result['v'] == 0.0 and result['omega'] == 0.0

    # Test case 5: Obstacle in top half (should be ignored)
    prev_omega = 0.0
    boxes = [[280, 100, 360, 180, 0.9, 0]]  # top half
    result = compute_navigation(boxes, 640, 480)
    print(f"\nTest 5 - Obstacle in top half (ignored):")
    print(f"  v={result['v']}, omega={result['omega']}")
    print(f"  Expected: v=0.5, omega=0.0 (go straight)")
    assert result['v'] == 0.5 and result['omega'] == 0.0

    # Verify trajectory generation
    print(f"\nTest 6 - Trajectory generation:")
    result = compute_navigation([], 640, 480)
    print(f"  Trajectory points: {len(result['trajectory'])}")
    print(f"  Sample trajectory: {result['trajectory']}")
    assert len(result['trajectory']) == 5

    print("\n✓ All tests passed!")


if __name__ == "__main__":
    test_planner()
