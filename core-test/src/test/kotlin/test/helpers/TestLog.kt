package test.helpers

/**
 * Test sink to capture printed output during tests.
 */
object TestLog {
    private val _entries = mutableListOf<String>()

    fun log(s: String) {
        _entries.add(s)
    }

    fun clear() {
        _entries.clear()
    }

    fun entries(): List<String> = _entries.toList()
}
