package com.AMMR.ricehacks

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.AMMR.ricehacks.data.PatientDataRepository
import com.AMMR.ricehacks.data.PatientHealthData
import com.AMMR.ricehacks.data.RelayAppSettings
import com.AMMR.ricehacks.data.RelaySettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsContent(
    patientSession: AuthenticatedUser,
    patientDataRepository: PatientDataRepository,
    settingsRepository: RelaySettingsRepository,
    selectedPage: SettingsPage?,
    onSelectPage: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    selectedVoiceLanguageCode: String = "en",
    onVoiceLanguageChanged: (String) -> Unit = {},
    onPatientSessionChanged: (AuthenticatedUser) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var settings by remember { mutableStateOf(RelayAppSettings()) }
    var healthData by remember { mutableStateOf<PatientHealthData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(patientSession.accessToken, refreshKey) {
        isLoading = true
        errorMessage = null
        val healthResult = runCatching {
            patientDataRepository.getMyHealthRecord(
                patientSession.accessToken,
                patientSession.userId
            )
        }
        healthResult.onSuccess { healthData = it }

        val settingsResult = runCatching {
            settingsRepository.fetchSettings(patientSession.accessToken)
        }
        settingsResult.onSuccess { fetchedSettings ->
            settings = fetchedSettings
            onDarkThemeChange(fetchedSettings.darkMode)
            onVoiceLanguageChanged(fetchedSettings.voiceLanguageCode)
        }

        errorMessage = healthResult.exceptionOrNull()?.message
            ?: settingsResult.exceptionOrNull()?.message
        isLoading = false
    }

    if (selectedPage == null) {
        SettingsListScreen(onSelectPage = {
            Log.d("NoraDebug", "Settings page selected: ${it.title}")
            onSelectPage(it)
        })
    } else {
        SettingsDetailScreen(
            page = selectedPage,
            patientSession = patientSession,
            healthData = healthData,
            settings = settings,
            isLoading = isLoading,
            errorMessage = errorMessage,
            settingsRepository = settingsRepository,
            onSettingsChanged = { nextSettings ->
                settings = nextSettings
                onDarkThemeChange(nextSettings.darkMode)
                onVoiceLanguageChanged(nextSettings.voiceLanguageCode)
            },
            onProfileSaved = { updatedSession ->
                onPatientSessionChanged(updatedSession)
                refreshKey += 1
            },
            onBack = {
                Log.d("NoraDebug", "Back from settings page: ${selectedPage.title}")
                onBack()
            },
            onRetry = { refreshKey += 1 },
            onSignOut = onSignOut
        )
    }
}

@Composable
private fun SettingsListScreen(onSelectPage: (SettingsPage) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                color = colorScheme.onBackground,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search settings",
                    modifier = Modifier.size(30.dp),
                    tint = colorScheme.primary
                )
            }
        }

        Text(
            text = "Account",
            color = colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            SettingsPage.entries.forEachIndexed { index, page ->
                SettingsRow(page = page, onClick = { onSelectPage(page) })
                if (index < SettingsPage.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(page: SettingsPage, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = colorScheme.primary
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = page.title,
                color = colorScheme.onSurface,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = page.description,
                color = colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsDetailScreen(
    page: SettingsPage,
    patientSession: AuthenticatedUser,
    healthData: PatientHealthData?,
    settings: RelayAppSettings,
    isLoading: Boolean,
    errorMessage: String?,
    settingsRepository: RelaySettingsRepository,
    onSettingsChanged: (RelayAppSettings) -> Unit,
    onProfileSaved: (AuthenticatedUser) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to settings",
                modifier = Modifier.size(30.dp)
            )
        }

        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 20.dp)
                .size(44.dp),
            tint = colorScheme.primary
        )
        Text(
            text = page.title,
            color = colorScheme.onBackground,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            text = page.description,
            color = colorScheme.onSurfaceVariant,
            fontSize = 20.sp,
            lineHeight = 30.sp,
            modifier = Modifier.padding(top = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            when {
                isLoading -> LoadingSettingsCard()
                errorMessage != null -> SettingsErrorCard(errorMessage, onRetry)
                else -> SettingsDetailFields(
                    page = page,
                    patientSession = patientSession,
                    healthData = healthData,
                    settings = settings,
                    settingsRepository = settingsRepository,
                    onSettingsChanged = onSettingsChanged,
                    onProfileSaved = onProfileSaved,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@Composable
private fun LoadingSettingsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text("Loading settings...")
    }
}

@Composable
private fun SettingsErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Try again")
        }
    }
}

