plugins {
    id("com.android.dynamic-feature")
}

android {
    namespace = "com.opentouch.sensorapp.mlruntime"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":app"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
}
