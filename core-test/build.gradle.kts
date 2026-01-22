// FILE: core-test/build.gradle.kts

import java.math.BigDecimal
import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("gob.core-test")
    id("org.jetbrains.kotlinx.kover")
    id("info.solidsoft.pitest")
}

// --- Pull test outputs from other modules onto :core-test test runtime classpath ---
// IMPORTANT: must be resolved as Project (not dependency notation), hence declared outside dependencies block.
val coreTestOutput =
    project(":core")
        .extensions
        .getByType<SourceSetContainer>()["test"]
        .output

val adapterConsoleTestOutput =
    project(":adapter-console")
        .extensions
        .getByType<SourceSetContainer>()["test"]
        .output

dependencies {
    implementation(project(":core"))
    implementation(project(":adapter-console"))

    // Bring test outputs from other modules onto :core-test test runtime classpath
    testImplementation(coreTestOutput)
    testImplementation(adapterConsoleTestOutput)

    // Kotlin test -> JUnit5
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("reflect"))

    // For IDE/Gradle to see J5:
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // JUnit Platform Suite (SmokeSuite)
    testImplementation("org.junit.platform:junit-platform-suite-api:1.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-suite-engine:1.10.1")

    // PIT JUnit5 plugin
    pitest("org.pitest:pitest-junit5-plugin:1.2.1")
}

kover {
    reports {
        verify {
            rule {
                bound {
                    coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
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
// === PITEST (mutate :core + :adapter-console, run SMOKE tests by tag)
// ====================================================================

val pitTargetClassesRaw: String =
    (findProperty("pitTargetClasses") as String?)?.trim()
        .takeUnless { it.isNullOrEmpty() }
        ?: "core.*,console.*"

val pitTargetClasses: Set<String> =
    pitTargetClassesRaw
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

val pitTargetTests: Set<String> =
    (findProperty("pitTargetTests") as String?)?.trim()
        ?.takeUnless { it.isEmpty() }
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: setOf("test.SmokeSuite")

// ====================================================================
// === MUTATORS PROFILES
// ====================================================================
//
// Switch via: -PpitProfile=ALL | -PpitProfile=SMOKE
// Default: ALL
//
// SMOKE mutators: CONDITIONALS_BOUNDARY, INCREMENTS, MATH
// SMOKE avoidCallsTo: kotlin.jvm.internal

val PIT_PROFILE_ALL = "ALL"
val PIT_PROFILE_SMOKE = "SMOKE"

val PIT_MUTATORS_ALL: Set<String> = setOf("ALL")

val PIT_MUTATORS_SMOKE: Set<String> = setOf(
    "CONDITIONALS_BOUNDARY",
    "INCREMENTS",
    "MATH"
)

val pitProfile: String =
    (findProperty("pitProfile") as String?)?.trim()
        ?.uppercase()
        ?.takeUnless { it.isEmpty() }
        ?: PIT_PROFILE_ALL

val pitMutators: Set<String> =
    when (pitProfile) {
        PIT_PROFILE_SMOKE -> PIT_MUTATORS_SMOKE
        PIT_PROFILE_ALL -> PIT_MUTATORS_ALL
        else -> error("Unknown -PpitProfile=$pitProfile. Allowed: $PIT_PROFILE_ALL, $PIT_PROFILE_SMOKE")
    }

val pitAvoidCallsTo: Set<String> =
    when (pitProfile) {
        PIT_PROFILE_SMOKE -> setOf("kotlin.jvm.internal")
        else -> emptySet()
    }

pitest {
    testPlugin.set("junit5")
    junit5PluginVersion.set("1.2.1")

    targetClasses.set(pitTargetClasses)
    targetTests.set(pitTargetTests)

    mutators.set(pitMutators)
    threads.set(Runtime.getRuntime().availableProcessors())

    timeoutFactor.set(BigDecimal("1.5"))
    timeoutConstInMillis.set(3000)

    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    verbose.set(true)

    failWhenNoMutations.set(false)
    useClasspathFile.set(true)

    additionalMutableCodePaths.set(
        files(
            project(":core").tasks.named("jar"),
            project(":adapter-console").tasks.named("jar")
        )
    )

    excludedClasses.set(
        setOf(
            "core.serde.*",
            "core.state.*Dto*",
            "*Main*"
        )
    )

    if (pitAvoidCallsTo.isNotEmpty()) {
        avoidCallsTo.set(pitAvoidCallsTo)
    }
}

tasks.named("pitest") {
    dependsOn(":core:jar")
    dependsOn(":adapter-console:jar")
}
