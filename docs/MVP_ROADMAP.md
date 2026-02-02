# MVP (M1) Development Roadmap

## ✅ COMPLETED: Phase 1 - Hero Agency & Salvage Economy

### Implemented Features:
- ✅ **ContractEvaluation.kt**: Score-based hero decision making (profit + risk + traits)
- ✅ **Hero Refusal**: Heroes decline unprofitable contracts (HeroDeclined event)
- ✅ **Theft Mechanics**: Heroes steal trophies based on greed/honesty/salvage policy
- ✅ **TrophyTheftSuspected Event**: Logging of theft incidents
- ✅ **SPLIT Salvage Policy**: 50/50 trophy distribution option
- ✅ **baseDifficulty in BoardContract**: Stored for hero evaluation
- ✅ **Refactored K9.4 Pickup**: Heroes choose best contract by attractiveness score
- ✅ **Player-Controlled Salvage**: PostContract command accepts salvage parameter (GUILD/HERO/SPLIT)
- ✅ **SPLIT Distribution in CloseReturn**: Proper 50/50 trophy split implementation

**Status**: 106/109 P1 tests passing (3 failures expected due to changed behavior)

---

## 🎯 REMAINING MVP FEATURES (Phase 2-5)

---

## **PHASE 2: PROGRESSION & PRESSURE SYSTEMS**
*Estimated: 1-2 weeks*

### 2.1 Tax/Tithe System (§6.9 Design Doc)
**Goal**: Create economic pressure forcing player to balance income vs growth

#### Data Model Changes:
```kotlin
// MetaState.kt
data class MetaState(
    // existing fields...
    val taxDueDay: Int,        // Next day player must pay tax
    val taxAmountDue: Int,      // Base tax amount (scales with guild rank)
    val taxPenalty: Int,        // Accumulated penalties for late payment
    val taxMissedCount: Int     // Counter for missed payments (3 strikes = game over)
)
```

#### New Command:
```kotlin
// Commands.kt
data class PayTax(
    val amount: Int,
    override val cmdId: Long
): Command
```

#### New Events:
```kotlin
// Events.kt
data class TaxDue(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amountDue: Int,
    val dueDay: Int  // Deadline
) : Event

data class TaxPaid(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amountPaid: Int,
    val amountDue: Int,
    val isPartialPayment: Boolean
) : Event

data class TaxMissed(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amountDue: Int,
    val penaltyAdded: Int,  // 10% of amountDue
    val missedCount: Int    // Total missed payments
) : Event

data class GuildShutdown(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val reason: String  // "tax_evasion", "too_many_missed_payments"
) : Event
```

#### Implementation Steps:

1. **Initialize Tax State** (GameStateInitialization.kt)
   - taxDueDay = 7 (first tax due on day 7)
   - taxAmountDue = 10 (base amount for rank F)
   - taxPenalty = 0
   - taxMissedCount = 0

2. **Tax Calculation Logic** (new file: core/TaxCalculation.kt)
   ```kotlin
   fun calculateTaxAmount(guildRank: Rank, baseAmount: Int): Int {
       return when (guildRank) {
           Rank.F -> baseAmount
           Rank.E -> baseAmount * 2
           Rank.D -> baseAmount * 4
           Rank.C -> baseAmount * 8
           // etc.
       }
   }
   ```

3. **AdvanceDay Integration** (Reducer.kt - K9 end of day)
   - Check: `if (newDay >= workingState.meta.taxDueDay)`
   - If tax not paid by due day:
     - Emit `TaxMissed` event
     - Increment `taxPenalty` by 10% of taxAmountDue
     - Increment `taxMissedCount`
     - If `taxMissedCount >= 3`: emit `GuildShutdown` → game over
   - Set next `taxDueDay += 7` (weekly cycle)
   - Recalculate `taxAmountDue` based on current guild rank

4. **PayTax Command Handler** (Reducer.kt)
   - Validate: `state.economy.moneyCopper >= cmd.amount`
   - Deduct money: `moneyCopper -= cmd.amount`
   - Reduce penalty/amountDue proportionally
   - If fully paid: reset `taxMissedCount = 0`, clear `taxPenalty`
   - Emit `TaxPaid` event

