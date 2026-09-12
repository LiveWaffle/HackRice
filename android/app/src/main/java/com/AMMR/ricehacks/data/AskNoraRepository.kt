package com.AMMR.ricehacks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AskNoraSession(
    val id: String,
    val initialMode: AskNoraMode,
    val createdAt: String,
    val endedAt: String? = null
)

data class AskNoraTurn(
    val speaker: AskNoraSpeaker,
    val text: String,
    val createdAt: String? = null
)

enum class AskNoraMode { Text, Voice }
enum class AskNoraSpeaker { User, Assistant }

interface AskNoraRepository {
    suspend fun createSession(accessToken: String, initialMode: AskNoraMode): AskNoraSession
    suspend fun appendTurn(accessToken: String, sessionId: String, turn: AskNoraTurn)
    suspend fun endSession(accessToken: String, sessionId: String)
    suspend fun fetchSessions(accessToken: String): List<AskNoraSession>
    suspend fun fetchTurns(accessToken: String, sessionId: String): List<AskNoraTurn>
}

class SupabaseAskNoraRepository(
    private val supabaseUrl: String,
    private val publishableKey: String,
) : AskNoraRepository {
    override suspend fun createSession(accessToken: String, initialMode: AskNoraMode): AskNoraSession {
        val result = request("/rest/v1/nora_sessions", "POST", accessToken, JSONObject()
            .put("mode", initialMode.name.lowercase()), returnRepresentation = true)
        val row = JSONArray(result).getJSONObject(0)
        return AskNoraSession(
            id = row.getString("id"),
            initialMode = initialMode,
            createdAt = row.getString("created_at")
        )
    }

    override suspend fun appendTurn(accessToken: String, sessionId: String, turn: AskNoraTurn) {
        request("/rest/v1/nora_turns", "POST", accessToken, JSONObject()
            .put("session_id", sessionId)
            .put("speaker", turn.speaker.name.lowercase())
            .put("text", turn.text), returnRepresentation = false)
    }

    override suspend fun endSession(accessToken: String, sessionId: String) {
        request("/rest/v1/nora_sessions?id=eq.$sessionId", "PATCH", accessToken,
            JSONObject().put("ended_at", "now()"), returnRepresentation = false)
    }

    override suspend fun fetchSessions(accessToken: String): List<AskNoraSession> {
        val result = request("/rest/v1/nora_sessions?select=*&order=created_at.desc", "GET", accessToken, null, returnRepresentation = true)
        val array = JSONArray(result)
        return List(array.length()) { i ->
            val row = array.getJSONObject(i)
            AskNoraSession(
                id = row.getString("id"),
                initialMode = if (row.getString("mode") == "voice") AskNoraMode.Voice else AskNoraMode.Text,
                createdAt = row.getString("created_at"),
                endedAt = row.optString("ended_at").takeIf { it.isNotBlank() }
            )
        }
    }

    override suspend fun fetchTurns(accessToken: String, sessionId: String): List<AskNoraTurn> {
        val result = request("/rest/v1/nora_turns?session_id=eq.$sessionId&order=created_at.asc", "GET", accessToken, null, returnRepresentation = true)
        val array = JSONArray(result)
        return List(array.length()) { i ->
            val row = array.getJSONObject(i)
            AskNoraTurn(
                speaker = if (row.getString("speaker") == "assistant") AskNoraSpeaker.Assistant else AskNoraSpeaker.User,
                text = row.getString("text"),
                createdAt = row.getString("created_at")
            )
        }
    }

    private suspend fun request(path: String, method: String, token: String, body: JSONObject?, returnRepresentation: Boolean): String = withContext(Dispatchers.IO) {
        val connection = (URL("$supabaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = 20_000; doOutput = body != null
            setRequestProperty("apikey", publishableKey); setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", "application/json")
            if (method != "GET") {
                setRequestProperty("Prefer", if (returnRepresentation) "return=representation" else "return=minimal")
            }
        }
        try {
            body?.let { connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (connection.responseCode !in 200..299) throw IllegalStateException("Ask Nora data error: $response")
            response
        } finally { connection.disconnect() }
    }
}
