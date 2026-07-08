package com.zenni.exposuremeter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zenni.exposuremeter.engine.Param
import com.zenni.exposuremeter.engine.StopTables
import com.zenni.exposuremeter.ui.components.DeltaBadge
import com.zenni.exposuremeter.ui.components.EvCompControl
import com.zenni.exposuremeter.ui.components.MeterPanel
import com.zenni.exposuremeter.ui.components.NdControl
import com.zenni.exposuremeter.ui.components.StopWheel

/**
 * The Phase-2 screen: manual-EV meter panel, delta badge, the three locking
 * wheels, and the EVcomp / ND controls. Portrait-first (brief §7). The UI is a
 * pure function of [ExposureViewModel.ui].
 */
@Composable
fun ExposureScreen(
    modifier: Modifier = Modifier,
    viewModel: ExposureViewModel = viewModel(),
) {
    val ui = viewModel.ui

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DeltaBadge(ui.delta)

        MeterPanel(
            ev100 = ui.inputs.ev100,
            onEvChanged = viewModel::onManualEvChanged,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WheelColumn(Param.APERTURE, "Aperture", ui, viewModel, Modifier.weight(1f))
            WheelColumn(Param.SHUTTER, "Shutter", ui, viewModel, Modifier.weight(1f))
            WheelColumn(Param.ISO, "ISO", ui, viewModel, Modifier.weight(1f))
        }

        HorizontalDivider()

        EvCompControl(ui.inputs.evComp, viewModel::onEvCompChanged)
        NdControl(ui.inputs.ndStops, viewModel::onNdChanged)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WheelColumn(
    param: Param,
    title: String,
    ui: ExposureUiState,
    viewModel: ExposureViewModel,
    modifier: Modifier = Modifier,
) {
    val scale = StopTables.scaleFor(param)
    val values = scale.values.map { it.display }
    val selectedPos = ui.state.indexOf(param) - scale.minIndex
    StopWheel(
        title = title,
        values = values,
        selectedIndex = selectedPos,
        locked = ui.state.isLocked(param),
        onSelected = { pos -> viewModel.onWheelChanged(param, scale.minIndex + pos) },
        onToggleLock = { viewModel.onToggleLock(param) },
        modifier = modifier,
    )
}
