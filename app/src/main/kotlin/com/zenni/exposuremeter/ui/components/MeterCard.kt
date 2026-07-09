package com.zenni.exposuremeter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zenni.exposuremeter.metering.Ev100Reading
import com.zenni.exposuremeter.ui.MeteringMode

/**
 * The meter panel (brief §5): a top-level mode switch over an incident readout
 * (EV + lux + hold), the reflected camera surface, or the manual EV controls.
 * Incident is disabled when the device has no light sensor.
 */
@Composable
fun MeterCard(
    mode: MeteringMode,
    hasLightSensor: Boolean,
    ev100: Double,
    liveLux: Double?,
    held: Boolean,
    spot: Offset,
    reflectedError: String?,
    onModeChanged: (MeteringMode) -> Unit,
    onToggleHold: () -> Unit,
    onManualEvChanged: (Double) -> Unit,
    onSpotChanged: (Offset) -> Unit,
    onReflectedReading: (Ev100Reading) -> Unit,
    onReflectedError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeSwitch(mode, hasLightSensor, onModeChanged)

            when (mode) {
                MeteringMode.INCIDENT -> IncidentBody(ev100, liveLux, held, onToggleHold)
                MeteringMode.MANUAL -> ManualEvControls(ev100, onManualEvChanged)
                MeteringMode.REFLECTED -> ReflectedBody(
                    ev100 = ev100,
                    held = held,
                    spot = spot,
                    reflectedError = reflectedError,
                    onToggleHold = onToggleHold,
                    onSpotChanged = onSpotChanged,
                    onReflectedReading = onReflectedReading,
                    onReflectedError = onReflectedError,
                )
            }
        }
    }
}

@Composable
private fun ModeSwitch(
    mode: MeteringMode,
    hasLightSensor: Boolean,
    onModeChanged: (MeteringMode) -> Unit,
) {
    val modes = MeteringMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, m ->
            val enabled = when (m) {
                MeteringMode.INCIDENT -> hasLightSensor
                MeteringMode.REFLECTED -> true
                MeteringMode.MANUAL -> true
            }
            SegmentedButton(
                selected = m == mode,
                onClick = { onModeChanged(m) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            ) {
                Text(m.label)
            }
        }
    }
}

@Composable
private fun IncidentBody(
    ev100: Double,
    liveLux: Double?,
    held: Boolean,
    onToggleHold: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HeldTag(held)
        Text(
            text = "EV ${formatEv(ev100)}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = liveLux?.let { "${formatLux(it)} lux" } ?: "waiting for sensor…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HoldButton(held, onToggleHold)
    }
}

@Composable
private fun ReflectedBody(
    ev100: Double,
    held: Boolean,
    spot: Offset,
    reflectedError: String?,
    onToggleHold: () -> Unit,
    onSpotChanged: (Offset) -> Unit,
    onReflectedReading: (Ev100Reading) -> Unit,
    onReflectedError: (String) -> Unit,
) {
    if (reflectedError != null) {
        Text(
            text = reflectedError,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CameraPreview(
            spot = spot,
            onSpotChanged = onSpotChanged,
            onReading = onReflectedReading,
            onError = onReflectedError,
        )
        HeldTag(held)
        Text(
            text = "EV ${formatEv(ev100)}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Tap the preview to meter a spot.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HoldButton(held, onToggleHold)
    }
}

@Composable
private fun HeldTag(held: Boolean) {
    if (held) {
        Text(
            text = "HELD",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun HoldButton(held: Boolean, onToggleHold: () -> Unit) {
    Row(modifier = Modifier.padding(top = 8.dp)) {
        if (held) {
            Button(onClick = onToggleHold) { Text("Resume live") }
        } else {
            FilledTonalButton(onClick = onToggleHold) { Text("Hold") }
        }
    }
}

/** Human-friendly lux: whole numbers, with a k suffix above 10 000. */
private fun formatLux(lux: Double): String = when {
    lux >= 10_000 -> "${(lux / 1000).toInt()}k"
    else -> lux.toInt().toString()
}