5. **Command Validation** (CommandValidation.kt)
   ```kotlin
   private fun validatePayTax(state: GameState, cmd: PayTax): ValidationResult {
       if (cmd.amount <= 0) return Rejected(INVALID_ARG, "amount must be > 0")
       if (state.economy.moneyCopper < cmd.amount) {
           return Rejected(INVALID_STATE, "Insufficient money: need ${cmd.amount}, have ${state.economy.moneyCopper}")
       }
       return Valid
   }
   ```

6. **Console Adapter Updates** (Main.kt)
   - Add command: `tax <amount>` or `tax pay <amount>`
   - Update `status` to show:
     - taxDueDay
     - taxAmountDue
     - taxPenalty
     - taxMissedCount
     - days until next tax deadline

7. **Invariants** (InvariantVerifier.kt)
   - TAX_DUE_DAY_POSITIVE: `taxDueDay > 0`
   - TAX_AMOUNT_NON_NEGATIVE: `taxAmountDue >= 0`
   - TAX_PENALTY_NON_NEGATIVE: `taxPenalty >= 0`
   - TAX_MISSED_COUNT_VALID: `taxMissedCount in 0..3`

8. **Serialization Updates**
   - Update `MetaStateDto` with new tax fields
   - Add serialization for new events

**Testing Requirements**:
- P2_TaxSystemTest.kt:
  - Tax due triggers on correct day
  - PayTax reduces amountDue/penalty correctly
  - Missed tax increments penalty by 10%
  - 3 missed taxes triggers GuildShutdown
  - Partial payments work
  - Tax amount scales with guild rank

---

### 2.2 Guild Rank Progression (§6.8 Design Doc)
**Goal**: Unlock more contracts/heroes as player completes missions

#### Data Model Changes:
```kotlin
// GuildState.kt
data class GuildState(
    val reputation: Int,
    val guildRank: Rank,
    val completedContractsTotal: Int,  // NEW: lifetime counter
    val contractsForNextRank: Int      // NEW: threshold for rank up
)
```

#### New Events:
```kotlin
data class GuildRankUp(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val oldRank: Rank,
    val newRank: Rank,
    val completedContracts: Int
) : Event
```

#### Implementation Steps:

1. **Rank Thresholds Configuration** (new file: core/GuildRankProgression.kt)
   ```kotlin
   data class RankThreshold(
       val rank: Rank,
       val contractsRequired: Int,
       val inboxMultiplier: Int,  // N_INBOX = 2 * multiplier
       val heroesMultiplier: Int  // N_HEROES = 2 * multiplier
   )

   val RANK_THRESHOLDS = listOf(
       RankThreshold(Rank.F, 0, 1, 1),      // 2 inbox, 2 heroes
       RankThreshold(Rank.E, 10, 2, 2),     // 4 inbox, 4 heroes
       RankThreshold(Rank.D, 30, 3, 3),     // 6 inbox, 6 heroes
       RankThreshold(Rank.C, 60, 4, 4),     // 8 inbox, 8 heroes
       // etc.
   )

   fun calculateNextRank(completedContracts: Int, currentRank: Rank): Pair<Rank, Int> {
       // Returns (newRank, contractsNeededForNext)
   }
   ```

2. **CloseReturn Integration** (Reducer.kt - handleCloseReturn)
   - After successful close (outcome == SUCCESS):
     ```kotlin
     val newCompleted = state.guild.completedContractsTotal + 1
     val (newRank, contractsForNext) = calculateNextRank(newCompleted, state.guild.guildRank)

     if (newRank != state.guild.guildRank) {
         ctx.emit(GuildRankUp(
             day = newDay,
             revision = workingState.meta.revision,
             cmdId = cmd.cmdId,
             seq = 0L,
             oldRank = state.guild.guildRank,
             newRank = newRank,
             completedContracts = newCompleted
         ))

         // Update guild state
         workingState = workingState.copy(
             guild = workingState.guild.copy(
                 guildRank = newRank,
                 completedContractsTotal = newCompleted,
                 contractsForNextRank = contractsForNext
             )
         )
     }
     ```

3. **AdvanceDay K9.2 Integration** (Reducer.kt - InboxGenerated)
   - Dynamic N_INBOX based on rank:
     ```kotlin
     val rankThreshold = RANK_THRESHOLDS.find { it.rank == workingState.guild.guildRank }
     val N_INBOX = 2 * (rankThreshold?.inboxMultiplier ?: 1)
     ```

