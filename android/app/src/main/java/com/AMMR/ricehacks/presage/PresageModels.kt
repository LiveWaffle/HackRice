package com.AMMR.ricehacks.presage

data class VitalMetric(
    val value: Float,
    val confidence: Float,
    val stable: Boolean
)

data class VitalsReading(
    val capturedAtMillis: Long,
    val pulseRateBpm: VitalMetric?,
    val breathingRatePerMinute: VitalMetric?,
    val validationCode: String,
    val source: String = "presage"
)