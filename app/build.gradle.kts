import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rayenghanmi.rytunex"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rayenghanmi.rytunex"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE")
                ?: project.findProperty("KEYSTORE_FILE")?.toString()
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: project.findProperty("KEYSTORE_PASSWORD")?.toString()
            val keyAliasName = System.getenv("KEY_ALIAS")
                ?: project.findProperty("KEY_ALIAS")?.toString()
            val keyPasswordVal = System.getenv("KEY_PASSWORD")
                ?: project.findProperty("KEY_PASSWORD")?.toString()

            if (!keystorePath.isNullOrBlank() &&
                !keystorePassword.isNullOrBlank() &&
                !keyAliasName.isNullOrBlank() &&
                !keyPasswordVal.isNullOrBlank()
            ) {
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
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) releaseSigning else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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