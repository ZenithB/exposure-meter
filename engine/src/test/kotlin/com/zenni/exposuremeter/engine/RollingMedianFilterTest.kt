package com.zenni.exposuremeter.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RollingMedianFilterTest {

    @Test
    fun `add returns the running median as samples accumulate`() {
        val f = RollingMedianFilter(window = 5)
        assertEquals(10.0, f.add(10.0), 1e-9) // [10]
        assertEquals(11.0, f.add(12.0), 1e-9) // [10,12] -> mean(10,12)
        assertEquals(11.0, f.add(11.0), 1e-9) // sorted [10,11,12] -> 11
        assertEquals(11.5, f.add(13.0), 1e-9) // sorted [10,11,12,13] -> mean(11,12)
        assertEquals(11.0, f.add(9.0), 1e-9)  // sorted [9,10,11,12,13] -> 11
    }

    @Test
    fun `window evicts oldest sample`() {
        val f = RollingMedianFilter(window = 3)
        f.add(1.0); f.add(2.0); f.add(3.0) // [1,2,3] median 2
        assertEquals(2.0, f.median(), 1e-9)
        f.add(100.0) // evict 1 -> [2,3,100] median 3
        assertEquals(3.0, f.median(), 1e-9)
        assertEquals(3, f.size)
    }

    @Test
    fun `single spike is rejected by the median`() {
        val f = RollingMedianFilter(window = 5)
        listOf(10.0, 10.2, 9.9, 10.1).forEach { f.add(it) }
        val withSpike = f.add(0.0) // PWM dark frame
        // Median stays near the steady value despite the outlier.
        assertEquals(10.0, withSpike, 0.2)
    }

    @Test
    fun `even window averages the two central values`() {
        val f = RollingMedianFilter(window = 4)
        f.add(1.0); f.add(2.0); f.add(3.0); f.add(4.0)
        assertEquals(2.5, f.median(), 1e-9)
    }

    @Test
    fun `empty filter has no median`() {
        val f = RollingMedianFilter(window = 5)
        assertThrows(IllegalStateException::class.java) { f.median() }
    }

    @Test
    fun `window must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { RollingMedianFilter(window = 0) }
    }
}
