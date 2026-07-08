// Root build script. Plugins are declared (apply false) so subprojects can
// apply them from the pinned version catalog. No plugin is applied at the root.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
