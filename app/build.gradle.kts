plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.secondbrain.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.secondbrain.app"
        minSdk = 26
        targetSdk = 35
        // CI menaikkan versionCode otomatis via -PversionCode=<github run number> agar
        // APK baru selalu dianggap update (bukan downgrade) oleh Android.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 2
        versionName = "1.1.0"
    }

    signingConfigs {
        // Keystore TETAP yang di-commit ke repo: semua build (lokal & CI) menghasilkan
        // tanda tangan yang sama, sehingga install APK baru meng-UPDATE app lama tanpa
        // bentrok dan tanpa kehilangan data. Jangan ganti/regenerasi file ini.
        create("shared") {
            storeFile = rootProject.file("keystore/debug.p12")
            storeType = "pkcs12"
            storePassword = "secondbrain"
            keyAlias = "secondbrain"
            keyPassword = "secondbrain"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Ekspor skema Room ke app/schemas mulai v4 — bekal test migrasi versi berikutnya.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
