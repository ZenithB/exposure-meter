package com.zenni.exposuremeter.engine

import kotlin.math.roundToInt

/**
 * One entry on a parameter wheel: a single nominal photographic value.
 *
 * @property index the exact position of this value on a **third-stop** grid,
 *   measured in thirds of a stop from the scale's reference point (f/1.0 for
 *   aperture, 1 s for shutter, ISO 100 for ISO). May be negative (e.g. long
 *   shutter times, ISO below 100). Arithmetic uses this integer exclusively.
 * @property exact the mathematically exact physical value for [index]
 *   (e.g. `2^(index/6)` for aperture). Kept for reference/derivation; never
 *   shown to the user.
 * @property display the conventional nominal string a photographer expects
 *   (e.g. `"5.6"`, `"1/125"`, `"400"`). Display only — never fed back into maths.
 */
public data class StopValue(
    val index: Int,
    val exact: Double,
    val display: String,
)

/**
 * An ordered scale of [StopValue]s for one [Param], spaced in third stops.
 *
 * The scale is the bridge between the exact log2 exposure maths and the nominal
 * values photographers read. Every value knows its exact third-stop [index], so
 * solving is integer arithmetic on indices; only rendering uses [StopValue.display].
 *
 * Entries are sorted ascending by [StopValue.index]. Because the grid is regular,
 * list position and [index] differ only by a constant offset ([minIndex]).
 */
public class StopScale internal constructor(
    public val param: Param,
    values: List<StopValue>,
) {
    /** All values, ascending by third-stop index. */
    public val values: List<StopValue> = values.sortedBy { it.index }

    /** Lowest third-stop index present on this scale. */
    public val minIndex: Int = this.values.first().index

    /** Highest third-stop index present on this scale. */
    public val maxIndex: Int = this.values.last().index

    /** The [StopValue] at third-stop [index]. @throws if out of range. */
    public fun at(index: Int): StopValue {
        require(index in minIndex..maxIndex) {
            "index $index out of range [$minIndex, $maxIndex] for $param"
        }
        return values[index - minIndex]
    }

    /** True when [index] lies within this scale's inclusive range. */
    public fun contains(index: Int): Boolean = index in minIndex..maxIndex

    /** Clamp [index] into `[minIndex, maxIndex]`. */
    public fun clamp(index: Int): Int = index.coerceIn(minIndex, maxIndex)

    /**
     * Snap a real-valued third-stop position to the nearest on-grid index,
     * clamped to the scale's range.
     *
     * @return a [Snapped] carrying the chosen index, whether clamping occurred,
     *   and the signed residual (requested minus snapped, in thirds) so callers
     *   can fold rounding error into an exposure-delta readout (brief §3.4).
     */
    public fun snap(thirds: Double): Snapped {
        val nearest = thirds.roundToInt()
        val clamped = clamp(nearest)
        return Snapped(
            index = clamped,
            residualThirds = thirds - clamped,
            clamped = clamped != nearest,
        )
    }

    /** Result of [snap]: the on-grid index plus the error left behind. */
    public data class Snapped(
        val index: Int,
        val residualThirds: Double,
        val clamped: Boolean,
    )
}
