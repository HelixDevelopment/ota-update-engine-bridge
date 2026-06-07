// Root build for ota-update-engine-bridge.
//
// Two modules:
//   :core    — pure Kotlin/JVM, framework-independent logic + data classes, fully unit-tested.
//              Builds & tests with system Gradle 9.5: `gradle :core:test`.
//   :android — Android library (com.android.library) with ONLY the framework wiring;
//              depends on :core. Best-effort under AGP-on-Gradle-9.5.
//
// Plugins are declared (apply false) here and applied per-module so each module
// stays decoupled (HelixConstitution §11.4.28).
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("com.android.library") version "8.5.2" apply false
}

allprojects {
    group = "digital.vasic.helix.ota"
    version = "1.0.0"
}
