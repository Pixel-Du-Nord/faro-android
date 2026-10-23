plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Coordinates of the published libraries. JitPack sets GROUP and VERSION from
// the repository and the tag, so faro-ui's POM always points at the faro
// artifact of the same build.
val faroGroup: String = System.getenv("GROUP")?.takeIf { it.isNotBlank() }
    ?: providers.gradleProperty("faro.group").get()
val faroVersion: String = System.getenv("VERSION")?.takeIf { it.isNotBlank() }
    ?: providers.gradleProperty("faro.version").get()

subprojects {
    group = faroGroup
    version = faroVersion
}
