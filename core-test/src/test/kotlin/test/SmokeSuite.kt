// FILE: core-test/src/test/kotlin/test/SmokeSuite.kt
package test

import org.junit.platform.suite.api.IncludeTags
import org.junit.platform.suite.api.SelectPackages
import org.junit.platform.suite.api.Suite

/**
 * JUnit Platform Suite for smoke tests.
 * This suite is tagged with @Smoke to ensure it only runs in smoke/PR test tasks.
 */
@Suite
@SelectPackages(
    "test",
    "core",
    "console"
)
@IncludeTags("smoke")
@Smoke
class SmokeSuite
