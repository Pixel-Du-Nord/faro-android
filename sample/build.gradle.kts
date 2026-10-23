import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val sampleKey = providers.gradleProperty("faro.sample.key").get()
val sampleApp = providers.gradleProperty("faro.sample.app").get()

android {
    namespace = "com.pixeldunord.faro.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pixeldunord.faro.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = project.version.toString()
        buildConfigField("String", "FARO_KEY", "\"$sampleKey\"")
        buildConfigField("String", "FARO_APP", "\"$sampleApp\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":faro"))
    implementation(project(":faro-ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
