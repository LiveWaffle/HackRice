package com.AMMR.ricehacks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.AMMR.ricehacks.data.AuthRepository
import com.AMMR.ricehacks.data.AuthenticatedPatient
import com.AMMR.ricehacks.ui.theme.RiceHacksTheme
import kotlinx.coroutines.launch

private const val DEMO_PATIENT_NAME = "Margaret Chen"
private const val DEMO_PATIENT_EMAIL = "gilliamandrew22@gmail.com"
private const val DEMO_PATIENT_PASSWORD = "HealthBridge1943!"

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
                    AppHeader()

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

                    LoginActions(
                        isCreatingAccount = isCreatingAccount,
                        isLoading = isLoading,
                        onPrimaryClick = {
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
                                            signInOrCreatePatient(
                                                authRepository = authRepository,
                                                email = trimmedEmail,
                                                password = password,
                                                displayName = trimmedName,
                                                isCreatingAccount = isCreatingAccount
                                            )
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
                        onToggleCreateAccount = {
                            isCreatingAccount = !isCreatingAccount
                            errorMessage = null
                            infoMessage = null
                        }
                    )
                }

                PrivacyCard()
            }
        }
    }
}

@Composable
private fun AppHeader() {
    val colorScheme = MaterialTheme.colorScheme

    Row(verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
private fun LoginActions(
    isCreatingAccount: Boolean,
    isLoading: Boolean,
    onPrimaryClick: () -> Unit,
    onToggleCreateAccount: () -> Unit
) {
    Button(
        onClick = onPrimaryClick,
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
        onClick = onToggleCreateAccount,
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

@Composable
private fun PrivacyCard() {
    val colorScheme = MaterialTheme.colorScheme

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

private suspend fun signInOrCreatePatient(
    authRepository: AuthRepository,
    email: String,
    password: String,
    displayName: String,
    isCreatingAccount: Boolean
): AuthenticatedPatient {
    if (isCreatingAccount) {
        val auth = authRepository.signUp(
            email = email,
            password = password,
            displayName = displayName
        )
        authRepository.ensurePatientProfile(
            accessToken = auth.accessToken,
            displayName = displayName
        )
        return auth
    }

    val isMargaretDemoLogin = email.equals(DEMO_PATIENT_EMAIL, ignoreCase = true)
    val profileName = displayName.ifBlank {
        if (isMargaretDemoLogin) DEMO_PATIENT_NAME else "HealthBridge patient"
    }
    val auth = runCatching {
        authRepository.signIn(email = email, password = password)
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

    return auth
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
