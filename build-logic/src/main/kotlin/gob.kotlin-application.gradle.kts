import org.gradle.api.tasks.JavaExec

plugins {
    id("gob.kotlin-jvm-base")
    application
    id("org.jetbrains.kotlinx.kover")
    // NOTE: shadow plugin is applied in the module build script to avoid Kotlin metadata
    // compatibility issues when compiling precompiled convention scripts in build-logic.
}

tasks.withType<JavaExec>().configureEach {
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}
