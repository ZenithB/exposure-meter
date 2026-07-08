package com.zenni.exposuremeter.engine

import kotlin.math.pow

/**
 * The three canonical third-stop parameter scales (brief §3.4).
 *
 * Nominal display values are the standardised photographic series (f/5.6, 1/125,
 * ISO 400 — not raw powers of two). They are conventionally rounded and cannot be
 * reproduced by naive rounding of the exact value (e.g. exact f/1.26 is written
 * "1.2", exact ISO 1270 is written "1250"), so the display strings are supplied
 * from the canonical tables below while the **exact** value and third-stop index
 * are derived by formula. All arithmetic elsewhere uses [StopValue.index].
 *
 * Reference points (third-stop index 0):
 *  - Aperture: f/1.0, exact `N = 2^(index/6)`   (Av = 2·log2 N = index/3 EV)
 *  - Shutter:  1 s,   exact `t = 2^(-index/3)` s (Tv = -log2 t = index/3 EV)
 *  - ISO:      100,   exact `S = 100·2^(index/3)`(Sv = log2(S/100) = index/3 EV)
 *
 * With these definitions every scale's third-stop index equals its exposure
 * contribution measured in thirds of a stop, which is what makes the exposure
 * constraint (§3.3) pure integer arithmetic — see [ExposureSolver].
 */
public object StopTables {

    /** Aperture f/1.0 → f/64 in third stops (indices 0..36). */
    public val aperture: StopScale = run {
        // Standard 1/3-stop f-number series, f/1.0 to f/64.
        val nominal = listOf(
            "1.0", "1.1", "1.2", "1.4", "1.6", "1.8",
            "2", "2.2", "2.5", "2.8", "3.2", "3.5",
            "4", "4.5", "5", "5.6", "6.3", "7.1",
            "8", "9", "10", "11", "13", "14",
            "16", "18", "20", "22", "25", "29",
            "32", "36", "40", "45", "51", "57",
            "64",
        )
        StopScale(
            Param.APERTURE,
            nominal.mapIndexed { i, s ->
                StopValue(index = i, exact = 2.0.pow(i / 6.0), display = s)
            },
        )
    }

    /** Shutter 30 s → 1/8000 s in third stops (indices -15..39). */
    public val shutter: StopScale = run {
        // Ordered fastest-light-to-least: 30 s (index -15) up to 1/8000 (index 39).
        // Values >= 1 s render as "Ns"; values < 1 s render as "1/x" (brief §3.4).
        val nominal = listOf(
            // index -15 .. -1  (long exposures, >= 1 s)
            "30s", "25s", "20s", "15s", "13s", "10s", "8s", "6s", "5s",
            "4s", "3.2s", "2.5s", "2s", "1.6s", "1.3s",
            // index 0 (1 s)
            "1s",
            // index 1 .. 39  (sub-second, 1/x)
            "1/1.3", "1/1.6", "1/2", "1/2.5", "1/3", "1/4", "1/5", "1/6",
            "1/8", "1/10", "1/13", "1/15", "1/20", "1/25", "1/30", "1/40",
            "1/50", "1/60", "1/80", "1/100", "1/125", "1/160", "1/200", "1/250",
            "1/320", "1/400", "1/500", "1/640", "1/800", "1/1000", "1/1250",
            "1/1600", "1/2000", "1/2500", "1/3200", "1/4000", "1/5000", "1/6400",
            "1/8000",
        )
        val minIndex = -15
        StopScale(
            Param.SHUTTER,
            nominal.mapIndexed { i, s ->
                val index = minIndex + i
                StopValue(index = index, exact = 2.0.pow(-index / 3.0), display = s)
            },
        )
    }

    /** ISO 25 → 102400 in third stops (indices -6..30). */
    public val iso: StopScale = run {
        // Standard 1/3-stop ISO/arithmetic-speed series.
        val nominal = listOf(
            "25", "32", "40", "50", "64", "80",
            "100", "125", "160", "200", "250", "320",
            "400", "500", "640", "800", "1000", "1250",
            "1600", "2000", "2500", "3200", "4000", "5000",
            "6400", "8000", "10000", "12800", "16000", "20000",
            "25600", "32000", "40000", "51200", "64000", "80000",
            "102400",
        )
        val minIndex = -6
        StopScale(
            Param.ISO,
            nominal.mapIndexed { i, s ->
                val index = minIndex + i
                StopValue(index = index, exact = 100.0 * 2.0.pow(index / 3.0), display = s)
            },
        )
    }

    /** The scale for a given [Param]. */
    public fun scaleFor(param: Param): StopScale = when (param) {
        Param.APERTURE -> aperture
        Param.SHUTTER -> shutter
        Param.ISO -> iso
    }
}
