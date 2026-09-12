package com.AMMR.ricehacks

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import com.AMMR.ricehacks.data.AuthenticatedUser
import com.AMMR.ricehacks.data.DirectGeminiHealthAiRepository
import com.AMMR.ricehacks.data.FakeQrAccessRepository
import com.AMMR.ricehacks.data.SupabaseAuthRepository
import com.AMMR.ricehacks.data.SupabasePatientDataRepository
import com.AMMR.ricehacks.ui.theme.RiceHacksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CaraDebug", "MainActivity onCreate")
        enableEdgeToEdge()
        setContent {
RiceHacksTheme {
                HealthBridgeApp()
            }
        }
    }
}

@Composable
fun HealthBridgeApp(modifier: Modifier = Modifier) {
    var patientSession by remember { mutableStateOf<AuthenticatedPatient?>(null) }
    val systemDarkTheme = isSystemInDarkTheme()
    var darkTheme by remember { mutableStateOf(systemDarkTheme) }
    Log.d("CaraDebug", "HealthBridgeApp recomposed, patientSession: ${patientSession?.email}")
}

@Composable
fun CaraApp(modifier: Modifier = Modifier) {
    HealthBridgeApp(modifier)
}
    val authRepository = remember {
        SupabaseAuthRepository(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        )
    }
    val qrAccessRepository = remember { FakeQrAccessRepository() }
    val patientDataRepository = remember {
        SupabasePatientDataRepository(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        )
    }
    val aiRepository = remember {
        DirectGeminiHealthAiRepository(apiKey = BuildConfig.GEMINI_API_KEY)
    }

    RiceHacksTheme(darkTheme = darkTheme) {
        val currentSession = patientSession
        if (currentSession != null) {
            LoggedInHomeScreen(
                patientSession = currentSession,
                qrAccessRepository = qrAccessRepository,
                patientDataRepository = patientDataRepository,
                aiRepository = aiRepository,
                darkTheme = darkTheme,
                onDarkThemeChange = { darkTheme = it },
                modifier = modifier
            )
        } else {
            LoginScreen(
                authRepository = authRepository,
                onSignedIn = { patientSession = it },
                modifier = modifier
            )
        }
    }
}
