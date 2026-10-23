import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.pixeldunord.faro"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "FARO_VERSION", "\"${project.version}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Deliberate: android.media.ExifInterface (no extra dependency), modest dependency versions
        // so apps are never forced to upgrade, no core-ktx just for helpers.
        disable += setOf("ExifInterface", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "UseKtx")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    explicitApi()
    // Depend on an older stdlib than the compiler's, so apps on Kotlin 2.1+ can use the library.
    coreLibrariesVersion = "2.1.21"
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // Metadata readable by apps on Kotlin 2.0+ (one version back from 2.1).
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Android's org.json is a stub in local unit tests; this is the real one.
    testImplementation(libs.org.json)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "faro"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Faro")
                description.set("Faro support requests for Android: form definition, proof of work, device facts and sending.")
                url.set("https://github.com/Pixel-Du-Nord/faro-android")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                organization {
                    name.set("Pixel du Nord LLC")
                    url.set("https://pixeldunord.com")
                }
                scm {
                    url.set("https://github.com/Pixel-Du-Nord/faro-android")
                }
            }
        }
    }
}
