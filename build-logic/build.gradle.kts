plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Kotlin plugins used by convention scripts
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.2.21")

    // External Gradle plugins referenced in convention scripts (plugins { id(...) })
    implementation("org.jetbrains.kotlinx.kover:org.jetbrains.kotlinx.kover.gradle.plugin:0.9.3")
    implementation("info.solidsoft.pitest:info.solidsoft.pitest.gradle.plugin:1.19.0-rc.3")
}
