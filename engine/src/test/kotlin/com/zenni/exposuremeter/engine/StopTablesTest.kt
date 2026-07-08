package com.zenni.exposuremeter.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class StopTablesTest {

    @Test
    fun `aperture scale spans f1_0 to f64 in thirds`() {
        val a = StopTables.aperture
        assertEquals(0, a.minIndex)
        assertEquals(36, a.maxIndex)
        assertEquals("1.0", a.at(0).display)
        assertEquals("64", a.at(36).display)
        // f/16 is exactly 4 stops (12 thirds) from f/1.0.
        assertEquals("16", a.at(24).display)
        // Full-stop landmarks fall on multiples of 3.
        assertEquals("2", a.at(6).display)
        assertEquals("2.8", a.at(9).display)
        assertEquals("5.6", a.at(15).display)
        assertEquals("8", a.at(18).display)
        assertEquals("11", a.at(21).display)
    }

    @Test
    fun `shutter scale spans 30s to 1_8000 with correct display rule`() {
        val s = StopTables.shutter
        assertEquals(-15, s.minIndex)
        assertEquals(39, s.maxIndex)
        assertEquals("30s", s.at(-15).display)
        assertEquals("1s", s.at(0).display)
        assertEquals("1/125", s.at(21).display)
        assertEquals("1/8000", s.at(39).display)
        // >= 1 s ends in "s"; < 1 s starts with "1/".
        s.values.forEach { v ->
            if (v.exact >= 1.0) assertTrue(v.display.endsWith("s"), "expected Ns for ${v.display}")
            else assertTrue(v.display.startsWith("1/"), "expected 1/x for ${v.display}")
        }
    }

    @Test
    fun `iso scale spans 25 to 102400 in thirds`() {
        val s = StopTables.iso
        assertEquals(-6, s.minIndex)
        assertEquals(30, s.maxIndex)
        assertEquals("25", s.at(-6).display)
        assertEquals("100", s.at(0).display)
        assertEquals("400", s.at(6).display)
        assertEquals("102400", s.at(30).display)
    }

    @Test
    fun `exact values track their third-stop index`() {
        // Aperture N = 2^(index/6); index/3 EV of Av.
        assertTrue(abs(StopTables.aperture.at(6).exact - 2.0) < 1e-9)
        assertTrue(abs(StopTables.aperture.at(24).exact - 16.0) < 1e-9)
        // Shutter t = 2^(-index/3).
        assertTrue(abs(StopTables.shutter.at(0).exact - 1.0) < 1e-9)
        assertTrue(abs(StopTables.shutter.at(3).exact - 0.5) < 1e-9)
        // ISO S = 100 * 2^(index/3).
        assertTrue(abs(StopTables.iso.at(0).exact - 100.0) < 1e-9)
        assertTrue(abs(StopTables.iso.at(3).exact - 200.0) < 1e-9)
    }

    @Test
    fun `snap returns nearest index with signed residual`() {
        val a = StopTables.aperture
        val snapped = a.snap(15.3)
        assertEquals(15, snapped.index)
        assertTrue(abs(snapped.residualThirds - 0.3) < 1e-9)
        assertTrue(!snapped.clamped)
    }

    @Test
    fun `snap clamps out-of-range positions and flags it`() {
        val s = StopTables.shutter
        val hi = s.snap(1000.0)
        assertEquals(39, hi.index)
        assertTrue(hi.clamped)
        val lo = s.snap(-1000.0)
        assertEquals(-15, lo.index)
        assertTrue(lo.clamped)
    }
}
