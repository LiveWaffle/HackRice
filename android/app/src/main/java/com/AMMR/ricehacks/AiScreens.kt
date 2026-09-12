package com.AMMR.ricehacks

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.AMMR.ricehacks.data.*
import com.AMMR.ricehacks.data.AiAgent
import com.AMMR.ricehacks.data.AuthenticatedUser
import com.AMMR.ricehacks.data.HealthAiRepository
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.AMMR.ricehacks.data.*
import com.AMMR.ricehacks.data.AiAgent
import com.AMMR.ricehacks.data.AuthenticatedUser
import com.AMMR.ricehacks.data.HealthAiRepository
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
@Composable
fun AskNoraScreen(
    patientSession: AuthenticatedPatient,
    aiRepository: HealthAiRepository,
    askNoraRepository: AskNoraRepository
) {
    var activeSession by remember { mutableStateOf<AskNoraSession?>(null) }
    var sessions by remember { mutableStateOf<List<AskNoraSession>>(emptyList()) }
    var isLoadingSessions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoadingSessions = true
        runCatching { askNoraRepository.fetchSessions(patientSession.accessToken) }
            .onSuccess { sessions = it }
        isLoadingSessions = false
    }

    if (activeSession != null) {
        BackHandler { activeSession = null }
        ActiveNoraSession(
            session = activeSession!!,
            patientSession = patientSession,
            aiRepository = aiRepository,
            askNoraRepository = askNoraRepository,
            onClose = {
                activeSession = null
                scope.launch {
                    runCatching { askNoraRepository.fetchSessions(patientSession.accessToken) }
                        .onSuccess { sessions = it }
                }
            }
        )
    } else {
        NoraSessionList(
            sessions = sessions,
            isLoading = isLoadingSessions,
            onStartNew = { mode ->
                scope.launch {
                    runCatching { askNoraRepository.createSession(patientSession.accessToken, mode) }
                        .onSuccess { activeSession = it }
                }
            },
            onSelectSession = { activeSession = it }
        )
    }
}

@Composable
fun MyAiScreen(
    patientSession: AuthenticatedUser,
    aiRepository: HealthAiRepository
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var selectedAgent by remember { mutableStateOf(AiAgent.Cara) }
    var question by remember { mutableStateOf("") }
    var extraInstructions by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<Pair<String, AiAgent>?>(null) }
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
            title = "Speak plainly",
            detail = "Answers with short sentences and avoids medical jargon when possible.",
            icon = Icons.Filled.RecordVoiceOver
        ),
        AiFrameworkStep(
            title = "Protect private details",
            detail = "Does not unlock doctor access or share records without your approval.",
            icon = Icons.Filled.PrivacyTip
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Ask Nora",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground
        )
        Text(
            text = "Your personal health companion.",
            fontSize = 18.sp,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { onStartNew(AskNoraMode.Text) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New Chat")
            }
            Button(
                onClick = { onStartNew(AskNoraMode.Voice) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.secondary)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start Call")
            }
        }

        Spacer(Modifier.height(32.dp))

