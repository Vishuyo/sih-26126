package com.example.model

import org.json.JSONObject

/**
 * Orientation telemetry sent to laptop compute node at 20-30Hz.
 * Format: {"timestamp": 123456789, "yaw": 12.4, "pitch": -2.1, "roll": 0.3}
 */
data class TelemetryPacket(
    val timestamp: Long = System.currentTimeMillis(),
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f
) {
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("timestamp", timestamp)
        json.put("yaw", String.format(java.util.Locale.US, "%.2f", yaw).toDouble())
        json.put("pitch", String.format(java.util.Locale.US, "%.2f", pitch).toDouble())
        json.put("roll", String.format(java.util.Locale.US, "%.2f", roll).toDouble())
        return json.toString()
    }
}
