package console

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class CapturedIO(
    val stdout: String,
    val stderr: String
)

private object ConsoleTestIo {
    private val lock = Any()

    fun runMainWithInput(inputUtf8: String): CapturedIO = synchronized(lock) {
        val origOut = System.out
        val origErr = System.err
        val origIn = System.`in`

        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()

        try {
            System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
            System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))
            System.setIn(ByteArrayInputStream(inputUtf8.toByteArray(Charsets.UTF_8)))

            main()

        } finally {
            System.setOut(origOut)
            System.setErr(origErr)
            System.setIn(origIn)
        }

        return CapturedIO(
            stdout = outBuf.toString(Charsets.UTF_8),
            stderr = errBuf.toString(Charsets.UTF_8)
        )
    }
}

/**
 * P0 - blocker
 */
class P0_ConsoleMainSmokeTest {

    @Test
    fun `P0 main terminates on quit (no hang)`() {
        val io = ConsoleTestIo.runMainWithInput("quit\n")
        assertTrue(io.stdout.contains("Console adapter ready"), "Expected startup banner before exit")
    }
}

/**
 * P1 - critical
 */
class P1_ConsoleMainSmokeTest {

    @Test
    fun `P1 main prints startup banner and seeds and help`() {
        val io = ConsoleTestIo.runMainWithInput("quit\n")
        val out = io.stdout

        assertTrue(out.contains("Console adapter ready"), "Expected startup banner in stdout")
        assertTrue(out.contains("stateSeed=42"), "Expected stateSeed in stdout")
        assertTrue(out.contains("rngSeed=100"), "Expected rngSeed in stdout")
        assertTrue(out.contains("Commands:"), "Expected help block in stdout")
    }

    @Test
    fun `P1 main does not write to stderr`() {
        val io = ConsoleTestIo.runMainWithInput("quit\n")
        assertEquals("", io.stderr.trim(), "Expected empty stderr")
    }
}

/**
 * P2 - high
 */
class P2_ConsoleMainSmokeTest {

    @Test
    fun `P2 unknown top-level command prints Russian hint`() {
        val io = ConsoleTestIo.runMainWithInput("nope\nquit\n")
        val out = io.stdout

        assertTrue(out.contains("Неизвестная команда: nope"), "Expected unknown command message")
        assertTrue(out.contains("Введите 'help'"), "Expected help hint for unknown command")
    }

    @Test
    fun `P2 list unknown target is treated as unknown command`() {
        val io = ConsoleTestIo.runMainWithInput("list ???\nquit\n")
        val out = io.stdout

        assertTrue(out.contains("Неизвестная команда: list ???"), "Expected unknown command message for list target")
        assertTrue(out.contains("Введите 'help'"), "Expected help hint for unknown command")
    }
}

/**
 * P3 - normal
 */
class P3_ConsoleMainSmokeTest {

    @Test
    fun `P3 status prints per-command trace markers`() {
        val io = ConsoleTestIo.runMainWithInput("status\nquit\n")
        val out = io.stdout

        assertTrue(out.contains("IN: \"status\""), "Expected input echo marker")
        assertTrue(out.contains("CTX: day="), "Expected context marker")
        assertTrue(out.contains("VARS: (none)"), "Expected vars marker for status command")
        assertTrue(out.contains("day="), "Expected status payload")
    }
}