4. **AdvanceDay K9.3 Integration** (Reducer.kt - HeroesArrived)
   - Dynamic N_HEROES based on rank:
     ```kotlin
     val rankThreshold = RANK_THRESHOLDS.find { it.rank == workingState.guild.guildRank }
     val N_HEROES = 2 * (rankThreshold?.heroesMultiplier ?: 1)
     ```

5. **Console Adapter Updates**
   - `status` shows:
     - guildRank
     - completedContractsTotal
     - contractsForNextRank
     - progress bar: `[====>    ] 15/30 contracts to rank D`

6. **Invariants**
   - GUILD_RANK_VALID: `guildRank in Rank.entries`
   - COMPLETED_CONTRACTS_NON_NEGATIVE: `completedContractsTotal >= 0`
   - CONTRACTS_FOR_NEXT_RANK_POSITIVE: `contractsForNextRank > 0`

**Testing Requirements**:
- P2_GuildRankProgressionTest.kt:
  - Rank up triggers at correct thresholds
  - N_INBOX/N_HEROES scale with rank
  - completedContractsTotal increments only on SUCCESS
  - Rank progression is monotonic (never downgrades)
  - Tax amount scales with rank after rank up

---

### 2.3 Threat Scaling (§6.7.2 Design Doc)
**Goal**: High stability attracts stronger monsters

#### Implementation Steps:

1. **Threat Level Calculation** (new file: core/ThreatScaling.kt)
   ```kotlin
   fun calculateThreatLevel(stability: Int): Int {
       return when {
           stability >= 80 -> 3  // High threat
           stability >= 60 -> 2  // Medium threat
           stability >= 40 -> 1  // Low threat
           else -> 0             // Minimal threat
       }
   }

   fun calculateBaseDifficulty(threatLevel: Int, rng: Rng): Int {
       val base = 1
       val threatBonus = threatLevel
       val variance = rng.nextInt(2)  // [0, 1]
       return base + threatBonus + variance
   }
   ```

2. **AdvanceDay K9.2 Integration** (Reducer.kt - InboxGenerated)
   - When creating ContractDraft:
     ```kotlin
     val threatLevel = calculateThreatLevel(workingState.region.stability)
     val baseDifficulty = calculateBaseDifficulty(threatLevel, rng)

     newDrafts.add(
         ContractDraft(
             // ...
             baseDifficulty = baseDifficulty,
             // ...
         )
     )
     ```

3. **Visual Feedback** (Console Adapter)
   - When listing inbox, show difficulty indicator:
     - `[EASY]` for difficulty 1-2
     - `[MEDIUM]` for difficulty 3-4
     - `[HARD]` for difficulty 5+

**Testing Requirements**:
- P2_ThreatScalingTest.kt:
  - Stability > 80 → difficulty >= 3
  - Stability < 40 → difficulty 1-2
  - Threat level affects contract difficulty deterministically (given same RNG seed)

---

## **PHASE 3: CONFLICT SYSTEMS**
*Estimated: 1 week*

### 3.1 Partial Outcomes (§6.4.1 Design Doc)
**Goal**: Add ambiguity requiring player judgment

#### Data Model Changes:
```kotlin
// Outcome.kt - ALREADY EXISTS, just add PARTIAL
enum class Outcome {
    SUCCESS,
    PARTIAL,  // NEW: incomplete proof, requires player decision
    FAIL,
    MISSING,
    DEATH
}
```

#### Implementation Steps:

1. **K9.5 Resolve Logic Update** (Reducer.kt - ContractResolved)
   ```kotlin
   // Current: outcome = Outcome.SUCCESS (always)
   // New: probabilistic outcomes based on hero power vs difficulty

   val hero = workingState.heroes.roster.find { it.id == active.heroIds.first() }
   val boardContract = workingState.contracts.board.find { it.id == active.boardContractId }

   val heroPower = calculateHeroPower(hero)
   val contractDifficulty = boardContract?.baseDifficulty ?: 1

   val successChance = ((heroPower - contractDifficulty + 5) * 20).coerceIn(10, 90)
   val roll = rng.nextInt(100)

   val outcome = when {
       roll < successChance -> Outcome.SUCCESS
       roll < successChance + 30 -> Outcome.PARTIAL  // 30% chance of partial
       else -> Outcome.FAIL
   }

   // Trophies based on outcome
   val trophiesCount = when (outcome) {
       Outcome.SUCCESS -> 1 + rng.nextInt(3)  // [1..3]
       Outcome.PARTIAL -> 1                    // Always 1 (incomplete proof)
       Outcome.FAIL -> 0
       else -> 0
   }
   ```

