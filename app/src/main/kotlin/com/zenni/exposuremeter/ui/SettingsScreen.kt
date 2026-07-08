package com.zenni.exposuremeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zenni.exposuremeter.data.Settings
import kotlin.math.roundToInt

/**
 * Settings (brief §6): incident calibration offset, haptics on detents. Theme
 * follows the system light/dark automatically (no toggle needed). Reflected
 * calibration is added alongside incident in Phase 4.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onIncidentCalibrationChanged: (Double) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDone) { Text("Done") }
        }

        // Incident calibration, ±3 EV in tenths (brief §6).
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Incident calibration", style = MaterialTheme.typography.titleMedium)
            Text(
                "Point at an evenly lit scene, compare with a trusted meter, and dial the difference.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CalibrationButton("−") {
                    onIncidentCalibrationChanged(roundTenth(settings.incidentCalibration - 0.1))
                }
                Text(
                    text = formatCalibration(settings.incidentCalibration),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                CalibrationButton("+") {
                    onIncidentCalibrationChanged(roundTenth(settings.incidentCalibration + 0.1))
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Haptic detents", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Vibrate on each wheel click.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.haptics, onCheckedChange = onHapticsChanged)
        }

        HorizontalDivider()

        Text(
            "Theme follows your system light / dark setting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalibrationButton(label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) {
        Text(label, style = LocalTextStyle.current, fontWeight = FontWeight.Bold)
    }
}

private fun roundTenth(v: Double): Double = (v * 10.0).roundToInt() / 10.0

private fun formatCalibration(ev: Double): String {
    if (ev == 0.0) return "0.0 EV"
    val sign = if (ev > 0) "+" else "−"
    return "$sign${"%.1f".format(kotlin.math.abs(ev))} EV"
}
