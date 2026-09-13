package com.AMMR.ricehacks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class AskNoraSession(
    val id: String,
    val initialMode: AskNoraMode,
    val createdAt: String,
    val title: String,
    val lastMessage: String? = null,
    val endedAt: String? = null,
    val conversationId: String? = null,
    val summary: String? = null
)

data class AskNoraTurn(
    val speaker: AskNoraSpeaker,
    val text: String,
    val createdAt: String? = null
)

data class IntakeDraft(
    val sessionId: String? = null,
    val intakeType: String,
    val rawStatement: String,
    val structured: Map<String, Any> = emptyMap(),
    val confidence: String,
    val needsReview: Boolean = true,
    val source: String,
    val patientId: String,
    val timestamp: String,
    val language: String,
) {
    fun toJsonObject(): JSONObject = JSONObject()
        .put("session_id", sessionId)
        .put("intake_type", intakeType)
        .put("raw_statement", rawStatement)
        .put("structured", JSONObject(structured))
        .put("confidence", confidence)
        .put("needs_review", needsReview)
        .put("source", source)
        .put("patient_id", patientId)
        .put("timestamp", timestamp)
        .put("language", language)
}

enum class AskNoraMode { Text, Voice }
enum class AskNoraSpeaker { User, Assistant }

interface AskNoraRepository {
    suspend fun createSession(accessToken: String, initialMode: AskNoraMode): AskNoraSession
    suspend fun appendTurn(accessToken: String, sessionId: String, turn: AskNoraTurn)
    suspend fun updateSessionTitle(accessToken: String, sessionId: String, title: String)
    suspend fun updateSessionConversationId(accessToken: String, sessionId: String, conversationId: String)
    suspend fun updateSessionSummary(accessToken: String, sessionId: String, summary: String)
    suspend fun endSession(accessToken: String, sessionId: String)
    suspend fun fetchSessions(accessToken: String): List<AskNoraSession>
    suspend fun fetchTurns(accessToken: String, sessionId: String): List<AskNoraTurn>
    suspend fun submitIntakeDraft(accessToken: String, sessionId: String, draft: IntakeDraft)
}

class SupabaseAskNoraRepository(
    private val supabaseUrl: String,
    private val publishableKey: String,
) : AskNoraRepository {
    override suspend fun createSession(accessToken: String, initialMode: AskNoraMode): AskNoraSession {
        val defaultTitle = if (initialMode == AskNoraMode.Voice) "New voice call" else "New chat"
        val result = request("/rest/v1/ask_nora_sessions", "POST", accessToken, JSONObject()
            .put("initial_mode", initialMode.name.lowercase())
            .put("title", defaultTitle), returnRepresentation = true)
        val row = JSONArray(result).getJSONObject(0)
        return AskNoraSession(
            id = row.getString("id"),
            initialMode = initialMode,
            createdAt = row.getString("created_at"),
            title = row.optString("title").ifBlank { defaultTitle }
        )
    }

    override suspend fun appendTurn(accessToken: String, sessionId: String, turn: AskNoraTurn) {
        val nextTurnIndex = fetchTurnCount(accessToken, sessionId)
        request("/rest/v1/ask_nora_turns", "POST", accessToken, JSONObject()
            .put("session_id", sessionId)
            .put("turn_index", nextTurnIndex)
            .put("speaker", turn.speaker.name.lowercase())
            .put("modality", "text")
            .put("text", turn.text), returnRepresentation = false)
    }

    override suspend fun updateSessionTitle(accessToken: String, sessionId: String, title: String) {
        request(
            "/rest/v1/ask_nora_sessions?id=eq.$sessionId",
            "PATCH",
            accessToken,
            JSONObject().put("title", title),
            returnRepresentation = false
        )
    }

    override suspend fun updateSessionConversationId(accessToken: String, sessionId: String, conversationId: String) {
        request(
            "/rest/v1/ask_nora_sessions?id=eq.$sessionId",
            "PATCH",
            accessToken,
            JSONObject().put("conversation_id", conversationId),
            returnRepresentation = false
        )
    }

    override suspend fun updateSessionSummary(accessToken: String, sessionId: String, summary: String) {
        request(
            "/rest/v1/ask_nora_sessions?id=eq.$sessionId",
            "PATCH",
            accessToken,
            JSONObject().put("summary", summary),
            returnRepresentation = false
        )
    }

    override suspend fun endSession(accessToken: String, sessionId: String) {
        request("/rest/v1/ask_nora_sessions?id=eq.$sessionId", "PATCH", accessToken,
            JSONObject().put("ended_at", Instant.now().toString()), returnRepresentation = false)
    }

    override suspend fun fetchSessions(accessToken: String): List<AskNoraSession> {
        val result = request("/rest/v1/ask_nora_sessions?select=*&order=created_at.desc", "GET", accessToken, null, returnRepresentation = true)
        val array = JSONArray(result)
        return List(array.length()) { i ->
            val row = array.getJSONObject(i)
            val mode = if (row.getString("initial_mode") == "voice") AskNoraMode.Voice else AskNoraMode.Text
            AskNoraSession(
                id = row.getString("id"),
                initialMode = mode,
                createdAt = row.getString("created_at"),
                title = row.optString("title").ifBlank {
                    if (mode == AskNoraMode.Voice) "Voice call" else "Text chat"
                },
                lastMessage = fetchLastTurn(accessToken, row.getString("id"))?.text,
                endedAt = row.optString("ended_at").takeIf { it.isNotBlank() },
                conversationId = row.optString("conversation_id").takeIf { it.isNotBlank() },
                summary = row.optString("summary").takeIf { it.isNotBlank() }
            )
        }
    }

    override suspend fun fetchTurns(accessToken: String, sessionId: String): List<AskNoraTurn> {
        val result = request("/rest/v1/ask_nora_turns?session_id=eq.$sessionId&order=turn_index.asc", "GET", accessToken, null, returnRepresentation = true)
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

    override suspend fun submitIntakeDraft(accessToken: String, sessionId: String, draft: IntakeDraft) {
        val payload = draft.copy(sessionId = sessionId).toJsonObject()
        request(
            "/rest/v1/ask_nora_intake_drafts",
            "POST",
            accessToken,
            payload,
            returnRepresentation = false
        )
    }

    private suspend fun fetchTurnCount(accessToken: String, sessionId: String): Int {
        val result = request(
            "/rest/v1/ask_nora_turns?session_id=eq.$sessionId&select=id",
            "GET",
            accessToken,
            null,
            returnRepresentation = true
        )
        return JSONArray(result).length()
    }

    private suspend fun fetchLastTurn(accessToken: String, sessionId: String): AskNoraTurn? {
        val result = request(
            "/rest/v1/ask_nora_turns?session_id=eq.$sessionId&select=speaker,text,created_at&order=turn_index.desc&limit=1",
            "GET",
            accessToken,
            null,
            returnRepresentation = true
        )
        val array = JSONArray(result)
        if (array.length() == 0) return null

        val row = array.getJSONObject(0)
        return AskNoraTurn(
            speaker = if (row.getString("speaker") == "assistant") AskNoraSpeaker.Assistant else AskNoraSpeaker.User,
            text = row.getString("text"),
            createdAt = row.optString("created_at").takeIf { it.isNotBlank() }
        )
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
