// FILE: core-test/build.gradle.kts

import java.math.BigDecimal

plugins {
    id("gob.core-test")
    id("org.jetbrains.kotlinx.kover")
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(project(":core"))

    // Kotlin test -> JUnit5
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("reflect"))

    // For IDE/Gradle to see J5:
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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
// === PITEST (mutate :core, run tests from :core-test)
// ====================================================================

val pitTargetClasses: String =
    (findProperty("pitTargetClasses") as String?)?.trim()
        .takeUnless { it.isNullOrEmpty() }
        ?: "core.*"

val pitTargetTests: Set<String> =
    (findProperty("pitTargetTests") as String?)?.trim()
        ?.takeUnless { it.isEmpty() }
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: setOf(
            "test.P1_*",
            "test.P2_*",
            "test.Smoke*"
        )

pitest {
    testPlugin.set("junit5")
    junit5PluginVersion.set("1.2.1")

    targetClasses.set(setOf(pitTargetClasses))
    targetTests.set(pitTargetTests)

    mutators.set(setOf("ALL"))
    threads.set(Runtime.getRuntime().availableProcessors())

    timeoutFactor.set(BigDecimal("1.5"))
    timeoutConstInMillis.set(3000)

    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    verbose.set(true)

    failWhenNoMutations.set(false)
    useClasspathFile.set(true)

    additionalMutableCodePaths.set(
        files(project(":core").tasks.named("jar"))
    )

    excludedClasses.set(
        setOf(
            "core.serde.*",
            "core.state.*Dto*",
            "*Main*"
        )
    )
}

tasks.named("pitest") {
    dependsOn(":core:jar")
}
