package com.AMMR.ricehacks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class PatientHealthData(
    val profile: PatientProfileData?,
    val healthRecord: PatientHealthRecordData?,
    val activeMedications: List<MedicationData>,
    val pastMedications: List<MedicationData>,
    val allergies: List<AllergyData>,
    val conditions: List<ConditionData>,
    val recentObservations: List<ObservationData>,
    val providers: List<ProviderData>,
    val accessLogs: List<PatientAccessLogData>
) {
    val isLinkedToRecord: Boolean = healthRecord != null
}

data class PatientProfileData(
    val displayName: String,
    val phoneNumber: String?,
    val emergencyContact: String?
)

data class PatientHealthRecordData(
    val dateOfBirth: String?,
    val sexAtBirth: String?,
    val bloodType: String?,
    val heightCm: Double?,
    val weightKg: Double?,
    val preferredLanguage: String?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
    val notes: String?
)

data class MedicationData(
    val name: String,
    val dose: String?,
    val route: String?,
    val frequency: String?,
    val startDate: String?,
    val endDate: String?,
    val status: String?,
    val notes: String?
)

data class AllergyData(
    val allergen: String,
    val reaction: String?,
    val severity: String?,
    val status: String?,
    val firstObservedDate: String?,
    val notes: String?
)

data class ConditionData(
    val name: String,
    val diagnosisCode: String?,
    val status: String?,
    val severity: String?,
    val diagnosedDate: String?,
    val notes: String?
)

data class ObservationData(
    val type: String,
    val valueNumeric: Double?,
    val valueText: String?,
    val unit: String?,
    val source: String?,
    val observedAt: String?,
    val notes: String?
)

data class ProviderData(
    val firstName: String?,
    val lastName: String?,
    val specialty: String?,
    val organizationName: String?,
    val phone: String?,
    val email: String?,
    val accessLevel: String?,
    val accessExpiresAt: String?
) {
    val displayName: String
        get() = listOfNotNull(firstName, lastName)
            .joinToString(" ")
            .ifBlank { organizationName ?: "Provider" }
}

data class PatientAccessLogData(
    val attemptedAt: String?,
    val doctorName: String?,
    val hospitalName: String?,
    val result: String?
)

interface PatientDataRepository {
    suspend fun getMyHealthRecord(accessToken: String): PatientHealthData
}

class SupabasePatientDataRepository(
    private val supabaseUrl: String,
    private val publishableKey: String
) : PatientDataRepository {
    override suspend fun getMyHealthRecord(accessToken: String): PatientHealthData {
        val response = requestRpc(
            path = "/rest/v1/rpc/get_my_health_record",
            accessToken = accessToken
        )

        return response.toPatientHealthData()
    }

    private suspend fun requestRpc(
        path: String,
        accessToken: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("$supabaseUrl$path").openConnection() as HttpURLConnection).apply {
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
            connection.outputStream.use { output ->
                output.write("{}".toByteArray(Charsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(readSupabaseError(response))
            }

            JSONObject(response)
        } finally {
            connection.disconnect()
        }
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
            "Could not load your health record. Please try again."
        }
    }
}

