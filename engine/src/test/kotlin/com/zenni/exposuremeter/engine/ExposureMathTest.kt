package com.zenni.exposuremeter.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ExposureMathTest {

    @Test
    fun `evFromLux uses C equals 250 calibration`() {
        // EV100 = log2(E / 2.5); E = 2.5 * 2^EV.
        assertEquals(0.0, ExposureMath.evFromLux(2.5), 1e-9)
        // Sunny-16 illuminance ~ 81920 lux -> EV100 15.
        assertEquals(15.0, ExposureMath.evFromLux(81920.0), 1e-6)
    }

    @Test
    fun `luxFromEv inverts evFromLux`() {
        val ev = 12.34
        assertEquals(ev, ExposureMath.evFromLux(ExposureMath.luxFromEv(ev)), 1e-9)
    }

    @Test
    fun `srgbToLinear matches known anchors`() {
        assertEquals(0.0, ExposureMath.srgbToLinear(0.0), 1e-12)
        assertEquals(1.0, ExposureMath.srgbToLinear(1.0), 1e-9)
        // Mid sRGB 0.5 linearises to ~0.214.
        assertTrue(abs(ExposureMath.srgbToLinear(0.5) - 0.2140) < 1e-3)
    }

    @Test
    fun `reflected metering of mid-grey recovers the camera EV`() {
        // Grey card: Y_region == Y_mid, so the luminance term is zero and the
        // result is just the camera's own EV at ISO 100.
        val ev = ExposureMath.reflectedEv100(
            apertureFNumber = 16.0,
            exposureTimeSeconds = 1.0 / 128.0,
            iso = 100.0,
            regionLuminanceLinear = 0.18,
            midGreyLinear = 0.18,
        )
        assertEquals(15.0, ev, 1e-6)
    }

    @Test
    fun `reflected metering brighter region reads higher EV`() {
        val base = ExposureMath.reflectedEv100(8.0, 1.0 / 60.0, 400.0, 0.18)
        val bright = ExposureMath.reflectedEv100(8.0, 1.0 / 60.0, 400.0, 0.36)
        // Doubling region luminance adds one stop.
        assertEquals(1.0, bright - base, 1e-9)
    }
}
