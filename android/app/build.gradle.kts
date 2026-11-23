plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

// Assinatura de release (Fase 9.3): somente por variáveis de ambiente; nada é gravado no repositório.
// Sem as variáveis, `assembleRelease` gera um APK NÃO assinado (o workflow de release exige a assinatura).
val khalinuxKeystorePath: String? = System.getenv("KHALINUX_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val assinaturaConfigurada: Boolean = khalinuxKeystorePath != null &&
    listOf("KHALINUX_KEYSTORE_PASSWORD", "KHALINUX_KEY_ALIAS", "KHALINUX_KEY_PASSWORD")
        .all { !System.getenv(it).isNullOrBlank() }
// Versão por propriedade Gradle (-PkhalinuxVersionName=1.0.0 -PkhalinuxVersionCode=2); padrões abaixo.
val khalinuxVersionName: String = providers.gradleProperty("khalinuxVersionName").getOrElse("1.0.0-rc.1")
val khalinuxVersionCode: Int = providers.gradleProperty("khalinuxVersionCode").map(String::toInt).getOrElse(2)
// R8 ligado por padrão no release; -PkhalinuxMinify=false desliga para diagnosticar problemas de ofuscação.
val khalinuxMinify: Boolean = providers.gradleProperty("khalinuxMinify").map(String::toBoolean).getOrElse(true)

android {
    namespace = "com.khalinux.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.khalinux.android"
        // targetSdk 28 é intencional: a partir do 29 o SELinux bloqueia execve em arquivos do app
        // ("Permission denied", exit 126). Mesmo caminho do Termux (APK fora da Play Store).
        minSdk = 28
        targetSdk = 28
        versionCode = khalinuxVersionCode
        versionName = khalinuxVersionName
    }

    lint {
        // Este APK é distribuição direta/sideload; não é destinado à Google Play.
        // O target 28 é necessário para o runtime proot funcionar no dispositivo.
        disable += "ExpiredTargetSdkVersion"
    }

    signingConfigs {
        if (assinaturaConfigurada) {
            create("release") {
                storeFile = file(khalinuxKeystorePath!!)
                storePassword = System.getenv("KHALINUX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KHALINUX_KEY_ALIAS")
                keyPassword = System.getenv("KHALINUX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = khalinuxMinify
            isShrinkResources = khalinuxMinify
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // O app é somente ARM64 (docs/install-guide.md).
            ndk { abiFilters += "arm64-v8a" }
            if (assinaturaConfigurada) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    sourceSets {
        // Catálogo de pacotes versionado em distribution/ embarcado como asset `catalog.json`.
        getByName("main").assets.srcDir("../../distribution/packages/catalog")
        // Descritores do Cyber Toolkit (Fase 5) embarcados como asset `tools.json`.
        getByName("main").assets.srcDir("../../distribution/toolkit")
        // Laboratórios (Fase 6): assets `labs.json` e `fixtures/` (copiadas para /opt/khalinux-labs no RootFS).
        getByName("main").assets.srcDir("../../distribution/labs")
        // Tutor didático offline (Fase 7.8): asset `knowledge.tsv`.
        getByName("main").assets.srcDir("../../distribution/tutor")
        // Catálogo versionado da Área RED (roteiro/protótipo legado).
        getByName("main").assets.srcDir("../../distribution/red")
        // Catálogo operacional versionado LAB-0, separado dos roteiros didáticos.
        getByName("main").assets.srcDir("../../distribution/lab")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation(project(":android:runtime"))

    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

kapt {
    arguments {
        // Esquemas do Room versionados em android/app/schemas (exportSchema = true).
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

kotlin {
    jvmToolchain(17)
}
