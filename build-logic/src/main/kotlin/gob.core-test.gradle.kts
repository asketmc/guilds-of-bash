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

tasks.register<Test>("testPr") {
    group = "verification"
    description = "PR gate tests: smoke + p0 + p1 (excludes perf, flaky)"
    useJUnitPlatform {
        includeTags("smoke", "p0", "p1")
        excludeTags("perf", "flaky")
    }
    failFast = true
}

tasks.register<Test>("testAllNoPerf") {
    group = "verification"
    description = "All tests except perf and flaky"
    useJUnitPlatform {
        excludeTags("perf", "flaky")
    }
}

tasks.register<Test>("perfTest") {
    group = "verification"
    description = "Runs perf/load tests only (manual)"
    useJUnitPlatform {
        includeTags("perf")
        excludeTags("flaky")
        // Exclude JUnit Platform Suite classes that are used for other test filtering
        excludeEngines("junit-platform-suite")
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
