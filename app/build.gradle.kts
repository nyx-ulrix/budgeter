import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Signing: keystore.properties on this PC (not in git), or BUDGETER_* environment variables in GitHub Actions.
// Debug and release builds share the key so a plugged-in install and a GitHub release can update each other.
// Without either, the standard debug key is used and auto-update can't replace that install.
val keys = Properties().apply { rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun key(name: String, env: String): String? = keys.getProperty(name) ?: System.getenv(env)?.takeIf { it.isNotBlank() }

// Version comes from the release tag (-PappVersion=1.2.3). versionCode must grow with it: 1.2.3 → 10203.
val appVersion = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "1.4.4"
val v = appVersion.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 } + listOf(0, 0, 0)

android {
    namespace = "com.nyxulrix.budgeter"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.nyxulrix.budgeter"
        minSdk = 26
        targetSdk = 36
        versionCode = v[0] * 10000 + v[1] * 100 + v[2]
        versionName = appVersion
        // Where the app looks for new versions (GitHub "owner/repo" with public releases).
        buildConfigField("String", "UPDATE_REPO", "\"${findProperty("updateRepo") ?: "nyx-ulrix/budgeter"}\"")
    }
    signingConfigs {
        key("storeFile", "BUDGETER_KEYSTORE")?.let { store ->
            create("release") {
                storeFile = file(store)
                storePassword = key("storePassword", "BUDGETER_KEYSTORE_PASSWORD")
                keyAlias = key("keyAlias", "BUDGETER_KEY_ALIAS") ?: "budgeter"
                keyPassword = key("keyPassword", "BUDGETER_KEY_PASSWORD")
            }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        val signing = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        debug { signingConfig = signing }
        release { isMinifyEnabled = false; signingConfig = signing }
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.10.4")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.camera:camera-camera2:1.5.0")
    implementation("androidx.camera:camera-lifecycle:1.5.0")
    implementation("androidx.camera:camera-view:1.5.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
