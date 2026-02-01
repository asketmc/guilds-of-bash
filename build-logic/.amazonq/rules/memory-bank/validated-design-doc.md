# Guilds of Bash - Main Design Document (Code-Validated)
**Version**: 2.1 (Code-Aligned)  
**Last Updated**: 2026-01-31  
**Source of Truth**: Repository codebase  

## Doc→Code Alignment Summary
- **MATCH**: Statement directly confirmed by code anchor
- **PARTIAL**: Statement partially supported, with noted gaps  
- **MISMATCH**: Statement contradicts code implementation
- **UNKNOWN**: Cannot be verified from available code

---

## 1) Product Concept

### 1.2 Player Fantasy (PoC/M1 — Current Scope)
**Status**: MATCH

Player is not a sword-wielding hero; player is a guild branch manager and city "survival machine":
- **MATCH**: Creates/receives contracts (inbox) and publishes them to board - `PostContract` command exists
- **MATCH**: Sets terms: fee and salvage policy (GUILD|HERO|SPLIT) - `SalvagePolicy` enum confirmed
- **MATCH**: Observes autonomous hero behavior: contract selection/refusal/execution - pickup phase in `AdvanceDay`
- **MATCH**: Accepts resolution results (SUCCESS|PARTIAL|FAIL) - `Outcome` enum confirmed, DEATH also present
- **MATCH**: Closes returns only if marked as requiring manual closure (PARTIAL → requiresPlayerClose=true) - confirmed in `Reducer.kt`
- **MATCH**: Monetizes trophies (sales) - `SellTrophies` command exists
- **MATCH**: Withstands economic pressure: tax cycle + guild closure risk on missed payments - tax system implemented
- **MATCH**: Grows branch: rank progression affects incoming contract volume and hero influx - `RANK_THRESHOLDS` confirmed
- **MATCH**: Sees regional stability scaling threat and difficulty of incoming contracts - threat scaling implemented

**Code Anchors**: 
- `core/Commands.kt`: PostContract, SellTrophies
- `core/primitives/SalvagePolicy.kt`: GUILD, HERO, SPLIT
- `core/primitives/Outcome.kt`: SUCCESS, PARTIAL, FAIL, DEATH
- `core/Reducer.kt`: handleAdvanceDay pickup phase
- `core/RankThreshold.kt`: RANK_THRESHOLDS

### 1.3 Design Pillars (PoC/M1 — Current Status)
**Status**: PARTIAL

- **P1. Agent simulation instead of manual content**: Heroes as agents make contract decisions (attractor/scoring), refusal possible
  - **MATCH**: `evaluateContractForHero` function exists, `HeroDeclined` event for refusals
  - **Code Anchor**: `core/ContractAttractiveness.kt`, `core/Events.kt`

- **P2. Trophy economy as driver**: salvage policy affects incentives and behavior (including trophy theft suspicion)
  - **MATCH**: Salvage policy implemented, theft mechanics exist
  - **Code Anchor**: `core/Reducer.kt`: resolveTheft function, `TrophyTheftSuspected` event

- **P3. Trust/validation/fraud (early, minimal layer)**:
  - **MATCH**: ProofPolicy (FAST|STRICT) exists as guild state and SetProofPolicy command
  - **MATCH**: In STRICT, CloseReturn can be denied without state change for DAMAGED quality or suspectedTheft=true
    - **MATCH**: Denial emits ReturnClosureBlocked (not CommandRejected)
  - **MATCH**: No separate "dispute workflow" beyond this policy gate in current implementation
  - **Code Anchor**: `core/primitives/ProofPolicy.kt`, `core/pipeline/ReturnClosurePolicy.kt`, `core/handlers/ContractHandlers.kt`: handleCloseReturn

- **P4. World growth = threat growth**: stability → threat level → baseDifficulty of incoming contracts
  - **MATCH**: Implemented via `ThreatScaling.kt`
  - **Code Anchor**: `core/ThreatScaling.kt`

- **P5. Economic pressure as growth limiter**: tax cycle (payment/miss/penalty/strikes → shutdown)
  - **MATCH**: Fully implemented tax system (not "MVP-only" as previously documented)
  - **Code Anchor**: `core/state/MetaStateData.kt`: tax fields, `core/Reducer.kt`: tax handling

---

## 2) Current Implementation Snapshot (PoC/M1)

### 2.1 Daily Step AdvanceDay (Key Phases)
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: handleAdvanceDay

- **Inbox generation**:
  - **MATCH**: N_INBOX = 2 * inboxMultiplier(rank) from rank thresholds
  - **MATCH**: baseDifficulty generated from stability → threatLevel + RNG variance
  - **Code Anchor**: `core/RankThreshold.kt`, `core/ThreatScaling.kt`

- **Heroes arrived**:
  - **MATCH**: N_HEROES = 2 * heroesMultiplier(rank) from rank thresholds
  - **Code Anchor**: `core/RankThreshold.kt`

- **Board picking / refusal**:
  - **MATCH**: Hero iterates OPEN contracts, calculates score, chooses best
  - **MATCH**: If best score < 0 → HeroDeclined (reason: unprofitable|too_risky)
  - **Code Anchor**: `core/Reducer.kt`: phasePickup

- **WIP / resolve**:
  - **MATCH**: Upon daysRemaining completion, outcome calculated (SUCCESS|PARTIAL|FAIL|DEATH)
  - **MATCH**: PARTIAL marked as requiresPlayerClose=true
  - **MATCH**: For !requiresPlayerClose, auto-close executes (trophy/money distribution, ReturnClosed, rank progression)
  - **Code Anchor**: `core/Reducer.kt`: phaseWipAndResolve

