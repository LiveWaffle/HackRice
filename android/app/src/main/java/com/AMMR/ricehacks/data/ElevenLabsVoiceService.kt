package com.AMMR.ricehacks.data

import android.content.Context
import android.util.Log
import com.AMMR.ricehacks.BuildConfig
import io.elevenlabs.ClientTool
import io.elevenlabs.ClientToolResult
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class HealthBriefingContext(
    val patientName: String,
    val flags: List<DuplicateTestFlag>,
    val bioAgeIndex: BioAgeIndex,
    val liveVitals: LiveVitals,
    val language: String,
) {
    fun asAgentVariables(): Map<String, String> = mapOf(
        "patient_name" to patientName,
        "language" to language,
        "health_briefing_json" to toJson().toString(),
    )

    private fun toJson(): JSONObject = JSONObject()
        .put("patient_name", patientName)
        .put("flags", JSONArray().apply {
            flags.forEach { flag ->
                put(JSONObject()
                    .put("test", flag.test)
                    .put("providers", JSONArray(flag.providers))
                    .put("days_apart", flag.daysApart))
            }
        })
        .put("bio_age_index", JSONObject()
            .put("score", bioAgeIndex.score)
            .put("trend", bioAgeIndex.trend))
        .put("live_vitals", JSONObject()
            .put("pulse_bpm", liveVitals.pulseBpm)
            .put("hrv_rmssd_ms", liveVitals.hrvRmssdMs)
            .put("breathing_rate", liveVitals.breathingRate)
            .put("stress_index", liveVitals.stressIndex))
        .put("language", language)
}

data class DuplicateTestFlag(
    val test: String,
    val providers: List<String>,
    val daysApart: Int,
)

data class BioAgeIndex(val score: Double, val trend: String)

data class LiveVitals(
    val pulseBpm: Double,
    val hrvRmssdMs: Double,
    val breathingRate: Double,
    val stressIndex: Double,
)

/**
 * Private-agent ElevenLabs service for the Ask Nora voice flow.
 *
 * The Android app never calls ElevenLabs directly for a token. We call our own
 * Supabase Edge Function, which requests a private-agent conversation token from
 * ElevenLabs using the server-side ELEVENLABS_API_KEY secret.
 */
