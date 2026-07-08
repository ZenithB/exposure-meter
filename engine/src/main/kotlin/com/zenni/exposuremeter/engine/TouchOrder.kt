package com.zenni.exposuremeter.engine

/**
 * Least-recently-touched ordering of the three wheels, used by the zero- and
 * one-lock rules to decide which free wheel absorbs a change (brief §4.1).
 *
 * [order] is oldest-first: `order.first()` is the least-recently user-touched
 * wheel. The default at first launch is shutter, then aperture, then ISO, so the
 * first adjustment is absorbed by shutter and (if shutter itself is moved) by
 * aperture — the brief's specified default.
 */
public data class TouchOrder(
    val order: List<Param> = listOf(Param.SHUTTER, Param.APERTURE, Param.ISO),
) {
    init {
        require(order.toSet() == Param.entries.toSet()) {
            "TouchOrder must contain each Param exactly once, was $order"
        }
    }

    /** Return a copy with [param] moved to most-recently-touched (last). */
    public fun touch(param: Param): TouchOrder =
        TouchOrder(order.filterNot { it == param } + param)

    /**
     * The least-recently-touched param among [candidates], or `null` if none of
     * the candidates are present (empty set).
     */
    public fun leastRecentlyTouched(candidates: Set<Param>): Param? =
        order.firstOrNull { it in candidates }

    public companion object {
        /** The brief's first-launch default: shutter, then aperture, then ISO. */
        public val DEFAULT: TouchOrder = TouchOrder()
    }
}
