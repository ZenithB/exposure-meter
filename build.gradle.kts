// Root build script. Plugins are declared (apply false) so subprojects can
// apply them from the pinned version catalog. No plugin is applied at the root.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}
