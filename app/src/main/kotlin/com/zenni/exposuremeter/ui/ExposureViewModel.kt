package com.zenni.exposuremeter.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zenni.exposuremeter.data.Settings
import com.zenni.exposuremeter.data.SettingsRepository
import com.zenni.exposuremeter.engine.ExposureAction
import com.zenni.exposuremeter.engine.ExposureDelta
import com.zenni.exposuremeter.engine.ExposureSolver
import com.zenni.exposuremeter.engine.ExposureState
import com.zenni.exposuremeter.engine.MeterInputs
import com.zenni.exposuremeter.engine.Param
import com.zenni.exposuremeter.engine.StopTables
import com.zenni.exposuremeter.metering.Ev100Reading
import com.zenni.exposuremeter.metering.IncidentLightMeter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Immutable snapshot the UI renders (brief §8: UI is a pure function of state). */
data class ExposureUiState(
    val state: ExposureState,
    val inputs: MeterInputs,
    val delta: ExposureDelta,
    val mode: MeteringMode,
    val held: Boolean,
    val hasLightSensor: Boolean,
    val liveLux: Double?,
    val settings: Settings,
    val showSettings: Boolean,
)

/**
 * Owns exposure state and merges every light source and UI event onto the pure
 * [ExposureSolver]. In incident mode it collects [IncidentLightMeter]; the "hold"
 * toggle freezes the metered EV while the wheels keep working (brief §5).
 * Calibration and haptics come from [SettingsRepository] (DataStore).
 */
class ExposureViewModel(app: Application) : AndroidViewModel(app) {

    private val incidentMeter = IncidentLightMeter(app)
    private val settingsRepository = SettingsRepository(app)

    // Dial + source values that resolve into MeterInputs.
    private var manualEv: Double = 12.0
    private var evComp: Double = 0.0
    private var ndStops: Double = 0.0
    private var liveReading: Ev100Reading? = null
    private var frozenEv: Double? = null
    private var settings: Settings = Settings()

    private var incidentJob: Job? = null
    private var state: ExposureState = defaultTriple()

    var ui by mutableStateOf(resolve(initial = true))
        private set

    init {
        // Persisted settings drive calibration + haptics live.
        settingsRepository.settings
            .onEach {
                settings = it
                ui = resolve()
            }
            .launchIn(viewModelScope)

        if (incidentMeter.isAvailable) startIncident() else ui = ui.copy(mode = MeteringMode.MANUAL)
    }

    // ---- Wheel + lock intents -----------------------------------------------

    fun onWheelChanged(param: Param, newIndex: Int) {
        val result = ExposureSolver.solve(
            state, ui.inputs, ExposureAction.ChangeParam(param, newIndex),
        )
        state = result.state
        ui = ui.copy(state = result.state, delta = result.delta)
    }

    fun onToggleLock(param: Param) {
        state = when (param) {
            Param.APERTURE -> state.copy(apertureLocked = !state.apertureLocked)
            Param.SHUTTER -> state.copy(shutterLocked = !state.shutterLocked)
            Param.ISO -> state.copy(isoLocked = !state.isoLocked)
        }
        ui = ui.copy(state = state, delta = ExposureSolver.delta(state, ui.inputs))
    }

    // ---- Metering source intents --------------------------------------------

    fun onModeChanged(mode: MeteringMode) {
        if (mode == ui.mode) return
        frozenEv = null
        val held = false
        ui = ui.copy(mode = mode, held = held)
        if (mode == MeteringMode.INCIDENT) startIncident() else stopIncident()
        ui = resolve()
    }

    /** Hold/freeze toggle (brief §5): freeze EV₁₀₀; wheels keep working. */
    fun onToggleHold() {
        val nowHeld = !ui.held
        frozenEv = if (nowHeld) (liveReading?.ev100 ?: ui.inputs.ev100) else null
        ui = ui.copy(held = nowHeld)
        ui = resolve()
    }

    fun onManualEvChanged(ev100: Double) {
        manualEv = ev100
        if (ui.mode == MeteringMode.MANUAL) ui = resolve()
    }

    fun onEvCompChanged(value: Double) {
        evComp = value.coerceIn(-5.0, 5.0)
        ui = resolve()
    }

    fun onNdChanged(value: Double) {
        ndStops = value.coerceIn(0.0, 10.0)
        ui = resolve()
    }

    // ---- Settings intents ----------------------------------------------------

    fun onShowSettings(show: Boolean) {
        ui = ui.copy(showSettings = show)
    }

    fun onIncidentCalibrationChanged(value: Double) {
        viewModelScope.launch { settingsRepository.setIncidentCalibration(value) }
    }

    fun onHapticsChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHaptics(enabled) }
    }

    // ---- Internal ------------------------------------------------------------

    private fun startIncident() {
        if (incidentJob?.isActive == true) return
        incidentJob = incidentMeter.readings()
            .onEach { reading ->
                liveReading = reading
                if (!ui.held) ui = resolve()
            }
            .launchIn(viewModelScope)
    }

    private fun stopIncident() {
        incidentJob?.cancel()
        incidentJob = null
    }

    /**
     * Rebuild [ui] from the current source/dial values: pick the effective EV for
     * the mode, apply per-mode calibration, then solve the free wheel.
     */
    private fun resolve(initial: Boolean = false): ExposureUiState {
        val ev = when {
            ui0Mode(initial) == MeteringMode.MANUAL -> manualEv
            ui.held -> frozenEv ?: manualEv
            else -> liveReading?.ev100 ?: manualEv
        }
        val calibration = when (ui0Mode(initial)) {
            MeteringMode.INCIDENT -> settings.incidentCalibration
            MeteringMode.REFLECTED -> settings.reflectedCalibration
            MeteringMode.MANUAL -> 0.0
        }
        val inputs = MeterInputs(
            ev100 = ev,
            evComp = evComp,
            ndStops = ndStops,
            calibration = calibration,
        )
        val result = ExposureSolver.solve(state, inputs, ExposureAction.MeterOrDialChanged)
        state = result.state
        return ExposureUiState(
            state = result.state,
            inputs = inputs,
            delta = result.delta,
            mode = ui0Mode(initial),
            held = if (initial) false else ui.held,
            hasLightSensor = incidentMeter.isAvailable,
            liveLux = liveReading?.lux,
            settings = settings,
            showSettings = if (initial) false else ui.showSettings,
        )
    }

    // During the very first resolve() the `ui` field is not yet initialised.
    private fun ui0Mode(initial: Boolean): MeteringMode =
        if (initial) {
            if (incidentMeter.isAvailable) MeteringMode.INCIDENT else MeteringMode.MANUAL
        } else {
            ui.mode
        }

    override fun onCleared() {
        stopIncident()
        super.onCleared()
    }

    private companion object {
        fun defaultTriple(): ExposureState = ExposureState(
            apertureIndex = StopTables.aperture.values.first { it.display == "8" }.index,
            shutterIndex = StopTables.shutter.values.first { it.display == "1/125" }.index,
            isoIndex = StopTables.iso.values.first { it.display == "400" }.index,
        )
    }
}
