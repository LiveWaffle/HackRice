package com.AMMR.ricehacks.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AuthenticatedUser(
    val accessToken: String,
    val userId: String?,
    val email: String?,
    val role: UserRole = UserRole.Patient
)

enum class UserRole {
    Patient,
    Doctor
}

interface AuthRepository {
    suspend fun signIn(email: String, password: String): AuthenticatedUser
    suspend fun signUp(email: String, password: String, displayName: String): AuthenticatedUser
    suspend fun ensurePatientProfile(accessToken: String, displayName: String)
}

class SupabaseAuthRepository(
    private val supabaseUrl: String,
    private val publishableKey: String
) : AuthRepository {
    override suspend fun signIn(email: String, password: String): AuthenticatedUser {
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
    ): AuthenticatedUser {
        val metadata = JSONObject()
            .put("display_name", displayName.trim())
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", metadata)

        return requestAuth(
            path = "/auth/v1/signup",
            body = body,
            noSessionMessage = "Confirm your email to sign in, or try signing in if you already have an account."
        )
    }

    override suspend fun ensurePatientProfile(accessToken: String, displayName: String) {
        val safeName = displayName.trim().ifBlank { "Cara patient" }
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
    ): AuthenticatedUser {
        Log.d("CaraDebug", "Supabase Auth request to $path")
        val response = request(path = path, body = body)
        val json = JSONObject(response)
        val accessToken = json.optString("access_token").ifBlank {
            json.optJSONObject("session")?.optString("access_token").orEmpty()
        }

        if (accessToken.isBlank()) {
            Log.e("CaraDebug", "Supabase Auth failed to return access token for $path")
            throw IllegalStateException(noSessionMessage)
        }

        val userJson = json.optJSONObject("user")
            ?: json.optJSONObject("session")?.optJSONObject("user")
        
        val email = userJson?.optString("email")
        val userId = userJson?.optString("id")
        
        // Simple logic: if email contains "doctor", assume doctor role for this hackathon
        val role = if (email?.contains("doctor", ignoreCase = true) == true) {
            UserRole.Doctor
        } else {
            UserRole.Patient
        }

        Log.d("CaraDebug", "Supabase Auth success for $path (user: $email, role: $role)")
        return AuthenticatedUser(
            accessToken = accessToken,
            userId = userId,
            email = email,
            role = role
        )
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
                val error = readSupabaseError(response)
                Log.e("CaraDebug", "Supabase request error at $path: $error")
                throw IllegalStateException(error)
            }

            response
        } catch (e: Exception) {
            Log.e("CaraDebug", "Supabase network/request exception at $path: ${e.message}")
            throw e
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
