package com.AMMR.ricehacks.presage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class PresageVitalsRepository(
    private val supabaseUrl: String,
    private val publishableKey: String
) {
    suspend fun saveReading(
        accessToken: String,
        reading: VitalsReading
    ) = withContext(Dispatchers.IO) {
        val pulse = requireNotNull(reading.pulseRateBpm)
        val breathing = requireNotNull(reading.breathingRatePerMinute)

        val requestBody = JSONObject().apply {
            put("p_pulse_rate", pulse.value.toDouble())
            put("p_pulse_confidence", pulse.confidence.toDouble())
            put("p_breathing_rate", breathing.value.toDouble())
            put("p_breathing_confidence", breathing.confidence.toDouble())
            put(
                "p_captured_at",
                Instant.ofEpochMilli(reading.capturedAtMillis).toString()
            )
            put("p_validation_code", reading.validationCode)
        }

        val connection = (
            URL("$supabaseUrl/rest/v1/rpc/save_presage_vitals")
                .openConnection() as HttpURLConnection
            ).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.bufferedWriter().use {
                it.write(requestBody.toString())
            }

            if (connection.responseCode !in 200..299) {
                val error = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                throw IllegalStateException(
                    error.ifBlank { "Could not save the Presage reading." }
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}