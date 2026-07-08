package com.zenni.exposuremeter.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StopFormatTest {

    @Test
    fun `whole and fractional stops render in thirds`() {
        assertEquals("0", StopFormat.stops(0.0))
        assertEquals("+1", StopFormat.stops(3.0))
        assertEquals("-1", StopFormat.stops(-3.0))
        assertEquals("+⅓", StopFormat.stops(1.0))
        assertEquals("+⅔", StopFormat.stops(2.0))
        assertEquals("-1⅓", StopFormat.stops(-4.0))
        assertEquals("+2⅔", StopFormat.stops(8.0))
    }

    @Test
    fun `ascii fractions available for logs and tests`() {
        assertEquals("+1 1/3", StopFormat.stops(4.0, useUnicodeFractions = false))
        assertEquals("-1/3", StopFormat.stops(-1.0, useUnicodeFractions = false))
    }

    @Test
    fun `small residuals round to nearest third`() {
        assertEquals("0", StopFormat.stops(0.3))
        assertEquals("-1", StopFormat.stops(-2.9))
    }

    @Test
    fun `badge appends EV unit`() {
        assertEquals("-1⅓ EV", StopFormat.badge(ExposureDelta(-4.0, clamped = false)))
        assertEquals("+1 EV", StopFormat.badge(ExposureDelta(3.0, clamped = false)))
    }
}
