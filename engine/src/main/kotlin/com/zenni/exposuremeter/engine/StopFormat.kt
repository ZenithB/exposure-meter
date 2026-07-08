package com.zenni.exposuremeter.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Formats exposure deltas as photographers read them — signed whole stops plus a
 * third fraction (brief §4: the delta badge is "always in thirds notation").
 *
 * Pure text; no Android or UI dependency. The consumer decides colour (amber
 * under ±1 EV, red beyond) from the numeric [ExposureDelta.thirds].
 */
public object StopFormat {

    private val THIRD_FRACTIONS = mapOf(0 to "", 1 to "⅓", 2 to "⅔") // ⅓ ⅔

    /**
     * Render [thirds] (thirds of a stop) as e.g. `"0"`, `"+2⅓"`, `"-1⅔"`.
     * The value is rounded to the nearest third for display.
     *
     * @param useUnicodeFractions when false, uses ASCII `"1/3"`/`"2/3"` instead of
     *   the vulgar-fraction glyphs (handy for logs and test assertions).
     */
    public fun stops(thirds: Double, useUnicodeFractions: Boolean = true): String {
        val nearest = thirds.roundToInt()
        if (nearest == 0) return "0"
        val sign = if (nearest < 0) "-" else "+"
        val magnitude = abs(nearest)
        val whole = magnitude / 3
        val frac = magnitude % 3
        val fracStr = if (useUnicodeFractions) {
            THIRD_FRACTIONS.getValue(frac)
        } else {
            when (frac) {
                1 -> "1/3"
                2 -> "2/3"
                else -> ""
            }
        }
        val body = when {
            whole == 0 -> fracStr.ifEmpty { "0" }
            fracStr.isEmpty() -> whole.toString()
            useUnicodeFractions -> "$whole$fracStr"
            else -> "$whole $fracStr"
        }
        return "$sign$body"
    }

    /** Render an [ExposureDelta] as a badge string, e.g. `"-1⅓ EV"`. */
    public fun badge(delta: ExposureDelta, useUnicodeFractions: Boolean = true): String =
        "${stops(delta.thirds, useUnicodeFractions)} EV"
}
