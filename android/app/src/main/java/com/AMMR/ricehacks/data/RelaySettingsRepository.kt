package com.AMMR.ricehacks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RelayAppSettings(
    val voiceLanguageCode: String = "en",
    val darkMode: Boolean = false,
    val highContrastMode: Boolean = false,
    val simpleVisitSummaries: Boolean = true,
    val buttonSize: String = "Large",
    val readingSpeed: String = "Slow",
    val alwaysShowCaptions: Boolean = true,
    val speakScreenChanges: Boolean = false,
    val reduceMotion: Boolean = true,
    val strongerTouchFeedback: Boolean = true,
    val confirmBeforeLeavingForms: Boolean = true,
    val medicineReminders: Boolean = true,
    val appointmentReminders: Boolean = true,
    val doctorAccessAlerts: Boolean = true,
    val weeklyRecordSummary: Boolean = false,
    val requireApprovalEveryScan: Boolean = true,
    val hideSensitiveNotes: Boolean = true
)

class RelaySettingsRepository(
    private val supabaseUrl: String,
    private val publishableKey: String
) {
    suspend fun fetchSettings(accessToken: String): RelayAppSettings {
        val response = request(
            path = "/rest/v1/patient_app_settings?select=*&limit=1",
            method = "GET",
            accessToken = accessToken
        )
        val row = JSONArray(response).optJSONObject(0) ?: return RelayAppSettings()
        return row.toRelayAppSettings()
    }

    suspend fun saveSettings(accessToken: String, settings: RelayAppSettings) {
        request(
            path = "/rest/v1/patient_app_settings?on_conflict=patient_id",
            method = "POST",
            accessToken = accessToken,
            requestBody = settings.toJson(),
            prefer = "resolution=merge-duplicates"
        )
    }

    suspend fun saveProfile(
        accessToken: String,
        userId: String?,
        displayName: String,
        phoneNumber: String,
        emergencyContact: String
    ) {
        val id = userId?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Could not save profile without a user id.")
        request(
            path = "/rest/v1/patient_profiles?id=eq.$id",
            method = "PATCH",
            accessToken = accessToken,
            requestBody = JSONObject()
                .put("display_name", displayName.trim())
                .put("phone_number", phoneNumber.trim().ifBlank { JSONObject.NULL })
                .put("emergency_contact", emergencyContact.trim().ifBlank { JSONObject.NULL })
                .put("updated_at", java.time.Instant.now().toString())
        )
    }

    private suspend fun request(
        path: String,
        method: String,
        accessToken: String,
        requestBody: JSONObject? = null,
        prefer: String? = null
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL("$supabaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = requestBody != null
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (prefer != null) {
                setRequestProperty("Prefer", prefer)
            }
        }

        try {
            requestBody?.let { body ->
                connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val response = stream.bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(readSupabaseError(response))
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}

private fun RelayAppSettings.toJson(): JSONObject {
    return JSONObject()
        .put("voice_language_code", voiceLanguageCode)
        .put("dark_mode", darkMode)
        .put("high_contrast_mode", highContrastMode)
        .put("simple_visit_summaries", simpleVisitSummaries)
        .put("button_size", buttonSize)
        .put("reading_speed", readingSpeed)
        .put("always_show_captions", alwaysShowCaptions)
        .put("speak_screen_changes", speakScreenChanges)
        .put("reduce_motion", reduceMotion)
        .put("stronger_touch_feedback", strongerTouchFeedback)
        .put("confirm_before_leaving_forms", confirmBeforeLeavingForms)
        .put("medicine_reminders", medicineReminders)
        .put("appointment_reminders", appointmentReminders)
        .put("doctor_access_alerts", doctorAccessAlerts)
        .put("weekly_record_summary", weeklyRecordSummary)
        .put("require_approval_every_scan", requireApprovalEveryScan)
        .put("hide_sensitive_notes", hideSensitiveNotes)
        .put("updated_at", java.time.Instant.now().toString())
}

private fun JSONObject.toRelayAppSettings(): RelayAppSettings {
    return RelayAppSettings(
        voiceLanguageCode = optString("voice_language_code").ifBlank { "en" },
        darkMode = optBoolean("dark_mode", false),
        highContrastMode = optBoolean("high_contrast_mode", false),
        simpleVisitSummaries = optBoolean("simple_visit_summaries", true),
        buttonSize = optString("button_size").ifBlank { "Large" },
        readingSpeed = optString("reading_speed").ifBlank { "Slow" },
        alwaysShowCaptions = optBoolean("always_show_captions", true),
        speakScreenChanges = optBoolean("speak_screen_changes", false),
        reduceMotion = optBoolean("reduce_motion", true),
        strongerTouchFeedback = optBoolean("stronger_touch_feedback", true),
        confirmBeforeLeavingForms = optBoolean("confirm_before_leaving_forms", true),
        medicineReminders = optBoolean("medicine_reminders", true),
        appointmentReminders = optBoolean("appointment_reminders", true),
        doctorAccessAlerts = optBoolean("doctor_access_alerts", true),
        weeklyRecordSummary = optBoolean("weekly_record_summary", false),
        requireApprovalEveryScan = optBoolean("require_approval_every_scan", true),
        hideSensitiveNotes = optBoolean("hide_sensitive_notes", true)
    )
}

private fun readSupabaseError(response: String): String {
    return runCatching {
        val json = JSONObject(response)
        json.optString("message").ifBlank {
            json.optString("msg").ifBlank {
                json.optString("error_description")
            }
        }
    }.getOrNull().orEmpty().ifBlank {
        "Settings could not be saved. Check that the settings SQL has been run."
    }
}
