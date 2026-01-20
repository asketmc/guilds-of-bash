plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.23"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}