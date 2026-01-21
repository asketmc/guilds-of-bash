import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
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
