// FILE: adapter-console/build.gradle.kts

import org.gradle.api.tasks.JavaExec

plugins {
    kotlin("jvm")
    application
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
