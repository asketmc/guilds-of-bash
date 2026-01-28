// FILE: core-test/src/test/kotlin/test/suites/SmokeSuite.kt
package test.suites

import org.junit.platform.suite.api.IncludeTags
import org.junit.platform.suite.api.SelectPackages
import org.junit.platform.suite.api.Suite

/**
 * JUnit Platform Suite for smoke tests.
 *
 * Runs all tests annotated with @Smoke (tag "smoke") across modules.
 * Intended for fast PR gate validation.
 *
 * Usage: ./gradlew :core-test:test --tests "test.suites.SmokeSuite"
 */
@Suite
@SelectPackages(
    "test",
    "core",
    "console"
)
@IncludeTags("smoke")
class SmokeSuite
