// FILE: core-test/build.gradle.kts
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.math.BigDecimal

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))

    // PIT mutation testing dependencies (JUnit 5 support)
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

// ====================================================================
// === KOVER
// ====================================================================

kover {
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

tasks.check {
    dependsOn("koverVerify")
}

// ====================================================================
// === PITEST (mutation testing)
// ====================================================================

// CLI overrides (optional):
//  -PpitTestFqn=test.P1_011_SerializationTest
//  -PpitTargetClasses=core\\..*
// If not provided, defaults are used.
val pitTestFqn: String? = (findProperty("pitTestFqn") as String?)?.trim()
val pitTargetClasses: String? = (findProperty("pitTargetClasses") as String?)?.trim()

val coreMainSourceSet = project(":core")
    .extensions
    .getByType<SourceSetContainer>()
    .named("main")
    .get()

val thisTestSourceSet = extensions
    .getByType<SourceSetContainer>()
    .named("test")
    .get()

pitest {
    // PIT expects regex patterns
    targetClasses.set(listOf(if (!pitTargetClasses.isNullOrEmpty()) pitTargetClasses else "core\\..*"))

    // default: all tests; override with -PpitTestFqn=<FQN>
    targetTests.set(listOf(if (!pitTestFqn.isNullOrEmpty()) pitTestFqn else "test\\..*"))

    // JUnit 5 support
    junit5PluginVersion.set("1.2.1")

    // Minimal / fast mutation profile for interactive use
    mutators.set(listOf("DEFAULTS"))

    // Avoid failing the build while filters are tuned
    failWhenNoMutations.set(false)

    // Performance
    threads.set(Runtime.getRuntime().availableProcessors())

    // Output formats
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)

    // Verbose output for debugging
    verbose.set(true)

    // Exclusions (regex)
    excludedClasses.set(listOf(
        "core\\.serde\\..*",
        "core\\.state\\..*Dto.*",
        ".*Main.*"
    ))

    // Time limits
    timeoutFactor.set(BigDecimal("1.5"))
    timeoutConstInMillis.set(3000)

    // Windows: keep classpath in a file (usually default, but keep explicit)
    useClasspathFile.set(true)

    // Multi-module: mutate :core classes, run tests from :core-test
    mainSourceSets.set(listOf(coreMainSourceSet))
    testSourceSets.set(listOf(thisTestSourceSet))
}

// Ensure core module is compiled before mutation testing
tasks.named("pitest") {
    dependsOn(":core:classes")
}

// Targeted: suppress compiler warnings for *test* compilation only to reduce noise
tasks.named<KotlinCompile>("compileTestKotlin") {
    kotlinOptions {
        freeCompilerArgs += listOf("-nowarn")
    }
}