2. **Hero Power Calculation** (new file: core/HeroPower.kt)
   ```kotlin
   fun calculateHeroPower(hero: Hero?): Int {
       if (hero == null) return 1

       val rankPower = hero.rank.ordinal + 1  // F=1, E=2, D=3, etc.
       val classPower = when (hero.klass) {
           HeroClass.WARRIOR -> 2
           HeroClass.MAGE -> 1
           HeroClass.CLERIC -> 1
       }
       val experienceBonus = hero.historyCompleted / 10  // +1 per 10 contracts

       return rankPower + classPower + experienceBonus
   }
   ```

3. **RequiresPlayerClose Logic** (Reducer.kt - ContractResolved)
   ```kotlin
   // Current: requiresPlayerClose = true (always)
   // New: only for PARTIAL outcomes

   val requiresPlayerClose = (outcome == Outcome.PARTIAL)
   ```

4. **CloseReturn Validation Update** (CommandValidation.kt)
   - Currently: validates return exists with requiresPlayerClose=true
   - Add check: if outcome == PARTIAL, player can choose to:
     - Accept partial proof (normal close)
     - Reject proof (new command: RejectReturn)

5. **Optional: RejectReturn Command** (Commands.kt)
   ```kotlin
   data class RejectReturn(
       val activeContractId: Long,
       val reason: String,  // "incomplete_proof", "suspicious", etc.
       override val cmdId: Long
   ): Command
   ```
   - On reject:
     - Contract status → FAILED
     - Hero reputation -= 10
     - No trophies awarded
     - Fee NOT paid
     - Emit `ReturnRejected` event

**Testing Requirements**:
- P2_PartialOutcomeTest.kt:
  - PARTIAL outcome triggers requiresPlayerClose
  - PARTIAL outcome gives exactly 1 trophy
  - Hero power vs difficulty affects success chance
  - SUCCESS outcome has requiresPlayerClose=false (auto-closes)

---

### 3.2 Proof Validation Policy (§6.5.1 Design Doc)
**Goal**: Player chooses speed vs accuracy in validation

#### Data Model Changes:
```kotlin
// GuildState.kt
enum class ProofPolicy {
    FAST,    // No validation, accepts everything (faster, more fraud)
    STRICT   // Full validation, detects fakes (slower, less fraud)
}

data class GuildState(
    // existing fields...
    val proofPolicy: ProofPolicy  // NEW: default FAST
)
```

#### New Command:
```kotlin
// Commands.kt
data class SetProofPolicy(
    val policy: ProofPolicy,
    override val cmdId: Long
): Command
```

#### New Events:
```kotlin
data class ProofPolicyChanged(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val oldPolicy: ProofPolicy,
    val newPolicy: ProofPolicy
) : Event

data class ProofRejected(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val activeContractId: Int,
    val reason: String  // "damaged_proof", "fake_trophy", "suspicious_quality"
) : Event
```

#### Implementation Steps:

1. **CloseReturn Validation Update** (Reducer.kt - handleCloseReturn)
   ```kotlin
   // Before trophy distribution:
   if (state.guild.proofPolicy == ProofPolicy.STRICT) {
       // Check trophy quality
       if (ret.trophiesQuality == Quality.RUINED) {
           ctx.emit(ProofRejected(
               day = state.meta.dayIndex,
               revision = state.meta.revision,
               cmdId = cmd.cmdId,
               seq = 0L,
               activeContractId = cmd.activeContractId.toInt(),
               reason = "damaged_proof"
           ))
           // Do NOT distribute trophies/fee
           // Keep contract in RETURN_READY state
           return state  // Early exit, no changes
       }

       // Check suspected theft
       if (ret.suspectedTheft) {
           ctx.emit(ProofRejected(
               day = state.meta.dayIndex,
               revision = state.meta.revision,
               cmdId = cmd.cmdId,
               seq = 0L,
               activeContractId = cmd.activeContractId.toInt(),
               reason = "suspicious_quantity"
           ))
           // Penalize hero reputation
           // ...
       }
   }
   // FAST policy: skip all checks, accept everything
   ```

