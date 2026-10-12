plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.KeyStore
import java.security.MessageDigest

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val signingStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE").orNull
val signingStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
val officialSigningConfigured = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
).all { !it.isNullOrBlank() }
val signingStore = signingStoreFile?.takeIf(String::isNotBlank)?.let(::file)
val expectedOfficialCertSha256 =
    "c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9"

fun validateOfficialSigning() {
    check(officialSigningConfigured) {
        "Official signing requires ANDROID_SIGNING_STORE_FILE, ANDROID_SIGNING_STORE_PASSWORD, " +
            "ANDROID_SIGNING_KEY_ALIAS, and ANDROID_SIGNING_KEY_PASSWORD"
    }
    val keystoreFile = signingStore!!
    check(keystoreFile.isFile) {
        "Official signing keystore was not found: ${keystoreFile.absolutePath}"
    }

    val certificate = try {
        KeyStore.getInstance("JKS").apply {
            keystoreFile.inputStream().use { input ->
                load(input, signingStorePassword!!.toCharArray())
            }
        }.getCertificate(signingKeyAlias!!)
    } catch (error: Exception) {
        throw GradleException("Official signing keystore validation failed.", error)
    }
    check(certificate != null) {
        "Official signing certificate alias was not found in the configured keystore"
    }

    val actualDigest = MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    check(actualDigest == expectedOfficialCertSha256) {
        "Configured signing certificate does not match the pinned ShortLink official certificate"
    }
}
val appVersionName = providers.gradleProperty("appVersionName").orElse("0.7.4").get()
val appVersionCode = providers.gradleProperty("appVersionCode").orElse("27").get().toInt()
require(appVersionCode > 0) { "appVersionCode must be positive" }

android {
    namespace = "com.reelshort.app"
    compileSdk = 35

    val reelshortApiBaseUrl = providers.gradleProperty("reelshortApiBaseUrl")
        .orElse("https://shortlink.hjj888.cc/api/app")
    // COS 桶名与地域用于 App 更新下载链接的精确域名校验（只接受本桶域名，避免任意账号桶通过后缀匹配）。
    val reelshortCosBucket = providers.gradleProperty("reelshortCosBucket")
        .orElse("update-1259596907")
    val reelshortCosRegion = providers.gradleProperty("reelshortCosRegion")
        .orElse("ap-chengdu")
    defaultConfig {
        applicationId = "com.reelshort.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "REELSHORT_API_BASE_URL", reelshortApiBaseUrl.get().asBuildConfigString())
        buildConfigField("String", "REELSHORT_COS_BUCKET", reelshortCosBucket.get().asBuildConfigString())
        buildConfigField("String", "REELSHORT_COS_REGION", reelshortCosRegion.get().asBuildConfigString())
    }

    signingConfigs {
        create("official") {
            if (officialSigningConfigured) {
                storeFile = signingStore
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("official")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("official")
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

// Only final APK/AAB producers require the pinned official keystore. The name is anchored at the end and
// matched case-sensitively so AGP's intermediate and test helpers (packageDebugResources,
// assembleDebugUnitTest, packageDebugUnitTestForUnitTest, assembleDebugAndroidTest) stay runnable without
// ANDROID_SIGNING_* secrets; packaging those artifacts still triggers validation through package<b>Debug</b>.
val officialPackagingTask = Regex("^(assemble|bundle|package)([A-Za-z0-9]+)?(Debug|Release)$")

gradle.taskGraph.whenReady {
    if (allTasks.any { task -> task.project == project && officialPackagingTask.matches(task.name) }) {
        validateOfficialSigning()
    }
}

tasks.register("printAppVersion") {
    doLast { println("$appVersionName/$appVersionCode") }
}

