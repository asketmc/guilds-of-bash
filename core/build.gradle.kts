// FILE: core/build.gradle.kts
plugins {
    id("gob.kotlin-library")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Entrypoint: start PIT “from core-test”
tasks.register("pitest") {
    group = "verification"
    description = "Runs PIT mutation testing for :core using tests from :core-test (delegates to :core-test:pitest)."
    dependsOn(":core-test:pitest")
}
