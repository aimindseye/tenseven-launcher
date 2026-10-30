plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
}

android {
    // The R class and BuildConfig package. The Kotlin sources imported from SableStart keep their
    // original org.sableos.start packages until the rename (ROADMAP.md, phase 1).
    namespace = "io.github.aimindseye.tenseven"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.aimindseye.tenseven"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Shrinking is switched on with the first release build (ROADMAP.md, phase 3).
            isMinifyEnabled = false
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
