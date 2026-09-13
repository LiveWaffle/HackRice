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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Good Morning Margaret!",
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
                Text("Your connected scans and visit data will appear here as they are added.", color = colorScheme.onSurfaceVariant, fontSize = 17.sp, lineHeight = 25.sp)
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