class ElevenLabsVoiceService(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
) {
    suspend fun fetchConversationToken(
        accessToken: String? = null,
        agentId: String = BuildConfig.ELEVENLABS_AGENT_ID,
    ): String = withContext(Dispatchers.IO) {
        require(agentId.isNotBlank()) {
            "Add ELEVENLABS_AGENT_ID to local.properties, then rebuild the app."
        }

        val encodedAgentId = URLEncoder.encode(agentId, StandardCharsets.UTF_8.name())
        val requestUrl = "$supabaseUrl/functions/v1/elevenlabs-conversation-token?agent_id=$encodedAgentId"

        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
            if (!accessToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
        }

        try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("ElevenLabs token request failed: $response")
            }

            val json = JSONObject(response)
            json.optString("conversationToken")
                .ifBlank { json.optString("token") }
                .ifBlank { json.optString("conversation_token") }
                .ifBlank {
                    val nested = json.optJSONObject("data")
                    nested?.optString("token") ?: ""
                }
                .ifBlank {
                    throw IllegalStateException("Supabase token response did not include a conversation token.")
                }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchConversationSummary(
        accessToken: String? = null,
        conversationId: String,
    ): String? = withContext(Dispatchers.IO) {
        if (conversationId.isBlank()) return@withContext null
        val encodedConversationId = URLEncoder.encode(conversationId, StandardCharsets.UTF_8.name())
        val requestUrl = "$supabaseUrl/functions/v1/elevenlabs-conversation-summary?conversation_id=$encodedConversationId"
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
            if (!accessToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
        }

        try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("ElevenLabs summary request failed: $response")
            }

            val json = JSONObject(response)
            json.optString("transcript_summary")
                .ifBlank { json.optString("summary") }
                .ifBlank { json.optString("call_summary_title") }
                .ifBlank { null }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun startIntake(
        context: Context,
        accessToken: String? = null,
        language: String = "en",
        patientName: String? = null,
        patientId: String? = null,
        onTranscript: (String) -> Unit = {},
        onTentativeTranscript: (String) -> Unit = {},
        onAgentResponse: (String) -> Unit = {},
        onAgentResponsePart: (String) -> Unit = {},
        onAgentResponseCorrection: (String) -> Unit = {},
        onInterruption: () -> Unit = {},
        onIntakeDraft: (IntakeDraft) -> Unit = {},
        onSessionStatusChange: (String) -> Unit = {},
        onSpeakingStateChange: (Boolean) -> Unit = {},
        onConversationIdReady: (String) -> Unit = {},
    ): ConversationSession {
        val normalizedLanguage = language.lowercase()
        require(normalizedLanguage in supportedLanguages) { "Unsupported agent language: $language" }

        val resolvedPatientName = patientName?.trim()?.ifBlank { "Patient" } ?: "Patient"
        Log.d("NoraVoice", "Starting ElevenLabs intake session for patientName=$resolvedPatientName language=$normalizedLanguage agentId=${BuildConfig.ELEVENLABS_AGENT_ID}")
        val token = fetchConversationToken(accessToken = accessToken)
        Log.d("NoraVoice", "Fetched EleventLabs conversation token successfully (length=${token.length})")

        val submitIntakeDraftTool = object : ClientTool {
            override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                Log.d("NoraVoice", "Client tool submit_intake_draft invoked with params=${parameters.keys}")
                val structured = (parameters["structured"] as? Map<*, *>)
                    ?.entries
                    ?.associate { (key, value) -> key.toString() to value as Any }
                    ?: emptyMap()
                val draft = IntakeDraft(
                    intakeType = (parameters["intake_type"] as? String ?: "other").ifBlank { "other" },
                    rawStatement = parameters["raw_statement"] as? String ?: "",
                    structured = structured,
                    confidence = (parameters["confidence"] as? String ?: "low").lowercase(),
                    needsReview = parameters["needs_review"] as? Boolean ?: true,
                    source = parameters["source"] as? String ?: "voice_intake",
                    patientId = parameters["patient_id"] as? String ?: patientId ?: "",
                    timestamp = parameters["timestamp"] as? String ?: java.time.Instant.now().toString(),
                    language = parameters["language"] as? String ?: language,
                )
                if (draft.patientId.isNotBlank()) {
                    onIntakeDraft(draft)
                }
                return ClientToolResult.success("Draft saved for review.")
            }
        }

        val session = ConversationClient.startSession(
            ConversationConfig(
                conversationToken = token,
                dynamicVariables = mapOf(
                    "language" to normalizedLanguage,
                    "conversation_mode" to "intake",
                    "patient_name" to resolvedPatientName,
                ),
                clientTools = mapOf("submit_intake_draft" to submitIntakeDraftTool),
                onConnect = { conversationId ->
                    Log.d("NoraVoice", "ElevenLabs session connected: conversationId=$conversationId")
                    onConversationIdReady(conversationId)
                    onSessionStatusChange("connected")
                },
                onStatusChange = { status ->
                    Log.d("NoraVoice", "ElevenLabs status: $status")
                    onSessionStatusChange(status.toString())
                },
                onError = { code, message ->
                    Log.e("NoraVoice", "ElevenLabs SDK error code=$code message=${message ?: "<null>"}")
                    onSessionStatusChange("error")
                },
                onDisconnect = { details ->
                    Log.d("NoraVoice", "ElevenLabs disconnected: $details")
                    onSessionStatusChange("disconnected")
                    onSpeakingStateChange(false)
                },
                onUserTranscriptEvent = { text, eventId ->
                    Log.d("NoraVoice", "User transcript event: text=$text eventId=$eventId")
                    onSpeakingStateChange(false)
                    onTranscript(text)
                },
                onTentativeUserTranscriptEvent = { text, eventId ->
                    Log.d("NoraVoice", "Tentative transcript: text=$text eventId=$eventId")
                    onTentativeTranscript(text)
                },
                onAgentResponseEvent = { text, eventId ->
                    Log.d("NoraVoice", "Agent response event: text=$text eventId=$eventId")
                    onSpeakingStateChange(true)
                    onAgentResponse(text)
                },
                onAgentResponsePartEvent = { partType, text, eventId ->
                    Log.d("NoraVoice", "Agent response part: partType=$partType text=$text eventId=$eventId")
                    onSpeakingStateChange(true)
                    onAgentResponsePart(text)
                },
                onAgentResponseCorrectionEvent = { text, eventId ->
                    Log.d("NoraVoice", "Agent response correction: text=$text eventId=$eventId")
                    onSpeakingStateChange(true)
                    onAgentResponseCorrection(text)
                },
                onInterruption = { eventId ->
                    Log.d("NoraVoice", "Interruption event: $eventId")
                    onSpeakingStateChange(false)
                    onInterruption()
                },
            ),
            context,
        )
        Log.d("NoraVoice", "ConversationClient.startSession returned session=${session.hashCode()}")
        return session
    }

    suspend fun startBriefing(
        context: Context,
        briefing: HealthBriefingContext,
        accessToken: String? = null,
        onTranscript: (String) -> Unit = {},
        onInterruption: () -> Unit = {},
    ): ConversationSession {
        require(briefing.language in supportedLanguages) {
            "Unsupported agent language: ${briefing.language}"
        }

        val token = fetchConversationToken(accessToken = accessToken)
        return ConversationClient.startSession(
            ConversationConfig(
                conversationToken = token,
                dynamicVariables = briefing.asAgentVariables() + mapOf("patient_name" to briefing.patientName),
                onUserTranscriptEvent = { text, _ -> onTranscript(text) },
                onInterruption = { _ -> onInterruption() },
            ),
            context,
        )
    }

    suspend fun setMuted(session: ConversationSession, muted: Boolean) {
        session.setMicrophoneMuted(muted)
    }

    suspend fun end(session: ConversationSession) = session.endSession()

    private companion object {
        val supportedLanguages = setOf("en", "es", "zh")
    }
}
