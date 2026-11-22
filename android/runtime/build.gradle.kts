plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    ndkVersion = "27.0.12077973"
    namespace = "com.khalinux.runtime"
    compileSdk = 35

    defaultConfig {
        minSdk = 35
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Este teste usa com.sun.net.httpserver, módulo JDK não exposto pelo
    // classpath de testes Android. Ele permanece no checkpoint para execução
    // em um source set JVM dedicado, mas não bloqueia o build Android.
    sourceSets["test"].java.exclude("**/ResumableDownloaderTest.kt")
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
}

kotlin {
    jvmToolchain(17)
}
