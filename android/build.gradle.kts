// :android — Android library holding ONLY the Android-framework wiring.
// Depends on :core for all framework-independent logic.
//
// android.os.UpdateEngine / UpdateEngineCallback are @SystemApi and are NOT in the
// public android.jar; this module therefore reaches them via REFLECTION so it
// compiles against the public SDK (see UpdateEngineBridge / ReflectiveUpdateEngine).
//
// Best-effort: AGP-on-Gradle-9.5 may not be supported; BUILD_STATUS.md reports the
// true result of `gradle :android:assembleRelease` honestly.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "digital.vasic.helix.ota.bridge.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    api(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