2. **SetProofPolicy Handler** (Reducer.kt)
   ```kotlin
   private fun handleSetProofPolicy(
       state: GameState,
       cmd: SetProofPolicy,
       rng: Rng,
       ctx: SeqContext
   ): GameState {
       val oldPolicy = state.guild.proofPolicy

       ctx.emit(ProofPolicyChanged(
           day = state.meta.dayIndex,
           revision = state.meta.revision,
           cmdId = cmd.cmdId,
           seq = 0L,
           oldPolicy = oldPolicy,
           newPolicy = cmd.policy
       ))

       return state.copy(
           guild = state.guild.copy(proofPolicy = cmd.policy)
       )
   }
   ```

3. **Console Adapter Updates** (Main.kt)
   - Add command: `policy fast` or `policy strict`
   - `status` shows current proofPolicy
   - `list returns` shows warning if FAST policy and suspicious returns exist

**Testing Requirements**:
- P2_ProofValidationTest.kt:
  - STRICT policy rejects RUINED trophies
  - STRICT policy detects suspectedTheft=true
  - FAST policy accepts everything
  - SetProofPolicy command works
  - ProofRejected keeps contract in RETURN_READY state

---

## **PHASE 4: CONSOLE UX ENHANCEMENTS**
*Estimated: 3-5 days*

### 4.1 Command Updates

#### Updated Commands:
```
Current:  post <inboxId> <fee> <salvage>
Enhanced: post <inboxId> <fee> <salvage>  (already done in Phase 1)

New Commands:
  tax pay <amount>
  policy <fast|strict>
  reject <activeId> <reason>  (optional, for RejectReturn)
```

#### Enhanced `status` Output:
```
=== GUILD STATUS ===
Day: 15  Revision: 234  RNG Draws: 1234

Economy:
  Money: 150 copper (reserved: 30, available: 120)
  Trophies: 12

Guild:
  Rank: E (15/30 contracts to rank D)
  Reputation: 75
  Proof Policy: STRICT

Region:
  Stability: 68 (MEDIUM threat level)

Tax:
  Next Due: Day 21 (6 days)
  Amount Due: 20 copper
  Penalty: 0 copper
  Missed Payments: 0/3

Contracts:
  Inbox: 4  Board: 2  Active (WIP): 3  Returns Needing Close: 1
```

#### Enhanced `list inbox` Output:
```
=== INBOX (4 contracts) ===
ID  Day  Rank  Fee  Difficulty  Title
1   15   F     5    [EASY]      "Goblin patrol near farms"
2   15   E     10   [MEDIUM]    "Bandit camp clearance"
3   15   F     0    [EASY]      "Lost livestock recovery"
4   15   D     25   [HARD]      "Orc raiding party"
```

#### Enhanced `list returns` Output:
```
=== RETURNS NEEDING CLOSE (2 packets) ===
ActiveID  Day  Outcome   Trophies  Quality   Theft?  Heroes
1001      14   SUCCESS   3         OK        No      [Hero #5, Hero #6]
1002      14   PARTIAL   1         DAMAGED   YES!    [Hero #7]
                                    ^^^^^^^ STRICT policy will reject this
```

### 4.2 Help System Enhancement

```
Commands:
  help                              Show this help
  help <topic>                      Show detailed help for topic

Topics:
  salvage    Explains GUILD/HERO/SPLIT policies
  tax        Tax system and payment
  rank       Guild rank progression
  policy     Proof validation policies
  threat     Threat scaling system

Examples:
  post 1 10 HERO                   Post contract #1 with 10 copper fee, hero gets trophies
  post 2 0 SPLIT                   Post contract #2 with no fee, 50/50 trophy split
  tax pay 20                       Pay 20 copper towards tax debt
  policy strict                    Enable strict proof validation
  sell                             Sell all trophies
  sell 5                           Sell 5 trophies
```

---

## **PHASE 5: BALANCE & VALIDATION**
*Estimated: 1 week*

### 5.1 Telemetry Integration (§13 Design Doc)