@Composable
private fun SettingsDetailFields(
    page: SettingsPage,
    patientSession: AuthenticatedUser,
    healthData: PatientHealthData?,
    settings: RelayAppSettings,
    settingsRepository: RelaySettingsRepository,
    onSettingsChanged: (RelayAppSettings) -> Unit,
    onProfileSaved: (AuthenticatedUser) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (page) {
            SettingsPage.Profile -> ProfileSettingsFields(
                patientSession = patientSession,
                healthData = healthData,
                settingsRepository = settingsRepository,
                onProfileSaved = onProfileSaved
            )
            SettingsPage.Preferences -> PreferencesSettingsFields(
                settings = settings,
                settingsRepository = settingsRepository,
                accessToken = patientSession.accessToken,
                onSettingsChanged = onSettingsChanged
            )
            SettingsPage.Accessibility -> AccessibilitySettingsFields(
                settings = settings,
                settingsRepository = settingsRepository,
                accessToken = patientSession.accessToken,
                onSettingsChanged = onSettingsChanged
            )
            SettingsPage.Notifications -> NotificationSettingsFields(
                settings = settings,
                settingsRepository = settingsRepository,
                accessToken = patientSession.accessToken,
                onSettingsChanged = onSettingsChanged
            )
            SettingsPage.Privacy -> PrivacySettingsFields(
                settings = settings,
                settingsRepository = settingsRepository,
                accessToken = patientSession.accessToken,
                onSettingsChanged = onSettingsChanged,
                onSignOut = onSignOut
            )
            SettingsPage.About -> {
                SettingsValueRow("App", "Relay")
                SettingsValueRow("Version", "1.0")
                SettingsValueRow("Project", "HackRice 2026")
                Text(
                    text = "Relay helps patients organize and share health information. It does not replace medical advice from a doctor.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 25.sp
                )
            }
            SettingsPage.HelpSupport -> {
                SettingsValueRow("Support hours", "8 AM to 8 PM")
                SettingsValueRow("Email", "support@relay.local")
                SettingsValueRow("Phone help", "(555) 010-1040")
                SettingsActionButton("Contact support")
            }
        }
    }
}

