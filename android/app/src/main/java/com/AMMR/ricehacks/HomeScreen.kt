package com.AMMR.ricehacks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.AMMR.ricehacks.data.UserRole
import com.AMMR.ricehacks.presage.PresageDailyTrackerCard
import com.AMMR.ricehacks.presage.PresageVitalsRepository
import com.AMMR.ricehacks.presage.SavedPresageVital
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class TrendRange(val label: String) { Day("Day"), Week("Week"), Month("Month"), Year("Year") }

@Composable
fun HomeTabContent(
    patientName: String,
    role: UserRole = UserRole.Patient,
    accessToken: String,
    presageVitalsRepository: PresageVitalsRepository,
    onViewHealthData: () -> Unit,
    onOpenRecord: () -> Unit,
    onStartScan: () -> Unit,
) {
    var range by remember { mutableStateOf(TrendRange.Week) }
    var trendVitals by remember { mutableStateOf<List<SavedPresageVital>>(emptyList()) }
    var trendsAreLoading by remember { mutableStateOf(true) }
    var trendsError by remember { mutableStateOf<String?>(null) }
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(accessToken) {
        trendsAreLoading = true
        trendsError = null
        runCatching {
            presageVitalsRepository.getRecentVitals(accessToken)
        }.onSuccess {
            trendVitals = it
        }.onFailure {
            trendsError = it.message ?: "Could not load vitals."
        }
        trendsAreLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Good Morning ${patientName.ifBlank { "there" }}!",
            color = colorScheme.onBackground,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (role == UserRole.Doctor) {
                "Manage your patient visits and scans."
            } else {
                "Welcome back. We'll keep this simple and take one step at a time."
            },
            color = colorScheme.onSurfaceVariant,
            fontSize = 19.sp,
            lineHeight = 28.sp
        )

        PresageDailyTrackerCard(
            accessToken = accessToken,
            presageVitalsRepository = presageVitalsRepository,
            onStartScan = onStartScan
        )

        if (role == UserRole.Doctor) {
            HomeActionCard(
                title = "Patient Scanner",
                message = "Scan a patient QR code to securely view their medical history.",
                buttonText = "Open Scanner",
                onClick = onStartScan
            )
        } else {
            HomeActionCard(
                title = "My health record",
                message = "Medicines, allergies, conditions, and documents.",
                buttonText = "Open record",
                onClick = onOpenRecord
            )
            DashboardCard("Your latest health summary") {
                Text("Your record is ready to review before your next visit.", fontSize = 18.sp, lineHeight = 26.sp)
                Text("2 providers • 3 current medicines • 1 recent vital", color = colorScheme.onSurfaceVariant, fontSize = 16.sp)
                OutlinedButton(onClick = onViewHealthData, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("View recent health summary", fontSize = 17.sp)
                }
            }
        }

        if (role == UserRole.Patient) {
            DashboardCard("Health trends") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TrendRange.entries.forEach { option ->
                        FilterChip(selected = range == option, onClick = { range = option }, label = { Text(option.label) })
                    }
                }
                Text("Presage scan trend • ${range.label}", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                when {
                    trendsAreLoading -> CircularProgressIndicator()
                    trendsError != null -> Text(
                        text = trendsError.orEmpty(),
                        color = colorScheme.error,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                    trendVitals.filterForRange(range).isEmpty() -> Text(
                        text = "No saved vitals in this range yet.",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 17.sp,
                        lineHeight = 25.sp
                    )
                    else -> trendVitals
                        .filterForRange(range)
                        .take(8)
                        .forEachIndexed { index, vital ->
                            if (index > 0) {
                                HorizontalDivider(color = colorScheme.outlineVariant)
                            }
                            HomeVitalTrendRow(vital = vital)
                        }
                }
            }

        }

        DashboardCard("Upcoming care") {
            Text("Next visit", fontSize = 16.sp, color = colorScheme.onSurfaceVariant)
            Text("Primary care follow-up", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Bring your questions and share your record only when you are ready.", fontSize = 17.sp, lineHeight = 25.sp)
        }
    }
}

@Composable
private fun HomeVitalTrendRow(vital: SavedPresageVital) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vital.type.toHomeVitalLabel(),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = vital.observedAt.toHomeVitalTime(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        Text(
            text = vital.toHomeVitalValue(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun List<SavedPresageVital>.filterForRange(range: TrendRange): List<SavedPresageVital> {
    val today = LocalDate.now()
    return filter { vital ->
        val date = vital.observedAt.toHomeVitalInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
            ?: return@filter false
        when (range) {
            TrendRange.Day -> date == today
            TrendRange.Week -> !date.isBefore(today.minusDays(7))
            TrendRange.Month -> !date.isBefore(today.minusMonths(1))
            TrendRange.Year -> !date.isBefore(today.minusYears(1))
        }
    }
}

private fun SavedPresageVital.toHomeVitalValue(): String {
    val value = valueNumeric?.roundToInt()?.toString() ?: "--"
    return listOfNotNull(value, unit).joinToString(" ")
}

private fun String.toHomeVitalLabel(): String {
    return when (lowercase()) {
        "pulse_rate" -> "Pulse"
        "breathing_rate" -> "Breathing"
        else -> replace("_", " ").replaceFirstChar { it.titlecase() }
    }
}

private fun String?.toHomeVitalTime(): String {
    val instant = toHomeVitalInstant() ?: return "Unknown time"
    return DateTimeFormatter
        .ofPattern("MMM d, h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

private fun String?.toHomeVitalInstant(): Instant? {
    val value = this ?: return null
    return runCatching {
        Instant.parse(value)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(value).toInstant()
    }.getOrNull()
}

@Composable
private fun HomeActionCard(
    title: String,
    message: String,
    buttonText: String,
    onClick: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
            Text(message, fontSize = 17.sp, lineHeight = 25.sp, color = colorScheme.onSurfaceVariant)
            Button(
                onClick = onClick ?: {},
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(buttonText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DashboardCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
