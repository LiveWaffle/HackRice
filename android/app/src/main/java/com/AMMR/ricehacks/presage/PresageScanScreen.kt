package com.AMMR.ricehacks.presage

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.presagetech.smartspectra.CameraPosition
import com.presagetech.smartspectra.ProcessingStatus
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun PresageScanScreen(
    accessToken: String,
    presageVitalsRepository: PresageVitalsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember { PresageController() }
    val scope = rememberCoroutineScope()

    val processingStatus by controller.sdk.processingStatus.observeAsState()
    val validationStatus by controller.sdk.validationStatus.observeAsState()
    val metrics by controller.sdk.metrics.observeAsState()
    val sdkError by controller.sdk.error.observeAsState()

    var latestPulse by remember { mutableStateOf<VitalMetric?>(null) }
    var latestBreathing by remember { mutableStateOf<VitalMetric?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasStoppedScan by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var historyRefreshKey by remember { mutableStateOf(0) }
    var savedVitals by remember { mutableStateOf<List<SavedPresageVital>>(emptyList()) }
    var isHistoryLoading by remember { mutableStateOf(true) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var cameraAvailability by remember { mutableStateOf<PresageCameraAvailability?>(null) }
    var selectedCameraPosition by remember { mutableStateOf<CameraPosition?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var configuredPreviewView by remember { mutableStateOf<PreviewView?>(null) }
    var configuredCameraPosition by remember { mutableStateOf<CameraPosition?>(null) }
    val hasPresageKey = controller.hasApiKey()

    fun configurePresagePreview() {
        val view = previewView ?: return
        val cameraPosition = selectedCameraPosition ?: return
        if (configuredPreviewView === view && configuredCameraPosition == cameraPosition) {
            return
        }

        configuredPreviewView = view
        configuredCameraPosition = cameraPosition
        view.post {
            controller.configure(view, cameraPosition)
        }
    }

    LaunchedEffect(context) {
        runCatching {
            controller.inspectCameraAvailability(context)
        }.onSuccess { availability ->
            cameraAvailability = availability
            selectedCameraPosition = availability.preferredPosition
            if (!availability.hasAnyCamera) {
                errorMessage = "No camera is available to Android. In the emulator, edit the device and set at least one camera to Webcam or Emulated."
            }
        }.onFailure {
            errorMessage = it.message
                ?: "Could not verify camera availability. Check emulator camera settings."
        }
    }

    LaunchedEffect(previewView, selectedCameraPosition) {
        configurePresagePreview()
    }

    DisposableEffect(controller) {
        onDispose {
            scope.launch {
                runCatching { controller.stop() }
            }
        }
    }

    LaunchedEffect(accessToken, historyRefreshKey) {
        isHistoryLoading = true
        historyError = null

        runCatching {
            presageVitalsRepository.getRecentVitals(accessToken)
        }.onSuccess {
            savedVitals = it
        }.onFailure {
            historyError = it.message ?: "Could not load saved vitals."
        }

        isHistoryLoading = false
    }

    LaunchedEffect(metrics) {
        metrics?.cardio?.pulseRateList
            ?.lastOrNull()
            ?.let { measurement ->
                latestPulse = VitalMetric(
                    value = measurement.value,
                    confidence = measurement.confidence,
                    stable = measurement.stable
                )
            }

        metrics?.breathing?.rateList
            ?.lastOrNull()
            ?.let { measurement ->
                latestBreathing = VitalMetric(
                    value = measurement.value,
                    confidence = measurement.confidence,
                    stable = measurement.stable
                )
            }
    }

    LaunchedEffect(sdkError) {
        sdkError?.let {
            errorMessage = it.message ?: "Presage could not complete the measurement."
        }
    }

    val startScan: () -> Unit = {
        if (!controller.hasApiKey()) {
            errorMessage = "Presage is not configured. Add PRESAGE_API_KEY in local.properties or your build environment."
        } else if (selectedCameraPosition == null) {
            errorMessage = "No Android camera is available for Presage. Check emulator camera settings or use a physical device."
        } else if (previewView == null) {
            errorMessage = "Camera preview is still loading. Try again in a moment."
        } else {
            latestPulse = null
            latestBreathing = null
            errorMessage = null
            saveMessage = null
            configurePresagePreview()

            scope.launch {
                runCatching {
                    controller.start()
                }.onFailure {
                    errorMessage = it.message ?: "Unable to start the camera."
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startScan()
        } else {
            errorMessage = "Camera permission is required for a health scan."
        }
    }

    val scanIsRunning =
        processingStatus == ProcessingStatus.STARTING ||
            processingStatus == ProcessingStatus.RUNNING

    val readingIsUsable =
        latestPulse?.stable == true &&
            latestBreathing?.stable == true

    val showReviewActions = !scanIsRunning && hasStoppedScan && readingIsUsable

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Health Vitals Scan",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Keep your face and upper chest visible, remain still, and avoid talking."
        )

        if (!hasPresageKey) {
            Text(
                text = "Presage is not configured yet. Add PRESAGE_API_KEY to the Android build config to enable camera-based vitals scanning.",
                color = MaterialTheme.colorScheme.error
            )
        }

        cameraAvailability?.let { availability ->
            Text(
                text = when {
                    !availability.hasAnyCamera -> "Camera unavailable to Android."
                    selectedCameraPosition == CameraPosition.FRONT -> "Using front camera."
                    selectedCameraPosition == CameraPosition.BACK -> "Using back camera because front camera is unavailable."
                    else -> "Checking camera..."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AndroidView(
            factory = { previewContext ->
                PreviewView(previewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this
                }
            },
            update = { view ->
                previewView = view
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        Text(
            text = validationStatus?.hint
                ?: "Press Start scan when you are positioned correctly."
        )

        Text(
            text = "Pulse: ${
                latestPulse?.let { "${it.value.toInt()} bpm (${it.confidence.toInt()}% confidence)" }
                    ?: "Waiting..."
            }"
        )

        Text(
            text = "Breathing: ${
                latestBreathing?.let {
                    "${it.value.toInt()} breaths/min (${it.confidence.toInt()}% confidence)"
                } ?: "Waiting..."
            }"
        )

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        saveMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = {
                if (!controller.hasApiKey()) {
                    errorMessage = "Presage is not configured. Add PRESAGE_API_KEY in local.properties or your build environment."
                } else if (scanIsRunning) {
                    hasStoppedScan = true
                    scope.launch {
                        runCatching { controller.stop() }
                    }
                } else if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    hasStoppedScan = false
                    startScan()
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            enabled = processingStatus != ProcessingStatus.STOPPING &&
                hasPresageKey &&
                selectedCameraPosition != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (scanIsRunning) "Stop scan" else "Start scan")
        }

        if (showReviewActions) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        hasStoppedScan = false
                        latestPulse = null
                        latestBreathing = null
                        errorMessage = null
                        saveMessage = null
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val reading = VitalsReading(
                            capturedAtMillis = System.currentTimeMillis(),
                            pulseRateBpm = latestPulse,
                            breathingRatePerMinute = latestBreathing,
                            validationCode = validationStatus?.code?.name ?: "UNKNOWN"
                        )

                        saveMessage = "Saving health reading..."
                        scope.launch {
                            runCatching {
                                presageVitalsRepository.saveReading(
                                    accessToken = accessToken,
                                    reading = reading
                                )
                            }.onSuccess {
                                saveMessage = "Health reading saved."
                                historyRefreshKey += 1
                                hasStoppedScan = false
                                latestPulse = null
                                latestBreathing = null
                            }.onFailure {
                                saveMessage = null
                                errorMessage = it.message ?: "Could not save health reading."
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Health Reading")
                }
            }
        }

        PresageVitalsHistorySection(
            savedVitals = savedVitals,
            isLoading = isHistoryLoading,
            errorMessage = historyError,
            onRefresh = { historyRefreshKey += 1 }
        )

        Text(
            text = "Wellness information only. This is not a medical diagnosis."
        )
        Text(
            text = "This feature is meant for wellness tracking and should not be used to diagnose, treat, or replace medical advice from a licensed clinician.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PresageVitalsHistorySection(
    savedVitals: List<SavedPresageVital>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved vitals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }

            when {
                isLoading -> CircularProgressIndicator()

                errorMessage != null -> Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )

                savedVitals.isEmpty() -> Text(
                    text = "No saved vitals yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> savedVitals.forEachIndexed { index, vital ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    SavedVitalRow(vital = vital)
                }
            }
        }
    }
}

@Composable
private fun SavedVitalRow(vital: SavedPresageVital) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = vital.type.toVitalLabel(),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = vital.observedAt.toDisplayTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = vital.valueText(),
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun SavedPresageVital.valueText(): String {
    val value = valueNumeric?.roundToInt()?.toString() ?: "--"
    return listOfNotNull(value, unit).joinToString(" ")
}

private fun String.toVitalLabel(): String {
    return when (lowercase()) {
        "pulse_rate" -> "Pulse"
        "breathing_rate" -> "Breathing"
        else -> replace("_", " ").replaceFirstChar { it.titlecase() }
    }
}

private fun String?.toDisplayTime(): String {
    val value = this ?: return "Unknown time"
    val instant = runCatching {
        Instant.parse(value)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(value).toInstant()
    }.getOrNull() ?: return value

    return DateTimeFormatter
        .ofPattern("MMM d, h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
