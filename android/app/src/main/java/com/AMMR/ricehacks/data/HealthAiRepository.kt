package com.AMMR.ricehacks.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class HealthAiAnswer(
    val answer: String
)

enum class AiAgent(val displayName: String, val iconLabel: String) {
    Cara("Cara", "Helper"),
    DrStat("Dr. Stat", "Medical Stats"),
    Routine("Routine", "Daily Plan")
}

interface HealthAiRepository {
    suspend fun askQuestion(
        patientSessionToken: String,
        message: String,
        agent: AiAgent = AiAgent.Cara
    ): HealthAiAnswer
}

class BackendHealthAiRepository(
    private val backendBaseUrl: String
) : HealthAiRepository {
    override suspend fun askQuestion(
        patientSessionToken: String,
        message: String,
        agent: AiAgent
    ): HealthAiAnswer {
        val response = request(
            path = "/api/patient/ai/message",
            patientSessionToken = patientSessionToken,
            body = JSONObject()
                .put("message", message)
                .put("agent", agent.name)
        )

        return HealthAiAnswer(answer = response.getString("answer"))
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

class DirectGeminiHealthAiRepository(
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash"
) : HealthAiRepository {
    override suspend fun askQuestion(
        patientSessionToken: String,
        message: String,
        agent: AiAgent
    ): HealthAiAnswer {
        Log.d("CaraDebug", "Gemini API request for agent: ${agent.displayName}")
        if (apiKey.isBlank()) {
            Log.e("CaraDebug", "Gemini API key is blank. Request failed.")
            throw IllegalStateException("Add GEMINI_API_KEY to local.properties, then rebuild the app.")
        }

        val response = request(
            body = buildGeminiRequest(message = message, agent = agent)
        )

        Log.d("CaraDebug", "Gemini API response received for agent: ${agent.displayName}")
        return HealthAiAnswer(answer = readGeminiAnswer(response))
    }

    private fun buildGeminiRequest(message: String, agent: AiAgent): JSONObject {
        Log.d("CaraDebug", "Building Gemini request for agent: ${agent.name}")
        val systemPrompt = when (agent) {
            AiAgent.Cara -> "You are Cara's patient helper for an elderly patient. Use plain language, short paragraphs, and the provided record context. Do not diagnose or prescribe. Tell the patient to ask their doctor for medical decisions."
            AiAgent.DrStat -> "You are Dr. Stat, a medical data and statistics expert. Analyze the patient's record context and provide insights into their health trends and medical statistics (e.g. blood pressure averages, glucose levels). Be technical but clear. Always include a disclaimer that you are an AI and not a doctor."
            AiAgent.Routine -> "You are Routine, a daily health and medication schedule helper. Help the patient organize their day based on their medications and conditions. Focus on 'when' and 'how' to take medicines and manage daily activities. Keep it organized and encouraging."
        }

        val systemInstruction = JSONObject()
            .put(
                "parts",
                JSONArray().put(
                    JSONObject().put("text", systemPrompt)
                )
            )

        val patientContext = """
            Patient: Margaret Chen
            Date of birth: June 14, 1943
            Blood type: O+
            Notes: Uses a walker. Mild hearing loss.
            Conditions: Type 2 Diabetes Mellitus, Hypertension, Osteoarthritis in both knees.
            Medications: Metformin 500mg twice daily, Lisinopril 10mg once daily, Acetaminophen 500mg as needed.
            Allergy: Penicillin, severe reaction with hives and difficulty breathing.
            Recent vitals: blood pressure 138/84 mmHg, fasting blood glucose 142 mg/dL.
        """.trimIndent()

        val userText = "Patient record context:\n$patientContext\n\nPatient question:\n$message"
        val contents = org.json.JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put(
                    "parts",
                    org.json.JSONArray().put(JSONObject().put("text", userText))
                )
        )

        return JSONObject()
            .put("systemInstruction", systemInstruction)
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("maxOutputTokens", 450)
            )
    }

    private suspend fun request(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val connection = (
            URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .openConnection() as HttpURLConnection
            ).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
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
                val error = readGeminiError(response)
                Log.e("CaraDebug", "Gemini API error: $error")
                throw IllegalStateException(error)
            }

            JSONObject(response)
        } catch (e: Exception) {
            Log.e("CaraDebug", "Gemini network/request exception: ${e.message}")
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun readGeminiAnswer(response: JSONObject): String {
        val candidates = response.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val parts = firstCandidate
            ?.optJSONObject("content")
            ?.optJSONArray("parts")

        if (parts == null || parts.length() == 0) {
            return "I could not create an answer right now. Please try again."
        }

        return (0 until parts.length())
            .joinToString("\n") { index -> parts.optJSONObject(index)?.optString("text").orEmpty() }
            .trim()
            .ifBlank { "I could not create an answer right now. Please try again." }
    }

    private fun readGeminiError(response: String): String {
        return runCatching {
            val json = JSONObject(response)
            json.optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty().ifBlank {
            "Gemini could not answer right now. Check your API key and try again."
        }
    }
}