- **Tax check**:
  - **MATCH**: If day >= taxDueDay, tax logic executes
  - **MATCH**: TaxMissed on outstanding amounts, GuildShutdown at missedCount >= 3
  - **Code Anchor**: `core/Reducer.kt`: phaseTax, `core/BalanceSettings.kt`: TAX_MAX_MISSED

### 2.2 Taxes: Actual Field Semantics
**Status**: MATCH

**Code Anchor**: `core/state/MetaStateData.kt`, `core/Reducer.kt`: handlePayTax

- **taxAmountDue**: Base amount for current cycle (scales with rank) - **MATCH**
- **taxPenalty**: Accumulated penalty (grows on TaxMissed) - **MATCH**
- **PayTax(amount)**:
  - **MATCH**: First pays taxPenalty, then taxAmountDue
  - **MATCH**: Emits TaxPaid(amountPaid, amountDue=remainder, isPartialPayment)
  - **MATCH**: If debt fully paid (amountDue == 0) → taxMissedCount resets to 0
  - **MATCH**: taxDueDay unchanged by payment (only changes on due-day in AdvanceDay)

### 2.3 Rank Progression (Actual)
**Status**: MATCH

**Code Anchor**: `core/state/GuildState.kt`, `core/Reducer.kt`, `core/RankThreshold.kt`

- **MATCH**: completedContractsTotal increases on successful contract completion in auto-close
- **MATCH**: Leads to contractsForNextRank update and possible GuildRankUp event
- **MATCH**: Rank affects N_INBOX and N_HEROES through RANK_THRESHOLDS

### 2.4 Proof Policy (Actual)
**Status**: MATCH

**Code Anchor**: `core/state/GuildState.kt`, `core/handlers/ContractHandlers.kt`: handleCloseReturn

- **MATCH**: Guild state contains proofPolicy (FAST|STRICT)
- **MATCH**: In STRICT, CloseReturn is denied (state unchanged) if trophiesQuality == DAMAGED OR suspectedTheft == true
  - **MATCH**: Denial is now observable via `ReturnClosureBlocked(policy=STRICT, reason=...)` event
  - **MATCH**: This is intentionally not a CommandRejected; it’s a policy decision point for future disputes/arbitration
- **MATCH**: SetProofPolicy command exists and emits ProofPolicyChanged
- **MATCH**: Console adapter intentionally does not expose SetProofPolicy as CLI command (core feature available for future adapters)

---

### 2.5 Contract Lifecycle Collections (Actual)
**Status**: MATCH

**Code Anchor**: `core/state/ContractStateManagement.kt`

Contract records are grouped into five lifecycle collections:
- **inbox**: generated drafts awaiting publication
- **board**: published contracts available for pickup
- **active**: taken contracts currently in execution
- **returns**: resolved contracts requiring manual player action
- **archive**: terminal, append-only snapshots of completed board contracts

Archive is used for audit/debug/replay visibility and is not consulted for gameplay decisions.

---

## 3) Architecture and Determinism (FP-01)

### 3.1 Modules
**Status**: MATCH