private fun JSONObject.toPatientHealthData(): PatientHealthData {
    return PatientHealthData(
        profile = optObject("profile")?.let {
            PatientProfileData(
                displayName = it.optString("display_name").ifBlank { "HealthBridge patient" },
                phoneNumber = it.optNullableString("phone_number"),
                emergencyContact = it.optNullableString("emergency_contact")
            )
        },
        healthRecord = optObject("health_record")?.let {
            PatientHealthRecordData(
                dateOfBirth = it.optNullableString("date_of_birth"),
                sexAtBirth = it.optNullableString("sex_at_birth"),
                bloodType = it.optNullableString("blood_type"),
                heightCm = it.optNullableDouble("height_cm"),
                weightKg = it.optNullableDouble("weight_kg"),
                preferredLanguage = it.optNullableString("preferred_language"),
                emergencyContactName = it.optNullableString("emergency_contact_name"),
                emergencyContactPhone = it.optNullableString("emergency_contact_phone"),
                notes = it.optNullableString("notes")
            )
        },
        activeMedications = optArray("active_medications").mapObjects { json ->
            MedicationData(
                name = json.optString("medication_name").ifBlank { "Medication" },
                dose = json.optNullableString("dose"),
                route = json.optNullableString("route"),
                frequency = json.optNullableString("frequency"),
                startDate = json.optNullableString("start_date"),
                endDate = json.optNullableString("end_date"),
                status = json.optNullableString("status"),
                notes = json.optNullableString("notes")
            )
        },
        pastMedications = optArray("past_medications").mapObjects { json ->
            MedicationData(
                name = json.optString("medication_name").ifBlank { "Medication" },
                dose = json.optNullableString("dose"),
                route = json.optNullableString("route"),
                frequency = json.optNullableString("frequency"),
                startDate = json.optNullableString("start_date"),
                endDate = json.optNullableString("end_date"),
                status = json.optNullableString("status"),
                notes = json.optNullableString("notes")
            )
        },
        allergies = optArray("allergies").mapObjects { json ->
            AllergyData(
                allergen = json.optString("allergen").ifBlank { "Allergy" },
                reaction = json.optNullableString("reaction"),
                severity = json.optNullableString("severity"),
                status = json.optNullableString("status"),
                firstObservedDate = json.optNullableString("first_observed_date"),
                notes = json.optNullableString("notes")
            )
        },
        conditions = optArray("conditions").mapObjects { json ->
            ConditionData(
                name = json.optString("condition_name").ifBlank { "Condition" },
                diagnosisCode = json.optNullableString("diagnosis_code"),
                status = json.optNullableString("status"),
                severity = json.optNullableString("severity"),
                diagnosedDate = json.optNullableString("diagnosed_date"),
                notes = json.optNullableString("notes")
            )
        },
        recentObservations = optArray("recent_observations").mapObjects { json ->
            ObservationData(
                type = json.optString("observation_type").ifBlank { "Observation" },
                valueNumeric = json.optNullableDouble("value_numeric"),
                valueText = json.optNullableString("value_text"),
                unit = json.optNullableString("unit"),
                source = json.optNullableString("source"),
                observedAt = json.optNullableString("observed_at"),
                notes = json.optNullableString("notes")
            )
        },
        providers = optArray("providers").mapObjects { json ->
            ProviderData(
                firstName = json.optNullableString("first_name"),
                lastName = json.optNullableString("last_name"),
                specialty = json.optNullableString("specialty"),
                organizationName = json.optNullableString("organization_name"),
                phone = json.optNullableString("phone"),
                email = json.optNullableString("email"),
                accessLevel = json.optNullableString("access_level"),
                accessExpiresAt = json.optNullableString("expires_at")
            )
        },
        accessLogs = optArray("access_logs").mapObjects { json ->
            PatientAccessLogData(
                attemptedAt = json.optNullableString("attempted_at"),
                doctorName = json.optNullableString("doctor_name"),
                hospitalName = json.optNullableString("hospital_name"),
                result = json.optNullableString("result")
            )
        }
    )
}

private fun JSONObject.optObject(name: String): JSONObject? {
    return if (isNull(name)) null else optJSONObject(name)
}

private fun JSONObject.optArray(name: String): JSONArray {
    return if (isNull(name)) JSONArray() else optJSONArray(name) ?: JSONArray()
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
    return (0 until length())
        .mapNotNull { index -> optJSONObject(index)?.let(transform) }
}

private fun JSONObject.optNullableString(name: String): String? {
    return if (isNull(name)) null else optString(name).ifBlank { null }
}

private fun JSONObject.optNullableDouble(name: String): Double? {
    return if (isNull(name)) null else optDouble(name)
}
