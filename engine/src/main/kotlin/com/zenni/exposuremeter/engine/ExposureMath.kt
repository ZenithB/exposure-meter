package com.zenni.exposuremeter.engine

import kotlin.math.ln
import kotlin.math.pow

/**
 * Low-level, stateless exposure maths in log2 space (brief §3).
 *
 * All scene-light values are expressed as **EV at ISO 100 (EV₁₀₀)**. Keeping a
 * single normalised light value decouples metering from the exposure-triangle
 * solver ([ExposureSolver]).
 */
public object ExposureMath {

    private const val LN2: Double = 0.6931471805599453

    /** log base 2. */
    public fun log2(x: Double): Double = ln(x) / LN2

    /**
     * Incident-metering conversion: illuminance (lux) → EV₁₀₀ (brief §3.1).
     *
     * Standard incident-meter equation `N²/t = E·S/C` with speed `S = 100` and
     * calibration constant `C = 250`, giving `E·100/250 = E/2.5`, hence:
     *
     * ```
     * EV₁₀₀ = log2(E / 2.5)
     * ```
     *
     * @param lux measured illuminance E (must be > 0).
     */
    public fun evFromLux(lux: Double): Double {
        require(lux > 0.0) { "illuminance must be positive, was $lux" }
        return log2(lux / 2.5)
    }

    /**
     * Inverse of [evFromLux]: EV₁₀₀ → illuminance (lux). Useful for the incident
     * readout card and for tests.
     */
    public fun luxFromEv(ev100: Double): Double = 2.5 * 2.0.pow(ev100)

    /**
     * Reflected spot-metering conversion (brief §3.2).
     *
     * Derives scene EV₁₀₀ for a tapped region from the camera's own auto-exposure
     * result plus the region's measured luminance relative to mid-grey:
     *
     * ```
     * EV₁₀₀ = log2(N² / t) − log2(S / 100) + log2(Y_region / Y_mid)
     * ```
     *
     * where N, t, S are the aperture, exposure time (s) and ISO the camera used
     * for the sampled frame (from `CaptureResult`), [regionLuminanceLinear] is the
     * mean **linearised** luma of the spot region and [midGreyLinear] ≈ 0.18.
     *
     * The region luminance MUST be linearised before it is averaged (see
     * [srgbToLinear]); averaging gamma-encoded luma skews readings by up to a
     * stop in high-contrast regions.
     *
     * @param apertureFNumber N actually used for the frame.
     * @param exposureTimeSeconds t actually used for the frame (must be > 0).
     * @param iso S actually used for the frame.
     * @param regionLuminanceLinear mean linear luma of the spot region (must be > 0).
     * @param midGreyLinear linear reflectance of mid-grey; defaults to 0.18.
     */
    public fun reflectedEv100(
        apertureFNumber: Double,
        exposureTimeSeconds: Double,
        iso: Double,
        regionLuminanceLinear: Double,
        midGreyLinear: Double = 0.18,
    ): Double {
        require(exposureTimeSeconds > 0.0) { "exposure time must be positive" }
        require(regionLuminanceLinear > 0.0) { "region luminance must be positive" }
        require(iso > 0.0) { "iso must be positive" }
        val cameraEv = log2(apertureFNumber * apertureFNumber / exposureTimeSeconds)
        return cameraEv - log2(iso / 100.0) + log2(regionLuminanceLinear / midGreyLinear)
    }

    /**
     * sRGB / BT.709 inverse OETF: convert one gamma-encoded channel value in
     * `[0, 1]` to linear light. Apply per channel/pixel **before** averaging a
     * region (brief §3.2).
     */
    public fun srgbToLinear(encoded: Double): Double {
        val c = encoded.coerceIn(0.0, 1.0)
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /**
     * Relative luminance from **linear** RGB using BT.709 coefficients.
     * Inputs must already be linearised (see [srgbToLinear]).
     */
    public fun linearLumaBt709(rLinear: Double, gLinear: Double, bLinear: Double): Double =
        0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear
}
