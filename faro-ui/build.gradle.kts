import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "com.pixeldunord.faro.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    api(project(":faro"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "faro-ui"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Faro UI")
                description.set("A ready-made, themable Jetpack Compose help screen for Faro, with screenshot redaction.")
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
