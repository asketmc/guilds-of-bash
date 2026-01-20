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
