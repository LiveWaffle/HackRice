package com.AMMR.ricehacks

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.FilterChipDefaults
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
    defaultVoiceLanguageCode: String = "en",
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
            defaultVoiceLanguageCode = defaultVoiceLanguageCode,
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

private const val DEFAULT_NORA_OPENING_MESSAGE = "Hi, I’m Nora. Tell me about your concern, question, or request and I’ll help you work through it."

private val TEXT_CHAT_QUICK_REPLIES = listOf(
    "I have a new symptom to report",
    "I want to ask about a medication",
    "I have a question about a recent visit",
    "Just checking in"
)

enum class NoraVoiceState {
    Connecting,
    Listening,
    Speaking
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
    defaultVoiceLanguageCode: String = "en",
    onClose: () -> Unit,
) {
    var mode by remember { mutableStateOf(session.initialMode) }
    var turns by remember { mutableStateOf<List<AskNoraTurn>>(emptyList()) }
    var sessionSummary by remember { mutableStateOf<String?>(null) }
    val voiceLanguageCode = defaultVoiceLanguageCode.takeIf { it.isNotBlank() } ?: "en"
    val voicePatientName = patientSession.displayName
        ?: patientSession.email?.substringBefore('@')
        ?: "Patient"
    var currentVoiceState by remember { mutableStateOf(NoraVoiceState.Listening) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voiceService = remember { ElevenLabsVoiceService() }
    var voiceSession by remember { mutableStateOf<ConversationSession?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var showTranscriptInVoice by remember { mutableStateOf(false) }
    var isNoraTyping by remember { mutableStateOf(false) }
    var showVoiceSetupOnce by remember { mutableStateOf(true) }
    var isStartingVoiceCall by remember { mutableStateOf(false) }
    val isSpeakingVoice = currentVoiceState == NoraVoiceState.Speaking
    val staticNoraResponseForUserMessage: (String) -> String? = { message ->
        val normalized = message.trim()
        if (normalized.equals("I have a new symptom to report", ignoreCase = true)) {
            "Thanks for telling me. Just so you know, I'm not a doctor and can't diagnose anything — but I can help you figure out what kind of care might make sense and help you put it into words for your provider.\n\nWhat's going on? And roughly how long has it been happening?"
        } else {
            null
        }
    }

    val saveTextSessionSummaryIfNeeded: suspend () -> Unit = {
        if (mode == AskNoraMode.Text) {
            val summaryText = sessionSummary?.takeIf { it.isNotBlank() }
                ?: turns.takeIf { it.isNotEmpty() }?.let { transcriptTurns ->
                    val transcript = transcriptTurns.joinToString("\n") { "${if (it.speaker == AskNoraSpeaker.User) "User" else "Nora"}: ${it.text}" }
                    runCatching { aiRepository.summarizeConversation(patientSession.accessToken, transcript) }
                        .getOrNull()
                }

            if (!summaryText.isNullOrBlank()) {
                sessionSummary = summaryText
                runCatching { askNoraRepository.updateSessionSummary(patientSession.accessToken, session.id, summaryText) }
            }
        }
    }

    LaunchedEffect(session.id) {
        sessionSummary = session.summary
        runCatching { askNoraRepository.fetchTurns(patientSession.accessToken, session.id) }
            .onSuccess { fetchedTurns ->
                turns = fetchedTurns
                if (fetchedTurns.isEmpty() && mode == AskNoraMode.Text) {
                    val opener = AskNoraTurn(AskNoraSpeaker.Assistant, DEFAULT_NORA_OPENING_MESSAGE)
                    runCatching { askNoraRepository.appendTurn(patientSession.accessToken, session.id, opener) }
                    turns = listOf(opener)
                }
                if (sessionSummary == null && mode == AskNoraMode.Text && fetchedTurns.isNotEmpty()) {
                    val transcript = fetchedTurns.joinToString("\n") { "${if (it.speaker == AskNoraSpeaker.User) "User" else "Nora"}: ${it.text}" }
                    runCatching {
                        val summary = aiRepository.summarizeConversation(patientSession.accessToken, transcript)
                        sessionSummary = summary
                        askNoraRepository.updateSessionSummary(patientSession.accessToken, session.id, summary)
                    }
                }
                if (sessionSummary == null && mode == AskNoraMode.Voice && !session.conversationId.isNullOrBlank()) {
                    runCatching {
                        val summary = voiceService.fetchConversationSummary(patientSession.accessToken, session.conversationId!!)
                        if (!summary.isNullOrBlank()) {
                            sessionSummary = summary
                            askNoraRepository.updateSessionSummary(patientSession.accessToken, session.id, summary)
                        }
                    }
                }
            }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            currentVoiceState = NoraVoiceState.Connecting
            scope.launch {
                runCatching {
                    voiceService.startIntake(
                        context = context,
                        accessToken = patientSession.accessToken,
                        language = voiceLanguageCode,
                        patientName = voicePatientName,
                        patientId = patientSession.userId,
                        onTranscript = { text ->
                            scope.launch {
                                val cleanText = sanitizeTranscriptText(text)
                                val turn = AskNoraTurn(AskNoraSpeaker.User, cleanText)
                                askNoraRepository.appendTurn(patientSession.accessToken, session.id, turn)
                                turns = turns + turn
                            }
                        },
                        onAgentResponse = { text ->
                            scope.launch {
                                val cleanText = sanitizeTranscriptText(text)
                                val turn = AskNoraTurn(AskNoraSpeaker.Assistant, cleanText)
                                askNoraRepository.appendTurn(patientSession.accessToken, session.id, turn)
                                turns = turns + turn
                            }
                        },
                        onIntakeDraft = { draft ->
                            scope.launch {
                                runCatching {
                                    askNoraRepository.submitIntakeDraft(
                                        patientSession.accessToken,
                                        session.id,
                                        draft.copy(patientId = patientSession.userId ?: draft.patientId)
                                    )
                                }
                            }
                        },
                        onSessionStatusChange = { status ->
                            val normalized = status.lowercase()
                            currentVoiceState = when {
                                normalized.contains("connect") -> NoraVoiceState.Connecting
                                normalized.contains("error") || normalized.contains("disconnect") -> NoraVoiceState.Listening
                                else -> NoraVoiceState.Listening
                            }
                        },
                        onSpeakingStateChange = { speaking ->
                            currentVoiceState = if (speaking) NoraVoiceState.Speaking else NoraVoiceState.Listening
                        },
                        onConversationIdReady = { conversationId ->
                            scope.launch {
                                runCatching {
                                    askNoraRepository.updateSessionConversationId(patientSession.accessToken, session.id, conversationId)
                                }
                            }
                        }
                    )
                }.onSuccess { startedSession ->
                    voiceSession = startedSession
                    voiceError = null
                    currentVoiceState = NoraVoiceState.Listening
                }.onFailure { throwable ->
                    Log.e("NoraVoice", "Unable to start ElevenLabs session", throwable)
                    voiceError = throwable.localizedMessage
                        ?: throwable.message
                        ?: "Could not connect to Nora’s voice assistant."
                    voiceSession = null
                    currentVoiceState = NoraVoiceState.Listening
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask Nora") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (mode == AskNoraMode.Text) {
                            scope.launch { saveTextSessionSummaryIfNeeded() }
                        }
                        onClose()
                    }) {
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
                Column(modifier = Modifier.fillMaxSize()) {
                    if (sessionSummary != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Conversation summary",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = sessionSummary ?: "",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }

                    TextChatView(
                        turns = turns,
                        isTyping = isNoraTyping,
                        quickReplies = if (turns.size <= 1) TEXT_CHAT_QUICK_REPLIES else emptyList(),
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
                                val staticReply = staticNoraResponseForUserMessage(text)
                                if (staticReply != null) {
                                    isNoraTyping = false
                                    val aiTurn = AskNoraTurn(AskNoraSpeaker.Assistant, staticReply)
                                    turns = turns + aiTurn
                                    runCatching {
                                        askNoraRepository.appendTurn(patientSession.accessToken, session.id, aiTurn)
                                    }
                                } else {
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
                        },
                        onQuickReply = { text ->
                            scope.launch {
                                val userTurn = AskNoraTurn(AskNoraSpeaker.User, text)
                                turns = turns + userTurn
                                runCatching { askNoraRepository.appendTurn(patientSession.accessToken, session.id, userTurn) }
                                isNoraTyping = true
                                val staticReply = staticNoraResponseForUserMessage(text)
                                if (staticReply != null) {
                                    isNoraTyping = false
                                    val aiTurn = AskNoraTurn(AskNoraSpeaker.Assistant, staticReply)
                                    turns = turns + aiTurn
                                    runCatching {
                                        askNoraRepository.appendTurn(patientSession.accessToken, session.id, aiTurn)
                                    }
                                } else {
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
                                            turns = turns + AskNoraTurn(
                                                AskNoraSpeaker.Assistant,
                                                "Nora could not respond: $message"
                                            )
                                        }
                                }
                            }
                        }
                    )
                }
            } else {
                if (showVoiceSetupOnce) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Nora voice",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                                Text(
                                    text = "Using the default English voice for this session.",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 16.sp
                                )
                                Button(
                                    onClick = {
                                        showVoiceSetupOnce = false
                                        isStartingVoiceCall = true
                                        currentVoiceState = NoraVoiceState.Connecting
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            scope.launch {
                                                runCatching {
                                                    voiceService.startIntake(
                                                        context = context,
                                                        accessToken = patientSession.accessToken,
                                                        language = voiceLanguageCode,
                                                        patientName = voicePatientName,
                                                        patientId = patientSession.userId,
                                                        onTranscript = { text ->
                                                            scope.launch {
                                                                val cleanText = sanitizeTranscriptText(text)
                                                                val turn = AskNoraTurn(AskNoraSpeaker.User, cleanText)
                                                                askNoraRepository.appendTurn(patientSession.accessToken, session.id, turn)
                                                                turns = turns + turn
                                                            }
                                                        },
                                                        onAgentResponse = { text ->
                                                            scope.launch {
                                                                val cleanText = sanitizeTranscriptText(text)
                                                                val turn = AskNoraTurn(AskNoraSpeaker.Assistant, cleanText)
                                                                askNoraRepository.appendTurn(patientSession.accessToken, session.id, turn)
                                                                turns = turns + turn
                                                            }
                                                        },
                                                        onIntakeDraft = { draft ->
                                                            scope.launch {
                                                                runCatching {
                                                                    askNoraRepository.submitIntakeDraft(
                                                                        patientSession.accessToken,
                                                                        session.id,
                                                                        draft.copy(patientId = patientSession.userId ?: draft.patientId)
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onSessionStatusChange = { status ->
                                                            val normalized = status.lowercase()
                                                            currentVoiceState = when {
                                                                normalized.contains("connect") -> NoraVoiceState.Connecting
                                                                else -> NoraVoiceState.Listening
                                                            }
                                                        },
                                                        onSpeakingStateChange = { speaking ->
                                                            currentVoiceState = if (speaking) NoraVoiceState.Speaking else NoraVoiceState.Listening
                                                        },
                                                        onConversationIdReady = { conversationId ->
                                                            scope.launch {
                                                                runCatching {
                                                                    askNoraRepository.updateSessionConversationId(patientSession.accessToken, session.id, conversationId)
                                                                }
                                                            }
                                                        }
                                                    )
                                                }.onSuccess { startedSession ->
                                                    voiceSession = startedSession
                                                    voiceError = null
                                                    currentVoiceState = NoraVoiceState.Listening
                                                }.onFailure { throwable ->
                                                    Log.e("NoraVoice", "Unable to start ElevenLabs session", throwable)
                                                    voiceError = throwable.localizedMessage
                                                        ?: throwable.message
                                                        ?: "Could not connect to Nora’s voice assistant."
                                                    voiceSession = null
                                                    currentVoiceState = NoraVoiceState.Listening
                                                }
                                                isStartingVoiceCall = false
                                            }
                                        } else {
                                            isStartingVoiceCall = false
                                            voiceError = null
                                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Start call")
                                }
                            }
                        }
                    }
                } else {
                    VoiceCallView(
                        turns = turns,
                        isActive = voiceSession != null,
                        isMuted = isMuted,
                        showTranscript = true,
                        errorMessage = voiceError,
                        onToggleTranscript = { },
                        onStart = {
                            isStartingVoiceCall = true
                            currentVoiceState = NoraVoiceState.Connecting
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                scope.launch {
                                    runCatching {
                                        voiceService.startIntake(
                                            context = context,
                                            accessToken = patientSession.accessToken,
                                            language = voiceLanguageCode,
                                            patientName = voicePatientName,
                                            patientId = patientSession.userId,
                                            onTranscript = { text ->
                                                scope.launch {
                                                    val cleanText = sanitizeTranscriptText(text)
                                                    val turn = AskNoraTurn(AskNoraSpeaker.User, cleanText)
                                                    askNoraRepository.appendTurn(patientSession.accessToken, session.id, turn)
                                                    turns = turns + turn
                                                }
                                            },
                                            onAgentResponse = { text ->
                                                scope.launch {
                                                    val cleanText = sanitizeTranscriptText(text)
                                                    val turn = AskNoraTurn(AskNoraSpeaker.Assistant, cleanText)
                                                    askNoraRepository.appendTurn(patientSession.accessToken, session.id, turn)
                                                    turns = turns + turn
                                                }
                                            },
                                            onIntakeDraft = { draft ->
                                                scope.launch {
                                                    runCatching {
                                                        askNoraRepository.submitIntakeDraft(
                                                            patientSession.accessToken,
                                                            session.id,
                                                            draft.copy(patientId = patientSession.userId ?: draft.patientId)
                                                        )
                                                    }
                                                }
                                            },
                                            onSessionStatusChange = { status ->
                                                val normalized = status.lowercase()
                                                currentVoiceState = when {
                                                    normalized.contains("connect") -> NoraVoiceState.Connecting
                                                    else -> NoraVoiceState.Listening
                                                }
                                            },
                                            onSpeakingStateChange = { speaking ->
                                                currentVoiceState = if (speaking) NoraVoiceState.Speaking else NoraVoiceState.Listening
                                            },
                                            onConversationIdReady = { conversationId ->
                                                scope.launch {
                                                    runCatching {
                                                        askNoraRepository.updateSessionConversationId(patientSession.accessToken, session.id, conversationId)
                                                    }
                                                }
                                            }
                                        )
                                    }.onSuccess { startedSession ->
                                        voiceSession = startedSession
                                        voiceError = null
                                        currentVoiceState = NoraVoiceState.Listening
                                    }.onFailure { throwable ->
                                        Log.e("NoraVoice", "Unable to start ElevenLabs session", throwable)
                                        voiceError = throwable.localizedMessage
                                            ?: throwable.message
                                            ?: "Could not connect to Nora’s voice assistant."
                                        voiceSession = null
                                        currentVoiceState = NoraVoiceState.Listening
                                    }
                                    isStartingVoiceCall = false
                                }
                            } else {
                                isStartingVoiceCall = false
                                voiceError = null
                                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onEnd = {
                            scope.launch {
                                val endedConversationId = voiceSession?.getId()
                                runCatching { voiceSession?.let { voiceService.end(it) } }
                                runCatching {
                                    if (!endedConversationId.isNullOrBlank()) {
                                        val summary = voiceService.fetchConversationSummary(patientSession.accessToken, endedConversationId)
                                        if (!summary.isNullOrBlank()) {
                                            sessionSummary = summary
                                            askNoraRepository.updateSessionSummary(patientSession.accessToken, session.id, summary)
                                        }
                                    }
                                }
                                voiceSession = null
                                voiceError = null
                                runCatching { askNoraRepository.endSession(patientSession.accessToken, session.id) }
                                onClose()
                            }
                        },
                        onMute = {
                            scope.launch {
                                val nextMuted = !isMuted
                                voiceSession?.let { voiceService.setMuted(it, nextMuted) }
                                isMuted = nextMuted
                            }
                        },
                        isStarting = isStartingVoiceCall,
                        isSpeaking = isSpeakingVoice,
                    )
                }
            }
        }
    }
}

@Composable
fun TextChatView(
    turns: List<AskNoraTurn>,
    isTyping: Boolean,
    quickReplies: List<String> = emptyList(),
    onSend: (String) -> Unit,
    onQuickReply: ((String) -> Unit)? = null,
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
            if (quickReplies.isNotEmpty()) {
                item {
                    FlowRow(
                        items = quickReplies,
                        onClick = { suggestion ->
                            onQuickReply?.invoke(suggestion) ?: onSend(suggestion)
                        }
                    )
                }
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
private fun FlowRow(
    items: List<String>,
    onClick: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                FilterChip(
                    selected = false,
                    onClick = { onClick(item) },
                    label = { Text(item, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colorScheme.secondaryContainer,
                        labelColor = colorScheme.onSecondaryContainer,
                        selectedContainerColor = colorScheme.primaryContainer,
                        selectedLabelColor = colorScheme.onPrimaryContainer
                    )
                )
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
    errorMessage: String? = null,
    onToggleTranscript: (Boolean) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onMute: () -> Unit,
    isStarting: Boolean = false,
    isSpeaking: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val shouldAutoScroll = lastVisibleIndex >= turns.lastIndex - 1 || turns.size <= 3
            if (shouldAutoScroll) {
                listState.animateScrollToItem(turns.lastIndex)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Crossfade(targetState = showTranscript, label = "voice_view_mode") { transcriptVisible ->
                    if (transcriptVisible) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        VoiceVisualizer(isActive = isActive, isSpeaking = isSpeaking)
                    }
                }
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
                    onClick = {
                        if (!isStarting) {
                            if (isActive) onEnd() else onStart()
                        }
                    },
                    containerColor = if (isActive) Color.Red else colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            if (isActive) Icons.Default.CallEnd else Icons.Default.Call,
                            contentDescription = if (isActive) "End Call" else "Start Call",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Text(
                text = when {
                    isStarting -> "Connecting to Nora..."
                    isActive -> if (isSpeaking) "Nora is speaking..." else "Nora is listening..."
                    else -> "Tap the phone to start"
                },
                fontSize = 18.sp,
                color = colorScheme.onSurfaceVariant
            )
            errorMessage?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun VoiceVisualizer(isActive: Boolean, isSpeaking: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_visualizer")
    val targetScale = when {
        isSpeaking -> 1.8f
        isActive -> 1.35f
        else -> 1f
    }
    val targetAlpha = when {
        isSpeaking -> 0.18f
        isActive -> 0.12f
        else -> 0.45f
    }

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = targetScale,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "visualizer_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = targetAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "visualizer_alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isActive || isSpeaking) {
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
            color = if (isSpeaking) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 8.dp
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier
                    .padding(40.dp)
                    .fillMaxSize(),
                tint = if (isSpeaking) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun sanitizeTranscriptText(text: String): String {
    val withoutToneTags = text.replace(Regex("\\s*\\[(warmly|patiently|empathetically|calmly|gently|warm|patient|empathetic)\\]\\s*", RegexOption.IGNORE_CASE), " ")
    val compacted = withoutToneTags.replace(Regex("\\s+"), " ").trim()
    return compacted
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
