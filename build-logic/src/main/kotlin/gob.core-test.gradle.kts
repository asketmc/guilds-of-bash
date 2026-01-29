import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("gob.kotlin-jvm-base")
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
}

// ====================================================================
// === TEST TASKS
// ====================================================================

/**
 * KISS fix:
 * - Prevent JUnit Platform Suite engine from running in "regular" test tasks.
 * - Suite classes (e.g., SmokeSuite) should be executed only by dedicated suite tasks,
 *   otherwise they may fail CI with NoTestsDiscoveredException depending on filters.
 */
fun Test.excludeSuiteEngine() {
    useJUnitPlatform {
        excludeEngines("junit-platform-suite")
    }
}

tasks.test {
    excludeSuiteEngine()
    useJUnitPlatform {
        excludeTags("flaky", "perf")
    }
}

tasks.register<Test>("testFast") {
    group = "verification"
    excludeSuiteEngine()
    useJUnitPlatform { excludeTags("slow", "flaky", "perf") }
}

tasks.register<Test>("testFull") {
    group = "verification"
    excludeSuiteEngine()
    useJUnitPlatform { excludeTags("flaky", "perf") }
}

tasks.register<Test>("testQuarantine") {
    group = "verification"
    excludeSuiteEngine()
    useJUnitPlatform { includeTags("flaky") }
    ignoreFailures = true
}

tasks.register<Test>("smokeTest") {
    group = "verification"
    // keep suite engine excluded here too; smoke is tag-based, not suite-based
    excludeSuiteEngine()
    useJUnitPlatform {
        includeTags("smoke")
        excludeTags("flaky", "perf")
    }
    failFast = true
}

tasks.register<Test>("testPr") {
    group = "verification"
    description = "PR gate tests: smoke + p0 + p1 (excludes perf, flaky)"
    excludeSuiteEngine()
    useJUnitPlatform {
        includeTags("smoke", "p0", "p1")
        excludeTags("perf", "flaky")
    }
    failFast = true
}

tasks.register<Test>("testAllNoPerf") {
    group = "verification"
    description = "All tests except perf and flaky"
    excludeSuiteEngine()
    useJUnitPlatform {
        excludeTags("perf", "flaky")
    }
}

tasks.register<Test>("perfTest") {
    group = "verification"
    description = "Runs perf/load tests only (manual)"
    excludeSuiteEngine()
    useJUnitPlatform {
        includeTags("perf")
        excludeTags("flaky")
    }
}

// ====================================================================
// === TEST COMPILATION NOISE REDUCTION
// ====================================================================

tasks.named<KotlinCompile>("compileTestKotlin") {
    compilerOptions {
        freeCompilerArgs.add("-nowarn")
    }
}
