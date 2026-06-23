// :core — PURE Kotlin/JVM library. NO Android plugin.
// Holds all framework-independent logic: typed status/error model + raw-int mapping,
// PayloadProperties (+ header array + parser), and the apply-request value type.
// Fully unit-tested with kotlin("test") (JUnit 5 platform).
plugins {
    id("org.jetbrains.kotlin.jvm")
    jacoco
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
