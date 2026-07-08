package com.zenni.exposuremeter.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.zenni.exposuremeter.engine.ExposureAction
import com.zenni.exposuremeter.engine.ExposureDelta
import com.zenni.exposuremeter.engine.ExposureSolver
import com.zenni.exposuremeter.engine.ExposureState
import com.zenni.exposuremeter.engine.MeterInputs
import com.zenni.exposuremeter.engine.Param
import com.zenni.exposuremeter.engine.StopTables

/** Immutable snapshot the UI renders. */
data class ExposureUiState(
    val state: ExposureState,
    val inputs: MeterInputs,
    val delta: ExposureDelta,
)

/**
 * Owns the exposure-triangle state and forwards every user intent to the pure
 * [ExposureSolver] in `:engine`. The UI is a pure function of [ui]; this class
 * holds no Android-specific logic beyond being a [ViewModel].
 *
 * In Phase 2 the "meter" is the manually entered EV. Phases 3–4 replace
 * [onManualEvChanged] with sensor/camera flows feeding the same [MeterInputs].
 */
class ExposureViewModel : ViewModel() {

    var ui by mutableStateOf(initialState())
        private set

    /** A wheel was scrolled to [newIndex]; the lock model moves the absorber. */
    fun onWheelChanged(param: Param, newIndex: Int) {
        val result = ExposureSolver.solve(
            ui.state, ui.inputs, ExposureAction.ChangeParam(param, newIndex),
        )
        ui = ui.copy(state = result.state, delta = result.delta)
    }

    /** Toggle a wheel's padlock. Locks reshape future solves but move nothing now. */
    fun onToggleLock(param: Param) {
        val s = ui.state
        val toggled = when (param) {
            Param.APERTURE -> s.copy(apertureLocked = !s.apertureLocked)
            Param.SHUTTER -> s.copy(shutterLocked = !s.shutterLocked)
            Param.ISO -> s.copy(isoLocked = !s.isoLocked)
        }
        ui = ui.copy(state = toggled, delta = ExposureSolver.delta(toggled, ui.inputs))
    }

    /** Manual EV entry / nudge (brief §6). Drives the free wheel via the solver. */
    fun onManualEvChanged(ev100: Double) = updateInputs(ui.inputs.copy(ev100 = ev100))

    /** Exposure-compensation dial, ±5 EV (brief §6). */
    fun onEvCompChanged(evComp: Double) =
        updateInputs(ui.inputs.copy(evComp = evComp.coerceIn(-5.0, 5.0)))

    /** ND-filter offset in stops (brief §6). */
    fun onNdChanged(ndStops: Double) =
        updateInputs(ui.inputs.copy(ndStops = ndStops.coerceIn(0.0, 10.0)))

    private fun updateInputs(inputs: MeterInputs) {
        val result = ExposureSolver.solve(ui.state, inputs, ExposureAction.MeterOrDialChanged)
        ui = ExposureUiState(result.state, inputs, result.delta)
    }

    private companion object {
        fun initialState(): ExposureUiState {
            // A pleasant default triple, then balanced against EV100 = 12 by the
            // solver (all wheels free, so the least-recently-touched — shutter —
            // moves to make the exposure correct).
            val state = ExposureState(
                apertureIndex = StopTables.aperture.values.first { it.display == "8" }.index,
                shutterIndex = StopTables.shutter.values.first { it.display == "1/125" }.index,
                isoIndex = StopTables.iso.values.first { it.display == "400" }.index,
            )
            val inputs = MeterInputs(ev100 = 12.0)
            val result = ExposureSolver.solve(state, inputs, ExposureAction.MeterOrDialChanged)
            return ExposureUiState(result.state, inputs, result.delta)
        }
    }
}
