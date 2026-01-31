package console

/**
 * Small configurable console IO wrapper so production code can be routed to a sink and
 * tests can capture output deterministically.
 */
object ConsoleIO {
    // Default sink writes to stdout
    @Volatile
    var sink: (String) -> Unit = { s -> kotlin.io.print(s) }

    fun write(s: String) = sink(s)
    fun writeln(s: String = "") = sink(s + "\n")
}
