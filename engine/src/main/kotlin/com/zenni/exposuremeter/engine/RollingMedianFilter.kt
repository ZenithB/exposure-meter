package com.zenni.exposuremeter.engine

/**
 * A fixed-window rolling median filter (brief §5) used to steady live meter
 * readings against flicker from PWM / LED lighting.
 *
 * Median (rather than mean) rejects the brief dark/bright excursions PWM sources
 * produce without lagging a genuine change in scene light. Pure Kotlin, so the
 * behaviour is unit-tested without a sensor.
 *
 * Because the median commutes with any monotonic transform, filtering EV₁₀₀
 * values is equivalent to filtering the underlying lux — callers may feed either.
 *
 * @param window number of most-recent samples to consider (must be >= 1).
 */
public class RollingMedianFilter(private val window: Int) {

    init {
        require(window >= 1) { "window must be >= 1, was $window" }
    }

    private val buffer = ArrayDeque<Double>()

    /** Number of samples currently held (0..window). */
    public val size: Int get() = buffer.size

    /**
     * Add [sample], evicting the oldest if the window is full, and return the
     * median of the samples now held. For an even count the median is the mean of
     * the two central values.
     */
    public fun add(sample: Double): Double {
        if (buffer.size == window) buffer.removeFirst()
        buffer.addLast(sample)
        return median()
    }

    /** The median of the samples currently held. @throws if empty. */
    public fun median(): Double {
        check(buffer.isNotEmpty()) { "no samples" }
        val sorted = buffer.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    /** Discard all held samples (e.g. on mode switch or sensor resubscribe). */
    public fun reset() {
        buffer.clear()
    }
}
