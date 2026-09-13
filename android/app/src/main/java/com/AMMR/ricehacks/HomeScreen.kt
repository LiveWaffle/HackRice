package com.AMMR.ricehacks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.AMMR.ricehacks.presage.PresageVitalsRepository
import com.AMMR.ricehacks.presage.PresageDailyTrackerCard
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeTabContent(
    accessToken: String,
    presageVitalsRepository: PresageVitalsRepository,
    onOpenRecord: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = timeOfDayGreeting(),
            color = colorScheme.onBackground,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Here is your health record for today.",
            color = colorScheme.onSurfaceVariant,
            fontSize = 20.sp,
            lineHeight = 30.sp
        )

        PresageDailyTrackerCard(
            accessToken = accessToken,
            presageVitalsRepository = presageVitalsRepository
)

        HomeActionCard(
            title = "My health record",
            message = "Medicines, allergies, conditions, and documents.",
            buttonText = "Open record",
            onClick = onOpenRecord
        )
        HomeActionCard(
            title = "Share with a doctor",
            message = "Choose what a provider can see before a visit.",
            buttonText = "Share safely"
        )
        HomeActionCard(
            title = "Upcoming care",
            message = "Keep appointment notes and questions in one place.",
            buttonText = "View care"
        )
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    message: String,
    buttonText: String,
    onClick: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = colorScheme.onSurface,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                lineHeight = 27.sp
            )
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = buttonText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
