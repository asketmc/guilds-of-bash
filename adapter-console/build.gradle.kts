// FILE: adapter-console/build.gradle.kts

import org.gradle.api.tasks.JavaExec
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    application
    id("org.jetbrains.kotlinx.kover")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("console.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "console.MainKt"
    }
}

// (detekt tasks configured in root)
