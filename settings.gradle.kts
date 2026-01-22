// FILE: settings.gradle.kts

rootProject.name = "Guilds-of-Bash"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
        id("org.jetbrains.kotlinx.kover") version "0.9.3"
        id("info.solidsoft.pitest") version "1.19.0-rc.3"
        id("com.gradleup.shadow") version "<SHADOW_VERSION_FOR_GRADLE_8_14>"
    }
}

include("core")
include("adapter-console")
include("core-test")
includeBuild("build-logic")
