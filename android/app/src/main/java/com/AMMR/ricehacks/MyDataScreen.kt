package com.AMMR.ricehacks

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import android.util.Log
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.AMMR.ricehacks.data.AuditLogEntry
import com.AMMR.ricehacks.data.AllergyData
import com.AMMR.ricehacks.data.AuthenticatedUser
import com.AMMR.ricehacks.data.ConditionData
import com.AMMR.ricehacks.data.DoctorScanRequest
import com.AMMR.ricehacks.data.FakeQrAccessRepository
import com.AMMR.ricehacks.data.MedicationData
import com.AMMR.ricehacks.data.ObservationData
import com.AMMR.ricehacks.data.PatientDataRepository
import com.AMMR.ricehacks.data.PatientHealthData
import com.AMMR.ricehacks.data.PatientAccessLogData
import com.AMMR.ricehacks.data.ProviderData
import com.AMMR.ricehacks.data.QrAccessRepository
import com.AMMR.ricehacks.data.SignedQrToken
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun MyDataQrScreen(
    patientSession: AuthenticatedUser,
    qrAccessRepository: QrAccessRepository,
    patientDataRepository: PatientDataRepository
) {
    var token by remember { mutableStateOf<SignedQrToken?>(null) }
    var secondsRemaining by remember { mutableStateOf(FakeQrAccessRepository.TOKEN_TTL_SECONDS) }
    var auditLogs by remember { mutableStateOf<List<AuditLogEntry>>(emptyList()) }
    var healthData by remember { mutableStateOf<PatientHealthData?>(null) }
    var isHealthDataLoading by remember { mutableStateOf(true) }
    var healthDataError by remember { mutableStateOf<String?>(null) }
    var reloadHealthDataKey by remember { mutableStateOf(0) }
    var statusText by remember { mutableStateOf("Getting secure code...") }
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(patientSession.accessToken, reloadHealthDataKey) {
        Log.d("CaraDebug", "MyDataQrScreen LaunchEffect: reloadHealthDataKey=$reloadHealthDataKey")
        isHealthDataLoading = true
        healthDataError = null
        runCatching {
            patientDataRepository.getMyHealthRecord(patientSession.accessToken, patientSession.userId)
        }.onSuccess { data ->
            Log.d("CaraDebug", "Health record loaded successfully")
            healthData = data
            isHealthDataLoading = false
        }.onFailure { throwable ->
            Log.e("CaraDebug", "Health record load failed: ${throwable.message}")
            healthDataError = throwable.message ?: "Could not load your health record."
            isHealthDataLoading = false
        }
    }

    LaunchedEffect(qrAccessRepository) {
        while (true) {
            val nextToken = qrAccessRepository.requestSignedAccessToken()
            Log.d("CaraDebug", "QR Token refreshed: ${nextToken.tokenId}")
            token = nextToken
            statusText = "Secure code is live"
            auditLogs = qrAccessRepository.getAuditLog()
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
            auditLogs = qrAccessRepository.getAuditLog()
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

        QrCodeCard(
            token = token,
            secondsRemaining = secondsRemaining,
            statusText = statusText
        )

        SecurityNoteCard()

        when {
            isHealthDataLoading -> LoadingHealthRecordCard()
            healthDataError != null -> HealthRecordErrorCard(
                message = healthDataError.orEmpty(),
                onRetry = { reloadHealthDataKey += 1 }
            )
            healthData?.isLinkedToRecord == false -> UnlinkedHealthRecordCard()
        }

        healthData?.takeIf { it.isLinkedToRecord }?.let { data ->
            HealthRecordOverviewCard(data)
            MyDataSectionCard(
                title = "Current prescriptions",
                items = data.activeMedications.map { it.toHealthRecordItem() },
                emptyText = "No current prescriptions are listed."
            )
            MyDataSectionCard(
                title = "Past prescriptions",
                items = data.pastMedications.map { it.toHealthRecordItem() },
                emptyText = "No past prescriptions are listed."
            )
            MyDataSectionCard(
                title = "Allergies",
                items = data.allergies.map { it.toHealthRecordItem() },
                emptyText = "No allergies are listed."
            )
            MyDataSectionCard(
                title = "Conditions",
                items = data.conditions.map { it.toHealthRecordItem() },
                emptyText = "No conditions are listed."
            )
            MyDataSectionCard(
                title = "Recent vitals",
                items = data.recentObservations.map { it.toHealthRecordItem() },
                emptyText = "No recent vitals are listed."
            )
            MyDataSectionCard(
                title = "Doctors with access",
                items = data.providers.map { it.toHealthRecordItem() },
                emptyText = "No providers have access yet."
            )
            PatientAccessLogCard(accessLogs = data.accessLogs)
        }

        AuditLogCard(auditLogs = auditLogs)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LoadingHealthRecordCard() {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading your health record...",
                color = colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                lineHeight = 25.sp
            )
        }
    }
}