@Composable
private fun ProfileSettingsFields(
    patientSession: AuthenticatedUser,
    healthData: PatientHealthData?,
    settingsRepository: RelaySettingsRepository,
    onProfileSaved: (AuthenticatedUser) -> Unit
) {
    val scope = rememberCoroutineScope()
    val profile = healthData?.profile
    val record = healthData?.healthRecord
    var fullName by remember(profile?.displayName, patientSession.displayName) {
        mutableStateOf(profile?.displayName ?: patientSession.displayName.orEmpty())
    }
    var phone by remember(profile?.phoneNumber) { mutableStateOf(profile?.phoneNumber.orEmpty()) }
    var emergencyContact by remember(profile?.emergencyContact) {
        mutableStateOf(profile?.emergencyContact.orEmpty())
    }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = fullName,
        onValueChange = { fullName = it },
        label = { Text("Full name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    SettingsValueRow("Date of birth", record?.dateOfBirth ?: "Not listed")
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Phone") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = emergencyContact,
        onValueChange = { emergencyContact = it },
        label = { Text("Emergency contact") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    StatusMessage(saveMessage)
    SettingsActionButton(
        text = if (isSaving) "Saving..." else "Save profile",
        enabled = !isSaving && fullName.isNotBlank()
    ) {
        isSaving = true
        saveMessage = null
        scope.launch {
            runCatching {
                settingsRepository.saveProfile(
                    accessToken = patientSession.accessToken,
                    userId = patientSession.userId,
                    displayName = fullName,
                    phoneNumber = phone,
                    emergencyContact = emergencyContact
                )
            }.onSuccess {
                saveMessage = "Saved profile."
                onProfileSaved(patientSession.copy(displayName = fullName.trim()))
            }.onFailure {
                saveMessage = it.message ?: "Could not save profile."
            }
            isSaving = false
        }
    }
}

@Composable
private fun PreferencesSettingsFields(
    settings: RelayAppSettings,
    settingsRepository: RelaySettingsRepository,
    accessToken: String,
    onSettingsChanged: (RelayAppSettings) -> Unit
) {
    SettingsSaveScope(settings, settingsRepository, accessToken, onSettingsChanged) { current, setCurrent ->
        AiSettingsPreferences(
            selectedLanguageCode = current.voiceLanguageCode,
            onLanguageSelected = { setCurrent(current.copy(voiceLanguageCode = it)) }
        )
        SettingsToggleRow("Use dark mode", current.darkMode) { setCurrent(current.copy(darkMode = it)) }
        SettingsToggleRow("High contrast mode", current.highContrastMode) { setCurrent(current.copy(highContrastMode = it)) }
        SettingsToggleRow("Use simple visit summaries", current.simpleVisitSummaries) { setCurrent(current.copy(simpleVisitSummaries = it)) }
    }
}

@Composable
private fun AccessibilitySettingsFields(
    settings: RelayAppSettings,
    settingsRepository: RelaySettingsRepository,
    accessToken: String,
    onSettingsChanged: (RelayAppSettings) -> Unit
) {
    SettingsSaveScope(settings, settingsRepository, accessToken, onSettingsChanged) { current, setCurrent ->
        OptionChips("Button size", listOf("Standard", "Large", "Extra large"), current.buttonSize) {
            setCurrent(current.copy(buttonSize = it))
        }
        OptionChips("Reading speed", listOf("Slow", "Normal", "Fast"), current.readingSpeed) {
            setCurrent(current.copy(readingSpeed = it))
        }
        SettingsToggleRow("Always show captions", current.alwaysShowCaptions) { setCurrent(current.copy(alwaysShowCaptions = it)) }
        SettingsToggleRow("Speak screen changes aloud", current.speakScreenChanges) { setCurrent(current.copy(speakScreenChanges = it)) }
        SettingsToggleRow("Reduce motion", current.reduceMotion) { setCurrent(current.copy(reduceMotion = it)) }
        SettingsToggleRow("Stronger touch feedback", current.strongerTouchFeedback) { setCurrent(current.copy(strongerTouchFeedback = it)) }
        SettingsToggleRow("Confirm before leaving forms", current.confirmBeforeLeavingForms) { setCurrent(current.copy(confirmBeforeLeavingForms = it)) }
    }
}

@Composable
private fun NotificationSettingsFields(
    settings: RelayAppSettings,
    settingsRepository: RelaySettingsRepository,
    accessToken: String,
    onSettingsChanged: (RelayAppSettings) -> Unit
) {
    SettingsSaveScope(settings, settingsRepository, accessToken, onSettingsChanged) { current, setCurrent ->
        SettingsToggleRow("Medicine reminders", current.medicineReminders) { setCurrent(current.copy(medicineReminders = it)) }
        SettingsToggleRow("Appointment reminders", current.appointmentReminders) { setCurrent(current.copy(appointmentReminders = it)) }
        SettingsToggleRow("Doctor access alerts", current.doctorAccessAlerts) { setCurrent(current.copy(doctorAccessAlerts = it)) }
        SettingsToggleRow("Weekly record summary", current.weeklyRecordSummary) { setCurrent(current.copy(weeklyRecordSummary = it)) }
    }
}

@Composable
private fun PrivacySettingsFields(
    settings: RelayAppSettings,
    settingsRepository: RelaySettingsRepository,
    accessToken: String,
    onSettingsChanged: (RelayAppSettings) -> Unit,
    onSignOut: () -> Unit
) {
    SettingsSaveScope(settings, settingsRepository, accessToken, onSettingsChanged) { current, setCurrent ->
        SettingsToggleRow("Require approval for every scan", current.requireApprovalEveryScan) { setCurrent(current.copy(requireApprovalEveryScan = it)) }
        SettingsToggleRow("Hide sensitive notes by default", current.hideSensitiveNotes) { setCurrent(current.copy(hideSensitiveNotes = it)) }
        SettingsValueRow("Trusted devices", "This phone only")
        SettingsValueRow("Last password change", "Not set")
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Sign out")
        }
    }
}

@Composable
private fun SettingsSaveScope(
    settings: RelayAppSettings,
    settingsRepository: RelaySettingsRepository,
    accessToken: String,
    onSettingsChanged: (RelayAppSettings) -> Unit,
    content: @Composable (RelayAppSettings, (RelayAppSettings) -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    var localSettings by remember(settings) { mutableStateOf(settings) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    content(localSettings) { nextSettings ->
        localSettings = nextSettings
        onSettingsChanged(nextSettings)
    }

    StatusMessage(saveMessage)
    SettingsActionButton(
        text = if (isSaving) "Saving..." else "Save settings",
        enabled = !isSaving
    ) {
        isSaving = true
        saveMessage = null
        scope.launch {
            runCatching {
                settingsRepository.saveSettings(accessToken, localSettings)
            }.onSuccess {
                saveMessage = "Saved settings."
                onSettingsChanged(localSettings)
            }.onFailure {
                saveMessage = it.message ?: "Could not save settings."
            }
            isSaving = false
        }
    }
}

@Composable
fun AiSettingsPreferences(
    selectedLanguageCode: String = "en",
    onLanguageSelected: (String) -> Unit = {}
) {
    OptionChips(
        title = "Voice language",
        options = listOf("English", "Español", "中文"),
        selected = when (selectedLanguageCode) {
            "es" -> "Español"
            "zh" -> "中文"
            else -> "English"
        },
        onSelected = {
            onLanguageSelected(
                when (it) {
                    "Español" -> "es"
                    "中文" -> "zh"
                    else -> "en"
                }
            )
        }
    )
}

@Composable
private fun OptionChips(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = { Text(option) }
                )
            }
        }
    }
}

@Composable
fun SettingsValueRow(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme

    Column {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
        Text(
            text = value,
            color = colorScheme.onSurface,
            fontSize = 21.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colorScheme.onSurface,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusMessage(message: String?) {
    message?.let {
        Text(
            text = it,
            color = if (it.startsWith("Saved")) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}
