package com.AMMR.ricehacks.presage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class SavedPresageVital(
    val type: String,
    val valueNumeric: Double?,
    val unit: String?,
    val source: String?,
    val observedAt: String?
)

class PresageVitalsRepository(
    private val supabaseUrl: String,
    private val publishableKey: String
) {
    suspend fun saveReading(
        accessToken: String,
        reading: VitalsReading
    ) {
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

        requestRpc(
            path = "/rest/v1/rpc/save_presage_vitals",
            accessToken = accessToken,
            requestBody = requestBody
        )
    }

    suspend fun getRecentVitals(
        accessToken: String
    ): List<SavedPresageVital> {
        return runCatching {
            val response = requestRpc(
                path = "/rest/v1/rpc/get_my_presage_vitals",
                accessToken = accessToken,
                requestBody = JSONObject()
            )
            response.toSavedPresageVitals()
        }.getOrElse {
            getRecentVitalsFromTables(accessToken)
        }
    }

    private suspend fun getRecentVitalsFromTables(
        accessToken: String
    ): List<SavedPresageVital> {
        val profileResponse = requestRest(
            path = "/rest/v1/patient_profiles?select=patient_record_id&limit=1",
            accessToken = accessToken
        )
        val patientRecordId = JSONArray(profileResponse)
            .optJSONObject(0)
            ?.optNullableString("patient_record_id")
            ?: return emptyList()

        val observationsResponse = requestRest(
            path = "/rest/v1/health_observations?select=observation_type,value_numeric,unit,source,recorded_at&patient_record_id=eq.$patientRecordId&order=recorded_at.desc&limit=50",
            accessToken = accessToken
        )

        return observationsResponse.toSavedPresageVitals()
    }

    private fun String.toSavedPresageVitals(): List<SavedPresageVital> {
        val array = JSONArray(this)

        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { json ->
                SavedPresageVital(
                    type = json.optString("observation_type"),
                    valueNumeric = if (json.isNull("value_numeric")) {
                        null
                    } else {
                        json.optDouble("value_numeric")
                    },
                    unit = json.optNullableString("unit"),
                    source = json.optNullableString("source"),
                    observedAt = json.optNullableString("recorded_at")
                        ?: json.optNullableString("observed_at")
                )
            }
        }
    }

    private suspend fun requestRpc(
        path: String,
        accessToken: String,
        requestBody: JSONObject
    ): String = withContext(Dispatchers.IO) {
        val connection = (
            URL("$supabaseUrl$path").openConnection() as HttpURLConnection
            ).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.bufferedWriter().use {
                it.write(requestBody.toString())
            }

            val responseStream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val response = responseStream
                .bufferedReader()
                .use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    response.ifBlank { "Supabase request failed." }
                )
            }

            response
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestRest(
        path: String,
        accessToken: String
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL("$supabaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val responseStream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val response = responseStream
                .bufferedReader()
                .use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    response.ifBlank { "Supabase vitals request failed." }
                )
            }

            response
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONObject.optNullableString(name: String): String? {
    return if (isNull(name)) {
        null
    } else {
        optString(name).ifBlank { null }
    }
}
