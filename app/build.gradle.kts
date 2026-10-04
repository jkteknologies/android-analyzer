plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jkteknologies.androidanalyzer"
    // compileSdk 37 (not 35 as originally pinned): the pinned 2026 library set
    // (Compose 1.12.x via BOM 2026.09.00, lifecycle 2.11.0, activity 1.13.0)
    // requires compiling against SDK 37+. minSdk/targetSdk stay 35 — the
    // user-visible API-35 contract (installs/runs on Android 15+) is unchanged.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jkteknologies.androidanalyzer"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // Built-in AGP lint: errors fatal (FR-005, R-09); warnings never fail the build.
    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // Deliberately minimal set (Constitution VII): UI stack + JUnit only.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
