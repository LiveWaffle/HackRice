package com.AMMR.ricehacks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AuthenticatedPatient(
    val accessToken: String,
    val email: String?
)

interface AuthRepository {
    suspend fun signIn(email: String, password: String): AuthenticatedPatient
    suspend fun signUp(email: String, password: String, displayName: String): AuthenticatedPatient
    suspend fun ensurePatientProfile(accessToken: String, displayName: String)
}

class SupabaseAuthRepository(
    private val supabaseUrl: String,
    private val publishableKey: String
) : AuthRepository {
    override suspend fun signIn(email: String, password: String): AuthenticatedPatient {
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)

        return requestAuth(
            path = "/auth/v1/token?grant_type=password",
            body = body
        )
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): AuthenticatedPatient {
        val metadata = JSONObject()
            .put("display_name", displayName.trim())
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", metadata)

        return requestAuth(
            path = "/auth/v1/signup",
            body = body,
            noSessionMessage = "Account created. Check your email to confirm it, then sign in."
        )
    }

    override suspend fun ensurePatientProfile(accessToken: String, displayName: String) {
        val safeName = displayName.trim().ifBlank { "HealthBridge patient" }
        val body = JSONObject().put("display_name", safeName)

        request(
            path = "/rest/v1/rpc/ensure_patient_profile",
            bearerToken = accessToken,
            body = body
        )
    }

    private suspend fun requestAuth(
        path: String,
        body: JSONObject,
        noSessionMessage: String = "Sign in did not return a session. Please try again."
    ): AuthenticatedPatient {
        val response = request(path = path, body = body)
        val json = JSONObject(response)
        val accessToken = json.optString("access_token").ifBlank {
            json.optJSONObject("session")?.optString("access_token").orEmpty()
        }

        if (accessToken.isBlank()) {
            throw IllegalStateException(noSessionMessage)
        }

        val email = json.optJSONObject("user")?.optString("email")
            ?: json.optJSONObject("session")?.optJSONObject("user")?.optString("email")

        return AuthenticatedPatient(accessToken = accessToken, email = email)
    }

    private suspend fun request(
        path: String,
        body: JSONObject,
        bearerToken: String? = null
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL("$supabaseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (bearerToken != null) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
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
                throw IllegalStateException(readSupabaseError(response))
            }

            response
        } finally {
            connection.disconnect()
        }
    }

    private fun readSupabaseError(response: String): String {
        return runCatching {
            val json = JSONObject(response)
            json.optString("msg").ifBlank {
                json.optString("error_description").ifBlank {
                    json.optString("message")
                }
            }
        }.getOrNull().orEmpty().ifBlank {
            "Supabase request failed. Please check your email and password."
        }
    }
}
