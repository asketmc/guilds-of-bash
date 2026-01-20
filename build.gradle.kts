import java.io.File
import java.security.MessageDigest
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency

plugins {
    kotlin("jvm") version "1.9.23" apply false

    // === KOVER ROOT AGGREGATOR ===
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

repositories {
    mavenCentral()
}

// === KOVER: declare which modules participate in merged coverage ===
dependencies {
    kover(project(":core"))
    kover(project(":core-test"))
}

subprojects {
    repositories {
        mavenCentral()
    }
}

// ====================================================================
// === Everything below is AI-context tooling
// ====================================================================

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

fun sha256Hex(text: String): String =
    sha256Hex(text.toByteArray(Charsets.UTF_8))

val aiContextRelPath = "ai/ai_context.txt"
val aiDeltaRelPath   = "ai/ai_context_delta.txt"
val aiManifestRelPath = "ai/ai_manifest.txt"
val aiRulesSourceRelPath = "ai/ai_rules.txt"

val aiExcludePathParts = listOf(
    "${File.separator}.gradle${File.separator}",
    "${File.separator}.idea${File.separator}",
    "${File.separator}build${File.separator}",
    "${File.separator}out${File.separator}",
    "${File.separator}node_modules${File.separator}",
    "${File.separator}.git${File.separator}",
)

val aiExcludeFileNames = setOf(
    "local.properties",
    ".env",
    ".env.local",
    "google-services.json"
)

val aiExcludeExtensions = setOf(
    "jks","keystore","p12","pem","key","crt"
)

val aiDeclLineRegex = Regex(
    """^\s*(public|private|protected|internal)?\s*(final|open|abstract|sealed|data|enum|annotation|value|inline|tailrec|suspend|operator|infix|external|expect|actual)?\s*(class|interface|object|fun|val|var|typealias)\b.*$"""
)

val aiSymbolDeclRegex = Regex(
    """^\s*(?:public|private|protected|internal\s+)?(?:[A-Za-z]+\s+)*?(class|interface|object|fun|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)\b"""
)

val aiEntrypointRegex = Regex("""\bfun\s+main\s*\(""")
val aiKotlinPluginVersionRegex = Regex("""kotlin\("jvm"\)\s+version\s+"([^"]+)"""")

val aiSymbolFocusFunctions = setOf(
    "main","step","canApply","initialState",
    "serialize","deserialize","hashState","hashEvents","verifyInvariants"
)

fun readAiManifest(file: File): Pair<String, Map<String,String>>? {
    if (!file.exists()) return null
    val lines = file.readLines(Charsets.UTF_8)
    val globalLine = lines.firstOrNull { it.startsWith("globalSha256=") } ?: return null
    val global = globalLine.substringAfter("=")
    val hashes = linkedMapOf<String,String>()
    val start = lines.indexOfFirst { it == "files:" }
    if (start == -1) return global to hashes
    for (i in start+1 until lines.size) {
        val line = lines[i]
        if (line.isBlank()) continue
        val parts = line.split('\t')
        if (parts.size == 2) hashes[parts[0]] = parts[1]
    }
    return global to hashes
}

fun buildFileBlock(file: File, rel: String): String {
    val bytes = file.readBytes()
    val hash = sha256Hex(bytes)
    val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrElse { "" }

    val sb = StringBuilder()
    sb.appendLine()
    sb.appendLine("== $rel (sha256=$hash) ==")

    if (text.isBlank()) {
        sb.appendLine("<non-utf8-or-empty>")
        return sb.toString()
    }

    val lines = text.lineSequence().toList()
    val pkg = lines.firstOrNull { it.trimStart().startsWith("package ") }?.trim()
    if (pkg != null) sb.appendLine(pkg)

    for ((idx,line) in lines.withIndex()) {
        if (aiDeclLineRegex.matches(line)) {
            sb.appendLine("${idx+1}: ${line.trimEnd()}")
        }
    }

    return sb.toString()
}

val generateAiContext = rootProject.tasks.register("generateAiContext") {
    group = "documentation"
    description = "Generates AI context snapshot + delta"

    val outFileProvider = rootProject.layout.buildDirectory.file(aiContextRelPath)
    val deltaFileProvider = rootProject.layout.buildDirectory.file(aiDeltaRelPath)
    val manifestFileProvider = rootProject.layout.buildDirectory.file(aiManifestRelPath)

    outputs.files(outFileProvider, deltaFileProvider, manifestFileProvider)

    doLast {
        val outFile = outFileProvider.get().asFile
        val deltaFile = deltaFileProvider.get().asFile
        val manifestFile = manifestFileProvider.get().asFile
        outFile.parentFile.mkdirs()

        fun isExcluded(file: File): Boolean {
            val path = file.absolutePath
            if (aiExcludePathParts.any { path.contains(it) }) return true
            val name = file.name
            if (name in aiExcludeFileNames) return true
            val ext = name.substringAfterLast('.', "")
            if (ext.isNotEmpty() && ext in aiExcludeExtensions) return true
            return false
        }

        val files = rootProject.rootDir
            .walkTopDown()
            .filter { it.isFile }
            .filterNot { isExcluded(it) }
            .filter {
                val n = it.name
                n.endsWith(".kt") || n.endsWith(".kts") ||
                        n == "build.gradle" || n == "build.gradle.kts" ||
                        n == "settings.gradle" || n == "settings.gradle.kts" ||
                        n == "gradle.properties"
            }
            .toList()
            .sortedBy { it.relativeTo(rootProject.rootDir).invariantSeparatorsPath }

        val prev = readAiManifest(manifestFile)
        val prevGlobal = prev?.first
        val prevHashes = prev?.second ?: emptyMap()

        val fileHashes = linkedMapOf<String,String>()
        val symbolIndex = linkedMapOf<String,String>()
        val entrypoints = mutableListOf<String>()

        val sb = StringBuilder(2_000_000)

        sb.appendLine("AI_CONTEXT v1")
        sb.appendLine("root=${rootProject.name}")
        sb.appendLine("generatedAtEpochMs=${System.currentTimeMillis()}")
        sb.appendLine()

        sb.appendLine("MODULES:")
        for (p in rootProject.allprojects.sortedBy { it.path }) {
            sb.appendLine("- ${p.path} dir=${p.projectDir.relativeTo(rootProject.rootDir).invariantSeparatorsPath}")
        }
        sb.appendLine()

        sb.appendLine("DECLARATIONS:")
        for (f in files) {
            val rel = f.relativeTo(rootProject.rootDir).invariantSeparatorsPath
            sb.append(buildFileBlock(f, rel))
        }

        val snapshotBytes = sb.toString().toByteArray(Charsets.UTF_8)
        val globalHash = sha256Hex(snapshotBytes)

        outFile.writeText(sb.toString(), Charsets.UTF_8)

        manifestFile.writeText(
            buildString {
                appendLine("globalSha256=$globalHash")
                appendLine("files:")
                for ((rel,h) in fileHashes.toSortedMap()) {
                    appendLine("$rel\t$h")
                }
            },
            Charsets.UTF_8
        )
    }
}

// Always generate context on build/classes
rootProject.allprojects {
    tasks.matching { it.name == "build" || it.name == "classes" }.configureEach {
        dependsOn(generateAiContext)
    }
}

// CI task
tasks.register("ciTest") {
    group = "verification"
    dependsOn(":core-test:test")
}

subprojects {
    tasks.matching { it.name == "build" }.configureEach {
        dependsOn(":core-test:test")
    }
}
