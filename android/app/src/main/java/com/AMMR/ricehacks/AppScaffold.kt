package com.AMMR.ricehacks

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.AMMR.ricehacks.data.AuthenticatedUser
import com.AMMR.ricehacks.data.HealthAiRepository
import com.AMMR.ricehacks.data.PatientDataRepository
import com.AMMR.ricehacks.data.QrAccessRepository
import com.AMMR.ricehacks.data.SupabaseAskNoraRepository
import com.AMMR.ricehacks.presage.PresageScanScreen
import com.AMMR.ricehacks.presage.PresageVitalsRepository
import kotlinx.coroutines.launch

@Composable
fun LoggedInHomeScreen(
    patientSession: AuthenticatedUser,
    qrAccessRepository: QrAccessRepository,
    patientDataRepository: PatientDataRepository,
    aiRepository: HealthAiRepository,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDestination by remember { mutableStateOf(AppDestination.Home) }
    var selectedSettingsPage by remember { mutableStateOf<SettingsPage?>(null) }
    var showingMargaretRecord by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val presageVitalsRepository = remember {
    PresageVitalsRepository(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    )
}
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colorScheme.background,
        topBar = {
            if (selectedDestination != AppDestination.Settings) {
                Surface(color = colorScheme.background) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Nora",
                                color = colorScheme.onBackground,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedDestination.label,
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedDestination = AppDestination.Settings
                                selectedSettingsPage = null
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Open settings",
                                modifier = Modifier.size(30.dp),
                                tint = colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = colorScheme.surfaceContainer) {
                AppDestination.entries
                    .filter { it.requiredRole == null || it.requiredRole == patientSession.role }
                    .forEach { destination ->
                        NavigationBarItem(
                            selected = selectedDestination == destination,
                            onClick = {
                                showingMargaretRecord = false
                                Log.d("NoraDebug", "Navigating to: ${destination.label}")
                                selectedDestination = destination
                                if (destination != AppDestination.Settings) {
                                    selectedSettingsPage = null
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = destination.label,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = colorScheme.background
        ) {
            when (selectedDestination) {
                AppDestination.Home -> {
                    if (showingMargaretRecord) {
                        MargaretRecordScreen()
                    } else {
                        HomeTabContent(
                            patientName = patientSession.email?.substringBefore('@') ?: "there",
                            role = patientSession.role,
                            accessToken = patientSession.accessToken,
                            presageVitalsRepository = presageVitalsRepository,
                            onViewHealthData = { selectedDestination = AppDestination.MyData },
                            onOpenRecord = { showingMargaretRecord = true },
                            onStartScan = { selectedDestination = AppDestination.Vitals }
                        )
                    }
                }
                AppDestination.Vitals -> PresageScanScreen(
                    onReadingReady = { reading ->
                        scope.launch {
                            runCatching {
                                presageVitalsRepository.saveReading(
                                    accessToken = patientSession.accessToken,
                                    reading = reading
                                )
                            }
                        }
                    }
                )
                AppDestination.MyData -> MyDataQrScreen(
                    patientSession = patientSession,
                    qrAccessRepository = qrAccessRepository,
                    patientDataRepository = patientDataRepository
                )
                AppDestination.Scanner -> DoctorScannerScreen(
                    patientDataRepository = patientDataRepository
                )
                AppDestination.Settings -> SettingsContent(
                    selectedPage = selectedSettingsPage,
                    onSelectPage = { selectedSettingsPage = it },
                    onBack = { selectedSettingsPage = null },
                    darkTheme = darkTheme,
                    onDarkThemeChange = onDarkThemeChange
                )
                AppDestination.AskNora -> AskNoraScreen(
                    patientSession = patientSession,
                    aiRepository = aiRepository,
                    askNoraRepository = SupabaseAskNoraRepository(
                        supabaseUrl = BuildConfig.SUPABASE_URL,
                        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
                    )
                )
            }
        }
    }
}