Text(
            text = "Past Conversations",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        answer?.let { (response, agent) ->
            AiAnswerCard(answer = response, agentName = agent.displayName)
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (sessions.isEmpty()) {
            Text("No past conversations yet.", color = colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessions) { session ->
                    Card(
                        onClick = { onSelectSession(session) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (session.initialMode == AskNoraMode.Voice) Icons.Default.Call else Icons.Default.Chat,
                                contentDescription = null,
                                tint = colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (session.initialMode == AskNoraMode.Voice) "Voice Call" else "Text Chat",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatDate(session.createdAt),
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveNoraSession(
    session: AskNoraSession,
    patientSession: AuthenticatedPatient,
    aiRepository: HealthAiRepository,
    askNoraRepository: AskNoraRepository,
    onClose: () -> Unit
) {
    var mode by remember { mutableStateOf(session.initialMode) }
    var turns by remember { mutableStateOf<List<AskNoraTurn>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voiceService = remember { ElevenLabsVoiceService() }
    var voiceSession by remember { mutableStateOf<ConversationSession?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var showTranscriptInVoice by remember { mutableStateOf(false) }

    LaunchedEffect(session.id) {
        runCatching { askNoraRepository.fetchTurns(patientSession.accessToken, session.id) }
            .onSuccess { turns = it }
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                val vs = voiceService.startIntake(
                    context = context,
                    onTranscript = { text ->
                        scope.launch {
                            val turn = AskNoraTurn(AskNoraSpeaker.User, text)
                            askNoraRepository.appendTurn(patientSession.accessToken, session.id, turn)
                            turns = turns + turn
                        }
                    },
                    onAgentResponse = { text ->
                        scope.launch {
                            val turn = AskNoraTurn(AskNoraSpeaker.Assistant, text)
                            askNoraRepository.appendTurn(patientSession.accessToken, session.id, turn)
                            turns = turns + turn
                        }
                    }
                )
                voiceSession = vs
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask Nora") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    val label = if (mode == AskNoraMode.Text) "Switch to Voice" else "Switch to Text"
                    val icon = if (mode == AskNoraMode.Text) Icons.Default.Mic else Icons.Default.Chat
                    TextButton(onClick = {
                        if (mode == AskNoraMode.Voice) {
                            scope.launch {
                                voiceSession?.let { voiceService.end(it) }
                                voiceSession = null
                            }
                        }
                        mode = if (mode == AskNoraMode.Text) AskNoraMode.Voice else AskNoraMode.Text
                    }) {
                        Icon(icon, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (mode == AskNoraMode.Text) {
                TextChatView(
                    turns = turns,
                    onSend = { text ->
                        scope.launch {
                            val userTurn = AskNoraTurn(AskNoraSpeaker.User, text)
                            turns = turns + userTurn
                            askNoraRepository.appendTurn(patientSession.accessToken, session.id, userTurn)
                            
                            runCatching { aiRepository.askQuestion(patientSession.accessToken, text) }
                                .onSuccess { response ->
                                    val aiTurn = AskNoraTurn(AskNoraSpeaker.Assistant, response.answer)
                                    turns = turns + aiTurn
                                    askNoraRepository.appendTurn(patientSession.accessToken, session.id, aiTurn)
                                }
                        }
                    }
                )
            } else {
                VoiceCallView(
                    turns = turns,
                    isActive = voiceSession != null,
                    isMuted = isMuted,
                    showTranscript = showTranscriptInVoice,
                    onToggleTranscript = { showTranscriptInVoice = it },
                    onStart = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            scope.launch {
                                val vs = voiceService.startIntake(
                                    context = context,
                                    onTranscript = { text ->
                                        scope.launch {
                                            val turn = AskNoraTurn(AskNoraSpeaker.User, text)
                                            askNoraRepository.appendTurn(patientSession.accessToken, session.id, turn)
                                            turns = turns + turn
                                        }
                                    },
                                    onAgentResponse = { text ->
                                        scope.launch {
                                            val turn = AskNoraTurn(AskNoraSpeaker.Assistant, text)
                                            askNoraRepository.appendTurn(patientSession.accessToken, session.id, turn)
                                            turns = turns + turn
                                        }
                                    }
                                )
                                voiceSession = vs
                            }
                        } else {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onEnd = {
                        scope.launch {
                            voiceSession?.let { voiceService.end(it) }
                            voiceSession = null
                            askNoraRepository.endSession(patientSession.accessToken, session.id)
                            onClose()
                        }
                    },
                    onMute = { isMuted = !isMuted }
                )
            }
        }
    }
}

@Composable
fun TextChatView(
    turns: List<AskNoraTurn>,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(turns) { turn ->
                ChatBubble(turn)
            }
        }

        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask Nora anything...") },
                    maxLines = 4,
                    shape = MaterialTheme.shapes.large
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSend(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(turn: AskNoraTurn) {
    val isUser = turn.speaker == AskNoraSpeaker.User
    val colorScheme = MaterialTheme.colorScheme
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) colorScheme.primary else colorScheme.secondaryContainer,
            contentColor = if (isUser) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = if (isUser) CornerSize(0.dp) else CornerSize(16.dp),
                bottomStart = if (isUser) CornerSize(16.dp) else CornerSize(0.dp)
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = turn.text,
                modifier = Modifier.padding(12.dp),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun VoiceCallView(
    turns: List<AskNoraTurn>,
    isActive: Boolean,
    isMuted: Boolean,
    showTranscript: Boolean,
    onToggleTranscript: (Boolean) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onMute: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (showTranscript) {
            val listState = rememberLazyListState()
            LaunchedEffect(turns.size) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1) }
            
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)
            ) {
                items(turns) { turn ->
                    Text(
                        text = "${if (turn.speaker == AskNoraSpeaker.User) "You: " else "Nora: "}${turn.text}",
                        fontSize = 18.sp,
                        fontWeight = if (turn.speaker == AskNoraSpeaker.Assistant) FontWeight.Bold else FontWeight.Normal,
                        color = if (turn.speaker == AskNoraSpeaker.Assistant) colorScheme.primary else colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                VoiceVisualizer(isActive = isActive)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMute,
                    modifier = Modifier.size(64.dp).background(colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Mute", modifier = Modifier.size(32.dp))
                }

                FloatingActionButton(
                    onClick = if (isActive) onEnd else onStart,
                    containerColor = if (isActive) Color.Red else colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        if (isActive) Icons.Default.CallEnd else Icons.Default.Call,
                        contentDescription = if (isActive) "End Call" else "Start Call",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { onToggleTranscript(!showTranscript) },
                    modifier = Modifier.size(64.dp).background(colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(if (showTranscript) Icons.Default.Image else Icons.Default.Description, contentDescription = "Toggle Transcript", modifier = Modifier.size(32.dp))
                }
            }
            
            Text(
                text = if (isActive) "Nora is listening..." else "Tap the phone to start",
                fontSize = 18.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VoiceVisualizer(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isActive) 0.1f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(contentAlignment = Alignment.Center) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
        Surface(
            modifier = Modifier.size(160.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 8.dp
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.padding(40.dp).fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatDate(dateString: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = parser.parse(dateString)
        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        date?.let { formatter.format(it) } ?: dateString
    } catch (ignored: Exception) {
        dateString
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
        detail = "Choose the language Cara uses for AI answers.",
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
private fun AiAnswerCard(answer: String, agentName: String) {
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
                text = "$agentName says",
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
