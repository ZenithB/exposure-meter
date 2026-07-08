package com.zenni.exposuremeter.ui

/** How scene light is being determined (brief §5, §6). */
enum class MeteringMode(val label: String) {
    /** Ambient light sensor (Phase 3). */
    INCIDENT("Incident"),

    /** Camera spot metering (Phase 4). */
    REFLECTED("Reflected"),

    /** Typed / nudged EV — the sunny-16 fallback (brief §6). */
    MANUAL("Manual"),
}
