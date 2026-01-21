// FILE: adapter-console/build.gradle.kts

import org.gradle.api.tasks.JavaExec

plugins {
    kotlin("jvm")
    application
    id("org.jetbrains.kotlinx.kover")
    // detekt применяется и настраивается в root build.gradle.kts
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

// (detekt tasks configured in root)
