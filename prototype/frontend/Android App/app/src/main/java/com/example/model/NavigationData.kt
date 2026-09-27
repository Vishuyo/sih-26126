package com.example.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounding box for obstacles detected by the laptop vision model.
 * Coordinates are normalized (0.0 to 1.0) relative to frame width and height.
 */
data class BoundingBox(
    val x: Float,          // Top-left x [0..1]
    val y: Float,          // Top-left y [0..1]
    val width: Float,      // Width [0..1]
    val height: Float,     // Height [0..1]
    val label: String = "Obstacle",
    val confidence: Float = 0.95f
)

/**
 * 2D Trajectory waypoint [0..1] normalized coordinate.
 */
data class TrajectoryPoint(
    val x: Float,
    val y: Float
)

/**
 * Commanded linear velocity (v in m/s) and angular steering velocity (omega in rad/s).
 */
data class VelocityCommand(
    val v: Float = 0f,
    val omega: Float = 0f
)

/**
 * Full downstream navigation packet received from laptop backend.
 */
data class NavigationData(
    val boundingBoxes: List<BoundingBox> = emptyList(),
    val trajectory: List<TrajectoryPoint> = emptyList(),
    val velocity: VelocityCommand = VelocityCommand(),
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJson(jsonStr: String): NavigationData {
            return try {
                val json = JSONObject(jsonStr)

                // 1. Parse Bounding Boxes
                val boxes = mutableListOf<BoundingBox>()
                val boxesArray = json.optJSONArray("bounding_boxes")
                    ?: json.optJSONArray("boxes")
                    ?: json.optJSONArray("detections")

                if (boxesArray != null) {
                    for (i in 0 until boxesArray.length()) {
                        val item = boxesArray.get(i)
                        if (item is JSONObject) {
                            val x = item.optDouble("x", 0.0).toFloat()
                            val y = item.optDouble("y", 0.0).toFloat()
                            val w = item.optDouble("w", item.optDouble("width", 0.2)).toFloat()
                            val h = item.optDouble("h", item.optDouble("height", 0.2)).toFloat()
                            val label = item.optString("label", "Obstacle")
                            val conf = item.optDouble("confidence", item.optDouble("conf", 0.9)).toFloat()
                            boxes.add(BoundingBox(x, y, w, h, label, conf))
                        } else if (item is JSONArray && item.length() >= 4) {
                            // [x, y, w, h] format
                            val x = item.optDouble(0, 0.0).toFloat()
                            val y = item.optDouble(1, 0.0).toFloat()
                            val w = item.optDouble(2, 0.2).toFloat()
                            val h = item.optDouble(3, 0.2).toFloat()
                            boxes.add(BoundingBox(x, y, w, h))
                        }
                    }
                }

                // 2. Parse Trajectory Points
                val trajList = mutableListOf<TrajectoryPoint>()
                val trajArray = json.optJSONArray("trajectory")
                    ?: json.optJSONArray("path")
                    ?: json.optJSONArray("points")

                if (trajArray != null) {
                    for (i in 0 until trajArray.length()) {
                        val item = trajArray.get(i)
                        if (item is JSONObject) {
                            val px = item.optDouble("x", 0.5).toFloat()
                            val py = item.optDouble("y", 0.5).toFloat()
                            trajList.add(TrajectoryPoint(px, py))
                        } else if (item is JSONArray && item.length() >= 2) {
                            val px = item.optDouble(0, 0.5).toFloat()
                            val py = item.optDouble(1, 0.5).toFloat()
                            trajList.add(TrajectoryPoint(px, py))
                        }
                    }
                }

                // 3. Parse Velocity Commands
                var v = 0f
                var omega = 0f
                if (json.has("velocity")) {
                    val velObj = json.optJSONObject("velocity")
                    if (velObj != null) {
                        v = velObj.optDouble("v", velObj.optDouble("linear", 0.0)).toFloat()
                        omega = velObj.optDouble("omega", velObj.optDouble("angular", 0.0)).toFloat()
                    }
                } else {
                    v = json.optDouble("v", json.optDouble("linear_velocity", 0.0)).toFloat()
                    omega = json.optDouble("omega", json.optDouble("angular_velocity", 0.0)).toFloat()
                }

                NavigationData(
                    boundingBoxes = boxes,
                    trajectory = trajList,
                    velocity = VelocityCommand(v, omega),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                NavigationData()
            }
        }
    }
}
