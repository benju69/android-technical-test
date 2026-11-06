import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "fr.leboncoin.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    dependencies {
        implementation(libs.retrofit.core)
        implementation(libs.retrofit.kotlin.serialization)
        implementation(libs.okhttp.logging)

        implementation(libs.kotlin.serialization.json)

        // Hilt annotations + compiler (no Hilt Gradle plugin needed in a library)
        implementation(libs.hilt.android)
        ksp(libs.hilt.compiler)

        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit) // Useless dependency
        androidTestImplementation(libs.androidx.espresso.core) // Useless dependency
    }
}
