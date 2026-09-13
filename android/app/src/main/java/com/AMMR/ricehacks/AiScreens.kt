package com.AMMR.ricehacks

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.AMMR.ricehacks.data.AskNoraMode
import com.AMMR.ricehacks.data.AskNoraRepository
import com.AMMR.ricehacks.data.AskNoraSession
import com.AMMR.ricehacks.data.AskNoraSpeaker
import com.AMMR.ricehacks.data.AskNoraTurn
import com.AMMR.ricehacks.data.AuthenticatedUser
import com.AMMR.ricehacks.data.ElevenLabsVoiceService
import com.AMMR.ricehacks.data.HealthAiRepository
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AskNoraScreen(
    patientSession: AuthenticatedUser,
    aiRepository: HealthAiRepository,
    askNoraRepository: AskNoraRepository,
) {
    var activeSession by remember { mutableStateOf<AskNoraSession?>(null) }
    var sessions by remember { mutableStateOf<List<AskNoraSession>>(emptyList()) }
    var isLoadingSessions by remember { mutableStateOf(false) }
    var isCreatingSession by remember { mutableStateOf(false) }
    var sessionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoadingSessions = true
        runCatching { askNoraRepository.fetchSessions(patientSession.accessToken) }
            .onSuccess {
                sessions = it
                sessionError = null
            }
            .onFailure { sessionError = it.message ?: "Could not load conversations." }
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
            isCreating = isCreatingSession,
            errorMessage = sessionError,
            onStartNew = { mode ->
                scope.launch {
                    isCreatingSession = true
                    sessionError = null
                    runCatching { askNoraRepository.createSession(patientSession.accessToken, mode) }
                        .onSuccess { session ->
                            sessions = listOf(session) + sessions
                            activeSession = session
                        }
                        .onFailure { sessionError = it.message ?: "Could not create a new conversation." }
                    isCreatingSession = false
                }
            },
            onSelectSession = { activeSession = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoraSessionList(
    sessions: List<AskNoraSession>,
    isLoading: Boolean,
    isCreating: Boolean,
    errorMessage: String?,
    onStartNew: (AskNoraMode) -> Unit,
    onSelectSession: (AskNoraSession) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            color = colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                onClick = { onStartNew(AskNoraMode.Text) },
                colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Chat")
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                onClick = { onStartNew(AskNoraMode.Voice) },
                colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Call")
                }
            }
        }

        errorMessage?.let {
            Text(
                text = it,
                color = colorScheme.error,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Conversations",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (isLoading || isCreating) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (sessions.isEmpty()) {
            Text("No past conversations yet.", color = colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessions) { session ->
                    NoraConversationRow(
                        session = session,
                        onClick = { onSelectSession(session) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoraConversationRow(
    session: AskNoraSession,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val modeLabel = if (session.initialMode == AskNoraMode.Voice) "Voice" else "Chat"

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (session.initialMode == AskNoraMode.Voice) Icons.Default.Call else Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    color = colorScheme.onSurface,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = session.lastMessage ?: "$modeLabel started ${formatDate(session.createdAt)}",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = modeLabel,
                color = colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveNoraSession(
    session: AskNoraSession,
    patientSession: AuthenticatedUser,
    aiRepository: HealthAiRepository,
    askNoraRepository: AskNoraRepository,
    onClose: () -> Unit,
) {
    var mode by remember { mutableStateOf(session.initialMode) }
    var turns by remember { mutableStateOf<List<AskNoraTurn>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voiceService = remember { ElevenLabsVoiceService() }
    var voiceSession by remember { mutableStateOf<ConversationSession?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var showTranscriptInVoice by remember { mutableStateOf(false) }
    var isNoraTyping by remember { mutableStateOf(false) }

    LaunchedEffect(session.id) {
        runCatching { askNoraRepository.fetchTurns(patientSession.accessToken, session.id) }
            .onSuccess { turns = it }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                val sessionInstance = voiceService.startIntake(
                    context = context,
                    accessToken = patientSession.accessToken,
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
                voiceSession = sessionInstance
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
                    val icon = if (mode == AskNoraMode.Text) Icons.Default.Mic else Icons.AutoMirrored.Filled.Chat
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
                    isTyping = isNoraTyping,
                    onSend = { text ->
                        scope.launch {
                            val shouldTitleSession = turns.isEmpty()
                            val userTurn = AskNoraTurn(AskNoraSpeaker.User, text)
                            turns = turns + userTurn
                            runCatching {
                                askNoraRepository.appendTurn(patientSession.accessToken, session.id, userTurn)
                            }
                            if (shouldTitleSession) {
                                runCatching {
                                    askNoraRepository.updateSessionTitle(
                                        patientSession.accessToken,
                                        session.id,
                                        titleFromMessage(text)
                                    )
                                }
                            }

                            isNoraTyping = true
                            runCatching { aiRepository.askQuestion(patientSession.accessToken, text) }
                                .onSuccess { response ->
                                    isNoraTyping = false
                                    val aiTurn = AskNoraTurn(AskNoraSpeaker.Assistant, response.answer)
                                    turns = turns + aiTurn
                                    runCatching {
                                        askNoraRepository.appendTurn(patientSession.accessToken, session.id, aiTurn)
                                    }
                                }
                                .onFailure { throwable ->
                                    isNoraTyping = false
                                    val message = throwable.message
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "Nora could not respond right now. Check that the backend is running and configured."
                                    val errorTurn = AskNoraTurn(
                                        AskNoraSpeaker.Assistant,
                                        "Nora could not respond: $message"
                                    )
                                    turns = turns + errorTurn
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
                                val startedSession = voiceService.startIntake(
                                    context = context,
                                    accessToken = patientSession.accessToken,
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
                                voiceSession = startedSession
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
                    onMute = {
                        scope.launch {
                            val nextMuted = !isMuted
                            voiceSession?.let { voiceService.setMuted(it, nextMuted) }
                            isMuted = nextMuted
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TextChatView(
    turns: List<AskNoraTurn>,
    isTyping: Boolean,
    onSend: (String) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size, isTyping) {
        val targetIndex = if (isTyping) turns.size else turns.size - 1
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(turns) { turn ->
                ChatBubble(turn)
            }
            if (isTyping) {
                item {
                    TypingIndicatorBubble()
                }
            }
        }

        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
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
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = CornerSize(16.dp),
                bottomStart = CornerSize(0.dp)
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = colorScheme.onSecondaryContainer,
                    strokeWidth = 2.dp
                )
                Text("Nora is typing...", fontSize = 16.sp)
            }
        }
    }
}

fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val regex = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|_(.*?)_")
        val matches = regex.findAll(text)
        for (match in matches) {
            append(text.substring(currentIndex, match.range.first))
            when {
                match.groups[1] != null -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(match.groups[1]!!.value)
                    }
                }
                match.groups[2] != null -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(match.groups[2]!!.value)
                    }
                }
                match.groups[3] != null -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(match.groups[3]!!.value)
                    }
                }
            }
            currentIndex = match.range.last + 1
        }
        append(text.substring(currentIndex, text.length))
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
                text = parseMarkdown(turn.text),
                modifier = Modifier.padding(12.dp),
                fontSize = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallView(
    turns: List<AskNoraTurn>,
    isActive: Boolean,
    isMuted: Boolean,
    showTranscript: Boolean,
    onToggleTranscript: (Boolean) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onMute: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (showTranscript) {
            val listState = rememberLazyListState()
            LaunchedEffect(turns.size) {
                if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                items(turns) { turn ->
                    Text(
                        text = buildAnnotatedString {
                            append(if (turn.speaker == AskNoraSpeaker.User) "You: " else "Nora: ")
                            append(parseMarkdown(turn.text))
                        },
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMute,
                    modifier = Modifier
                        .size(64.dp)
                        .background(colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        modifier = Modifier.size(32.dp)
                    )
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
                    modifier = Modifier
                        .size(64.dp)
                        .background(colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        if (showTranscript) Icons.Default.Image else Icons.Default.Description,
                        contentDescription = "Toggle Transcript",
                        modifier = Modifier.size(32.dp)
                    )
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
    val infiniteTransition = rememberInfiniteTransition(label = "voice_visualizer")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "visualizer_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isActive) 0.1f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "visualizer_alpha"
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
                modifier = Modifier
                    .padding(40.dp)
                    .fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun titleFromMessage(message: String): String {
    val cleaned = message.trim().replace(Regex("\\s+"), " ")
    return if (cleaned.length <= 42) cleaned else "${cleaned.take(39)}..."
}

private fun formatDate(dateString: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = parser.parse(dateString)
        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        date?.let { formatter.format(it) } ?: dateString
    } catch (_: Exception) {
        dateString
    }
}

data class AiFrameworkStep(
    val title: String,
    val detail: String,
    val icon: ImageVector,
)

@Composable
fun AiSettingsPreferences() {
    var volume by remember { mutableStateOf(0.75f) }
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
        icon = Icons.Default.Call
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
        icon = Icons.Default.Mic
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
private fun PreferenceControlCard(
    title: String,
    detail: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
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
    onSelected: (String) -> Unit,
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
    onCheckedChange: (Boolean) -> Unit,
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
