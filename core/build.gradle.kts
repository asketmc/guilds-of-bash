plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Удобный entrypoint: запуск PIT “из core” (делегирует в core-test, где живут тесты)
tasks.register("pitest") {
    group = "verification"
    description = "Runs PIT mutation testing for :core using tests from :core-test (delegates to :core-test:pitest)."
    dependsOn(":core-test:pitest")
}
