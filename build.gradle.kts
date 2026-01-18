import java.io.File
import java.security.MessageDigest
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency

plugins {
    kotlin("jvm") version "1.9.23" apply false
}

subprojects {
    repositories {
        mavenCentral()
    }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

fun sha256Hex(text: String): String =
    sha256Hex(text.toByteArray(Charsets.UTF_8))

val aiContextRelPath = "ai/ai_context.txt"
val aiDeltaRelPath   = "ai/ai_context_delta.txt"
val aiManifestRelPath = "ai/ai_manifest.txt"
val aiRulesSourceRelPath = "ai/ai_rules.txt"   // optional file in repo

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

        // ENV
        run {
            val buildText = rootProject.buildFile.readText()
            val kotlinVer = aiKotlinPluginVersionRegex.find(buildText)?.groupValues?.get(1)

            sb.appendLine("ENV:")
            sb.appendLine("- gradleVersion=${gradle.gradleVersion}")
            if (!kotlinVer.isNullOrBlank())
                sb.appendLine("- kotlinGradlePluginVersion=$kotlinVer")
            sb.appendLine("- javaVersion=${System.getProperty("java.version")}")
            sb.appendLine("- javaVendor=${System.getProperty("java.vendor")}")
            sb.appendLine("- osName=${System.getProperty("os.name")}")
            sb.appendLine("- osArch=${System.getProperty("os.arch")}")
            sb.appendLine("- osVersion=${System.getProperty("os.version")}")
            sb.appendLine()
        }

        // MODULES
        sb.appendLine("MODULES:")
        for (p in rootProject.allprojects.sortedBy { it.path }) {
            sb.appendLine("- ${p.path} dir=${p.projectDir.relativeTo(rootProject.rootDir).invariantSeparatorsPath}")
        }
        sb.appendLine()

        // MODULE_GRAPH
        sb.appendLine("MODULE_GRAPH:")
        val cfgNames = listOf(
            "api","implementation","compileOnly","runtimeOnly",
            "testImplementation","testCompileOnly","testRuntimeOnly"
        )

        for (p in rootProject.allprojects.sortedBy { it.path }) {
            val deps = linkedSetOf<String>()
            for (cn in cfgNames) {
                val cfg = p.configurations.findByName(cn) ?: continue
                cfg.dependencies.withType(ProjectDependency::class.java).forEach { d ->
                    deps.add(d.path)
                }
            }
            if (deps.isNotEmpty()) {
                sb.appendLine("- ${p.path} -> ${deps.sorted().joinToString(", ")}")
            }
        }
        sb.appendLine()

        // DEPENDENCIES_CORE
        sb.appendLine("DEPENDENCIES_CORE:")
        val coreProject = rootProject.findProject(":core")
        if (coreProject != null) {
            val coords = linkedSetOf<String>()
            val coreCfgNames = listOf("api","implementation","compileOnly","runtimeOnly")
            for (cn in coreCfgNames) {
                val cfg = coreProject.configurations.findByName(cn) ?: continue
                for (dep in cfg.dependencies) {
                    if (dep is ProjectDependency) continue
                    if (dep is ExternalModuleDependency || dep is ModuleDependency) {
                        val g = dep.group ?: continue
                        val v = dep.version
                        coords.add(if (!v.isNullOrBlank()) "$g:${dep.name}:$v" else "$g:${dep.name}")
                    }
                }
            }
            for (c in coords.sorted()) sb.appendLine("- $c")
        }
        sb.appendLine()

        // Optional project rules
        val rulesFile = File(rootProject.rootDir, aiRulesSourceRelPath)
        if (rulesFile.exists()) {
            sb.appendLine("PROJECT_RULES:")
            rulesFile.readLines().forEach { sb.appendLine(it.trimEnd()) }
            sb.appendLine()
        }

        // FILES
        sb.appendLine("FILES:")
        for (f in files) {
            sb.appendLine("- ${f.relativeTo(rootProject.rootDir).invariantSeparatorsPath}")
        }
        sb.appendLine()

        // Scan symbols + hashes
        for (f in files) {
            val rel = f.relativeTo(rootProject.rootDir).invariantSeparatorsPath
            val bytes = f.readBytes()
            val hash = sha256Hex(bytes)
            fileHashes[rel] = hash

            val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrElse { "" }
            if (text.isBlank()) continue
            val lines = text.lineSequence().toList()

            for ((idx,line) in lines.withIndex()) {
                if (aiEntrypointRegex.containsMatchIn(line)) {
                    entrypoints.add("$rel:${idx+1}")
                }
                val m = aiSymbolDeclRegex.find(line) ?: continue
                val kind = m.groupValues[1]
                val name = m.groupValues[2]
                val keep = when (kind) {
                    "fun" -> name in aiSymbolFocusFunctions
                    else -> true
                }
                if (keep) symbolIndex.putIfAbsent(name, rel)
            }
        }

        // ENTRYPOINTS
        sb.appendLine("ENTRYPOINTS:")
        for (e in entrypoints.distinct().sorted()) sb.appendLine("- $e")
        sb.appendLine()

        // SYMBOL_INDEX
        sb.appendLine("SYMBOL_INDEX:")
        for ((sym,rel) in symbolIndex.toSortedMap()) {
            sb.appendLine("- $sym -> $rel")
        }
        sb.appendLine()

        // DECLARATIONS
        sb.appendLine("DECLARATIONS:")
        for (f in files) {
            val rel = f.relativeTo(rootProject.rootDir).invariantSeparatorsPath
            sb.append(buildFileBlock(f, rel))
        }

        val snapshotBytes = sb.toString().toByteArray(Charsets.UTF_8)
        val globalHash = sha256Hex(snapshotBytes)

        val changedFiles = fileHashes.keys.filter { prevHashes[it] != fileHashes[it] }.sorted()
        val removedFiles = (prevHashes.keys - fileHashes.keys).sorted()

        // FULL CONTEXT
        outFile.writeText(
            buildString {
                appendLine("globalSha256=$globalHash")
                if (!prevGlobal.isNullOrBlank()) {
                    appendLine("baseGlobalSha256=$prevGlobal")
                    appendLine("changedFiles:")
                    changedFiles.forEach { appendLine("- $it") }
                    appendLine("removedFiles:")
                    removedFiles.forEach { appendLine("- $it") }
                }
                append(sb)
            },
            Charsets.UTF_8
        )

        // DELTA
        if (!prevGlobal.isNullOrBlank()) {
            val d = StringBuilder()
            d.appendLine("AI_CONTEXT_DELTA v1")
            d.appendLine("baseGlobalSha256=$prevGlobal")
            d.appendLine("targetGlobalSha256=$globalHash")
            d.appendLine()
            d.appendLine("changedFiles:")
            changedFiles.forEach { d.appendLine("- $it") }
            d.appendLine("removedFiles:")
            removedFiles.forEach { d.appendLine("- $it") }
            d.appendLine()
            d.appendLine("DECLARATIONS_CHANGED:")
            for (rel in changedFiles) {
                val f = File(rootProject.rootDir, rel)
                if (f.exists()) d.append(buildFileBlock(f, rel))
            }
            deltaFile.writeText(d.toString(), Charsets.UTF_8)
        }

        // MANIFEST
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
