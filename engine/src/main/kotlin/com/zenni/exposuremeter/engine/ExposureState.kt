package com.zenni.exposuremeter.engine

/**
 * The full exposure-triangle UI state the solver reads and rewrites.
 *
 * Parameter values are third-stop indices into their [StopScale] (see
 * [StopTables]); this keeps the solver in exact integer arithmetic. Locks and
 * [touchOrder] drive the lock model (brief §4).
 */
public data class ExposureState(
    val apertureIndex: Int,
    val shutterIndex: Int,
    val isoIndex: Int,
    val apertureLocked: Boolean = false,
    val shutterLocked: Boolean = false,
    val isoLocked: Boolean = false,
    val touchOrder: TouchOrder = TouchOrder.DEFAULT,
) {
    /** Third-stop index of [param]. */
    public fun indexOf(param: Param): Int = when (param) {
        Param.APERTURE -> apertureIndex
        Param.SHUTTER -> shutterIndex
        Param.ISO -> isoIndex
    }

    /** Whether [param]'s wheel is locked (frozen). */
    public fun isLocked(param: Param): Boolean = when (param) {
        Param.APERTURE -> apertureLocked
        Param.SHUTTER -> shutterLocked
        Param.ISO -> isoLocked
    }

    /** Copy with [param] set to third-stop [index]. */
    public fun withIndex(param: Param, index: Int): ExposureState = when (param) {
        Param.APERTURE -> copy(apertureIndex = index)
        Param.SHUTTER -> copy(shutterIndex = index)
        Param.ISO -> copy(isoIndex = index)
    }

    /** The set of currently unlocked (free) params. */
    public val freeParams: Set<Param>
        get() = Param.entries.filterNot { isLocked(it) }.toSet()

    /** The current [StopValue] for [param]. */
    public fun valueOf(param: Param): StopValue =
        StopTables.scaleFor(param).at(indexOf(param))
}
