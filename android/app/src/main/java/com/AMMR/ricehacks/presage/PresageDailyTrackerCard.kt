package com.AMMR.ricehacks.presage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun PresageDailyTrackerCard(
    accessToken: String,
    presageVitalsRepository: PresageVitalsRepository,
    modifier: Modifier = Modifier
) {
    var observations by remember {
        mutableStateOf<List<SavedPresageVital>>(emptyList())
    }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accessToken) {
        isLoading = true
        errorMessage = null

        runCatching {
            presageVitalsRepository.getRecentVitals(accessToken)
        }.onSuccess {
            observations = it
        }.onFailure {
            errorMessage = it.message ?: "Could not load today's vitals."
        }

        isLoading = false
    }

    val todaysPresageObservations = observations.filter {
        it.source.equals("presage", ignoreCase = true) &&
            wasObservedToday(it)
    }

    val pulse = todaysPresageObservations.firstOrNull {
        it.type.equals("pulse_rate", ignoreCase = true)
    }

    val breathing = todaysPresageObservations.firstOrNull {
        it.type.equals("breathing_rate", ignoreCase = true)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Today's vitals",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            when {
                isLoading -> CircularProgressIndicator()

                errorMessage != null -> Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error
                )

                pulse == null && breathing == null -> Text(
                    text = "No Presage reading saved today."
                )

                else -> {
                    VitalRow(
                        label = "Pulse",
                        value = pulse?.valueNumeric?.roundToInt()?.let {
                            "$it bpm"
                        } ?: "Not recorded"
                    )

                    VitalRow(
                        label = "Breathing",
                        value = breathing?.valueNumeric?.roundToInt()?.let {
                            "$it breaths/min"
                        } ?: "Not recorded"
                    )
                }
            }

            Text(
                text = "Wellness information only. Not a medical diagnosis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VitalRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun wasObservedToday(observation: SavedPresageVital): Boolean {
    val timestamp = observation.observedAt ?: return false
    val instant = parseInstant(timestamp) ?: return false

    return instant
        .atZone(ZoneId.systemDefault())
        .toLocalDate() == LocalDate.now()
}

private fun parseInstant(value: String): Instant? {
    return runCatching {
        Instant.parse(value)
    }.getOrNull() ?: runCatching {
        OffsetDateTime.parse(value).toInstant()
    }.getOrNull()
}