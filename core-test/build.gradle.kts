// FILE: core-test/build.gradle.kts
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))

    // PIT mutation testing dependencies
    pitest("org.pitest:pitest-junit5-plugin:1.2.1")
}

tasks.test {
    useJUnitPlatform {
        // quarantine: keep known flaky tests out of the default suite
        excludeTags("flaky")
    }
}

/**
 * FAST: PR suite (exclude @Tag("slow") and @Tag("flaky"))
 */
tasks.register<Test>("testFast") {
    group = "verification"
    description = "Fast PR suite: excludes @Tag(\"slow\") and @Tag(\"flaky\")."
    useJUnitPlatform {
        excludeTags("slow", "flaky")
    }
}

/**
 * FULL: includes slow, excludes quarantined flaky
 */
tasks.register<Test>("testFull") {
    group = "verification"
    description = "Full suite: excludes @Tag(\"flaky\")."
    useJUnitPlatform {
        excludeTags("flaky")
    }
}

/**
 * QUARANTINE: runs only @Tag("flaky") (nightly monitoring)
 */
tasks.register<Test>("testQuarantine") {
    group = "verification"
    description = "Quarantine suite: runs only @Tag(\"flaky\")."
    useJUnitPlatform {
        includeTags("flaky")
    }
    // quarantine is observation-only
    ignoreFailures = true
}

/**
 * SMOKE: ultra-fast PR smoke tests (critical P0/P1 tests)
 * These are annotated with @Smoke and executed via JUnit5 tag "smoke".
 */
tasks.register<Test>("smokeTest") {
    group = "verification"
    description = "Ultra-fast smoke tests to run in PR checks: includes @Smoke-tagged tests only."
    useJUnitPlatform {
        includeTags("smoke")
        // Keep quarantine/flaky excluded
        excludeTags("flaky")
    }
    // Keep smoke fast; fail fast to surface critical regressions quickly
    failFast = true
}

// Configure kover with safer agent arguments location to avoid FileNotFound on Windows
kover {
    // Keep default reports/verify, but set temporary directory for agent args inside buildDir
    // Kover plugin doesn't expose direct setting for agent args file, but we can set
    // environment variable or JVM arg via test task if needed. For now, keep default config
    reports {
        verify {
            rule {
                bound {
                    coverageUnits.set(CoverageUnit.LINE)
                    minValue = 20
                }
            }
        }
    }
}

// Ensure koverVerify runs as part of check
tasks.check {
    dependsOn("koverVerify")
}

// (detekt tasks configured in root)

// ====================================================================
// === PITEST (mutation testing)
// ====================================================================

pitest {
    // Target classes from the :core module for mutation
    // Use a regex that matches package+subpackages (core\..*) to avoid accidental
    // mismatches (Pitest treats targetClasses as regex patterns).
    targetClasses.set(listOf("core\\..*"))

    // Use tests from this module (core-test)
    targetTests.set(listOf("test.*"))

    // Enable JUnit 5 support
    junit5PluginVersion.set("1.2.1")

    // Minimal / fast mutation profile for iteractive use (DEFAULTS). For deeper
    // analysis run a dedicated nightly job with STRONGER or CUSTOM mutators.
    mutators.set(listOf("DEFAULTS"))

    // Avoid failing the build while we debug filters (prevents CI from failing
    // with "No mutations found" during tuning). Toggle to `true` for strict checks.
    failWhenNoMutations.set(false)

    // Performance: use all available CPU cores
    threads.set(Runtime.getRuntime().availableProcessors())

    // Quality gates (aligned with existing Kover coverage minimum)
    mutationThreshold.set(60)  // minimum 60% of mutants must be killed
    coverageThreshold.set(20)  // aligned with Kover minimum line coverage

    // Output formats
    outputFormats.set(listOf("HTML", "XML"))

    // Verbose output for better debugging
    verbose.set(true)

    // Exclude classes that are not critical for mutation testing
    excludedClasses.set(listOf(
        "core.serde.*",           // Serialization DTOs
        "core.state.*Dto",        // Data transfer objects
        "*.Main*"                 // Entry points
    ))

    // Time limits
    timeoutFactor.set(BigDecimal("1.5"))        // Allow 1.5x normal test execution time
    timeoutConstInMillis.set(3000) // Base timeout: 3 seconds

    // IMPORTANT: Tell PIT where to find the classes to mutate
    // We need to include both the core module's classes
    mainSourceSets.set(listOf(project(":core").sourceSets.main.get()))
    testSourceSets.set(listOf(sourceSets.test.get()))
}

// Ensure core module is compiled before mutation testing
tasks.named("pitest") {
    dependsOn(":core:classes")
}

/**
 * FAST PIT: quick mutation testing for PR pipeline
 * Targets only critical reducer and validation logic
 */
tasks.register("pitestFast") {
    group = "verification"
    description = "Fast mutation testing: critical classes only (Reducer, CommandValidation)"

    doFirst {
        project.extensions.configure<info.solidsoft.gradle.pitest.PitestPluginExtension>("pitest") {
            targetClasses.set(listOf(
                "core.Reducer*",
                "core.CommandValidation*",
                "core.Events*",
                "core.Commands*"
            ))
            mutators.set(listOf("DEFAULTS"))  // Faster mutators for quick feedback
            threads.set(Runtime.getRuntime().availableProcessors())
        }
    }

    finalizedBy("pitest")
}

// Targeted: suppress compiler warnings for *test* compilation only to reduce noise while
// we iteratively fix test code. This uses -nowarn for the compileTestKotlin task only.
// NOTE: This is a pragmatic temporary measure; prefer fixing root causes (remove unused
// imports/vars, update deprecated APIs, add proper generics/nullability, or local @Suppress).
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    kotlinOptions {
        // Suppress warnings during test compilation to reduce CI noise.
        // Remove or narrow this once tests are cleaned up.
        freeCompilerArgs += listOf("-nowarn")
    }
}
