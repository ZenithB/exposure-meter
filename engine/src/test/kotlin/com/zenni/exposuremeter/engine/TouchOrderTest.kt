package com.zenni.exposuremeter.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TouchOrderTest {

    @Test
    fun `default order is shutter then aperture then iso`() {
        assertEquals(
            listOf(Param.SHUTTER, Param.APERTURE, Param.ISO),
            TouchOrder.DEFAULT.order,
        )
    }

    @Test
    fun `touch moves a param to most-recently-touched`() {
        val touched = TouchOrder.DEFAULT.touch(Param.SHUTTER)
        assertEquals(listOf(Param.APERTURE, Param.ISO, Param.SHUTTER), touched.order)
    }

    @Test
    fun `least recently touched respects candidate set`() {
        val order = TouchOrder.DEFAULT
        assertEquals(Param.SHUTTER, order.leastRecentlyTouched(setOf(Param.APERTURE, Param.SHUTTER)))
        assertEquals(Param.APERTURE, order.leastRecentlyTouched(setOf(Param.APERTURE, Param.ISO)))
        assertEquals(null, order.leastRecentlyTouched(emptySet()))
    }
}
