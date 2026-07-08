package com.zenni.exposuremeter.metering

/**
 * A single metered light value, normalised to EV at ISO 100 (brief §8). Metering
 * adapters (incident sensor, reflected camera) emit `Flow<Ev100Reading>`; the
 * ViewModel merges these with UI events.
 *
 * @property ev100 scene light as EV₁₀₀ (already median-filtered for incident).
 * @property lux raw illuminance in lux, present only for incident readings
 *   (the reflected path has no lux); shown in the incident readout (brief §5).
 */
data class Ev100Reading(
    val ev100: Double,
    val lux: Double? = null,
)