#### Event Logging System (new file: core/telemetry/EventLogger.kt)
```kotlin
data class TelemetryRecord(
    val timestamp: Long,
    val eventType: String,
    val eventData: Map<String, Any>
)

class EventLogger {
    private val records = mutableListOf<TelemetryRecord>()

    fun log(event: Event) {
        // Extract key metrics from event
        val data = when (event) {
            is HeroDeclined -> mapOf(
                "heroId" to event.heroId,
                "boardId" to event.boardContractId,
                "reason" to event.reason
            )
            is TrophyTheftSuspected -> mapOf(
                "heroId" to event.heroId,
                "expected" to event.expectedTrophies,
                "reported" to event.reportedTrophies,
                "stolenAmount" to (event.expectedTrophies - event.reportedTrophies)
            )
            // etc.
            else -> emptyMap()
        }

        records.add(TelemetryRecord(
            timestamp = System.currentTimeMillis(),
            eventType = event::class.simpleName ?: "Unknown",
            eventData = data
        ))
    }

    fun export(): String {
        // CSV or JSON export
    }
}
```

#### Integration Points:
- Reducer.kt: log all events after emission
- Console adapter: optional `telemetry` command to dump stats

#### Key Metrics to Track:
- Hero refusal rate by salvage policy
- Theft incidents by greed/honesty/salvage combination
- Average trophies per contract by difficulty
- Tax payment patterns (late/on-time/partial)
- Guild rank progression speed
- Player choices: salvage policy distribution

### 5.2 Balance Tuning Parameters (new file: core/BalanceConfig.kt)

```kotlin
object BalanceConfig {
    // Hero Decision Making
    const val MIN_PROFIT_THRESHOLD = 5          // Minimum profit score to accept contract
    const val RISK_PENALTY_PER_DIFFICULTY = 15  // Penalty for difficulty > comfort zone
    const val GREED_BONUS_DIVISOR = 10          // How much greed affects salvage value

    // Theft Mechanics
    const val THEFT_CHANCE_GUILD_NO_FEE = 100   // % chance if GUILD + fee=0 (use hero.greed directly)
    const val THEFT_AMOUNT_PERCENT = 50         // % of trophies stolen
    const val FEE_THEFT_REDUCTION = 2           // Each copper of fee reduces theft by greed/N

    // Tax System
    const val TAX_INTERVAL_DAYS = 7             // How often tax is due
    const val TAX_BASE_AMOUNT = 10              // Base tax for rank F
    const val TAX_PENALTY_PERCENT = 10          // Penalty % for missed payment
    const val TAX_MAX_MISSED = 3                // Strikes before game over

    // Guild Rank Progression
    val RANK_THRESHOLDS = mapOf(
        Rank.F to 0,
        Rank.E to 10,
        Rank.D to 30,
        Rank.C to 60,
        Rank.B to 100,
        Rank.A to 150,
        Rank.S to 250
    )

    // Threat Scaling
    const val STABILITY_HIGH_THRESHOLD = 80     // Stability for high threat
    const val STABILITY_MEDIUM_THRESHOLD = 60   // Stability for medium threat
    const val THREAT_DIFFICULTY_BONUS = 1       // Bonus difficulty per threat level

    // Combat Resolution
    const val HERO_RANK_POWER_BASE = 1          // Power per rank (F=1, E=2, etc.)
    const val WARRIOR_POWER_BONUS = 2           // Extra power for warrior class
    const val MAGE_POWER_BONUS = 1              // Extra power for mage
    const val EXPERIENCE_POWER_DIVISOR = 10     // +1 power per N completed contracts

    const val SUCCESS_BASE_CHANCE = 50          // Base % success chance
    const val POWER_ADVANTAGE_MULTIPLIER = 20   // Each point of (heroPower - difficulty) = +20%
    const val PARTIAL_OUTCOME_CHANCE = 30       // % chance of PARTIAL if not success
}
```

### 5.3 Golden Replay Tests (core-test/)

#### Multi-Day Scenario Tests:
```kotlin
// P2_GoldenReplayTest.kt
@Test
fun `full MVP loop - 30 day run with all features`() {
    // Seed: fixed for deterministic replay
    val state = initialState(seed = 12345u)
    val rng = Rng(seed = 67890L)

    // Script 30-day gameplay:
    // Day 1-3: Learn mechanics (post contracts, heroes take, resolve, close)
    // Day 4-7: Experiment with salvage policies
    // Day 7: Pay first tax
    // Day 10: Rank up to E
    // Day 14: Encounter theft incident
    // Day 15: Switch to STRICT policy
    // Day 21: Second tax payment
    // Day 25: Rank up to D
    // Day 28: Handle PARTIAL outcome
    // Day 30: Final state validation

    // Validate:
    // - Final money > 0
    // - Guild rank = D
    // - No missed tax payments
    // - Telemetry shows expected theft rate
    // - No invariant violations across 30 days
}
```

