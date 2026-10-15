import java.io.File
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android Gradle plugin.
    id("dev.flutter.flutter-gradle-plugin")
}

// The published version is owned by pubspec.yaml; -PappVersionName / -PappVersionCode let the
// release tooling override it without editing the repository.
val appVersionName: String =
    (project.findProperty("appVersionName") as String?) ?: flutter.versionName
val appVersionCode: Int =
    ((project.findProperty("appVersionCode") as String?) ?: flutter.versionCode.toString()).toInt()

require(appVersionCode > 0) { "appVersionCode must be a positive integer" }

/**
 * SHA-256 of the pinned ShortLink official release certificate. Published builds are signed with
 * exactly this identity, so every packaging task validates the configured keystore against it.
 */
val officialCertificateSha256 =
    "c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9"

val signingStoreFilePath: String? = System.getenv("ANDROID_SIGNING_STORE_FILE")
val signingStorePassword: String? = System.getenv("ANDROID_SIGNING_STORE_PASSWORD")
val signingKeyAlias: String? = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
val signingKeyPassword: String? = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")

val officialSigningConfigured: Boolean =
    !signingStoreFilePath.isNullOrBlank() &&
        !signingStorePassword.isNullOrBlank() &&
        !signingKeyAlias.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank()

android {
    namespace = "com.reelshort.app"
    compileSdk = 36
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Same package as the Kotlin app so the upgrade can replace it in place.
        applicationId = "com.reelshort.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (officialSigningConfigured) {
            create("official") {
                storeFile = File(signingStoreFilePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // There is deliberately no debug keystore: development builds carry the same release
            // identity as published builds, so an installed app is always overwritten in place.
            if (officialSigningConfigured) {
                signingConfig = signingConfigs.getByName("official")
            }
        }
        release {
            if (officialSigningConfigured) {
                signingConfig = signingConfigs.getByName("official")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

/**
 * Fails the build when the configured keystore is absent, has no such alias, or does not hold the
 * pinned official certificate. There is no debug-certificate fallback.
 */
fun validateOfficialSigning() {
    if (!officialSigningConfigured) {
        throw GradleException(
            "Official signing requires ANDROID_SIGNING_STORE_FILE, " +
                "ANDROID_SIGNING_STORE_PASSWORD, ANDROID_SIGNING_KEY_ALIAS, " +
                "and ANDROID_SIGNING_KEY_PASSWORD",
        )
    }
    val storeFile = File(signingStoreFilePath!!)
    if (!storeFile.isFile) {
        throw GradleException("Official signing keystore was not found: $signingStoreFilePath")
    }
    val keyStore = KeyStore.getInstance("JKS")
    storeFile.inputStream().use { keyStore.load(it, signingStorePassword!!.toCharArray()) }
    val certificate =
        keyStore.getCertificate(signingKeyAlias!!)
            ?: throw GradleException(
                "Official signing certificate alias was not found in the configured keystore",
            )
    val actualSha256 =
        MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
    if (!actualSha256.equals(officialCertificateSha256, ignoreCase = true)) {
        throw GradleException(
            "Configured signing certificate does not match the pinned ShortLink official certificate",
        )
    }
}

val packagingTaskPattern = Regex("^(assemble|bundle|package)([A-Za-z0-9]+)?(Debug|Release)$")

gradle.taskGraph.whenReady {
    if (allTasks.any { packagingTaskPattern.matches(it.name) }) {
        validateOfficialSigning()
    }
}

/** Prints `versionName/versionCode`, matching the Kotlin app's task of the same name. */
tasks.register("printAppVersion") {
    doLast { println("$appVersionName/$appVersionCode") }
}

flutter {
    source = "../.."
}
