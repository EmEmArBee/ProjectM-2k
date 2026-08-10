plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.asfaltosonoro.projectmoverlay"
    compileSdk = 34
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.asfaltosonoro.projectmoverlay"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DENABLE_GLES=ON",
                    "-DBUILD_SHARED_LIBS=ON",
                    "-DENABLE_TESTING=OFF"
                )
                // Costruiamo solo per le ABI più comuni per tenere l'APK leggero.
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Le presets di MilkDrop (.milk) vengono incluse come asset e copiate
    // nella storage interna dell'app al primo avvio.
    androidResources {
        noCompress += listOf("milk")
    }

    // Fix per "UnsatisfiedLinkError: library ... not found": il packaging
    // "moderno" (non compresso, mmap diretto dall'APK) di AGP a volte non
    // funziona correttamente con librerie native compilate da CMake esterno.
    // Tornando al packaging "legacy" (compresse, estratte su disco
    // all'installazione) il caricamento è molto più affidabile.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
