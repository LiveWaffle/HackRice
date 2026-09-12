package com.AMMR.ricehacks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.AMMR.ricehacks.data.AuthenticatedUser
import com.AMMR.ricehacks.data.HealthAiAnswer
import com.AMMR.ricehacks.data.HealthAiRepository
import kotlinx.coroutines.launch

@Composable
fun MyAiScreen(
    patientSession: AuthenticatedUser,
    aiRepository: HealthAiRepository
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<HealthAiAnswer?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val starterQuestions = listOf(
        "What should I ask my doctor?",
        "Explain my medicines simply",
        "What changed since my last visit?"
    )
    val frameworkSteps = listOf(
        AiFrameworkStep(
            title = "Read approved data",
            detail = "Uses prescriptions, allergies, conditions, and recent visits after you sign in.",
            icon = Icons.Filled.Medication
        ),
        AiFrameworkStep(
            title = "Stay medical",
            detail = "Answers health, symptom, treatment, medication, and record questions only.",
            icon = Icons.Filled.RecordVoiceOver
        ),
        AiFrameworkStep(
            title = "Escalate urgent needs",
            detail = "Flags urgent symptoms and questions that require a licensed professional.",
            icon = Icons.Filled.PrivacyTip
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "My AI",
            color = colorScheme.onBackground,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Ask simple questions about your health record before a visit.",
            color = colorScheme.onSurfaceVariant,
            fontSize = 20.sp,
            lineHeight = 30.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Talk to Nora",
                    color = colorScheme.onPrimaryContainer,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Type or choose a medical question. Nora will answer using your saved record first.",
                    color = colorScheme.onPrimaryContainer,
                    fontSize = 18.sp,
                    lineHeight = 26.sp
                )
                OutlinedTextField(
                    value = question,
                    onValueChange = {
                        question = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your question for Nora") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.large
                )



                Button(
                    onClick = {
                        val trimmedQuestion = question.trim()
                        if (trimmedQuestion.isBlank()) {
                            errorMessage = "Please enter a question."
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null
                        Log.d("NoraDebug", "Asking Nora: $trimmedQuestion")
                        scope.launch {
                            runCatching {
                                aiRepository.askQuestion(
                                    patientSessionToken = patientSession.accessToken,
                                    message = trimmedQuestion
                                )
                            }.onSuccess { response ->
                                Log.d("NoraDebug", "AI answer received from Nora")
                                answer = response
                                isLoading = false
                            }.onFailure { throwable ->
                                Log.e("NoraDebug", "AI error from Nora: ${throwable.message}")
                                errorMessage = throwable.message ?: "The AI helper is not ready yet."
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = if (isLoading) "Asking..." else "Ask Nora",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = colorScheme.error,
                fontSize = 17.sp,
                lineHeight = 24.sp
            )
        }

        answer?.let { response ->
            AiAnswerCard(answer = response.answer, flagged = response.flagged)
        }

        starterQuestions.forEach { starterQuestion ->
            AiQuestionCard(
                question = starterQuestion,
                onClick = {
                    question = starterQuestion
                    errorMessage = null
                }
            )
        }

        Text(
            text = "How it works",
            color = colorScheme.onBackground,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )

        frameworkSteps.forEach { step ->
            AiFrameworkCard(step = step)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AiSettingsPreferences() {
    var volume by remember { mutableFloatStateOf(0.75f) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var selectedVoice by remember { mutableStateOf("Warm") }
    var simpleAnswers by remember { mutableStateOf(true) }
    var readAnswersAloud by remember { mutableStateOf(true) }
    val languages = listOf("English", "Spanish", "Mandarin")
    val voices = listOf("Warm", "Calm", "Bright")

    SettingsValueRow("Text size", "Large")
    SettingsValueRow("Language", selectedLanguage)

    PreferenceControlCard(
        title = "Voice volume",
        detail = "${(volume * 100).toInt()} percent",
        icon = Icons.AutoMirrored.Filled.VolumeUp
    ) {
        Slider(
            value = volume,
            onValueChange = { volume = it },
            modifier = Modifier.fillMaxWidth()
        )
    }

    PreferenceControlCard(
        title = "Language options",
        detail = "Choose the language Nora uses for AI answers.",
        icon = Icons.Filled.Translate
    ) {
        ChipColumn(
            options = languages,
            selected = selectedLanguage,
            onSelected = { selectedLanguage = it }
        )
    }

    PreferenceControlCard(
        title = "Voice options",
        detail = "Choose the voice style for spoken answers.",
        icon = Icons.Filled.RecordVoiceOver
    ) {
        ChipColumn(
            options = voices,
            selected = selectedVoice,
            onSelected = { selectedVoice = it }
        )
    }

    AiToggleRow(
        label = "Use simple AI answers",
        checked = simpleAnswers,
        onCheckedChange = { simpleAnswers = it }
    )
    AiToggleRow(
        label = "Read AI answers aloud",
        checked = readAnswersAloud,
        onCheckedChange = { readAnswersAloud = it }
    )
}

@Composable
private fun AiAnswerCard(answer: String, flagged: Boolean) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (flagged) "Nora flagged this" else "Nora says",
                color = colorScheme.onSecondaryContainer,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = answer,
                color = colorScheme.onSecondaryContainer,
                fontSize = 18.sp,
                lineHeight = 27.sp
            )
        }
    }
}

@Composable
private fun AiQuestionCard(
    question: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = colorScheme.primary
            )
            Text(
                text = question,
                color = colorScheme.onSurface,
                fontSize = 20.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AiFrameworkCard(step: AiFrameworkStep) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                color = colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = colorScheme.onSecondaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.title,
                    color = colorScheme.onSurface,
                    fontSize = 21.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = step.detail,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PreferenceControlCard(
    title: String,
    detail: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = colorScheme.onSurface,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = detail,
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun ChipColumn(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = option,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    }
}

@Composable
private fun AiToggleRow(
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private data class AiFrameworkStep(
    val title: String,
    val detail: String,
    val icon: ImageVector
)