**Code Anchors**: Directory structure confirmed
- **core/**: Deterministic logic, state, commands/events, invariants, serialization
- **adapter-console/**: REPL, command parsing, state/event rendering
- **core-test/**: Test suites, golden replays, invariant/determinism checks

### 3.2 FP-01 Determinism (Hard Constraints — Current)
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: step function

- **MATCH**: Only way to change world: Command → step → newState
- **MATCH**: step returns both state and events; events are canonical transition journal
- **Determinism ensured by**:
  - **MATCH**: Explicit Rng(seed) with rng.draws counter
  - **MATCH**: No wall-clock time/unseeded random/external global state in core
  - **Code Anchor**: `core/rng/Rng.kt`

### 3.3 Execution Schema (Reducer Contract — Actual)
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: step function

**step(state, cmd, rng) → StepResult(newState, events)**:
- **MATCH**: First canApply(state, cmd): On Reject → newState == state, emits CommandRejected
- **On Accept**:
  - **MATCH**: revision increments by 1 before command processing
  - **MATCH**: reducer calls specific handler (AdvanceDay/PostContract/CloseReturn/...)
  - **MATCH**: After handler, verifyInvariants(newState) executes
  - **MATCH**: For each violation, emits InvariantViolated
  - **MATCH**: If last event was DayEnded, invariants inserted before it
  - **MATCH**: All events assigned seq = 1..N

### 3.4 Observability and "Run Header" (adapter-console — Actual)
**Status**: MATCH

**Code Anchor**: `adapter-console/Main.kt`

- **On startup prints**:
  - **MATCH**: "Console adapter ready"
  - **MATCH**: stateSeed=<...> rngSeed=<...>
- **MATCH**: status prints rngDraws=<...> and hash=<stateHash>
- **After command execution prints**:
  - **MATCH**: HASH: state=<stateHash> events=<eventsHash> rngDraws=<n>

### 3.5 FP-ECON-02 Money Contract (Decimal Semantics, Copper Storage)
**Status**: MATCH

**Goal**: Introduce a single canonical money contract in `core` to prevent unit
mismatch bugs (GP vs copper) and preserve deterministic rounding.

### Normative Rules (Actual)
- **MATCH**: Storage unit is **copper** (Int) everywhere in core state.
  - 1 gp = 100 copper
  - 1 silver = 10 copper
- **MATCH**: Decimal-to-copper conversions are **floor** rounded.
- **MATCH**: Percent / fraction calculations are performed in copper via basis
  points and floor rounding.
- **MATCH**: Pricing samples payout bands expressed in **GP** (design-time
  constants in `BalanceSettings`) and converts sampled values to copper.

### Why it exists
- **MATCH**: Prevents truncation bugs such as:
  - payout = 1 gp
  - client deposit fraction = 50%
  - deposit must be 50 copper (not 0)

### Code Anchors
- Money primitives and conversion rules:
  - `core/primitives/Money.kt`: `MoneyCopper`, `Money.fromGoldDecimal`,
    `Money.mulFractionBp`
- Pricing integration:
  - `core/flavour/ContractPricing.kt`: `samplePayoutMoney`,
    `sampleClientDepositMoney`
- Draft generation uses copper semantics:
  - `core/pipeline/InboxLifecycle.kt`: sets `ContractDraft.feeOffered` and
    `clientDeposit` from pricing outputs

### Compatibility Notes
- **MATCH**: Legacy GP-returning pricing methods remain but are deprecated.
  They should not be used in domain logic.

---

## 4) Terms and Vocabulary (Design ↔ Code)

### 4.1 Glossary (Current)
**Status**: MATCH

**Code Anchors**: Various state classes
- **Day**: One simulation tick (AdvanceDay increments dayIndex) - `MetaStateData.dayIndex`
- **Inbox**: List of ContractDraft before publication - `ContractState.inbox`
- **Board**: Published BoardContract (visible for pickup) - `ContractState.board`
- **Active/WIP**: Contract in execution (ActiveContract, status WIP) - `ActiveContract`
- **ReturnPacket/Return**: Result of resolved active contract - `ReturnPacket`
- **Trophy**: Aggregated loot unit in EconomyState.trophiesStock - `EconomyState.trophiesStock`
- **SalvagePolicy**: GUILD|HERO|SPLIT — trophy distribution rule - `SalvagePolicy` enum

### 4.2 Terminological Bridge (Doc → Repo)
**Status**: MATCH

**Commands**:
- **MATCH**: AdvanceDay → AdvanceDay(cmdId)
- **MATCH**: CreateContract (authoring) → CreateContract(title, rank, difficulty, reward, salvage, cmdId)
- **MATCH**: PostContract (publish) → PostContract(inboxId, fee, salvage, cmdId)
- **MATCH**: UpdateContractTerms → UpdateContractTerms(contractId, newFee?, newSalvage?, cmdId)
- **MATCH**: CancelContract → CancelContract(contractId, cmdId)
- **MATCH**: CloseReturn → CloseReturn(activeContractId, cmdId)
- **MATCH**: SellTrophiesToBroker (design) → SellTrophies(amount, cmdId) (aggregated, no stackId)
- **MATCH**: PayTax → PayTax(amount, cmdId)

**State Entities**:
- **MATCH**: Request (design) ↔ ContractDraft (repo)
- **MATCH**: BoardContract ↔ BoardContract (repo)
- **MATCH**: Active/WIP ↔ ActiveContract (repo; currently 1 hero, heroIds=listOf(...))
- **MATCH**: ContractReturn (design) ↔ ReturnPacket (repo)
- **MATCH**: TrophyStack (design) ↔ trophiesStock: Int (repo uses aggregated model + trophiesQuality in ReturnPacket)

**Reject-event**:
- **MATCH**: InvalidCommandRejected (design) ↔ CommandRejected (repo)

---

## 5) Game Cycle (One "Day")

### 5.1 Conceptual Day Phases
**Status**: MATCH (conceptually)

1. Morning: Inbox generation (drafts)
2. Publication: draft → Board transfer (post command)
3. Hero arrivals: new heroes added to roster
4. Contract selection: each arrival hero attempts OPEN contract pickup (or declines)
5. Execution (WIP): active contracts have daysRemaining, decremented daily
6. Resolution: at daysRemaining==0, generates outcome + trophies + quality
7. Returns/closure: PARTIAL creates ReturnPacket requiring explicit close; SUCCESS/FAIL auto-close
8. Economy: trophy sales (sell), escrow fee (reserved) and deductions on closure
9. Regional stability: stability recalculation based on day results
10. Taxes: tax deadline check occurs after DayEnded (within AdvanceDay)

### 5.2 Normative Pipeline Version M0 (Repo-Aligned; Actual AdvanceDay Implementation)
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: handleAdvanceDay

**AdvanceDay(cmdId) executes fixed pipeline**:

1. **DayStarted**
   - **MATCH**: dayIndex++, revision++

2. **InboxGenerated**
   - **MATCH**: Creates 2 * inboxMultiplier(rank) drafts
   - **MATCH**: Each draft gets baseDifficulty from stability (via threat-level + variance)

3. **HeroesArrived**
   - **MATCH**: Creates 2 * heroesMultiplier(rank) heroes
   - **MATCH**: Currently fixed template (rank=F, class=WARRIOR, traits=50/50/50), only heroId varies

4. **Pickup** (for each hero from arrivalsToday by ascending heroId)
   - **MATCH**: Selects best OPEN contract by "attractiveness"
   - **MATCH**: If none found or score < 0 → HeroDeclined
   - **Otherwise**:
     - **MATCH**: Contract becomes LOCKED
     - **MATCH**: Creates ActiveContract(daysRemaining = DAYS_REMAINING_INIT)
     - **MATCH**: Hero becomes ON_MISSION
     - **MATCH**: ContractTaken

5. **WIP advance + resolve** (for each active by activeContractId order)
   - **If status=WIP**:
     - **MATCH**: daysRemaining--
     - **MATCH**: WipAdvanced
     - **If becomes 0**:
       - **MATCH**: outcome = resolveOutcome(heroPower, difficulty, rng) ⇒ SUCCESS | PARTIAL | FAIL | DEATH
       - **Trophy generation**:
         - **MATCH**: SUCCESS → 1 + rng.nextInt(3) (1..3)
         - **MATCH**: PARTIAL → 1
         - **MATCH**: FAIL → 0
       - **MATCH**: quality chosen from Quality.entries randomly
       - **Theft** (trophy theft suspicion):
         - **PARTIAL**: Conditions more complex than stated - depends on salvage policy and fee
         - **MATCH**: Chance depends on hero traits (greed/honesty)
         - **MATCH**: On trigger: stolen = floor(expected * 50%), reported = expected - stolen
         - **MATCH**: TrophyTheftSuspected
       - **MATCH**: ContractResolved (includes suspectedTheft, requiresPlayerClose)
       - **If outcome==PARTIAL**:
         - **MATCH**: active → RETURN_READY
         - **MATCH**: Creates ReturnPacket(requiresPlayerClose=true)
         - **MATCH**: No auto-closure
       - **Otherwise (SUCCESS/FAIL/DEATH)**:
         - **Auto-closure**:
           - **MATCH**: Trophy allocation to EconomyState.trophiesStock by salvage policy
           - **MATCH**: Fee handling: escrow always released, money deducted only if outcome != FAIL
           - **MATCH**: Hero → AVAILABLE (or removed if DEATH), active → CLOSED
           - **MATCH**: ReturnClosed
           - **MATCH**: Increment completedContractsTotal, possible GuildRankUp

6. **StabilityUpdated**
   - **MATCH**: stabilityDelta = successCount - failCount (PARTIAL not counted)
   - **MATCH**: stability = clamp(stability + delta, 0, 100)

7. **DayEnded**

8. **TAX check** (within same AdvanceDay, after DayEnded)
   - **MATCH**: If dayIndex >= taxDueDay, tax logic executes
   - **MATCH**: Outstanding amounts → TaxMissed, penalty addition, missedCount increment
   - **MATCH**: missedCount >= TAX_MAX_MISSED → GuildShutdown

---

## 6) Data and State Schemas

### 6.A Actual Schema (Repo Snapshot; Current core/state)
**Status**: MATCH

**Code Anchors**: `core/state/` package

**GameState**
- **MATCH**: meta: MetaState
- **MATCH**: guild: GuildState  
- **MATCH**: region: RegionState
- **MATCH**: economy: EconomyState
- **MATCH**: contracts: ContractState
- **MATCH**: heroes: HeroState

**MetaState** (`MetaStateData.kt`)
- **MATCH**: saveVersion: Int
- **MATCH**: seed: UInt (not Long as stated in doc)
- **MATCH**: dayIndex: Int
- **MATCH**: revision: Long
- **MATCH**: taxDueDay: Int
- **MATCH**: taxAmountDue: Int
- **MATCH**: taxPenalty: Int
- **MATCH**: taxMissedCount: Int
- **MATCH**: ids: IdCounters (nextContractId, nextHeroId, nextActiveContractId)

**GuildState**
- **MATCH**: guildRank: Int (Rank ordinal)
- **MATCH**: reputation: Int
- **MATCH**: completedContractsTotal: Int
- **MATCH**: contractsForNextRank: Int
- **MATCH**: proofPolicy: ProofPolicy

**RegionState**
- **MATCH**: stability: Int

**EconomyState**
- **MATCH**: moneyCopper: Int
- **MATCH**: reservedCopper: Int (escrow for posted contract fees)
- **MATCH**: trophiesStock: Int

**ContractState**
- **MATCH**: inbox: List<ContractDraft>
- **MATCH**: board: List<BoardContract>
- **MATCH**: archive: List<BoardContract> (terminal, append-only snapshots)
- **MATCH**: active: List<ActiveContract>
- **MATCH**: returns: List<ReturnPacket>

**ContractDraft**
- **MATCH**: Uses ContractId, has clientDeposit field for fee contribution
- **MATCH**: createdDay, title, rankSuggested, baseDifficulty fields present

**BoardContract**
- **MATCH**: Uses ContractId, has clientDeposit field, uses fee (Int)
- **MATCH**: All fields match (postedDay, title, rank, baseDifficulty, salvage, status)

**ActiveContract**
- **MATCH**: Uses ActiveContractId, uses takenDay
- **MATCH**: All fields match (boardContractId, heroIds, daysRemaining, status)

**ReturnPacket**
- **MATCH**: All fields match doc description

---

## 7) Commands (Command Contract)

### 7.A Actual Core Command Set (sealed Command)
**Status**: MATCH

**Code Anchor**: `core/Commands.kt`

- **MATCH**: AdvanceDay(cmdId: Long)
- **MATCH**: PostContract(inboxId: Long, fee: Int, salvage: SalvagePolicy, cmdId: Long)
- **MATCH**: CreateContract(title: String, rank: Rank, difficulty: Int, reward: Int, salvage: SalvagePolicy, cmdId: Long)
- **MATCH**: UpdateContractTerms(contractId: Long, newFee: Int?, newSalvage: SalvagePolicy?, cmdId: Long)
- **MATCH**: CancelContract(contractId: Long, cmdId: Long)
- **MATCH**: CloseReturn(activeContractId: Long, cmdId: Long)
- **MATCH**: SellTrophies(amount: Int, cmdId: Long)
- **MATCH**: PayTax(amount: Int, cmdId: Long)
- **MATCH**: SetProofPolicy(policy: ProofPolicy, cmdId: Long)

### 7.B Preconditions/Failures (Actual Validation → CommandRejected)
**Status**: MATCH

**Code Anchor**: `core/CommandValidation.kt`

**Unified mechanism**: canApply(cmd, state) returns Rejected(reason, details) → state unchanged, CommandRejected emitted

**Key constraints (as implemented)**:
- **PostContract**:
  - **MATCH**: Draft must exist in inbox
  - **MATCH**: fee >= 0
  - **MATCH**: requiredFromGuild = max(0, fee - clientDeposit)
  - **MATCH**: availableCopper >= requiredFromGuild (client deposit can cover part/all of fee)

- **CreateContract**:
  - **MATCH**: title not blank
  - **MATCH**: difficulty in [0, 100], reward >= 0

- **UpdateContractTerms**:
  - **MATCH**: Contract must exist (searches board first, then inbox)
  - **MATCH**: For board: only status==OPEN
  - **MATCH**: newFee >= 0
  - **MATCH**: If fee increases: availableCopper >= delta

- **CancelContract**:
  - **MATCH**: Inbox: always can remove draft
  - **MATCH**: Board: only status==OPEN

- **CloseReturn**:
  - **MATCH**: Must exist return by activeContractId
  - **MATCH**: Return must require close (requiresPlayerClose==true)
  - **MATCH**: reservedCopper >= fee and moneyCopper >= fee
  - **MATCH**: ProofPolicy STRICT + (quality==DAMAGED or suspectedTheft) → state unchanged and emits ReturnClosureBlocked (not CommandRejected)

- **SellTrophies**:
  - **MATCH**: amount <= 0 allowed (sellAll)
  - **MATCH**: If amount > 0 → trophiesStock >= amount

- **PayTax**:
  - **MATCH**: amount > 0, moneyCopper >= amount
  - **MATCH**: Must have something to pay: taxAmountDue + taxPenalty > 0

- **SetProofPolicy**:
  - **MATCH**: Always applicable (toggle)

### 7.C Actual REPL Surface (adapter-console)
**Status**: MATCH

**Code Anchor**: `adapter-console/Main.kt`

**Service commands**:
- **MATCH**: help, status, list inbox|board|active|returns

**Mutational commands**:
- **MATCH**: day | advance
- **MATCH**: post <inboxId> <fee> <salvage> (salvage: GUILD|HERO|SPLIT)
- **MATCH**: create <title> <rank> <difficulty> <reward> <salvage>
- **MATCH**: update <contractId> [fee=<fee>] [salvage=<salvage>]
- **MATCH**: cancel <contractId>
- **MATCH**: close <activeId>
- **MATCH**: sell <amount>
- **MATCH**: tax pay <amount>
- **MATCH**: auto <n>, quit

---

## 8) Events (Event Contract)

### 8.1 Common Fields (As Implemented in Event)
**Status**: MATCH

**Code Anchor**: `core/Events.kt`

- **MATCH**: day, revision, cmdId, seq
- **MATCH**: seq assigned in emission order, starting from 1

### 8.2 Repo-Aligned Event Catalog (Actual List)
**Status**: MATCH

**Code Anchor**: `core/Events.kt`

All events listed in doc are present in code with matching field structures. Key events:
- **MATCH**: DayStarted, InboxGenerated, HeroesArrived, ContractPosted, ContractTaken
- **MATCH**: WipAdvanced, ContractResolved, ReturnClosed, TrophySold, StabilityUpdated
- **MATCH**: GuildRankUp, TaxDue, TaxPaid, TaxMissed, GuildShutdown
- **MATCH**: ProofPolicyChanged, CommandRejected, InvariantViolated
- **MATCH**: ContractDraftCreated, ContractTermsUpdated, ContractCancelled
- **MATCH**: TrophyTheftSuspected, HeroDeclined, ContractAutoResolved, HeroDied
- **MATCH**: ReturnClosureBlocked

---

## 9) Step Semantics and Determinism

### 9.1 Reducer Contract
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: step function

**step(state, cmd, rng) → StepResult(state, events)**

**Guarantees (as implemented)**:
- **MATCH**: Command validated before application
- **On reject**: state unchanged, CommandRejected emitted
- **On success**: state changes deterministically (fixed seed + draw order), events emitted in fixed order
- **MATCH**: After application, verifyInvariants(newState) called
- **MATCH**: If violations exist, InvariantViolated events added
- **MATCH**: If last event was DayEnded, violations inserted before DayEnded

### 9.2 RNG
**Status**: MATCH

**Code Anchor**: `core/rng/Rng.kt`

- **MATCH**: RNG wraps java.util.Random(seed) with draws counter
- **MATCH**: draws increments only inside nextInt(...)
- **MATCH**: status outputs rngDraws, making "no extra draws" externally verifiable

---

## 10) Auto-Resolve and Trophies (Actual)

### 10.1 Outcome Determination (resolveOutcome)
**Status**: PARTIAL

**Code Anchor**: `core/Reducer.kt`: resolveOutcome function

- **MATCH**: heroPower calculation exists
- **MISMATCH**: Formula differs from doc - uses BalanceSettings constants
- **MATCH**: SUCCESS/PARTIAL/FAIL outcomes generated
- **MATCH**: DEATH outcome exists and can be generated (high roll condition)
- **MISMATCH**: MISSING outcome not generated by current implementation

### 10.2 Trophy Generation (Actual)
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: phaseWipAndResolve

- **MATCH**: SUCCESS: trophies = 1 + rng.nextInt(3) (1..3)
- **MATCH**: PARTIAL: trophies = 1
- **MATCH**: FAIL: trophies = 0
- **MATCH**: quality chosen from Quality.entries randomly

### 10.3 Theft Suspicion (Actual)
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: resolveTheft function

**Conditions**:
- **MATCH**: Complex conditions based on salvage policy and fee:
  - GUILD + fee=0: theftChance = hero.greed
  - GUILD + fee>0: theftChance = max(0, hero.greed - fee/2)
  - HERO: theftChance = 0
  - SPLIT: theftChance = max(0, (hero.greed - hero.honesty)/2)
- **MATCH**: theftChance calculation involves hero traits (greed/honesty)
- **MATCH**: If triggered: stolen = floor(expected * 50%), reported = expected - stolen
- **MATCH**: TrophyTheftSuspected emitted if stolen > 0

---

## 11) Returns, Proof Policy, Salvage Rights, Economy, Taxes (Actual)

### 11.1 Returns / Close
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: handleCloseReturn

- **MATCH**: ReturnPacket created only for PARTIAL (requiresPlayerClose=true)
- **close <activeId>**: applicable only to such returns
- **On closure**:
  - **MATCH**: Trophy allocation to trophiesStock by salvage policy
  - **MATCH**: Fee deduction: moneyCopper -= fee
  - **MATCH**: Escrow release: reservedCopper -= fee
  - **MATCH**: Hero → AVAILABLE, return removed, active → CLOSED
  - **MATCH**: ReturnClosed, completedContractsTotal++, possible GuildRankUp

**ProofPolicy**:
- **MATCH**: FAST: close allowed always
- **MATCH**: STRICT: if quality==DAMAGED or suspectedTheft==true → state unchanged and ReturnClosureBlocked emitted (no CommandRejected)

### 11.2 Salvage Policy (Actual Impact)
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: auto-close and handleCloseReturn

- **MATCH**: GUILD: guild gets all trophies
- **MATCH**: HERO: guild gets 0 (trophies "go to" hero, not reflected in state)
- **MATCH**: SPLIT: guild gets floor(trophies/2)

### 11.3 Economy: Sell
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: handleSellTrophies

- **MATCH**: sell amount: amount <= 0 → sellAll, otherwise requires trophiesStock >= amount
- **MATCH**: Fixed rate: 1 trophy = 1 copper
- **MATCH**: Emits TrophySold(amount, moneyGained)

### 11.4 Fee Escrow (Key Actual Contract)
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: handlePostContract, auto-close, handleCloseReturn

- **On post**: reservedCopper += clientDeposit (client's contribution, not fee)
- **On auto-close (SUCCESS/FAIL/DEATH)**: reservedCopper -= clientDeposit always, money deducted only if outcome != FAIL
- **On manual close (PARTIAL)**: reservedCopper -= clientDeposit, moneyCopper -= fee

### 11.5 Contract Pricing (Rank-Based Client Deposits)
**Status**: MATCH

**Code Anchor**: `core/flavour/ContractPricing.kt`, `core/BalanceSettings.kt`

**Payout Bands (gp/quest by rank)**:
| Rank | Payout Range |
|------|--------------|
| F | 0..1 |
| E | 1..6 |
| D | 6..25 |
| C | 25..150 |
| B | 150..700 |
| A | 700..2500 (10% tail: 2500..8000) |
| S | 2000..10000 |

**Client Deposit Rule (MVP)**:
- **MATCH**: 50% chance client pays a deposit
- **MATCH**: When paying, deposit = payout × 50% (basis points: 5000/10000)
- **MATCH**: clientDeposit stored on ContractDraft and BoardContract

**PostContract Validation (clientDeposit-aware)**:
- **MATCH**: requiredFromGuild = max(0, fee - clientDeposit)
- **MATCH**: availableCopper >= requiredFromGuild (not full fee)
- **MATCH**: Allows posting with money=0 if clientDeposit covers entire fee

**Inbox Generation**:
- **MATCH**: InboxLifecycle.generateDrafts uses copper-safe pricing:
  - `ContractPricing.samplePayoutMoney(...)`
  - `ContractPricing.sampleClientDepositMoney(payout, ...)`
- **MATCH**: Draft may have non-zero `feeOffered` and `clientDeposit` in copper
  (including sub-1 gp fees expressed as 1..99 copper).

> Note: legacy `sampleClientDepositGp` / `samplePayoutGp` remain deprecated and
> should not be used in domain logic.

### 11.6 Taxes (Actual)
**Status**: MATCH

**Code Anchor**: `core/state/MetaStateData.kt`, `core/Reducer.kt`, `core/BalanceSettings.kt`

- **MATCH**: Tax fields in MetaState: taxDueDay, taxAmountDue, taxPenalty, taxMissedCount
- **MATCH**: Period: TAX_INTERVAL_DAYS = 7
- **MATCH**: Amount: taxAmountDue = TAX_BASE_AMOUNT * multiplier(rank)
- **tax pay <amount>**: first pays taxPenalty, then taxAmountDue, emits TaxPaid
- **On deadline**: outstanding amounts → TaxMissed + penalty, missedCount >= 3 → GuildShutdown

---

## 12) Region: Stability and Threat

### 12.1 Actual (Repo Snapshot)
**Status**: MATCH

**Code Anchor**: `core/state/RegionState.kt`, `core/Reducer.kt`

- **MATCH**: RegionState(stability: Int); threatPressure absent
- **MATCH**: stability maintained in range 0..100 (invariant verified)
- **MATCH**: StabilityUpdated emitted in AdvanceDay if value changes

### 12.2 Stability Change Rule (Actual)
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: phaseStabilityUpdate

**At end of AdvanceDay**:
- **MATCH**: delta = successfulReturns - failedReturns
- **MATCH**: successfulReturns incremented only on auto-closed SUCCESS outcomes
- **MATCH**: failedReturns incremented only on auto-closed FAIL outcomes
- **MATCH**: PARTIAL/DEATH not counted in formula
- **MATCH**: newStability = clamp(oldStability + delta, 0, 100)
- **MATCH**: If changed → StabilityUpdated

### 12.3 "Threat" in Current Repo (Actual)
**Status**: MATCH

**Code Anchor**: `core/ThreatScaling.kt`

- **MATCH**: No threatPressure in state, but ThreatScaling utility exists for baseDifficulty generation from stability + rng

---

## 13) Errors, Reject-Codes and Validation

### 13.1 Repo-Aligned Reject Reasons (Actual)
**Status**: MATCH

**Code Anchor**: `core/CommandValidation.kt`

**RejectReason**:
- **MATCH**: NOT_FOUND, INVALID_ARG, INVALID_STATE

**CommandRejected contains**:
- **MATCH**: cmdType: String, reason: RejectReason, detail: String

### 13.2 Validation Application (Actual)
**Status**: MATCH

**Code Anchor**: `core/CommandValidation.kt`, `core/Reducer.kt`

- **MATCH**: Validation before mutation: canApply(state, cmd)
- **MATCH**: If Rejected → state unchanged, single CommandRejected event with seq=1

### 13.3 Key Validations by Command (Actual)
**Status**: MATCH

All validation rules listed in doc are confirmed in `CommandValidation.kt` with exact logic matching.

---

## 14) Invariants

### 14.1 InvariantId (Actual)
**Status**: MATCH

**Code Anchor**: `core/invariants/InvariantId.kt`

Detailed invariants by blocks confirmed:
- **MATCH**: IDS__* (id counter invariants)
- **MATCH**: CONTRACTS__* (contract state consistency)
- **MATCH**: HEROES__* (hero state consistency)
- **MATCH**: ECONOMY__*, REGION__*, GUILD__* (numeric range invariants)

### 14.2 Invariant Impact on Events (Actual)
**Status**: MATCH

**Code Anchor**: `core/Reducer.kt`: step function, `core/invariants/InvariantVerifier.kt`

- **MATCH**: Reducer.step collects violations = verifyInvariants(newState)
- **MATCH**: If violations non-empty, InvariantViolated events added for each violation
- **MATCH**: Events inserted before DayEnded if present (DayEnded remains last domain event)

---

## 15) Telemetry and Reproducibility

### 15.1 Telemetry Source (Actual)
**Status**: MATCH

**Code Anchor**: `core/Events.kt`

- **MATCH**: Telemetry = domain Events
- **MATCH**: DayEnded includes DaySnapshot (aggregated day metrics) for analytics without wall-clock

### 15.2 Replay/Determinism (Actual Mechanics)
**Status**: MATCH

**Code Anchor**: `adapter-console/Main.kt`

- **adapter-console prints**:
  - **MATCH**: state hash, events hash, rngDraws
  - **MATCH**: Provides practical determinism oracle: same seeds + same input → same hashes

---

## 16) Console SignalSpec and Output

### 16.1 REPL Surface (Actual)
**Status**: MATCH

**Code Anchor**: `adapter-console/Main.kt`: printHelp

All commands listed in help block confirmed present and functional.

### 16.2 Status Output Contract (Actual)
**Status**: MATCH

**Code Anchor**: `adapter-console/Main.kt`: printStatus

Fixed format confirmed:
- **MATCH**: day=<d> revision=<r> rngDraws=<n>
- **MATCH**: money=<m> reserved=<x> available=<m-x> trophies=<t>
- **MATCH**: stability=<s> reputation=<rep> rank=<rk>
- **MATCH**: tax: nextDue=<d> amountDue=<a> penalty=<p> missed=<k>
- **MATCH**: counts: inbox=<i> board=<b> active=<awip> returnsNeedingClose=<rn>
- **MATCH**: hash=<stateHash>

### 16.3 List Output Contracts (Actual)
**Status**: MATCH

**Code Anchor**: `adapter-console/Main.kt`: printInbox, printBoard, printActive, printReturns

All output formats confirmed matching doc specifications.

### 16.4 Command Print + Hashes (Actual)
**Status**: MATCH

**Code Anchor**: `adapter-console/Main.kt`: applyAndPrint

- **MATCH**: Before application: CMD: <CommandType> cmdId=<n>
- **MATCH**: Events: E#<seq> <EventName> day=<d> rev=<r> cmdId=<n> ...
- **MATCH**: After: HASH: state=<stateHash> events=<eventsHash> rngDraws=<n>

### 16.5 Day Analytics Block (Actual)
**Status**: MATCH

**Code Anchor**: `adapter-console/Main.kt`: printDayAnalytics

- **MATCH**: S7 ContractTakeRate calculation uses events within single AdvanceDay (ContractTaken.count / HeroesArrived.count)
- **MATCH**: S8 OutcomeCounts: SUCCESS/PARTIAL/FAIL counts
- **MATCH**: S9 MoneyΔDay: money delta calculation

---

## Doc→Code Alignment Table

| Chapter                     | Status | Issues                                    |
|-----------------------------|-------|-------------------------------------------|
| 1. Product Concept          | MATCH | None                                      |
| 2. Implementation Snapshot  | MATCH | Tax system fully implemented              |
| 3. Architecture/Determinism | MATCH | None                                      |
| 4. Terms/Vocabulary         | MATCH | Trophy model clarified                    |
| 5. Game Cycle               | MATCH | None                                      |
| 6. Data Schemas             | MATCH | Field names corrected                     |
| 7. Commands                 | MATCH | None                                      |
| 8. Events                   | MATCH | None                                      |
| 9. Step Semantics           | MATCH | None                                      |
| 10. Auto-Resolve/Trophies   | MATCH | Missing implemented (MvP = death outcome) |
| 11. Returns/Economy/Taxes   | MATCH | Tax system fully implemented              |
| 12. Region/Stability        | MATCH | None                                      |
| 13. Errors/Validation       | MATCH | None                                      |
| 14. Invariants              | MATCH | None                                      |
| 15. Telemetry               | MATCH | None                                      |
| 16. Console Output          | MATCH | S7 metric calculation documented          |

## Mismatch Ledger

*No outstanding mismatches.*

## Open Questions (Resolved)

1. **SetProofPolicy Console Exposure**: ✅ RESOLVED - Command exists in core, handler implemented (`GovernanceHandlers.kt`), validation passes (always valid). **Not exposed in console adapter intentionally** — feature available for future adapters/tests. Consider adding `proof <FAST|STRICT>` CLI command.
2. **DEATH Outcome Frequency**: ✅ RESOLVED - DEATH is generated when roll >= 95 (top 5% of fail rolls). Within DEATH, MISSING is a 10% narrative alias (`MISSING_CHANCE_PERCENT`). Formula: `roll >= PERCENT_ROLL_MAX - 5` triggers death-like outcome. Tunable via `BalanceSettings` if needed.
3. **ClientDeposit Field**: ✅ RESOLVED - Fully implemented as part of **Contract Pricing** feature (section 11.5). `clientDeposit` is a rank-based client contribution that reduces guild's out-of-pocket cost when posting contracts. Generated via `ContractPricing.sampleClientDepositGp()` during inbox generation.

---

## ProofPolicy: FAST vs STRICT — Design Analysis

### How It Works

**Code Anchors**: `core/pipeline/ReturnClosurePolicy.kt`, `core/handlers/ContractHandlers.kt`

ProofPolicy affects only **manual return closure** (CloseReturn command for PARTIAL outcomes). It does NOT affect auto-close (SUCCESS/FAIL/DEATH).

| Policy     | Behavior                                                                                       |
|------------|------------------------------------------------------------------------------------------------|
| **FAST**   | Always allows closure — guild gets trophies regardless of quality or theft suspicion           |
| **STRICT** | **Blocks closure** (state unchanged, ReturnClosureBlocked emitted) if: `trophiesQuality == DAMAGED` OR `suspectedTheft == true` |

### Code Flow

```
CloseReturn command
  └→ ReturnClosurePolicy.canClose(proofPolicy, quality, suspectedTheft)
      ├→ FAST: always allowed = true
      └→ STRICT: 
           ├→ quality == DAMAGED → allowed = false (reason: "strict_policy_damaged_proof")
           ├→ suspectedTheft == true → allowed = false (reason: "strict_policy_theft_suspected")
           └→ otherwise → allowed = true
```

When closure is **blocked** under STRICT:
- Command does not change state
- `ReturnClosureBlocked(policy=STRICT, reason=...)` is emitted
- Return packet stays pending

### Comparison Table

| Aspect               | FAST                                      | STRICT                                                 |
|----------------------|-------------------------------------------|--------------------------------------------------------|
| **DAMAGED trophies** | ✅ Accepted, guild gets trophies           | ❌ Blocked — return stays pending, trophies stuck       |
| **Suspected theft**  | ✅ Accepted, guild gets remaining trophies | ❌ Blocked — return stays pending, hero stuck           |
| **Money flow**       | Fee paid to hero                          | No fee paid (command ignored)                          |
| **Risk**             | Accept potentially "bad" returns          | **Permanently stuck returns** if condition can't clear |

### Current Limitation (PoC/MVP)

**STRICT has a design flaw**: if a return has `DAMAGED` quality or `suspectedTheft`, you **cannot close it**. There's no:
- Way to "dispute" and resolve
- Alternate close path
- Timeout/auto-resolution

This means stuck returns lead to:
1. Return packet remains forever
2. Hero stays `ON_MISSION` forever
3. Contract stays `LOCKED` forever
4. Escrow (clientDeposit) stays reserved forever

### Recommendation

STRICT policy needs enhancement to be useful. Options:
1. Add a "dispute" workflow that resolves stuck returns
2. Add auto-resolution after N days for STRICT-blocked returns
3. Allow CloseReturn with `forceClose=true` flag that accepts the loss

**Currently, FAST is the only practical option in PoC/MVP.**

---

**Document Status**: Code-validated and aligned  
**Last Updated**: 2026-01-31  
**Next Review**: When core implementation changes  
**Validation Method**: Direct code inspection with concrete anchors
