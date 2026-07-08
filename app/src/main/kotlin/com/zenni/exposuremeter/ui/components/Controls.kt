package com.zenni.exposuremeter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zenni.exposuremeter.engine.StopFormat
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Manual EV entry (brief §6): a large EV readout with type-or-nudge controls
 * (⅓ / 1 EV). Used as the meter body in [MeterCard] when in manual mode.
 */
@Composable
fun ManualEvControls(
    ev100: Double,
    onEvChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "EV ${formatEv(ev100)}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NudgeButton("−1") { onEvChanged(ev100 - 1.0) }
            NudgeButton("−⅓") { onEvChanged(ev100 - 1.0 / 3.0) }
            EvEntryField(ev100, onEvChanged)
            NudgeButton("+⅓") { onEvChanged(ev100 + 1.0 / 3.0) }
            NudgeButton("+1") { onEvChanged(ev100 + 1.0) }
        }
    }
}

/** Compact ±5 EV exposure-compensation stepper, thirds (brief §6). */
@Composable
fun EvCompControl(evComp: Double, onChanged: (Double) -> Unit, modifier: Modifier = Modifier) {
    StepperRow(
        label = "Exposure comp.",
        value = if (evComp == 0.0) "0" else "${StopFormat.stops(evComp * 3.0)} EV",
        onMinus = { onChanged(evComp - 1.0 / 3.0) },
        onPlus = { onChanged(evComp + 1.0 / 3.0) },
        modifier = modifier,
    )
}

/** Compact ND-filter stepper in whole stops, labelled both ways (brief §6). */
@Composable
fun NdControl(ndStops: Double, onChanged: (Double) -> Unit, modifier: Modifier = Modifier) {
    val whole = ndStops.roundToInt()
    val label = if (whole <= 0) {
        "Off"
    } else {
        val factor = 2.0.pow(whole).roundToInt()
        "ND$factor · $whole ${if (whole == 1) "stop" else "stops"}"
    }
    StepperRow(
        label = "ND filter",
        value = label,
        onMinus = { onChanged((whole - 1).toDouble()) },
        onPlus = { onChanged((whole + 1).toDouble()) },
        modifier = modifier,
    )
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NudgeButton("−", onMinus)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(120.dp),
            )
            NudgeButton("+", onPlus)
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) {
        Text(label, style = LocalTextStyle.current, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EvEntryField(ev100: Double, onEvChanged: (Double) -> Unit) {
    var text by remember(ev100) { mutableStateOf(formatEv(ev100)) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = { text.toDoubleOrNull()?.let(onEvChanged) },
        ),
        modifier = Modifier.width(96.dp),
    )
}

/** Format EV compactly: whole numbers show no decimal, else one place. */
internal fun formatEv(ev: Double): String {
    val rounded = (ev * 10.0).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
    else rounded.toString()
}
