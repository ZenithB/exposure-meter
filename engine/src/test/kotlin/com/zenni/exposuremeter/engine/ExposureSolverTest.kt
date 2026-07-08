package com.zenni.exposuremeter.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises every rule in brief §4 plus the Phase-1 acceptance checks.
 *
 * Index landmarks used below (see [StopTables]):
 *  - aperture: f/5.6 = 15, f/8 = 18, f/16 = 24, f/1.0 = 0, f/64 = 36
 *  - shutter:  1/125 = 21, 1s = 0, 1/8000 = 39, 30s = -15
 *  - iso:      100 = 0, 200 = 3, 400 = 6
 */
class ExposureSolverTest {

    private fun state(
        a: Int, t: Int, s: Int,
        aL: Boolean = false, tL: Boolean = false, sL: Boolean = false,
        order: TouchOrder = TouchOrder.DEFAULT,
    ) = ExposureState(a, t, s, aL, tL, sL, order)

    /** A balanced sunny-16 baseline: f/16 @ 1/125 @ ISO 100 for EV100 = 15. */
    private val sunny16 = state(a = 24, t = 21, s = 0)
    private val ev15 = MeterInputs(ev100 = 15.0)

    // ---- Acceptance: sunny-16 sanity (brief Phase 1) -------------------------

    @Test
    fun `sunny-16 EV100 15 ISO 100 yields f16 at 1_125`() {
        // Lock aperture and ISO; the free shutter must solve to 1/125.
        val locked = sunny16.copy(apertureLocked = true, isoLocked = true)
        val result = ExposureSolver.solve(locked, ev15, ExposureAction.MeterOrDialChanged)
        assertEquals(21, result.state.shutterIndex)
        assertEquals("1/125", result.state.valueOf(Param.SHUTTER).display)
        assertEquals("16", result.state.valueOf(Param.APERTURE).display)
        assertTrue(result.delta.isBalanced)
    }

    @Test
    fun `baseline sunny-16 triple is balanced`() {
        assertTrue(ExposureSolver.delta(sunny16, ev15).isBalanced)
    }

    // ---- Rule §4.1: zero locks, least-recently-touched absorbs ---------------

    @Test
    fun `zero locks - changing ISO is absorbed by shutter`() {
        // DEFAULT order [SHUTTER, APERTURE, ISO]; others of ISO are {A,T} ->
        // shutter is least-recently touched.
        val result = ExposureSolver.solve(
            sunny16, ev15, ExposureAction.ChangeParam(Param.ISO, 3), // ISO 200
        )
        assertEquals(3, result.state.isoIndex)
        assertEquals(24, result.state.apertureIndex) // unchanged
        assertEquals(24, result.state.shutterIndex)  // +1 stop to compensate
        assertTrue(result.delta.isBalanced)
    }

    @Test
    fun `zero locks - changing shutter is absorbed by aperture`() {
        // Others of SHUTTER are {A, ISO}; least-recently touched is APERTURE.
        val result = ExposureSolver.solve(
            sunny16, ev15, ExposureAction.ChangeParam(Param.SHUTTER, 24), // 1/1000
        )
        assertEquals(24, result.state.shutterIndex)
        assertEquals(0, result.state.isoIndex)       // unchanged
        assertEquals(21, result.state.apertureIndex) // -1 stop (f/11)
        assertTrue(result.delta.isBalanced)
    }

    @Test
    fun `zero locks - touch order advances so the other wheel absorbs next`() {
        // First move ISO (shutter absorbs). Then move ISO again is not the point;
        // move APERTURE: others {SHUTTER, ISO}; ISO was just user-touched, so the
        // least-recently touched is SHUTTER -> shutter absorbs again.
        val first = ExposureSolver.solve(sunny16, ev15, ExposureAction.ChangeParam(Param.ISO, 3))
        val second = ExposureSolver.solve(
            first.state, ev15, ExposureAction.ChangeParam(Param.APERTURE, 21), // f/11
        )
        assertEquals(21, second.state.apertureIndex)
        assertEquals(3, second.state.isoIndex)       // held (recently touched)
        assertEquals(27, second.state.shutterIndex)  // shutter absorbs the -1 stop
        assertTrue(second.delta.isBalanced)
    }

    // ---- Rule §4.2: one lock -------------------------------------------------

    @Test
    fun `one lock - adjusting an unlocked wheel moves the other unlocked wheel`() {
        val locked = sunny16.copy(isoLocked = true)
        val result = ExposureSolver.solve(
            locked, ev15, ExposureAction.ChangeParam(Param.APERTURE, 27), // f/22, -? +1 stop closed
        )
        assertEquals(27, result.state.apertureIndex)
        assertEquals(0, result.state.isoIndex)      // locked, unchanged
        assertEquals(18, result.state.shutterIndex) // shutter is the only absorber
        assertTrue(result.delta.isBalanced)
    }

    @Test
    fun `locked wheel cannot be changed`() {
        val locked = sunny16.copy(isoLocked = true)
        val result = ExposureSolver.solve(
            locked, ev15, ExposureAction.ChangeParam(Param.ISO, 6),
        )
        assertEquals(sunny16.isoIndex, result.state.isoIndex) // no-op
        assertEquals(locked, result.state)
    }

