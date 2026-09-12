package com.AMMR.ricehacks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class HealthAiAnswer(
    val answer: String,
    val flagged: Boolean = false
)

interface HealthAiRepository {
    suspend fun askQuestion(
        patientSessionToken: String,
        message: String
    ): HealthAiAnswer
}

class BackendHealthAiRepository(
    private val backendBaseUrl: String
) : HealthAiRepository {
    override suspend fun askQuestion(
        patientSessionToken: String,
        message: String
    ): HealthAiAnswer {
        val response = request(
            path = "/api/patient/ai/message",
            patientSessionToken = patientSessionToken,
            body = JSONObject()
                .put("message", message)
        )

        return HealthAiAnswer(
            answer = response.getString("answer"),
            flagged = response.optBoolean("flagged", false)
        )
    }

    private suspend fun request(
        path: String,
        patientSessionToken: String,
        body: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("$backendBaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Patient-Session", patientSessionToken)
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(readBackendError(response))
            }

            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBackendError(response: String): String {
        return runCatching {
            JSONObject(response).optString("error")
        }.getOrNull().orEmpty().ifBlank {
            "The AI helper is not ready yet."
        }
    }
}
