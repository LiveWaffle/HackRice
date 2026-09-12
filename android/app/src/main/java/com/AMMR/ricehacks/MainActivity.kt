package com.AMMR.ricehacks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.AMMR.ricehacks.data.ApprovalResult
import com.AMMR.ricehacks.data.AuthRepository
import com.AMMR.ricehacks.data.AuthenticatedPatient
import com.AMMR.ricehacks.data.AuditLogEntry
import com.AMMR.ricehacks.data.DoctorScanRequest
import com.AMMR.ricehacks.data.FakeQrAccessRepository
import com.AMMR.ricehacks.data.QrAccessRepository
import com.AMMR.ricehacks.data.SignedQrToken
import com.AMMR.ricehacks.data.SupabaseAuthRepository
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.AMMR.ricehacks.ui.theme.RiceHacksTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.max

private const val DEMO_PATIENT_NAME = "Margaret Chen"
private const val DEMO_PATIENT_EMAIL = "gilliamandrew22@gmail.com"
private const val DEMO_PATIENT_PASSWORD = "HealthBridge1943!"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val authRepository = remember {
        SupabaseAuthRepository(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        )
    }
    val qrAccessRepository = remember { FakeQrAccessRepository() }

    if (patientSession != null) {
        LoggedInHomeScreen(
            qrAccessRepository = qrAccessRepository,
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

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onSignedIn: (AuthenticatedPatient) -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isCreatingAccount by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val greeting = remember { timeOfDayGreeting() }
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colorScheme.background
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .background(colorScheme.background),
            color = colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            color = colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "HealthBridge",
                                color = colorScheme.onBackground,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Your health record",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(44.dp))

                    Text(
                        text = greeting,
                        color = colorScheme.onBackground,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sign in to see your medicines, allergies, records, and doctors.",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 20.sp,
                        lineHeight = 30.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    FilledTonalButton(
                        onClick = {
                            displayName = DEMO_PATIENT_NAME
                            email = DEMO_PATIENT_EMAIL
                            password = DEMO_PATIENT_PASSWORD
                            isCreatingAccount = false
                            errorMessage = null
                            infoMessage = "Margaret's login is filled in. Tap Sign in."
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = "Use Margaret's login",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (isCreatingAccount) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                                errorMessage = null
                                infoMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Your name", fontSize = 18.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            shape = MaterialTheme.shapes.large
                        )

                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                            infoMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email", fontSize = 18.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = MaterialTheme.shapes.large
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                            infoMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password", fontSize = 18.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = MaterialTheme.shapes.large
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = colorScheme.error,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    infoMessage?.let { message ->
                        Text(
                            text = message,
                            color = colorScheme.primary,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    Button(
                        onClick = {
                            val trimmedEmail = email.trim()
                            val trimmedName = displayName.trim()

                            when {
                                trimmedEmail.isBlank() -> errorMessage = "Please enter your email."
                                password.length < 6 -> errorMessage = "Password must be at least 6 characters."
                                isCreatingAccount && trimmedName.isBlank() -> errorMessage = "Please enter your name."
                                else -> {
                                    isLoading = true
                                    errorMessage = null
                                    infoMessage = null
                                    scope.launch {
                                        runCatching {
                                            if (isCreatingAccount) {
                                                val auth = authRepository.signUp(
                                                    email = trimmedEmail,
                                                    password = password,
                                                    displayName = trimmedName
                                                )
                                                authRepository.ensurePatientProfile(
                                                    accessToken = auth.accessToken,
                                                    displayName = trimmedName
                                                )
                                                auth
                                            } else {
                                                val isMargaretDemoLogin = trimmedEmail.equals(
                                                    DEMO_PATIENT_EMAIL,
                                                    ignoreCase = true
                                                )
                                                val profileName = trimmedName.ifBlank {
                                                    if (isMargaretDemoLogin) DEMO_PATIENT_NAME else "HealthBridge patient"
                                                }
                                                val auth = runCatching {
                                                    authRepository.signIn(
                                                        email = trimmedEmail,
                                                        password = password
                                                    )
                                                }.getOrElse { signInError ->
                                                    if (isMargaretDemoLogin) {
                                                        authRepository.signUp(
                                                            email = DEMO_PATIENT_EMAIL,
                                                            password = DEMO_PATIENT_PASSWORD,
                                                            displayName = DEMO_PATIENT_NAME
                                                        )
                                                    } else {
                                                        throw signInError
                                                    }
                                                }
                                                authRepository.ensurePatientProfile(
                                                    accessToken = auth.accessToken,
                                                    displayName = profileName
                                                )
                                                auth
                                            }
                                        }.onSuccess { auth ->
                                            isLoading = false
                                            onSignedIn(auth)
                                        }.onFailure { throwable ->
                                            isLoading = false
                                            errorMessage = throwable.message
                                                ?: "We could not sign you in. Please try again."
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = when {
                                isLoading && isCreatingAccount -> "Creating account..."
                                isLoading -> "Signing in..."
                                isCreatingAccount -> "Create account"
                                else -> "Sign in"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            isCreatingAccount = !isCreatingAccount
                            errorMessage = null
                            infoMessage = null
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = if (isCreatingAccount) "I already have an account" else "Create new account",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FilledTonalButton(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = "Forgot password?",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Private by design",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "You choose which providers can see your health information.",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 17.sp,
                            lineHeight = 25.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        TextButton(
                            onClick = {},
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "After sign in, we may ask one quick security question.",
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class AppDestination(
    val label: String,
    val icon: ImageVector
) {
    Home("Home", Icons.Filled.Home),
    MyData("My Data", Icons.Filled.Folder),
    Settings("Settings", Icons.Filled.Settings),
    Ai("AI", Icons.Filled.AutoAwesome)
}

private enum class SettingsPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val action: String
) {
    Profile(
        "Profile",
        "Name, birthday, phone number, and emergency contact.",
        Icons.Filled.Person,
        "Update profile"
    ),
    Preferences(
        "Preferences",
        "Text size, language, and how the app should feel.",
        Icons.Filled.Tune,
        "Change preferences"
    ),
    Notifications(
        "Notifications",
        "Reminders for appointments, medicines, and shared records.",
        Icons.Filled.Notifications,
        "Manage notifications"
    ),
    Privacy(
        "Privacy",
        "Provider access, sign-in security, and data sharing choices.",
        Icons.Filled.Lock,
        "Review privacy"
    ),
    About(
        "About",
        "App version, medical disclaimer, and project information.",
        Icons.Filled.Info,
        "View about"
    ),
    HelpSupport(
        "Help & support",
        "Get help signing in or using your health record.",
        Icons.Filled.SupportAgent,
        "Contact support"
    )
}

private data class HealthRecordItem(
    val title: String,
    val detail: String,
    val note: String
)

private val currentPrescriptions = listOf(
    HealthRecordItem("Lisinopril", "10 mg, once daily", "Blood pressure"),
    HealthRecordItem("Metformin", "500 mg, twice daily", "Blood sugar"),
    HealthRecordItem("Atorvastatin", "20 mg at night", "Cholesterol")
)

private val pastPrescriptions = listOf(
    HealthRecordItem("Amoxicillin", "500 mg, ended May 2026", "Sinus infection"),
    HealthRecordItem("Prednisone", "5 day pack, ended Jan 2026", "Inflammation"),
    HealthRecordItem("Ibuprofen", "As needed, stopped Dec 2025", "Back pain")
)

private val allergies = listOf(
    HealthRecordItem("Penicillin", "Rash", "Tell every provider before new medicine"),
    HealthRecordItem("Latex", "Skin irritation", "Use latex-free gloves")
)

private val conditions = listOf(
    HealthRecordItem("High blood pressure", "Managed", "Last reading 128/82"),
    HealthRecordItem("Type 2 diabetes", "Monitoring", "A1C due next visit")
)

private val recentVisits = listOf(
    HealthRecordItem("Primary care visit", "Aug 28, 2026", "Medication review completed"),
    HealthRecordItem("Cardiology follow-up", "Jul 16, 2026", "No urgent changes")
)

@Composable
fun LoggedInHomeScreen(
    qrAccessRepository: QrAccessRepository,
    modifier: Modifier = Modifier
) {
    var selectedDestination by remember { mutableStateOf(AppDestination.Home) }
    var selectedSettingsPage by remember { mutableStateOf<SettingsPage?>(null) }
    val colorScheme = MaterialTheme.colorScheme

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
                                text = "HealthBridge",
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
            NavigationBar(
                containerColor = colorScheme.surfaceContainer
            ) {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedDestination == destination,
                        onClick = {
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
                AppDestination.Home -> HomeTabContent()
                AppDestination.MyData -> MyDataQrScreen(
                    repository = qrAccessRepository
                )
                AppDestination.Settings -> SettingsContent(
                    selectedPage = selectedSettingsPage,
                    onSelectPage = { selectedSettingsPage = it },
                    onBack = { selectedSettingsPage = null }
                )
                AppDestination.Ai -> MyAiScreen()
            }
        }
    }
}

@Composable
private fun SettingsContent(
    selectedPage: SettingsPage?,
    onSelectPage: (SettingsPage) -> Unit,
    onBack: () -> Unit
) {
    if (selectedPage == null) {
        SettingsListScreen(onSelectPage = onSelectPage)
    } else {
        SettingsDetailScreen(
            page = selectedPage,
            onBack = onBack
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
            IconButton(
                onClick = {},
                modifier = Modifier.size(56.dp)
            ) {
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
                SettingsRow(
                    page = page,
                    onClick = { onSelectPage(page) }
                )
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
private fun SettingsRow(
    page: SettingsPage,
    onClick: () -> Unit
) {
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
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(56.dp)
        ) {
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
            SettingsDetailFields(page = page)
        }
    }
}

@Composable
private fun SettingsDetailFields(page: SettingsPage) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (page) {
            SettingsPage.Profile -> {
                SettingsValueRow("Full name", "Mary Johnson")
                SettingsValueRow("Date of birth", "May 14, 1951")
                SettingsValueRow("Phone", "(555) 014-2201")
                SettingsValueRow("Emergency contact", "Daniel Johnson")
                SettingsActionButton("Save profile")
            }
            SettingsPage.Preferences -> {
                AiSettingsPreferences()
                SettingsToggleRow("High contrast mode", false)
                SettingsToggleRow("Use simple visit summaries", true)
                SettingsActionButton("Save preferences")
            }
            SettingsPage.Notifications -> {
                SettingsToggleRow("Medicine reminders", true)
                SettingsToggleRow("Appointment reminders", true)
                SettingsToggleRow("Doctor access alerts", true)
                SettingsToggleRow("Weekly record summary", false)
                SettingsActionButton("Save notifications")
            }
            SettingsPage.Privacy -> {
                SettingsToggleRow("Require approval for every scan", true)
                SettingsToggleRow("Hide sensitive notes by default", true)
                SettingsValueRow("Trusted devices", "This phone only")
                SettingsValueRow("Last password change", "Not set")
                SettingsActionButton("Review access history")
            }
            SettingsPage.About -> {
                SettingsValueRow("App", "HealthBridge")
                SettingsValueRow("Version", "1.0")
                SettingsValueRow("Project", "HackRice 2026")
                Text(
                    text = "HealthBridge helps patients organize and share health information. It does not replace medical advice from a doctor.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 25.sp
                )
            }
            SettingsPage.HelpSupport -> {
                SettingsValueRow("Support hours", "8 AM to 8 PM")
                SettingsValueRow("Email", "support@healthbridge.local")
                SettingsValueRow("Phone help", "(555) 010-1040")
                SettingsActionButton("Contact support")
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
private fun SettingsToggleRow(label: String, initialChecked: Boolean) {
    var checked by remember { mutableStateOf(initialChecked) }
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
        Switch(
            checked = checked,
            onCheckedChange = { checked = it }
        )
    }
}

@Composable
private fun SettingsActionButton(text: String) {
    Button(
        onClick = {},
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
private fun MyDataQrScreen(repository: QrAccessRepository) {
    var token by remember { mutableStateOf<SignedQrToken?>(null) }
    var secondsRemaining by remember { mutableStateOf(FakeQrAccessRepository.TOKEN_TTL_SECONDS) }
    var approvalRequest by remember { mutableStateOf<DoctorScanRequest?>(null) }
    var auditLogs by remember { mutableStateOf<List<AuditLogEntry>>(emptyList()) }
    var statusText by remember { mutableStateOf("Getting secure code...") }
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(repository) {
        while (true) {
            val nextToken = repository.requestSignedAccessToken()
            token = nextToken
            approvalRequest = null
            statusText = "Secure code is live"
            auditLogs = repository.getAuditLog()
            delay(FakeQrAccessRepository.REFRESH_SECONDS * 1000L)
        }
    }

    LaunchedEffect(token?.tokenId) {
        while (token != null) {
            val currentToken = token ?: break
            secondsRemaining = max(
                0,
                ((currentToken.expiresAtMillis - System.currentTimeMillis()) / 1000L).toInt()
            )

            if (approvalRequest == null) {
                approvalRequest = repository.getPendingApprovalRequest(currentToken.tokenId)
                if (approvalRequest != null) {
                    statusText = "Doctor is asking to view your record"
                }
            }

            auditLogs = repository.getAuditLog()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "My Data",
            color = colorScheme.onBackground,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Show this code to your doctor. It changes often and only works after you approve.",
            color = colorScheme.onSurfaceVariant,
            fontSize = 20.sp,
            lineHeight = 30.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(286.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { secondsRemaining / FakeQrAccessRepository.TOKEN_TTL_SECONDS.toFloat() },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round,
                        color = colorScheme.primary,
                        trackColor = colorScheme.surfaceVariant
                    )
                    Surface(
                        modifier = Modifier.size(230.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            token?.signedToken?.let { signedToken ->
                                Image(
                                    bitmap = remember(signedToken) {
                                        generateQrBitmap(signedToken).asImageBitmap()
                                    },
                                    contentDescription = "Short-lived secure QR code for doctor access",
                                    modifier = Modifier.padding(14.dp)
                                )
                            } ?: Text(
                                text = "Loading",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                Text(
                    text = "$secondsRemaining seconds left",
                    color = colorScheme.onSurface,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusText,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                    lineHeight = 26.sp
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "How this protects you",
                    color = colorScheme.onSurface,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "The QR code is not your patient ID. It is a short-lived signed token from the server, and your data stays locked until you approve the request.",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 25.sp
                )
            }
        }

        MyDataSectionCard(
            title = "Current prescriptions",
            items = currentPrescriptions,
            emptyText = "No current prescriptions are listed."
        )

        MyDataSectionCard(
            title = "Past prescriptions",
            items = pastPrescriptions,
            emptyText = "No past prescriptions are listed."
        )

        MyDataSectionCard(
            title = "Allergies",
            items = allergies,
            emptyText = "No allergies are listed."
        )

        MyDataSectionCard(
            title = "Conditions",
            items = conditions,
            emptyText = "No conditions are listed."
        )

        MyDataSectionCard(
            title = "Recent visits",
            items = recentVisits,
            emptyText = "No recent visits are listed."
        )

        AuditLogCard(auditLogs = auditLogs)

        Spacer(modifier = Modifier.height(16.dp))
    }

    approvalRequest?.let { request ->
        PatientApprovalDialog(
            request = request,
            onApprove = {
                statusText = "Approved. Your doctor can view the shared record."
                approvalRequest = null
            },
            onDeny = { timedOut ->
                statusText = if (timedOut) {
                    "Request timed out and was denied."
                } else {
                    "Request denied."
                }
                approvalRequest = null
            },
            submitDecision = { approved ->
                val result = repository.submitApprovalDecision(request.requestId, approved)
                auditLogs = repository.getAuditLog()
                result
            }
        )
    }
}

@Composable
private fun MyDataSectionCard(
    title: String,
    items: List<HealthRecordItem>,
    emptyText: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                color = colorScheme.onSurface,
                fontSize = 23.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold
            )

            if (items.isEmpty()) {
                Text(
                    text = emptyText,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 25.sp
                )
            } else {
                items.forEachIndexed { index, item ->
                    HealthRecordRow(item = item)
                    if (index < items.lastIndex) {
                        HorizontalDivider(color = colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthRecordRow(item: HealthRecordItem) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.title,
            color = colorScheme.onSurface,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = item.detail,
            color = colorScheme.onSurfaceVariant,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = item.note,
            color = colorScheme.primary,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun PatientApprovalDialog(
    request: DoctorScanRequest,
    onApprove: () -> Unit,
    onDeny: (timedOut: Boolean) -> Unit,
    submitDecision: suspend (approved: Boolean) -> ApprovalResult
) {
    var secondsRemaining by remember(request.requestId) {
        mutableStateOf(FakeQrAccessRepository.APPROVAL_TIMEOUT_SECONDS)
    }
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(request.requestId) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining -= 1
        }

        submitDecision(false)
        onDeny(true)
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    tint = colorScheme.primary
                )
                Text(
                    text = "Approve doctor access?",
                    color = colorScheme.onBackground,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    text = "Only approve if this doctor is with you now.",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 20.sp,
                    lineHeight = 30.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ApprovalDetail("Doctor", request.doctorName)
                        ApprovalDetail("Hospital", request.hospitalName)
                        ApprovalDetail("Device", request.deviceInfo)
                    }
                }

                Text(
                    text = "Denying automatically in $secondsRemaining seconds",
                    color = colorScheme.error,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 24.dp)
                )

                Button(
                    onClick = {
                        scope.launch {
                            submitDecision(true)
                            onApprove()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .height(58.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = "Approve",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            submitDecision(false)
                            onDeny(false)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = "Deny",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalDetail(label: String, value: String) {
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
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AuditLogCard(auditLogs: List<AuditLogEntry>) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recent access log",
                color = colorScheme.onSurface,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold
            )

            if (auditLogs.isEmpty()) {
                Text(
                    text = "No doctor scan attempts yet.",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 25.sp
                )
            } else {
                auditLogs.take(3).forEach { log ->
                    Text(
                        text = "${log.approvalResult.name}: ${log.hospitalId}",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 17.sp,
                        lineHeight = 25.sp
                    )
                }
            }
        }
    }
}

private fun generateQrBitmap(content: String): android.graphics.Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
    val bitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)

    for (x in 0 until 512) {
        for (y in 0 until 512) {
            bitmap.setPixel(
                x,
                y,
                if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            )
        }
    }

    return bitmap
}

@Composable
private fun HomeTabContent() {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = timeOfDayGreeting(),
            color = colorScheme.onBackground,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Here is your health record for today.",
            color = colorScheme.onSurfaceVariant,
            fontSize = 20.sp,
            lineHeight = 30.sp
        )

        HomeActionCard(
            title = "My health record",
            message = "Medicines, allergies, conditions, and documents.",
            buttonText = "Open record"
        )
        HomeActionCard(
            title = "Share with a doctor",
            message = "Choose what a provider can see before a visit.",
            buttonText = "Share safely"
        )
        HomeActionCard(
            title = "Upcoming care",
            message = "Keep appointment notes and questions in one place.",
            buttonText = "View care"
        )
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    message: String,
    buttonText: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = colorScheme.onSurface,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                lineHeight = 27.sp
            )
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = buttonText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun timeOfDayGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    return when (hour) {
        in 5..11 -> "Good morning!"
        in 12..16 -> "Good afternoon!"
        else -> "Good evening!"
    }
}

private object PreviewAuthRepository : AuthRepository {
    override suspend fun signIn(email: String, password: String): AuthenticatedPatient {
        return AuthenticatedPatient(accessToken = "preview-token", email = email)
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): AuthenticatedPatient {
        return AuthenticatedPatient(accessToken = "preview-token", email = email)
    }

    override suspend fun ensurePatientProfile(accessToken: String, displayName: String) = Unit
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LoginScreenPreview() {
    RiceHacksTheme(dynamicColor = false) {
        LoginScreen(
            authRepository = PreviewAuthRepository,
            onSignedIn = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LoggedInHomeScreenPreview() {
    RiceHacksTheme(dynamicColor = false) {
        LoggedInHomeScreen(qrAccessRepository = FakeQrAccessRepository())
    }
}
