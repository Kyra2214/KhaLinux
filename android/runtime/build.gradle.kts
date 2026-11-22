plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Os testes de download usam com.sun.net.httpserver, que pertence ao módulo
// JDK `jdk.httpserver` e não é resolvido automaticamente pelo compilador Kotlin.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-J--add-modules=jdk.httpserver")
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
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
}

kotlin {
    jvmToolchain(17)
}
