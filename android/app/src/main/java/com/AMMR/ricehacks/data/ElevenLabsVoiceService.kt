package com.AMMR.ricehacks.data

import android.content.Context
import com.AMMR.ricehacks.BuildConfig
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import org.json.JSONArray
import org.json.JSONObject

/** The per-session facts that the briefing agent is permitted to discuss. */
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
 * Starts ElevenLabs' official Android Agents SDK in voice/WebRTC mode.
 * This hackathon build uses a public agent ID. Set the agent's fixed voice in
 * the ElevenLabs dashboard. The SDK owns microphone capture and speaker playback.
 */
class ElevenLabsVoiceService {
    suspend fun startIntake(
        context: Context,
        language: String = "en",
        onTranscript: (String) -> Unit = {},
        onTentativeTranscript: (String) -> Unit = {},
        onAgentResponse: (String) -> Unit = {},
        onAgentResponsePart: (String) -> Unit = {},
        onAgentResponseCorrection: (String) -> Unit = {},
        onInterruption: () -> Unit = {},
    ): ConversationSession {
        require(language in supportedLanguages) { "Unsupported agent language: $language" }
        requireConfiguredAgent()
        return ConversationClient.startSession(
            ConversationConfig(
                agentId = BuildConfig.ELEVENLABS_AGENT_ID,
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
        onTranscript: (String) -> Unit = {},
        onInterruption: () -> Unit = {},
    ): ConversationSession {
        require(briefing.language in supportedLanguages) {
            "Unsupported agent language: ${briefing.language}"
        }

        requireConfiguredAgent()
        return ConversationClient.startSession(
            ConversationConfig(
                agentId = BuildConfig.ELEVENLABS_AGENT_ID,
                dynamicVariables = briefing.asAgentVariables(),
                onUserTranscriptEvent = { text, _ -> onTranscript(text) },
                onInterruption = { _ -> onInterruption() },
            ),
            context,
        )
    }

    suspend fun end(session: ConversationSession) = session.endSession()

    private companion object {
        // Keep this list aligned with the languages enabled on the agent itself.
        val supportedLanguages = setOf("en", "es")

        fun requireConfiguredAgent() {
            check(BuildConfig.ELEVENLABS_AGENT_ID.isNotBlank() &&
                BuildConfig.ELEVENLABS_AGENT_ID != "PASTE_YOUR_PUBLIC_AGENT_ID_HERE") {
                "Add ELEVENLABS_AGENT_ID to local.properties, then rebuild the app."
            }
        }
    }
}
