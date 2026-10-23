plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Coordinates of the published libraries: com.github.Pixel-Du-Nord.faro-android:faro and :faro-ui.
// The group is fixed here on purpose. JitPack passes GROUP=com.github.Pixel-Du-Nord (the account),
// and with that group the artifacts came out of the build as com.github.Pixel-Du-Nord:faro. The
// version still comes from JitPack (VERSION = the tag), so faro-ui always points at the faro of the
// same build.
val faroGroup: String = providers.gradleProperty("faro.group").get()
val faroVersion: String = System.getenv("VERSION")?.takeIf { it.isNotBlank() }
    ?: providers.gradleProperty("faro.version").get()

subprojects {
    group = faroGroup
    version = faroVersion

    // Only the POM describes the published libraries. JitPack rewrites the "component" of a Gradle
    // Module Metadata file (.module) to com.github.<account>:<repository> for every module of a
    // multi-module build (here com.github.Pixel-Du-Nord:faro-android), whatever the build wrote,
    // while it serves the POM under the right coordinates. Without the .module file, Gradle reads the
    // POM, which carries the same dependencies (the Compose BOM as an import).
    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }
}
