package com.AMMR.ricehacks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

private data class DemoPatientRecord(
    val name: String,
    val age: String,
    val bloodType: String,
    val height: String,
    val weight: String,
    val gender: String,
    val allergies: List<HealthRecordItem>,
    val conditions: List<HealthRecordItem>,
    val medications: List<HealthRecordItem>,
    val providers: List<HealthRecordItem>
)

private data class VitalPoint(
    val date: String,
    val value: Float
)

private val margaretPulseHistory = listOf(
    VitalPoint("Sep 7", 72f),
    VitalPoint("Sep 8", 73f),
    VitalPoint("Sep 9", 71f),
    VitalPoint("Sep 10", 74f),
    VitalPoint("Sep 11", 72f),
    VitalPoint("Sep 12", 73f),
    VitalPoint("Sep 13", 72f)
)

private val margaretBreathingHistory = listOf(
    VitalPoint("Sep 7", 15f),
    VitalPoint("Sep 8", 15f),
    VitalPoint("Sep 9", 14f),
    VitalPoint("Sep 10", 15f),
    VitalPoint("Sep 11", 16f),
    VitalPoint("Sep 12", 15f),
    VitalPoint("Sep 13", 15f)
)

private val margaretRecord = DemoPatientRecord(
    name = "Margaret Chen",
    age = "83",
    bloodType = "O+",
    height = "5 ft 4 in",
    weight = "142 lb",
    gender = "Female",
    allergies = listOf(
        HealthRecordItem(
            "Penicillin",
            "Moderate",
            "Causes hives"
        ),
        HealthRecordItem(
            "Latex",
            "Mild",
            "Causes skin irritation"
        )
    ),
    conditions = listOf(
        HealthRecordItem(
            "High blood pressure",
            "Managed",
            "Treated with medication"
        ),
        HealthRecordItem(
            "Type 2 diabetes",
            "Monitoring",
            "Managed by primary care provider"
        )
    ),
    medications = listOf(
        HealthRecordItem(
            "Lisinopril",
            "10 mg, once daily",
            "For high blood pressure"
        ),
        HealthRecordItem(
            "Metformin",
            "500 mg, twice daily",
            "For type 2 diabetes"
        ),
        HealthRecordItem(
            "Atorvastatin",
            "20 mg, every evening",
            "For cholesterol management"
        )
    ),
    providers = listOf(
        HealthRecordItem(
            "Dr. Tung Sahur",
            "Primary Care Physician",
            "Rice Medical Group"
        ),
        HealthRecordItem(
            "Dr. Hayitsmi Itsverity",
            "Cardiologist",
            "Houston Heart Center"
        )
    )
)

@Composable
fun MargaretRecordScreen() {
    val record = margaretRecord

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Health record",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Synthetic demonstration data—not a real patient.",
            color = MaterialTheme.colorScheme.error,
            fontSize = 16.sp
        )

        MargaretVitalsHistorySection()

        Card(
            modifier = Modifier.fillMaxWidth(),
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
                    text = record.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                SettingsValueRow("Age", record.age)
                SettingsValueRow("Blood type", record.bloodType)
                SettingsValueRow("Height", record.height)
                SettingsValueRow("Weight", record.weight)
                SettingsValueRow("Gender", record.gender)
            }
        }

        MargaretRecordSectionCard(
            title = "Allergies",
            items = record.allergies,
            emptyText = "No allergies are listed."
        )

        MargaretRecordSectionCard(
            title = "Conditions",
            items = record.conditions,
            emptyText = "No conditions are listed."
        )

        MargaretRecordSectionCard(
            title = "Current medications",
            items = record.medications,
            emptyText = "No medications are listed."
        )

        MargaretRecordSectionCard(
            title = "Providers",
            items = record.providers,
            emptyText = "No providers are listed."
        )
    }
}

