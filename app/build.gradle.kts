import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
}

// The APK files are named Tenseven-Launcher-debug.apk, Tenseven-Launcher-release.apk (signed, see below) or
// Tenseven-Launcher-release-unsigned.apk (no signing key configured). The lite build (:app:assembleLite) is the
// release build without the accessibility service and the notification listener, which Google Play Protect blocks a
// downloaded APK for: Tenseven-Launcher-lite.apk or Tenseven-Launcher-lite-unsigned.apk.
base {
    archivesName.set("Tenseven-Launcher")
}

// Release signing. The key never lives in this repository: either a keystore.properties file at the repository root
// (git-ignored) with storeFile, storePassword, keyAlias and keyPassword, or the environment variables
// TENSEVEN_KEYSTORE, TENSEVEN_KEYSTORE_PASSWORD, TENSEVEN_KEY_ALIAS and TENSEVEN_KEY_PASSWORD. With neither, the
// release build is left unsigned, as in CI. See docs/BUILD.md.
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.isFile) file.inputStream().use { load(it) }
    }

fun signingValue(
    property: String,
    variable: String,
): String? = (keystoreProperties.getProperty(property) ?: System.getenv(variable))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "TENSEVEN_KEYSTORE")
val releaseStorePassword = signingValue("storePassword", "TENSEVEN_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "TENSEVEN_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "TENSEVEN_KEY_PASSWORD")
val releaseSigned =
    releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

android {
    // The R class and BuildConfig package; the Kotlin sources use the same package.
    namespace = "io.github.aimindseye.tenseven"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.aimindseye.tenseven"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseSigned) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8: shrinks, optimises and obfuscates (renames) the app's code, and removes unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigned) signingConfig = signingConfigs.getByName("release")
        }
        // Release without the two services Play Protect blocks (src/lite/AndroidManifest.xml). The same application id
        // and key, so a lite install can be updated to the full release.
        create("lite") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            if (releaseSigned) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    lint {
        abortOnError = rootProject.extra["strictQuality"] as Boolean
        sarifReport = true
        htmlReport = true
        ignoreTestSources = true
    }
}

dependencies {
    constraints {
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.collection)
        implementation(libs.androidx.collection.jvm)
        implementation(libs.androidx.collection.ktx)
        implementation(libs.androidx.lifecycle.common)
        implementation(libs.androidx.lifecycle.common.java8)
        implementation(libs.androidx.lifecycle.common.jvm)
        implementation(libs.androidx.lifecycle.process)
        implementation(libs.androidx.lifecycle.runtime)
        implementation(libs.androidx.lifecycle.runtime.android)
        implementation(libs.androidx.lifecycle.runtime.ktx)
        implementation(libs.androidx.lifecycle.runtime.ktx.android)
    }

    implementation(project(":design"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    testImplementation(libs.junit)
}
