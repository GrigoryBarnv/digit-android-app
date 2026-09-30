plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.opentouch.sensorapp"
    dynamicFeatures += setOf(":mlruntime")
    ndkVersion = "27.0.12077973"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.opentouch.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "1.1.15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("internalRelease") {
            // Fixed, shared keystore for internal testing builds only. Checked into
            // the repo on purpose so every CI run and every teammate's local build
            // signs with the SAME key, so app updates always install cleanly instead
            // of hitting "signature mismatch" errors. NOT for Play Store publishing -
            // generate and secure a real release keystore before that.
            storeFile = file("release-debug.keystore")
            storePassword = "opentouch2026"
            keyAlias = "opentouchrelease"
            keyPassword = "opentouch2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("internalRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

tasks.register("printVersionName") {
    group = "help"
    description = "Prints the Android app version name for CI scripts."
    doLast {
        print(android.defaultConfig.versionName)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(project(":libausbc"))
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.android.play:feature-delivery:2.1.0")
    // Local debug builds run AI directly so Android Studio testing does not
    // require a Play-distributed dynamic feature. Release keeps this out of
    // the base APK and receives it through :mlruntime instead.
    debugImplementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    // Lets MainActivity call installSplashScreen() and take explicit control
    // of the system's mandatory cold-start icon screen (dismiss it the instant
    // the app's first frame is ready), instead of relying on whatever timing
    // Android's fully automatic Android 12+ splash behavior happens to use -
    // see MainActivity.kt and themes.xml (Theme.Digitapp.Starting).
    implementation("androidx.core:core-splashscreen:1.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
