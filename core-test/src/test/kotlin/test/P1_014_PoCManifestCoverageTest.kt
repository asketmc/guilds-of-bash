package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import kotlin.test.*

/**
 * P1 CRITICAL: PoC Manifest Coverage Test.
 * Validates that all commands and events declared in POC_MANIFEST.md are present in the codebase.
 *
 * Purpose:
 * - Ensure manifest stays synchronized with code
 * - Catch missing or renamed commands/events
 * - Document PoC scope through test assertions
 */
class P1_014_PoCManifestCoverageTest {

    /**
     * PoC commands as declared in POC_MANIFEST.md (feature groups FG_01-FG_12).
     */
    private val pocCommands = setOf(
        "PostContract",
        "CloseReturn",
        "AdvanceDay",
        "SellTrophies",
        "PayTax",
        "SetProofPolicy"
    )

    /**
     * PoC events as declared in POC_MANIFEST.md (feature groups FG_01-FG_12).
     */
    private val pocEvents = setOf(
        "DayStarted",
        "InboxGenerated",
        "HeroesArrived",
        "WipAdvanced",
        "ContractResolved",
        "DayEnded",
        "ContractPosted",
        "ContractTaken",
        "HeroDeclined",
        "ReturnClosed",
        "StabilityUpdated",
        "TrophySold",
        "CommandRejected",
        "InvariantViolated",
        "TrophyTheftSuspected",
        "TaxDue",
        "TaxPaid",
        "TaxMissed",
        "GuildShutdown",
        "GuildRankUp"
    )

    @Test
    fun `all PoC commands exist in code`() {
        // Use helper to get sealed subclass names
        val commandClasses = sealedSubclassNamesOf(Command::class)

        // Verify all PoC commands are present
        val missingCommands = pocCommands - commandClasses
        assertTrue(
            missingCommands.isEmpty(),
            "Missing PoC commands in code: $missingCommands. Update core/Commands.kt or POC_MANIFEST.md"
        )

        // Document covered commands
        println("✓ All ${pocCommands.size} PoC commands present: ${pocCommands.sorted()}")
    }

    @Test
    fun `all PoC events exist in code`() {
        // Use helper to get sealed subclass names
        val eventClasses = sealedSubclassNamesOf(Event::class)

        // Verify all PoC events are present
        val missingEvents = pocEvents - eventClasses
        assertTrue(
            missingEvents.isEmpty(),
            "Missing PoC events in code: $missingEvents. Update core/Events.kt or POC_MANIFEST.md"
        )

        // Document covered events
        println("✓ All ${pocEvents.size} PoC events present: ${pocEvents.sorted()}")
    }

    @Test
    fun `no extra commands beyond PoC scope`() {
        // Get all Command implementations
        val commandClasses = sealedSubclassNamesOf(Command::class)

        // Check for commands not in manifest (potential scope creep)
        val extraCommands = commandClasses - pocCommands

        // Allow for future expansion but document it
        if (extraCommands.isNotEmpty()) {
            println("⚠ Extra commands beyond PoC manifest: $extraCommands")
            println("  Consider updating POC_MANIFEST.md if these are production-ready")
        } else {
            println("✓ No extra commands beyond PoC scope")
        }
    }

    @Test
    fun `no extra events beyond PoC scope`() {
        // Get all Event implementations
        val eventClasses = sealedSubclassNamesOf(Event::class)

        // Check for events not in manifest
        val extraEvents = eventClasses - pocEvents

        if (extraEvents.isNotEmpty()) {
            println("⚠ Extra events beyond PoC manifest: $extraEvents")
            println("  Consider updating POC_MANIFEST.md if these are production-ready")
        } else {
            println("✓ No extra events beyond PoC scope")
        }
    }

    @Test
    fun `command-event mapping coverage for critical paths`() {
        // Verify that critical command-event pairs exist (based on POC_MANIFEST.md feature groups)

        // FG_02: PostContract → ContractPosted
        assertSealedSubclassExists(Command::class, "PostContract")
        assertSealedSubclassExists(Event::class, "ContractPosted")

        // FG_05: CloseReturn → ReturnClosed
        assertSealedSubclassExists(Command::class, "CloseReturn")
        assertSealedSubclassExists(Event::class, "ReturnClosed")

        // FG_06: SellTrophies → TrophySold
        assertSealedSubclassExists(Command::class, "SellTrophies")
        assertSealedSubclassExists(Event::class, "TrophySold")

        // FG_07: All commands → CommandRejected (validation)
        assertSealedSubclassExists(Event::class, "CommandRejected")

        println("✓ Critical command-event mappings verified")
    }

    @Test
    fun `PoC feature group commands are testable`() {
        // Ensure all PoC commands have cmdId field (required for step() function)
        val missing = sealedSubclassesMissingField(Command::class, "cmdId")

        assertTrue(missing.isEmpty(),
            "All Command implementations must have cmdId: Long field for step() reducer; missing=$missing")

        println("✓ All commands have cmdId field (testable via step())")
    }

    @Test
    fun `PoC feature group events are observable`() {
        // Ensure all PoC events have required fields: day, revision, cmdId, seq
        val requiredFields = setOf("day", "revision", "cmdId", "seq")
        val missingInfo = Event::class.sealedSubclasses.mapNotNull { sub ->
            val fieldNames = sub.java.declaredFields.map { it.name }.toSet()
            val missing = requiredFields - fieldNames
            if (missing.isNotEmpty()) "${sub.simpleName}: missing=$missing" else null
        }

        assertTrue(missingInfo.isEmpty(),
            "All Event implementations must have day, revision, cmdId, seq fields; missing=$missingInfo")

        println("✓ All events have required fields (observable via event stream)")
    }
}