@Composable
private fun MargaretVitalsHistorySection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Vitals history",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Tap a date to see its reading.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp
        )

        InteractiveVitalChart(
            title = "Pulse",
            unit = "bpm",
            points = margaretPulseHistory,
            minimumValue = 60f,
            maximumValue = 90f
        )

        InteractiveVitalChart(
            title = "Breathing rate",
            unit = "breaths/min",
            points = margaretBreathingHistory,
            minimumValue = 10f,
            maximumValue = 20f
        )

        Text(
            text = "Synthetic Presage-style demo readings. Wellness information only.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun InteractiveVitalChart(
    title: String,
    unit: String,
    points: List<VitalPoint>,
    minimumValue: Float,
    maximumValue: Float
) {
    var selectedIndex by remember {
        mutableIntStateOf(points.lastIndex)
    }

    val selectedPoint = points[selectedIndex]
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val pointCenterColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${selectedPoint.date}: " +
                        "${selectedPoint.value.roundToInt()} $unit",
                    color = lineColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(points) {
                        detectTapGestures { tap ->
                            val horizontalPadding = 12.dp.toPx()
                            val usableWidth =
                                (size.width - horizontalPadding * 2)
                                    .coerceAtLeast(1f)

                            val tappedX =
                                (tap.x - horizontalPadding)
                                    .coerceIn(0f, usableWidth)

                            selectedIndex = (
                                tappedX / usableWidth * points.lastIndex
                            )
                                .roundToInt()
                                .coerceIn(points.indices)
                        }
                    }
            ) {
                val horizontalPadding = 12.dp.toPx()
                val verticalPadding = 12.dp.toPx()

                val graphWidth =
                    (size.width - horizontalPadding * 2)
                        .coerceAtLeast(1f)

                val graphHeight =
                    (size.height - verticalPadding * 2)
                        .coerceAtLeast(1f)

                val valueRange =
                    (maximumValue - minimumValue)
                        .coerceAtLeast(1f)

                val coordinates = points.mapIndexed { index, point ->
                    val horizontalFraction =
                        index.toFloat() / points.lastIndex

                    val verticalFraction = (
                        (point.value - minimumValue) / valueRange
                    ).coerceIn(0f, 1f)

                    Offset(
                        x = horizontalPadding +
                            graphWidth * horizontalFraction,
                        y = verticalPadding +
                            graphHeight * (1f - verticalFraction)
                    )
                }

                // Horizontal grid lines.
                for (line in 0..2) {
                    val y = verticalPadding +
                        graphHeight * (line / 2f)

                    drawLine(
                        color = guideColor,
                        start = Offset(horizontalPadding, y),
                        end = Offset(
                            horizontalPadding + graphWidth,
                            y
                        ),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // The complete trend remains visible.
                coordinates.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = lineColor,
                        start = start,
                        end = end,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Draw every data point.
                coordinates.forEach { coordinate ->
                    drawCircle(
                        color = lineColor,
                        radius = 4.dp.toPx(),
                        center = coordinate
                    )
                }

                // Highlight the selected date.
                val selectedCoordinate = coordinates[selectedIndex]

                drawLine(
                    color = guideColor,
                    start = Offset(
                        selectedCoordinate.x,
                        verticalPadding
                    ),
                    end = Offset(
                        selectedCoordinate.x,
                        verticalPadding + graphHeight
                    ),
                    strokeWidth = 2.dp.toPx()
                )

                drawCircle(
                    color = lineColor,
                    radius = 9.dp.toPx(),
                    center = selectedCoordinate
                )

                drawCircle(
                    color = pointCenterColor,
                    radius = 4.dp.toPx(),
                    center = selectedCoordinate
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                points.forEach { point ->
                    Text(
                        text = point.date.removePrefix("Sep "),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun MargaretRecordSectionCard(
    title: String,
    items: List<HealthRecordItem>,
    emptyText: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )

            if (items.isEmpty()) {
                Text(
                    text = emptyText,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { item ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = item.detail,
                            fontSize = 17.sp
                        )
                        Text(
                            text = item.note,
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
