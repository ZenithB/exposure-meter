package com.zenni.exposuremeter.metering

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.zenni.exposuremeter.engine.ExposureMath
import com.zenni.exposuremeter.engine.RollingMedianFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Incident metering via the ambient light sensor (brief §3.1, §5).
 *
 * Subscribes to [Sensor.TYPE_LIGHT] at `SENSOR_DELAY_UI`, converts each
 * illuminance sample (lux) to EV₁₀₀ with [ExposureMath.evFromLux], and steadies
 * the stream with a short rolling median to reject PWM/LED flicker. Exposes the
 * result as a cold [Flow]; the sensor is registered on collection and
 * unregistered when the collector stops.
 */
class IncidentLightMeter(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    /** True when the device actually has an ambient light sensor (brief §5). */
    val isAvailable: Boolean get() = lightSensor != null

    /**
     * Cold flow of median-filtered incident readings. Emits nothing (and closes)
     * if the device has no light sensor. Non-positive lux samples are dropped
     * ([ExposureMath.evFromLux] requires positive illuminance).
     */
    fun readings(medianWindow: Int = 5): Flow<Ev100Reading> = callbackFlow {
        val sensor = lightSensor
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val filter = RollingMedianFilter(medianWindow)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values.firstOrNull()?.toDouble() ?: return
                if (lux <= 0.0) return
                val ev = filter.add(ExposureMath.evFromLux(lux))
                trySend(Ev100Reading(ev100 = ev, lux = lux))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
