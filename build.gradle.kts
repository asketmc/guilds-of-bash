// FILE: build.gradle.kts

import java.io.File
import java.security.MessageDigest
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "1.9.23" apply false
    kotlin("plugin.serialization") version "1.9.23" apply false

    // === DOKKA (docs-as-artifact) ===
    id("org.jetbrains.dokka") version "2.1.0"

    // === KOVER ROOT AGGREGATOR ===
    id("org.jetbrains.kotlinx.kover") version "0.8.3"

    // === DETEKT (static analysis for Kotlin) ===
    id("io.gitlab.arturbosch.detekt") version "1.23.7"

    // === PITEST (mutation testing for quality assurance) ===
    id("info.solidsoft.pitest") version "1.15.0" apply false
}

repositories {
    mavenCentral()
}

// === KOVER: declare which modules participate in merged coverage ===
dependencies {
    kover(project(":core"))
    kover(project(":core-test"))
}

// --- Repositories for all subprojects ---
subprojects {
    repositories {
        mavenCentral()
    }
}

// --- DOKKA: apply to Kotlin modules (multi-module HTML via root task dokkaHtmlMultiModule) ---
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jetbrains.dokka")
    }
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        apply(plugin = "org.jetbrains.dokka")
    }
}

// Stable output directory for CI artifact
tasks.withType<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>().configureEach {
    outputDirectory.set(layout.buildDirectory.dir("reports/dokka/html"))
}

// --- Java/Kotlin toolchain alignment (Java 17) ---
subprojects {
    // Java toolchain for any Java/Groovy plugins
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
    }

    // Kotlin JVM target
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

// --- Detekt: apply + configure once for Kotlin modules ---
subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = "io.gitlab.arturbosch.detekt")

        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension>("detekt") {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            baseline = rootProject.file("config/detekt/detekt-baseline.xml")

            // Анализируем Kotlin исходники проекта, исключая сгенерённое/сборочное.
            source.setFrom(
                files(
                    "src/main/kotlin",
                    "src/test/kotlin"
                ).filter { it.exists() }
            )
            // NOTE: removed incorrect `exclude(...)` calls from DetektExtension scope.
        }

        tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
            jvmTarget = "17"
            ignoreFailures = true // adoption mode; позже можно ужесточить

            reports {
                xml.required.set(true)
                html.required.set(true)
                txt.required.set(false)
            }

            include("**/*.kt")
            exclude("**/build/**")
            exclude("**/*.kts")
        }

        // Чтобы detekt запускался вместе с check
        tasks.named("check") {
            dependsOn(tasks.withType<io.gitlab.arturbosch.detekt.Detekt>())
        }
    }
}

// Detekt configuration for the root project (applies to subprojects)
// We configure sensible defaults and generate HTML/XML reports under build/reports/detekt
// Limit detekt root sources to production sources only (avoid build scripts)
val detektSourceFiles = files(rootProject.subprojects.mapNotNull { p ->
    val f = file("${p.projectDir}/src/main/kotlin")
    if (f.exists()) f else null
})

detekt {
    toolVersion = "1.23.7"
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
    baseline = file("config/detekt/detekt-baseline.xml")
}

// Root-level detekt task (if executed) should not scan build scripts
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    include("**/*.kt")
    exclude("**/build/**")
    exclude("**/*.kts")
    ignoreFailures = true
}

// Ensure kover's agent args file directory exists before any Test task runs (fixes Windows FileNotFoundException)
subprojects {
    tasks.withType<Test>().configureEach {
        doFirst {
            val koverTmp = file("${buildDir}/tmp/test")
            if (!koverTmp.exists()) {
                logger.lifecycle("Creating directory for kover agent args: ${koverTmp.absolutePath}")
                koverTmp.mkdirs()
            }
        }
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
    """^\s*(?:public|private|protected|internal\s+)?(?:[A-Za-z]+\s+)*?(class|interface|object|fun|typealias)\s+([A-ZaZ_][A-Zael0-9_]*)\b"""
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
        if (parts.size == 2) {
            hashes[parts[0]] = parts[1]
        }
    }
    return global to hashes
}