### 5.4 Balance Validation Tests

#### P2_BalanceValidationTest.kt:
```kotlin
@Test
fun `salvage policy impact on hero satisfaction`() {
    // Measure: how many contracts are refused by salvage policy
    // Expected: GUILD > SPLIT > HERO (refusal rate)
}

@Test
fun `theft rate by salvage policy`() {
    // Expected: GUILD + fee=0 → high theft
    //           SPLIT → medium theft
    //           HERO → no theft
}

@Test
fun `tax pressure creates meaningful choices`() {
    // Simulate: player who never pays tax vs always pays on time
    // Expected: non-payer hits game over by day 21
    //           payer survives but has less money for growth
}

@Test
fun `guild rank progression speed`() {
    // Expected: F→E in ~10-15 days
    //           E→D in ~20-25 days
}

@Test
fun `threat scaling creates challenge`() {
    // Simulate: high stability (80+) player
    // Expected: contracts have difficulty 3-5
    //           some heroes refuse due to risk
}
```

---

## **SUCCESS CRITERIA FOR MVP COMPLETION**

### Functional Requirements:
- ✅ Heroes make autonomous decisions (refuse unprofitable contracts)
- ✅ Heroes steal trophies under bad conditions
- ✅ Player chooses salvage policy with visible consequences
- ✅ Tax system creates economic pressure
- ✅ Guild rank progression unlocks content
- ✅ Threat scaling creates risk/reward tension
- ✅ Partial outcomes require player judgment
- ✅ Proof validation policy affects fraud rate

### Testing Requirements:
- ✅ All P1 tests passing (70/70)
- ✅ All P2 tests passing (estimated 30-40 new tests)
- ✅ Golden replay tests validate determinism
- ✅ Balance validation tests confirm design intent

### Performance Requirements:
- ✅ 100 days simulated in < 1 second
- ✅ No memory leaks over 1000+ day runs
- ✅ Serialization roundtrip stable

### UX Requirements:
- ✅ Console help system explains all mechanics
- ✅ Status command shows all relevant info
- ✅ Telemetry export available for analysis

---

## **ESTIMATED TIMELINE**

- **Phase 2 (Tax + Rank + Threat)**: 5-7 working days
- **Phase 3 (Partial + Validation)**: 3-4 working days
- **Phase 4 (Console UX)**: 2-3 working days
- **Phase 5 (Balance + Testing)**: 5-7 working days

**Total MVP Completion**: **4-5 weeks** (assuming 1 developer, focused work)

---

## **DEPENDENCIES & RISKS**

### External Dependencies:
- None (pure Kotlin, no new libraries needed)

### Internal Dependencies:
- Phase 3 depends on Phase 2 (need tax pressure to test player decisions)
- Phase 5 depends on Phase 2-4 (can't balance incomplete features)

### Risks:
1. **Balance Tuning**: May require 2-3 iteration cycles to get theft/tax/threat values right
   - Mitigation: Externalize all constants to BalanceConfig.kt for easy tweaking
2. **Test Maintenance**: 3 failing P1 tests need updating with new salvage behavior
   - Mitigation: Batch fix in Phase 5 alongside P2 test creation
3. **Feature Creep**: Design doc has many "post-MVP" ideas that may tempt scope expansion
   - Mitigation: Strict adherence to this roadmap; defer party contracts, alchemy, dungeons

---

## **POST-MVP (Future Phases, Not in M1)**

### Deferred to M2+:
- Party contracts (2-3 heroes per contract)
- Alchemy/crafting (trophy → potion conversion)
- Manual dungeons (player-controlled combat)
- Multiplayer co-op (dual manager mode)
- Advanced events (raids, migrations, anomalies)
- Hero permadeath and recruitment
- Contract types beyond "kill monster"
- Proof forgery by heroes
- Multiple buyers (price competition)
- Production chains (trophy → equipment)

These remain in design doc for future reference but are **explicitly out of scope for MVP**.
