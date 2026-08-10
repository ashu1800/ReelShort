plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val fixedDebugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
val appVersionName = providers.gradleProperty("appVersionName").orElse("0.7.4").get()
val appVersionCode = providers.gradleProperty("appVersionCode").orElse("27").get().toInt()
require(appVersionCode > 0) { "appVersionCode must be positive" }

android {
    namespace = "com.reelshort.app"
    compileSdk = 35

    val reelshortApiBaseUrl = providers.gradleProperty("reelshortApiBaseUrl")
        .orElse("https://shortlink.hjj888.cc/api/app")
    defaultConfig {
        applicationId = "com.reelshort.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "REELSHORT_API_BASE_URL", reelshortApiBaseUrl.get().asBuildConfigString())
    }

    signingConfigs {
        create("fixedDebug") {
            storeFile = fixedDebugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixedDebug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("fixedDebug")
        }
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
    implementation(project(":app-core"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

tasks.matching {
    it.name in setOf("packageDebug", "bundleDebug", "packageRelease", "bundleRelease")
}.configureEach {
    doFirst {
        check(fixedDebugKeystore.isFile) {
            "Fixed Debug keystore is missing at ${fixedDebugKeystore.absolutePath}. " +
                "Restore the pinned keystore before building Android packages."
        }
    }
}

tasks.register("printAppVersion") {
    doLast { println("$appVersionName/$appVersionCode") }
}

