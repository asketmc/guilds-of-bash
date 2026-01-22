// FILE: core-test/build.gradle.kts

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.math.BigDecimal

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover") // version from root
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(project(":core"))

    // Kotlin test -> JUnit5
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("reflect"))

    // Если IDE/Gradle внезапно перестают видеть тесты как JUnit5, этот агрегат стабилизирует стек:
    // (junit-jupiter включает engine; типовой Gradle пример — testImplementation("org.junit.jupiter:junit-jupiter:<ver>"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // PIT JUnit5 plugin
    pitest("org.pitest:pitest-junit5-plugin:1.2.1")
}

// ====================================================================
// === OPTIONAL: disable UP-TO-DATE locally (IDE-friendly)
// ====================================================================
// Use: -PnoTestCache=true  OR pass --rerun-tasks
val noTestCache = (findProperty("noTestCache") as String?)?.toBooleanStrictOrNull() == true
if (noTestCache) {
    tasks.withType<Test>().configureEach {
        outputs.upToDateWhen { false }
    }
    tasks.named("pitest") {
        outputs.upToDateWhen { false }
    }
}

// ====================================================================
// === TEST TASKS
// ====================================================================

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform {
        excludeTags("flaky", "perf")
    }
}

tasks.register<Test>("testFast") {
    group = "verification"
    useJUnitPlatform { excludeTags("slow", "flaky", "perf") }
}

tasks.register<Test>("testFull") {
    group = "verification"
    useJUnitPlatform { excludeTags("flaky", "perf") }
}

tasks.register<Test>("testQuarantine") {
    group = "verification"
    useJUnitPlatform { includeTags("flaky") }
    ignoreFailures = true
}

tasks.register<Test>("smokeTest") {
    group = "verification"
    useJUnitPlatform {
        includeTags("smoke")
        excludeTags("flaky", "perf")
    }
    failFast = true
}

tasks.register<Test>("perfTest") {
    group = "verification"
    description = "Runs perf/load tests only (manual)"
    useJUnitPlatform { includeTags("perf") }
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
// === PITEST (mutate :core, run tests from :core-test)
// ====================================================================
//
// Важно: для multi-module схемы добавляем :core как mutable code path через additionalMutableCodePaths. :contentReference[oaicite:2]{index=2}
// PIT паттерны — классовые (обычно с wildcard), не regex. :contentReference[oaicite:3]{index=3}

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
            // по умолчанию исключаем perf (P3_*) из mutation
            "test.P1_*",
            "test.P2_*",
            "test.Smoke*"
        )

pitest {
    testPlugin.set("junit5")
    junit5PluginVersion.set("1.2.1")

    targetClasses.set(setOf(pitTargetClasses))
    targetTests.set(pitTargetTests)

    mutators.set(setOf("DEFAULTS"))
    threads.set(Runtime.getRuntime().availableProcessors())

    timeoutFactor.set(BigDecimal("1.5"))
    timeoutConstInMillis.set(3000)

    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    verbose.set(true)

    failWhenNoMutations.set(false)
    useClasspathFile.set(true)

    // Мутируем именно :core (jar как mutable code path)
    additionalMutableCodePaths.set(
        files(project(":core").tasks.named("jar"))
    )

    // Exclusions для production-кода (НЕ для тестов)
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

// ====================================================================
// === TEST COMPILATION NOISE REDUCTION
// ====================================================================

tasks.named<KotlinCompile>("compileTestKotlin") {
    compilerOptions {
        freeCompilerArgs.add("-nowarn")
    }
}
