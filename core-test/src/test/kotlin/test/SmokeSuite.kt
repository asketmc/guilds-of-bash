// FILE: core-test/src/test/kotlin/test/SmokeSuite.kt
package test

import org.junit.platform.suite.api.IncludeTags
import org.junit.platform.suite.api.SelectPackages
import org.junit.platform.suite.api.Suite

@Suite
@SelectPackages(
    "test",
    "core",
    "console"
)
@IncludeTags("smoke")
class SmokeSuite
