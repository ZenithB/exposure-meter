package com.zenni.exposuremeter.engine

import kotlin.math.abs

/**
 * The metering + dial inputs that, together with the exposure state, determine
 * correct exposure (brief §3.3).
 *
 * @property ev100 metered scene light as EV at ISO 100 (may be frozen by hold).
 * @property evComp exposure-compensation dial, in EV (±5, thirds; brief §6).
 * @property ndStops ND-filter offset, in stops (brief §6).
 * @property calibration per-mode calibration offset, in EV (brief §6).
 *
 * Sign convention (brief §3.3, taken verbatim): the required setting satisfies
 * `log2(N²) − log2(t) = EV₁₀₀ + log2(S/100) + EVcomp + NDstops + CAL`. Increasing
 * [evComp], [ndStops] or [calibration] therefore raises the required
 * (aperture+shutter) index sum. NOTE for product owner (Zenni): the brief's
 * prose glosses ND as "longer exposure required", which is the opposite sense to
 * this literal equation; the equation is implemented as written and the sign is
 * isolated in [requiredThirds] so it can be flipped after review if intended.
 */
public data class MeterInputs(
    val ev100: Double,
    val evComp: Double = 0.0,
    val ndStops: Double = 0.0,
    val calibration: Double = 0.0,
)

/**
 * A user's interaction with the wheels for one solve.
 */
public sealed interface ExposureAction {
    /**
     * The user scrolled [param] to third-stop [newIndex]. The solver moves the
     * appropriate free wheel to keep exposure correct (brief §4.1–§4.3).
     */
    public data class ChangeParam(val param: Param, val newIndex: Int) : ExposureAction

    /**
     * The meter reading or a dial (EVcomp/ND/CAL) changed; no wheel was touched.
     * The solver re-solves the free wheel(s) so the frozen/locked params still
     * expose correctly (brief §4.3–§4.4, live metering).
     */
    public data object MeterOrDialChanged : ExposureAction
}

/**
 * Over/under-exposure of the current settings versus the metered light.
 *
 * @property thirds signed deviation in thirds of a stop. **Negative = under**,
 *   positive = over. Zero (within rounding) means the settings match the meter.
 * @property clamped true when a solved value hit the end of its wheel's range and
 *   could not fully satisfy the meter (brief §4.5 shortfall).
 */
public data class ExposureDelta(
    val thirds: Double,
    val clamped: Boolean,
) {
    /** True when the settings are effectively correct (|delta| < 1/12 stop). */
    public val isBalanced: Boolean get() = abs(thirds) < 0.25 && !clamped
}

/** The outcome of a solve: the new state and the resulting exposure delta. */
public data class SolveResult(
    val state: ExposureState,
    val delta: ExposureDelta,
)

/**
 * The lock-model solver (brief §4, §8) — the pure heart of the app.
 *
 * The exposure constraint, derived in [MeterInputs], reduces to pure integer
 * arithmetic on third-stop indices because each scale's index equals its EV
 * contribution in thirds (see [StopTables]):
 *
 * ```
 * apertureIndex + shutterIndex − isoIndex  =  requiredThirds(inputs)
 * ```
 *
 * where `requiredThirds = 3·(ev100 + evComp + ndStops + calibration)` (a real
 * number; its fractional part is the sub-third residual folded into the delta).
 * The [ExposureDelta] is `required − (a + t − s)` using the final indices, so a
 * negative delta means the settings underexpose relative to the meter.
 */
public object ExposureSolver {

    /**
     * The right-hand side of the exposure constraint, in thirds of a stop.
     * Isolated so the EVcomp/ND/CAL sign convention lives in exactly one place
     * (see the note on [MeterInputs]).
     */
    public fun requiredThirds(inputs: MeterInputs): Double =
        3.0 * (inputs.ev100 + inputs.evComp + inputs.ndStops + inputs.calibration)

    /** The current constraint value `a + t − s` for [state], in thirds. */
    public fun currentThirds(state: ExposureState): Int =
        state.apertureIndex + state.shutterIndex - state.isoIndex

