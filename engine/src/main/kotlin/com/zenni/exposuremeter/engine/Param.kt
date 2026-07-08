package com.zenni.exposuremeter.engine

/**
 * The three legs of the exposure triangle. Each maps to one on-screen wheel.
 *
 * All exposure arithmetic in this module is done in **third-stop index space**
 * (see [StopScale]); this enum simply identifies which wheel a value belongs to.
 */
public enum class Param {
    APERTURE,
    SHUTTER,
    ISO,
}
