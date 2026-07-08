// :engine — pure Kotlin/JVM. NO Android dependencies (brief §2, §8): the
// exposure maths must be unit-testable without an emulator or the Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
