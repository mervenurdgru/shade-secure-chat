import java.util.Properties
import com.google.protobuf.gradle.*


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    alias(libs.plugins.hilt)
    alias(libs.plugins.protobuf)
    id("com.google.gms.google-services")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.shade.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shade.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    // ── Ortam flavor'ları ─────────────────────────────────────────────────────
    flavorDimensions += "env"

    productFlavors {
        /**
         * dev — geliştirici makinesi / Cloudflare tünel.
         * Aynı anda prod apk ile yanyana kurulabilir (.dev suffix).
         */
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"

            val apiUrl    = localProperties.getProperty("DEV_API_URL")      ?: ""
            val wsUrl     = localProperties.getProperty("DEV_WS_URL")       ?: ""
            val certPin   = localProperties.getProperty("DEV_CERT_PIN_HASH")?: ""

            buildConfigField("String",  "API_URL",       "\"$apiUrl\"")
            buildConfigField("String",  "WS_URL",        "\"$wsUrl\"")
            buildConfigField("String",  "CERT_PIN_HASH", "\"$certPin\"")
            buildConfigField("String",  "ENVIRONMENT",   "\"dev\"")
            buildConfigField("Boolean", "STRICT_LOGGING","true")
        }

        /**
         * staging — QA / test sunucusu.
         * Prod ile aynı anda kurulabilir (.staging suffix).
         */
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"

            val apiUrl  = localProperties.getProperty("STAGING_API_URL")       ?: ""
            val wsUrl   = localProperties.getProperty("STAGING_WS_URL")        ?: ""
            val certPin = localProperties.getProperty("STAGING_CERT_PIN_HASH") ?: ""

            buildConfigField("String",  "API_URL",       "\"$apiUrl\"")
            buildConfigField("String",  "WS_URL",        "\"$wsUrl\"")
            buildConfigField("String",  "CERT_PIN_HASH", "\"$certPin\"")
            buildConfigField("String",  "ENVIRONMENT",   "\"staging\"")
            buildConfigField("Boolean", "STRICT_LOGGING","false")
        }

        /**
         * prod — canlı sunucu. Suffix yok.
         */
        create("prod") {
            dimension = "env"

            val apiUrl  = localProperties.getProperty("PROD_API_URL")       ?: ""
            val wsUrl   = localProperties.getProperty("PROD_WS_URL")        ?: ""
            val certPin = localProperties.getProperty("PROD_CERT_PIN_HASH") ?: ""

            buildConfigField("String",  "API_URL",       "\"$apiUrl\"")
            buildConfigField("String",  "WS_URL",        "\"$wsUrl\"")
            buildConfigField("String",  "CERT_PIN_HASH", "\"$certPin\"")
            buildConfigField("String",  "ENVIRONMENT",   "\"prod\"")
            buildConfigField("Boolean", "STRICT_LOGGING","false")
        }
    }

    buildTypes {
        debug {
            // debug'da minify kapalı — hızlı iterasyon için
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // android.util.Log gibi Android stub'larının "not mocked" throw etmesini engelle
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }

    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.bcprov.jdk18on)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlin.bip39)
    
    implementation(libs.protobuf.kotlin.lite)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // CameraX (web pairing QR scanner)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit Barcode (QR decoding)
    implementation(libs.mlkit.barcode.scanning)

    // Paging 3 — sonsuz kaydırma / büyük mesaj listeleri
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.paging)
}

val rustDir = "${projectDir}/rust/shade-crypto"
val jniLibsDir = "${projectDir}/src/main/jniLibs"

val cargoPath = localProperties.getProperty("cargo.path") ?: "cargo"

tasks.register<Exec>("buildRustDebug") {
    workingDir(rustDir)
    commandLine(cargoPath, "ndk", "-t", "arm64-v8a", "-t", "x86_64", "-o", jniLibsDir, "build")
}

tasks.register<Exec>("buildRustRelease") {
    workingDir(rustDir)
    commandLine(
        cargoPath, "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "-t", "x86",
        "-o", jniLibsDir,
        "build", "--release"
    )
}

afterEvaluate {
    // Flavor × buildType kombinasyonları için Rust build bağlantısı
    android.applicationVariants.configureEach {
        val variantName = name.replaceFirstChar { it.uppercase() }
        val rustTask = if (buildType.name == "release") "buildRustRelease" else "buildRustDebug"
        tasks.findByName("merge${variantName}NativeLibs")?.dependsOn(rustTask)
    }
}