    /**
     * Compute the exposure delta for a state without changing anything — used for
     * the live badge under three locks (brief §4.4) and after any solve.
     */
    public fun delta(state: ExposureState, inputs: MeterInputs, clamped: Boolean = false): ExposureDelta =
        ExposureDelta(
            thirds = requiredThirds(inputs) - currentThirds(state),
            clamped = clamped,
        )

    /**
     * Apply [action] under the lock model and return the new state + delta.
     *
     * Rules (brief §4):
     *  - [ExposureAction.ChangeParam] on a locked wheel is a no-op (the UI freezes
     *    it; this guards the engine).
     *  - Otherwise the changed wheel is set (clamped) and marked most-recently
     *    touched, then an *absorber* free wheel is moved to satisfy the constraint:
     *      • 2 unlocked others (zero-lock rule §4.1): the least-recently-touched of
     *        the two absorbs; the third holds.
     *      • 1 unlocked other (one-lock rule §4.2): it absorbs.
     *      • 0 unlocked others (two-/three-lock §4.3): nothing absorbs; the delta
     *        badge shows the deviation the user dialled in.
     *  - [ExposureAction.MeterOrDialChanged]: the least-recently-touched *unlocked*
     *    wheel absorbs the new reading (auto-updating the free wheel under two
     *    locks §4.3; holding user-set wheels stable under fewer locks). With no
     *    unlocked wheel (three locks §4.4) nothing moves and only the delta updates.
     *
     * Solved values are snapped to the third-stop grid and clamped to range;
     * clamping shortfall and the sub-third residual both surface in the delta.
     */
    public fun solve(
        state: ExposureState,
        inputs: MeterInputs,
        action: ExposureAction,
    ): SolveResult = when (action) {
        is ExposureAction.ChangeParam -> solveChange(state, inputs, action)
        ExposureAction.MeterOrDialChanged -> solveMeter(state, inputs)
    }

    private fun solveChange(
        state: ExposureState,
        inputs: MeterInputs,
        action: ExposureAction.ChangeParam,
    ): SolveResult {
        val changed = action.param
        if (state.isLocked(changed)) {
            // Locked wheels do not move; report the state's current delta unchanged.
            return SolveResult(state, delta(state, inputs))
        }

        val scale = StopTables.scaleFor(changed)
        val newIndex = scale.clamp(action.newIndex)
        var next = state.withIndex(changed, newIndex)
            .copy(touchOrder = state.touchOrder.touch(changed))

        val otherUnlocked = (Param.entries.toSet() - changed).filterNot { next.isLocked(it) }.toSet()
        val absorber = when (otherUnlocked.size) {
            0 -> null // both others locked: two-lock free-wheel scroll (§4.3)
            1 -> otherUnlocked.first() // one-lock partner (§4.2)
            else -> next.touchOrder.leastRecentlyTouched(otherUnlocked) // zero-lock (§4.1)
        } ?: return SolveResult(next, delta(next, inputs)) // nothing absorbs

        return absorbInto(next, inputs, absorber)
    }

    private fun solveMeter(state: ExposureState, inputs: MeterInputs): SolveResult {
        val absorber = state.touchOrder.leastRecentlyTouched(state.freeParams)
            ?: return SolveResult(state, delta(state, inputs)) // three locks (§4.4)
        return absorbInto(state, inputs, absorber)
    }

    /**
     * Move [absorber] to the on-grid index that best satisfies the constraint,
     * holding the other two params fixed. Solving for one index:
     *
     *  - aperture: a = required − t + s
     *  - shutter:  t = required − a + s
     *  - iso:      s = a + t − required
     */
    private fun absorbInto(
        state: ExposureState,
        inputs: MeterInputs,
        absorber: Param,
    ): SolveResult {
        val required = requiredThirds(inputs)
        val a = state.apertureIndex
        val t = state.shutterIndex
        val s = state.isoIndex
        val target: Double = when (absorber) {
            Param.APERTURE -> required - t + s
            Param.SHUTTER -> required - a + s
            Param.ISO -> a + t - required
        }
        val snapped = StopTables.scaleFor(absorber).snap(target)
        val next = state.withIndex(absorber, snapped.index)
        return SolveResult(next, delta(next, inputs, clamped = snapped.clamped))
    }
}
