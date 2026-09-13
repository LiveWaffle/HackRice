package com.AMMR.ricehacks.data

import com.AMMR.ricehacks.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

const val NORA_CHAT_EDGE_FUNCTION = "ask-nora-ai"

data class HealthAiAnswer(
    val answer: String,
    val flagged: Boolean = false
)

interface HealthAiRepository {
    suspend fun askQuestion(
        patientSessionToken: String,
        message: String
    ): HealthAiAnswer

    suspend fun summarizeConversation(
        patientSessionToken: String,
        transcript: String
    ): String
}

class BackendHealthAiRepository(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
) : HealthAiRepository {
    override suspend fun askQuestion(
        patientSessionToken: String,
        message: String
    ): HealthAiAnswer {
        val response = request(
            mode = "message",
            patientSessionToken = patientSessionToken,
            body = JSONObject()
                .put("message", message)
        )

        return HealthAiAnswer(
            answer = response.getString("answer"),
            flagged = response.optBoolean("flagged", false)
        )
    }

    override suspend fun summarizeConversation(
        patientSessionToken: String,
        transcript: String
    ): String {
        if (transcript.isBlank()) return "No conversation details available yet."
        val response = request(
            mode = "summary",
            patientSessionToken = patientSessionToken,
            body = JSONObject()
                .put("transcript", transcript)
        )
        return response.optString("answer").ifBlank { "Conversation summary is unavailable right now." }
    }

    private suspend fun request(
        mode: String,
        patientSessionToken: String,
        body: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val requestBody = JSONObject(body.toString())
            .put("mode", mode)
        val functionUrl = "$supabaseUrl/functions/v1/$NORA_CHAT_EDGE_FUNCTION"
        val connection = (URL(functionUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $patientSessionToken")
        }

        try {
            connection.outputStream.use { output ->
                output.write(requestBody.toString().toByteArray(Charsets.UTF_8))
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
