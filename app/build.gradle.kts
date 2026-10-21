import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing credentials: CI env vars -> Gradle -P properties -> untracked keystore.properties
val signingCredentials = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystorePath = (
    System.getenv("KEYSTORE_FILE")
        ?: project.findProperty("KEYSTORE_FILE")?.toString()
        ?: signingCredentials.getProperty("KEYSTORE_FILE")
    ).orEmpty()
val keystorePassword = (
    System.getenv("KEYSTORE_PASSWORD")
        ?: project.findProperty("KEYSTORE_PASSWORD")?.toString()
        ?: signingCredentials.getProperty("KEYSTORE_PASSWORD")
    ).orEmpty()
val keyAliasName = (
    System.getenv("KEY_ALIAS")
        ?: project.findProperty("KEY_ALIAS")?.toString()
        ?: signingCredentials.getProperty("KEY_ALIAS")
    ).orEmpty()
val keyPasswordVal = (
    System.getenv("KEY_PASSWORD")
        ?: project.findProperty("KEY_PASSWORD")?.toString()
        ?: signingCredentials.getProperty("KEY_PASSWORD")
    ).orEmpty()
val canSignWithReleaseKey = keystorePath.isNotBlank() &&
        keystorePassword.isNotBlank() &&
        keyAliasName.isNotBlank() &&
        keyPasswordVal.isNotBlank()

android {
    namespace = "com.rayenghanmi.rytunex"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rayenghanmi.rytunex"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        create("release") {
            if (canSignWithReleaseKey) {
                val keystoreFile = if (File(keystorePath).isAbsolute) {
                    File(keystorePath)
                } else {
                    rootProject.file(keystorePath).takeIf { it.exists() } ?: file(keystorePath)
                }
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = keystorePassword
                    keyAlias = keyAliasName
                    keyPassword = keyPasswordVal
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) releaseSigning else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            // Share the release keystore locally; machines without it keep debug signing
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    // Material 3 Expressive & MaterialKolor for dynamic native themes
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("com.materialkolor:material-kolor:2.0.2")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    // Shizuku API for privileged shell execution without Root
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}