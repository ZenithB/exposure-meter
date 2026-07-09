package com.zenni.exposuremeter.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
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
    val spot: Offset,
    val reflectedError: String?,
    val settings: Settings,
    val showSettings: Boolean,
)

/**
 * Owns exposure state and merges every light source and UI event onto the pure
 * [ExposureSolver]. Incident readings come from [IncidentLightMeter]; reflected
 * readings are pushed in from the camera surface via [onReflectedReading]. The
 * "hold" toggle freezes the metered EV while the wheels keep working (brief §5).
 * Calibration and haptics come from [SettingsRepository] (DataStore).
 */
class ExposureViewModel(app: Application) : AndroidViewModel(app) {

    private val incidentMeter = IncidentLightMeter(app)
    private val settingsRepository = SettingsRepository(app)

    // Source + dial values that resolve into MeterInputs.
    private var manualEv: Double = 12.0
    private var evComp: Double = 0.0
    private var ndStops: Double = 0.0
    private var incidentReading: Ev100Reading? = null
    private var reflectedReading: Ev100Reading? = null
    private var frozenEv: Double? = null
    private var settings: Settings = Settings()

    private var incidentJob: Job? = null
    private var state: ExposureState = defaultTriple()

    var ui by mutableStateOf(resolve(initial = true))
        private set

    init {
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
        ui = ui.copy(mode = mode, held = false, reflectedError = null)
        if (mode == MeteringMode.INCIDENT) startIncident() else stopIncident()
        ui = resolve()
    }

    /** Hold/freeze toggle (brief §5): freeze EV₁₀₀; wheels keep working. */
    fun onToggleHold() {
        val nowHeld = !ui.held
        frozenEv = if (nowHeld) currentSourceEv() else null
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

    // ---- Reflected (camera) intents -----------------------------------------

    /** A metered frame from the camera surface (brief §3.2). */
    fun onReflectedReading(reading: Ev100Reading) {
        reflectedReading = reading
        if (ui.mode == MeteringMode.REFLECTED && !ui.held) ui = resolve()
    }

    /** The camera cannot supply aperture; disable reflected metering (brief §3.2). */
    fun onReflectedError(message: String) {
        ui = ui.copy(reflectedError = message)
    }

    /** The user tapped a new spot on the preview (brief §3.2). */
    fun onSpotChanged(spot: Offset) {
        ui = ui.copy(spot = spot)
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
                incidentReading = reading
                if (ui.mode == MeteringMode.INCIDENT && !ui.held) ui = resolve()
            }
            .launchIn(viewModelScope)
    }

    private fun stopIncident() {
        incidentJob?.cancel()
        incidentJob = null
    }

    /** The latest live EV for the current mode (before hold/calibration). */
    private fun currentSourceEv(): Double = when (currentMode()) {
        MeteringMode.MANUAL -> manualEv
        MeteringMode.INCIDENT -> incidentReading?.ev100 ?: manualEv
        MeteringMode.REFLECTED -> reflectedReading?.ev100 ?: manualEv
    }

    /**
     * Rebuild [ui] from the current source/dial values: pick the effective EV for
     * the mode, apply per-mode calibration, then solve the free wheel.
     */
    private fun resolve(initial: Boolean = false): ExposureUiState {
        val mode = currentMode(initial)
        val ev = when {
            mode == MeteringMode.MANUAL -> manualEv
            !initial && ui.held -> frozenEv ?: manualEv
            mode == MeteringMode.INCIDENT -> incidentReading?.ev100 ?: manualEv
            mode == MeteringMode.REFLECTED -> reflectedReading?.ev100 ?: manualEv
            else -> manualEv
        }
        val calibration = when (mode) {
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
            mode = mode,
            held = if (initial) false else ui.held,
            hasLightSensor = incidentMeter.isAvailable,
            liveLux = incidentReading?.lux,
            spot = if (initial) Offset(0.5f, 0.5f) else ui.spot,
            reflectedError = if (initial) null else ui.reflectedError,
            settings = settings,
            showSettings = if (initial) false else ui.showSettings,
        )
    }

    private fun currentMode(initial: Boolean = false): MeteringMode =
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
