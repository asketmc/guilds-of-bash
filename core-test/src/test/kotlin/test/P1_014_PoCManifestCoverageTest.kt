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
        // Get all Command sealed interface implementations via reflection
        val commandClasses = Command::class.sealedSubclasses.map { it.simpleName }

        // Verify all PoC commands are present
        val missingCommands = pocCommands - commandClasses.toSet()
        assertTrue(
            missingCommands.isEmpty(),
            "Missing PoC commands in code: $missingCommands. Update core/Commands.kt or POC_MANIFEST.md"
        )

        // Document covered commands
        println("✓ All ${pocCommands.size} PoC commands present: ${pocCommands.sorted()}")
    }

    @Test
    fun `all PoC events exist in code`() {
        // Get all Event sealed interface implementations via reflection
        val eventClasses = Event::class.sealedSubclasses.map { it.simpleName }

        // Verify all PoC events are present
        val missingEvents = pocEvents - eventClasses.toSet()
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
        val commandClasses = Command::class.sealedSubclasses.map { it.simpleName ?: "Unknown" }

        // Check for commands not in manifest (potential scope creep)
        val extraCommands = commandClasses.toSet() - pocCommands

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
        val eventClasses = Event::class.sealedSubclasses.map { it.simpleName ?: "Unknown" }

        // Check for events not in manifest
        val extraEvents = eventClasses.toSet() - pocEvents

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
        val postContractExists = Command::class.sealedSubclasses.any { it.simpleName == "PostContract" }
        val contractPostedExists = Event::class.sealedSubclasses.any { it.simpleName == "ContractPosted" }
        assertTrue(postContractExists && contractPostedExists,
            "FG_02 Contract Posting: PostContract command and ContractPosted event must exist")

        // FG_05: CloseReturn → ReturnClosed
        val closeReturnExists = Command::class.sealedSubclasses.any { it.simpleName == "CloseReturn" }
        val returnClosedExists = Event::class.sealedSubclasses.any { it.simpleName == "ReturnClosed" }
        assertTrue(closeReturnExists && returnClosedExists,
            "FG_05 Return Processing: CloseReturn command and ReturnClosed event must exist")

        // FG_06: SellTrophies → TrophySold
        val sellTrophiesExists = Command::class.sealedSubclasses.any { it.simpleName == "SellTrophies" }
        val trophySoldExists = Event::class.sealedSubclasses.any { it.simpleName == "TrophySold" }
        assertTrue(sellTrophiesExists && trophySoldExists,
            "FG_06 Trophy Sales: SellTrophies command and TrophySold event must exist")

        // FG_07: All commands → CommandRejected (validation)
        val commandRejectedExists = Event::class.sealedSubclasses.any { it.simpleName == "CommandRejected" }
        assertTrue(commandRejectedExists,
            "FG_07 Command Validation: CommandRejected event must exist for all command validation")

        println("✓ Critical command-event mappings verified")
    }

    @Test
    fun `PoC feature group commands are testable`() {
        // Ensure all PoC commands have cmdId field (required for step() function)
        // This is structural validation - if code compiles, this should pass
        // But we document it explicitly as a contract

        val allCommandsHaveCmdId = Command::class.sealedSubclasses.all { kClass ->
            kClass.java.declaredFields.any { it.name == "cmdId" }
        }

        assertTrue(allCommandsHaveCmdId,
            "All Command implementations must have cmdId: Long field for step() reducer")

        println("✓ All commands have cmdId field (testable via step())")
    }

    @Test
    fun `PoC feature group events are observable`() {
        // Ensure all PoC events have required fields: day, revision, cmdId, seq
        // This validates Event sealed interface contract

        val requiredFields = setOf("day", "revision", "cmdId", "seq")
        val allEventsHaveRequiredFields = Event::class.sealedSubclasses.all { kClass ->
            val fieldNames = kClass.java.declaredFields.map { it.name }.toSet()
            requiredFields.all { it in fieldNames }
        }

        assertTrue(allEventsHaveRequiredFields,
            "All Event implementations must have day, revision, cmdId, seq fields")

        println("✓ All events have required fields (observable via event stream)")
    }
}
