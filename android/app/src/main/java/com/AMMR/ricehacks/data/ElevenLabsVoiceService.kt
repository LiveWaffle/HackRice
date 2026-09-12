package com.AMMR.ricehacks.data

import android.content.Context
import com.AMMR.ricehacks.BuildConfig
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
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
    ): String {
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

        return try {
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

    suspend fun startIntake(
        context: Context,
        accessToken: String? = null,
        language: String = "en",
        onTranscript: (String) -> Unit = {},
        onTentativeTranscript: (String) -> Unit = {},
        onAgentResponse: (String) -> Unit = {},
        onAgentResponsePart: (String) -> Unit = {},
        onAgentResponseCorrection: (String) -> Unit = {},
        onInterruption: () -> Unit = {},
    ): ConversationSession {
        require(language in supportedLanguages) { "Unsupported agent language: $language" }

        val token = fetchConversationToken(accessToken = accessToken)
        return ConversationClient.startSession(
            ConversationConfig(
                conversationToken = token,
                dynamicVariables = mapOf("language" to language, "conversation_mode" to "intake"),
                onUserTranscriptEvent = { text, _ -> onTranscript(text) },
                onTentativeUserTranscriptEvent = { text, _ -> onTentativeTranscript(text) },
                onAgentResponseEvent = { text, _ -> onAgentResponse(text) },
                onAgentResponsePartEvent = { _, text, _ -> onAgentResponsePart(text) },
                onAgentResponseCorrectionEvent = { text, _ -> onAgentResponseCorrection(text) },
                onInterruption = { _ -> onInterruption() },
            ),
            context,
        )
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
                dynamicVariables = briefing.asAgentVariables(),
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
        val supportedLanguages = setOf("en", "es")
    }
}
