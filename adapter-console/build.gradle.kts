// FILE: adapter-console/build.gradle.kts

import org.gradle.api.tasks.JavaExec
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("gob.kotlin-application")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
