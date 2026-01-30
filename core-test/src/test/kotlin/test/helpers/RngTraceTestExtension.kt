package test.helpers

import core.rng.RngTrace
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * Opt-in RNG trace capture for forensic debugging.
 *
 * Enabled when system property `gob.rng.trace` is set to a directory path.
 * Writes one file per test method.
 */
class RngTraceTestExtension : BeforeEachCallback, AfterEachCallback {

    private val buffer = ArrayList<String>(512)

    override fun beforeEach(context: ExtensionContext) {
        val dir = System.getProperty(PROP_DIR)?.takeIf { it.isNotBlank() } ?: return
        buffer.clear()
        RngTrace.sink = { e ->
            buffer.add("${e.drawIndex}\t${e.method}\t${e.bound ?: "-"}\t${e.value}")
        }

        // Ensure directory exists early.
        Files.createDirectories(Path.of(dir))
    }

    override fun afterEach(context: ExtensionContext) {
        val dir = System.getProperty(PROP_DIR)?.takeIf { it.isNotBlank() }
        try {
            if (dir != null && buffer.isNotEmpty()) {
                val fileName = buildString {
                    append(context.requiredTestClass.simpleName)
                    append("__")
                    append(context.requiredTestMethod.name)
                    append(".rng.tsv")
                }
                val target = Path.of(dir).resolve(fileName)
                Files.write(target, buffer)
            }
        } finally {
            RngTrace.sink = null
            buffer.clear()
        }
    }

    private companion object {
        private const val PROP_DIR = "gob.rng.trace"
    }
}
