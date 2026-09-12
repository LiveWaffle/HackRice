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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.AMMR.ricehacks.data.ApprovalResult
import com.AMMR.ricehacks.data.AuditLogEntry
import com.AMMR.ricehacks.data.DoctorScanRequest
import com.AMMR.ricehacks.data.FakeQrAccessRepository
import com.AMMR.ricehacks.data.QrAccessRepository
import com.AMMR.ricehacks.data.SignedQrToken
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun MyDataQrScreen(repository: QrAccessRepository) {
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

        QrCodeCard(
            token = token,
            secondsRemaining = secondsRemaining,
            statusText = statusText
        )

        SecurityNoteCard()

        MyDataSectionCard("Current prescriptions", currentPrescriptions, "No current prescriptions are listed.")
        MyDataSectionCard("Past prescriptions", pastPrescriptions, "No past prescriptions are listed.")
        MyDataSectionCard("Allergies", allergies, "No allergies are listed.")
        MyDataSectionCard("Conditions", conditions, "No conditions are listed.")
        MyDataSectionCard("Recent visits", recentVisits, "No recent visits are listed.")
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
