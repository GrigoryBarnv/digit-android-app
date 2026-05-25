// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

extra.apply {
    set("androidXVersion", "1.7.0")
    set("versionCompiler", 36)
    set("versionTarget", 36)
    set("minSdkVersion", 24)
    set("javaSourceCompatibility", JavaVersion.VERSION_11)
    set("javaTargetCompatibility", JavaVersion.VERSION_11)
    set("ndkVersion", "27.0.12077973")
    set("constraintlayoutVersion", "2.1.4")
    set("materialVersion", "1.12.0")
}