@Composable
private fun HealthRecordErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Record did not load",
                color = colorScheme.onErrorContainer,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = colorScheme.onErrorContainer,
                fontSize = 17.sp,
                lineHeight = 25.sp
            )
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = "Try again",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun UnlinkedHealthRecordCard() {
    val colorScheme = MaterialTheme.colorScheme

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
                text = "No health record is linked yet",
                color = colorScheme.onSurface,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your account is signed in, but it is not connected to a medical record yet.",
                color = colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
                lineHeight = 25.sp
            )
        }
    }
}

@Composable
fun HealthRecordOverviewCard(data: PatientHealthData) {
    val colorScheme = MaterialTheme.colorScheme
    val record = data.healthRecord

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
                text = data.profile?.displayName ?: "Health record",
                color = colorScheme.onSurface,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            )
            SettingsValueRow("Date of birth", record?.dateOfBirth ?: "Not listed")
            SettingsValueRow("Blood type", record?.bloodType ?: "Not listed")
            SettingsValueRow("Preferred language", record?.preferredLanguage ?: "Not listed")
            record?.notes?.let { notes ->
                Text(
                    text = notes,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 25.sp
                )
            }
        }
    }
}

@Composable
private fun QrCodeCard(
    token: SignedQrToken?,
    secondsRemaining: Int,
    statusText: String
) {
    val colorScheme = MaterialTheme.colorScheme

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
}

@Composable
private fun PatientAccessLogCard(accessLogs: List<PatientAccessLogData>) {
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
                text = "Shared record history",
                color = colorScheme.onSurface,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold
            )

            if (accessLogs.isEmpty()) {
                Text(
                    text = "No approved doctor access is listed yet.",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 25.sp
                )
            } else {
                accessLogs.take(5).forEach { log ->
                    val doctor = log.doctorName ?: "Provider"
                    val hospital = log.hospitalName ?: "Hospital"
                    Text(
                        text = "$doctor at $hospital: ${log.result ?: "access recorded"}",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 17.sp,
                        lineHeight = 25.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityNoteCard() {
    val colorScheme = MaterialTheme.colorScheme

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
}

@Composable
fun MyDataSectionCard(
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
fun HealthRecordRow(item: HealthRecordItem) {
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

private fun MedicationData.toHealthRecordItem(): HealthRecordItem {
    val doseLine = listOfNotNull(dose, frequency, route).joinToString(", ").ifBlank {
        status ?: "Medication listed"
    }
    val noteLine = notes ?: listOfNotNull(
        startDate?.let { "Started $it" },
        endDate?.let { "Ended $it" }
    ).joinToString(" ").ifBlank { "No extra notes." }

    return HealthRecordItem(name, doseLine, noteLine)
}

private fun AllergyData.toHealthRecordItem(): HealthRecordItem {
    return HealthRecordItem(
        title = allergen,
        detail = listOfNotNull(reaction, severity?.let { "$it severity" }).joinToString(", ")
            .ifBlank { status ?: "Allergy listed" },
        note = notes ?: firstObservedDate?.let { "First observed $it" } ?: "Tell every provider before new medicine."
    )
}

private fun ConditionData.toHealthRecordItem(): HealthRecordItem {
    return HealthRecordItem(
        title = name,
        detail = listOfNotNull(status, severity, diagnosisCode).joinToString(", ")
            .ifBlank { "Condition listed" },
        note = notes ?: diagnosedDate?.let { "Diagnosed $it" } ?: "No extra notes."
    )
}

private fun ObservationData.toHealthRecordItem(): HealthRecordItem {
    val value = valueText ?: listOfNotNull(
        valueNumeric?.let { number ->
            if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()
        },
        unit
    ).joinToString(" ")

    return HealthRecordItem(
        title = type.replace('_', ' ').replaceFirstChar { it.uppercase() },
        detail = value.ifBlank { "Observation listed" },
        note = notes ?: listOfNotNull(source, observedAt).joinToString(", ").ifBlank { "No extra notes." }
    )
}

private fun ProviderData.toHealthRecordItem(): HealthRecordItem {
    return HealthRecordItem(
        title = displayName,
        detail = listOfNotNull(specialty, organizationName).joinToString(", ")
            .ifBlank { accessLevel ?: "Provider access" },
        note = accessExpiresAt?.let { "Access expires $it" }
            ?: listOfNotNull(phone, email).joinToString(" ").ifBlank { "No contact details listed." }
    )
}
