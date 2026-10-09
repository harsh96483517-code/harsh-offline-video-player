plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = "com.harsh.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.harsh.player"
        // minSdk 21 keeps us compatible with older Android TV boxes (Android 5.1 Lollipop)
        // while still satisfying the minimum required by AndroidX Media3 (21).
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // Only the architectures relevant to TV boxes / phones. Keep the APK small.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    // --- Core AndroidX ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // --- Storage Access Framework folder browsing ---
    implementation("androidx.documentfile:documentfile:1.0.1")

    // --- Android TV (Leanback launcher intent, banner support) ---
    implementation("androidx.leanback:leanback:1.0.0")

    // --- Lifecycle / ViewModel / Coroutines ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Media3 / ExoPlayer (primary playback engine) ---
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // --- Persistence: DataStore Preferences (settings) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Image loading: Coil with strict memory limits ---
    implementation("io.coil-kt:coil:2.6.0")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
