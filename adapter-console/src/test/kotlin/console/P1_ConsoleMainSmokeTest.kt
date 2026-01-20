package console

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class P1_ConsoleMainSmokeTest {

    @Test
    fun `main prints startup banner`() {
        val origOut = System.out
        val origErr = System.err

        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()

        try {
            System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
            System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))

            main()

        } finally {
            System.setOut(origOut)
            System.setErr(origErr)
        }

        val out = outBuf.toString(Charsets.UTF_8).trim()
        assertTrue(out.contains("Console adapter ready"), "Expected startup banner in stdout")
    }

    @Test
    fun `main does not write to stderr`() {
        val origOut = System.out
        val origErr = System.err

        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()

        try {
            System.setOut(PrintStream(outBuf, true, Charsets.UTF_8))
            System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))

            main()

        } finally {
            System.setOut(origOut)
            System.setErr(origErr)
        }

        val err = errBuf.toString(Charsets.UTF_8).trim()
        assertEquals("", err, "Expected empty stderr")
    }
}
