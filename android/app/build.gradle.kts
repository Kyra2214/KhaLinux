plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.khalinux.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.khalinux.android"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase3"
    }
}

dependencies {
    implementation(project(":android:runtime"))
}

kotlin {
    jvmToolchain(17)
}