    // ---- Rule §4.3: two locks, auto free wheel + delta on manual scroll ------

    @Test
    fun `two locks - meter change auto-updates the free wheel`() {
        val locked = sunny16.copy(apertureLocked = true, isoLocked = true)
        val darker = MeterInputs(ev100 = 14.0) // one stop less light
        val result = ExposureSolver.solve(locked, darker, ExposureAction.MeterOrDialChanged)
        assertEquals(18, result.state.shutterIndex) // 1/60, one stop slower
        assertTrue(result.delta.isBalanced)
    }

    @Test
    fun `two locks - scrolling the free wheel off shows a delta badge`() {
        val locked = sunny16.copy(apertureLocked = true, isoLocked = true)
        // User forces shutter to 1/1000 (index 24) though correct is 1/125 (21):
        // three stops too fast -> one stop under (delta -3 thirds).
        val result = ExposureSolver.solve(
            locked, ev15, ExposureAction.ChangeParam(Param.SHUTTER, 24),
        )
        assertEquals(24, result.state.shutterIndex)
        assertEquals(-3.0, result.delta.thirds, 1e-9)
        assertFalse(result.delta.isBalanced)
        assertEquals("-1 EV", StopFormat.badge(result.delta))
    }

    // ---- Rule §4.4: three locks ---------------------------------------------

    @Test
    fun `three locks - nothing moves and the delta tracks the meter`() {
        val locked = sunny16.copy(apertureLocked = true, shutterLocked = true, isoLocked = true)
        val darker = MeterInputs(ev100 = 13.0) // two stops less light
        val result = ExposureSolver.solve(locked, darker, ExposureAction.MeterOrDialChanged)
        assertEquals(locked, result.state) // frozen
        assertEquals(-6.0, result.delta.thirds, 1e-9) // -2 EV
    }

    // ---- Rule §4.5: range clamping ------------------------------------------

    @Test
    fun `clamping - too-bright scene pins shutter fast and shows shortfall`() {
        // Aperture f/1.0 (0) + ISO 100 (0), EV100 = 20 -> required 60, but shutter
        // maxes at 1/8000 (39). Shortfall of +7 EV (over), flagged clamped.
        val locked = state(a = 0, t = 21, s = 0, aL = true, sL = true)
        val bright = MeterInputs(ev100 = 20.0)
        val result = ExposureSolver.solve(locked, bright, ExposureAction.MeterOrDialChanged)
        assertEquals(39, result.state.shutterIndex)
        assertTrue(result.delta.clamped)
        assertEquals(21.0, result.delta.thirds, 1e-9) // +7 EV over
        assertFalse(result.delta.isBalanced)
    }

    @Test
    fun `clamping - too-dark scene pins shutter slow and flags it`() {
        val locked = state(a = 36, t = 0, s = 0, aL = true, sL = true) // f/64
        val dark = MeterInputs(ev100 = -5.0)
        val result = ExposureSolver.solve(locked, dark, ExposureAction.MeterOrDialChanged)
        assertEquals(-15, result.state.shutterIndex) // 30 s end stop
        assertTrue(result.delta.clamped)
    }

    // ---- Residual folding (brief §3.4) --------------------------------------

    @Test
    fun `sub-third residual surfaces in the delta`() {
        // EV100 = 10.1 -> required 30.3; aperture f/5.6 (15) + ISO 100 locked;
        // shutter solves to 15.3 -> snaps to 15, leaving 0.3 thirds in the delta.
        val locked = state(a = 15, t = 15, s = 0, aL = true, sL = true)
        val inputs = MeterInputs(ev100 = 10.1)
        val result = ExposureSolver.solve(locked, inputs, ExposureAction.MeterOrDialChanged)
        assertEquals(15, result.state.shutterIndex)
        assertEquals(0.3, result.delta.thirds, 1e-9)
        assertFalse(result.delta.clamped)
    }

    // ---- Meter change with fewer locks picks least-recently-touched free -----

    @Test
    fun `meter change with one lock moves least-recently-touched free wheel`() {
        val locked = sunny16.copy(isoLocked = true) // free {A, T}
        val darker = MeterInputs(ev100 = 14.0)
        val result = ExposureSolver.solve(locked, darker, ExposureAction.MeterOrDialChanged)
        assertEquals(24, result.state.apertureIndex) // aperture held
        assertEquals(18, result.state.shutterIndex)  // shutter (LRT) absorbs
    }

    // ---- Dial inputs feed the constraint ------------------------------------

    @Test
    fun `EVcomp and ND stops shift the required exposure`() {
        val base = ExposureSolver.requiredThirds(MeterInputs(ev100 = 15.0))
        val comp = ExposureSolver.requiredThirds(MeterInputs(ev100 = 15.0, evComp = 1.0))
        val nd = ExposureSolver.requiredThirds(MeterInputs(ev100 = 15.0, ndStops = 2.0))
        assertEquals(45.0, base, 1e-9)
        assertEquals(48.0, comp, 1e-9) // +1 EV = +3 thirds
        assertEquals(51.0, nd, 1e-9)   // +2 stops = +6 thirds
    }
}
