package com.driverdrowsiness.app

import org.json.JSONObject

data class DriverMetrics(
    val ear: Double? = null,
    val mar: Double? = null,
    val perclosPercent: Int = 0,
    val blinksPerMinute: Int = 0,
    val headPitch: Int = 0,
    val riskScore: Int = 0,
    val riskLabel: String = "No data",
    val status: String = "Waiting for camera or backend",
    val yawnCount: Int = 0,
    val alertCount: Int = 0,
    val source: String = "Not started"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", "driver_metrics")
        put("timestamp", System.currentTimeMillis())
        put("ear", ear ?: JSONObject.NULL)
        put("mar", mar ?: JSONObject.NULL)
        put("perclos_percent", perclosPercent)
        put("blinks_per_minute", blinksPerMinute)
        put("head_pitch_degrees", headPitch)
        put("risk_score", riskScore)
        put("risk_label", riskLabel)
        put("status", status)
        put("yawn_count", yawnCount)
        put("alert_count", alertCount)
        put("source", source)
    }

    companion object {
        fun fromJson(j: JSONObject): DriverMetrics = DriverMetrics(
            ear = j.optDouble("ear").takeUnless { j.isNull("ear") },
            mar = j.optDouble("mar").takeUnless { j.isNull("mar") },
            perclosPercent = j.optInt("perclos_percent"),
            blinksPerMinute = j.optInt("blinks_per_minute"),
            headPitch = j.optInt("head_pitch_degrees"),
            riskScore = j.optInt("risk_score"),
            riskLabel = j.optString("risk_label", "Unknown"),
            status = j.optString("status", "Backend update"),
            yawnCount = j.optInt("yawn_count"),
            alertCount = j.optInt("alert_count"),
            source = j.optString("source", "Python backend")
        )
    }
}
