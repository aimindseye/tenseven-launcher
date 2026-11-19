import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kover)
}

base {
    archivesName.set("TenSeven-phone-L0")
}

android {
    namespace = "io.github.aimindseye.tenseven.phone"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.aimindseye.tenseven.phone"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "2.0-L0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        getByName("test").kotlin.srcDir(
            rootProject.file("app/src/test/java/io/github/aimindseye/tenseven/features/phone"),
        )
    }
    lint {
        abortOnError = rootProject.extra["strictQuality"] as Boolean
        ignoreTestSources = true
    }
}

dependencies {
    constraints {
        implementation(libs.androidx.activity.compose)
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

// AGP built-in Kotlin compiles java roots; explicitly include the same roots in both analyzers.
val behavioralTests = rootProject.file("app/src/test/java/io/github/aimindseye/tenseven/features/phone")
detekt { source.setFrom(fileTree("src") { include("**/*.kt") }, behavioralTests) }
tasks.withType<BaseKtLintCheckTask>().configureEach {
    when {
        name.endsWith("MainSourceSet") -> source(fileTree("src/main/java") { include("**/*.kt") })
        name.endsWith("TestSourceSet") -> source(fileTree("src/test/java") { include("**/*.kt") }, behavioralTests)
    }
}
