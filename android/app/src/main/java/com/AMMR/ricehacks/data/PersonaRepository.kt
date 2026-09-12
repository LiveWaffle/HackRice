package com.AMMR.ricehacks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class PersonaInquiry(
    val inquiryId: String,
    val status: String?,
    val sessionToken: String?,
    val oneTimeLink: String?,
    val oneTimeLinkShort: String?
)

interface PersonaRepository {
    suspend fun createInquiry(
        patientSessionToken: String,
        fields: Map<String, String> = emptyMap()
    ): PersonaInquiry

    suspend fun getInquiryStatus(
        patientSessionToken: String,
        inquiryId: String
    ): PersonaInquiry
}

class BackendPersonaRepository(
    private val backendBaseUrl: String
) : PersonaRepository {
    override suspend fun createInquiry(
        patientSessionToken: String,
        fields: Map<String, String>
    ): PersonaInquiry {
        val fieldsJson = JSONObject()
        fields.forEach { (key, value) -> fieldsJson.put(key, value) }

        val response = request(
            path = "/api/patient/persona/inquiry",
            method = "POST",
            patientSessionToken = patientSessionToken,
            body = JSONObject().put("fields", fieldsJson)
        )

        return response.toPersonaInquiry()
    }

    override suspend fun getInquiryStatus(
        patientSessionToken: String,
        inquiryId: String
    ): PersonaInquiry {
        val response = request(
            path = "/api/patient/persona/inquiry/$inquiryId",
            method = "GET",
            patientSessionToken = patientSessionToken
        )

        return response.toPersonaInquiry()
    }

    private suspend fun request(
        path: String,
        method: String,
        patientSessionToken: String,
        body: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("$backendBaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Patient-Session", patientSessionToken)
            if (body != null) {
                doOutput = true
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(response.ifBlank { "Persona request failed." })
            }

            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toPersonaInquiry(): PersonaInquiry {
        return PersonaInquiry(
            inquiryId = getString("inquiryId"),
            status = optString("status").ifBlank { null },
            sessionToken = optString("sessionToken").ifBlank { null },
            oneTimeLink = optString("oneTimeLink").ifBlank { null },
            oneTimeLinkShort = optString("oneTimeLinkShort").ifBlank { null }
        )
    }
}
