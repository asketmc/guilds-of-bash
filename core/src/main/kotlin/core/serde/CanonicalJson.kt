package core.serde

import core.primitives.*
import core.state.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Canonical JSON serialization for GameState.
 * - Compact (no pretty print)
 * - Deterministic (stable field ordering)
 * - Value classes as raw Int
 * - arrivalsToday dropped on save, empty on load
 */

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
}

private const val SUPPORTED_SAVE_VERSION = 1

/**
 * Serialize a [GameState] into canonical JSON.
 *
 * @param state State to serialize.
 * @return Canonical JSON string (deterministic).
 */
fun serialize(state: GameState): String {
    val dto = toDto(state)
    return json.encodeToString(dto)
}

/**
 * Deserialize canonical JSON into a [GameState].
 *
 * @param jsonString Canonical JSON produced by [serialize].
 * @return Deserialized [GameState].
 * @throws IllegalArgumentException if the save version is unsupported.
 */
fun deserialize(jsonString: String): GameState {
    val dto = json.decodeFromString<GameStateDto>(jsonString)

    // Validate save version
    require(dto.meta.saveVersion == SUPPORTED_SAVE_VERSION) {
        "Unsupported saveVersion: ${dto.meta.saveVersion}"
    }

    return fromDto(dto)
}

// --- toDto mappings ---

/**
 * Map domain [GameState] to its serializable DTO.
 *
 * @param state Domain state.
 * @return DTO suitable for serialization.
 */
fun toDto(state: GameState): GameStateDto {
    return GameStateDto(
        meta = toDto(state.meta),
        guild = toDto(state.guild),
        region = toDto(state.region),
        economy = toDto(state.economy),
        contracts = toDto(state.contracts),
        heroes = toDto(state.heroes)
    )
}

/**
 * Map domain [MetaState] to DTO.
 *
 * @param meta Domain meta state.
 */
fun toDto(meta: MetaState): MetaStateDto {
    return MetaStateDto(
        saveVersion = meta.saveVersion,
        seed = meta.seed,
        dayIndex = meta.dayIndex,
        revision = meta.revision,
        ids = IdCountersDto(
            nextContractId = meta.ids.nextContractId,
            nextHeroId = meta.ids.nextHeroId,
            nextActiveContractId = meta.ids.nextActiveContractId
        ),

        taxDueDay = meta.taxDueDay,
        taxAmountDue = meta.taxAmountDue,
        taxPenalty = meta.taxPenalty,
        taxMissedCount = meta.taxMissedCount
    )
}

/**
 * Map domain [GuildState] to DTO.
 *
 * @param guild Domain guild state.
 */
fun toDto(guild: GuildState): GuildStateDto {
    return GuildStateDto(
        guildRank = guild.guildRank,
        reputation = guild.reputation,
        completedContractsTotal = guild.completedContractsTotal,
        contractsForNextRank = guild.contractsForNextRank
    )
}

/**
 * Map domain [RegionState] to DTO.
 *
 * @param region Domain region state.
 */
fun toDto(region: RegionState): RegionStateDto {
    return RegionStateDto(
        stability = region.stability
    )
}

/**
 * Map domain [EconomyState] to DTO.
 *
 * @param economy Domain economy state.
 */
fun toDto(economy: EconomyState): EconomyStateDto {
    return EconomyStateDto(
        moneyCopper = economy.moneyCopper,
        trophiesStock = economy.trophiesStock,
        reservedCopper = economy.reservedCopper
    )
}

/**
 * Map domain [ContractState] to DTO.
 *
 * @param contracts Domain contract state.
 */
fun toDto(contracts: ContractState): ContractStateDto {
    return ContractStateDto(
        inbox = contracts.inbox.map { toDto(it) },
        board = contracts.board.map { toDto(it) },
        archive = contracts.archive.map { toDto(it) },
        active = contracts.active.map { toDto(it) },
        returns = contracts.returns.map { toDto(it) }
    )
}

/**
 * Map domain [ContractDraft] to DTO.
 *
 * @param draft Domain draft.
 */
fun toDto(draft: ContractDraft): ContractDraftDto {
    return ContractDraftDto(
        id = draft.id.value,
        createdDay = draft.createdDay,
        nextAutoResolveDay = draft.nextAutoResolveDay,
        title = draft.title,
        rankSuggested = draft.rankSuggested.name,
        feeOffered = draft.feeOffered,
        salvage = draft.salvage.name,
        baseDifficulty = draft.baseDifficulty,
        proofHint = draft.proofHint,
        clientDeposit = draft.clientDeposit
    )
}

/**
 * Map domain [BoardContract] to DTO.
 *
 * @param board Domain board contract.
 */
fun toDto(board: BoardContract): BoardContractDto {
    return BoardContractDto(
        id = board.id.value,
        postedDay = board.postedDay,
        title = board.title,
        rank = board.rank.name,
        fee = board.fee,
        salvage = board.salvage.name,
        baseDifficulty = board.baseDifficulty,
        status = board.status.name,
        clientDeposit = board.clientDeposit
    )
}

/**
 * Map domain [ActiveContract] to DTO.
 *
 * @param active Domain active contract.
 */
fun toDto(active: ActiveContract): ActiveContractDto {
    return ActiveContractDto(
        id = active.id.value,
        boardContractId = active.boardContractId.value,
        takenDay = active.takenDay,
        daysRemaining = active.daysRemaining,
        heroIds = active.heroIds.map { it.value },
        status = active.status.name
    )
}

/**
 * Map domain [ReturnPacket] to DTO.
 *
 * @param packet Domain return packet.
 */
fun toDto(packet: ReturnPacket): ReturnPacketDto {
    return ReturnPacketDto(
        activeContractId = packet.activeContractId.value,
        boardContractId = packet.boardContractId.value,
        heroIds = packet.heroIds.map { it.value },
        resolvedDay = packet.resolvedDay,
        outcome = packet.outcome.name,
        trophiesCount = packet.trophiesCount,
        trophiesQuality = packet.trophiesQuality.name,
        reasonTags = packet.reasonTags,
        requiresPlayerClose = packet.requiresPlayerClose,
        suspectedTheft = packet.suspectedTheft
    )
}

/**
 * Map domain [HeroState] to DTO.
 *
 * @param heroes Domain hero state.
 */
fun toDto(heroes: HeroState): HeroStateDto {
    return HeroStateDto(
        roster = heroes.roster.map { toDto(it) }
        // arrivalsToday is NOT serialized
    )
}

/**
 * Map domain [Hero] to DTO.
 *
 * @param hero Domain hero.
 */
fun toDto(hero: Hero): HeroDto {
    return HeroDto(
        id = hero.id.value,
        name = hero.name,
        rank = hero.rank.name,
        klass = hero.klass.name,
        traits = TraitsDto(
            greed = hero.traits.greed,
            honesty = hero.traits.honesty,
            courage = hero.traits.courage
        ),
        status = hero.status.name,
        historyCompleted = hero.historyCompleted
    )
}

// --- fromDto mappings ---

/**
 * Map DTO [GameStateDto] back into domain [GameState].
 *
 * @param dto DTO produced by decoding JSON.
 */
fun fromDto(dto: GameStateDto): GameState {
    return GameState(
        meta = fromDtoMeta(dto.meta),
        guild = fromDto(dto.guild),
        region = fromDto(dto.region),
        economy = fromDto(dto.economy),
        contracts = fromDto(dto.contracts),
        heroes = fromDto(dto.heroes)
    )
}

/**
 * Map DTO [MetaStateDto] into domain [MetaState].
 *
 * @param dto Meta DTO.
 */
fun fromDtoMeta(dto: MetaStateDto): MetaState {
    return MetaState(
        saveVersion = dto.saveVersion,
        seed = dto.seed,
        dayIndex = dto.dayIndex,
        revision = dto.revision,
        ids = IdCounters(
            nextContractId = dto.ids.nextContractId,
            nextHeroId = dto.ids.nextHeroId,
            nextActiveContractId = dto.ids.nextActiveContractId
        ),

        taxDueDay = dto.taxDueDay,
        taxAmountDue = dto.taxAmountDue,
        taxPenalty = dto.taxPenalty,
        taxMissedCount = dto.taxMissedCount
    )
}

/**
 * Map DTO [GuildStateDto] into domain [GuildState].
 *
 * @param dto Guild DTO.
 */
fun fromDto(dto: GuildStateDto): GuildState {
    return GuildState(
        guildRank = dto.guildRank,
        reputation = dto.reputation,
        completedContractsTotal = dto.completedContractsTotal,
        contractsForNextRank = dto.contractsForNextRank
    )
}

/**
 * Map DTO [RegionStateDto] into domain [RegionState].
 *
 * @param dto Region DTO.
 */
fun fromDto(dto: RegionStateDto): RegionState {
    return RegionState(
        stability = dto.stability
    )
}

/**
 * Map DTO [EconomyStateDto] into domain [EconomyState].
 *
 * @param dto Economy DTO.
 */
fun fromDto(dto: EconomyStateDto): EconomyState {
    return EconomyState(
        moneyCopper = dto.moneyCopper,
        trophiesStock = dto.trophiesStock,
        reservedCopper = dto.reservedCopper
    )
}

/**
 * Map DTO [ContractStateDto] into domain [ContractState].
 *
 * @param dto Contract state DTO.
 */
fun fromDto(dto: ContractStateDto): ContractState {
    return ContractState(
        inbox = dto.inbox.map { fromDtoDraft(it) },
        board = dto.board.map { fromDtoBoard(it) },
        archive = dto.archive.map { fromDtoBoard(it) },
        active = dto.active.map { fromDtoActive(it) },
        returns = dto.returns.map { fromDtoReturn(it) }
    )
}

/**
 * Map DTO [ContractDraftDto] into domain [ContractDraft].
 *
 * @param dto Draft DTO.
 */
fun fromDtoDraft(dto: ContractDraftDto): ContractDraft {
    return ContractDraft(
        id = ContractId(dto.id),
        createdDay = dto.createdDay,
        nextAutoResolveDay = dto.nextAutoResolveDay,
        title = dto.title,
        rankSuggested = Rank.valueOf(dto.rankSuggested),
        feeOffered = dto.feeOffered,
        salvage = SalvagePolicy.valueOf(dto.salvage),
        baseDifficulty = dto.baseDifficulty,
        proofHint = dto.proofHint,
        clientDeposit = dto.clientDeposit
    )
}

/**
 * Map DTO [BoardContractDto] into domain [BoardContract].
 *
 * @param dto Board DTO.
 */
fun fromDtoBoard(dto: BoardContractDto): BoardContract {
    return BoardContract(
        id = ContractId(dto.id),
        postedDay = dto.postedDay,
        title = dto.title,
        rank = Rank.valueOf(dto.rank),
        fee = dto.fee,
        salvage = SalvagePolicy.valueOf(dto.salvage),
        baseDifficulty = dto.baseDifficulty,
        status = BoardStatus.valueOf(dto.status),
        clientDeposit = dto.clientDeposit
    )
}

/**
 * Map DTO [ActiveContractDto] into domain [ActiveContract].
 *
 * @param dto Active DTO.
 */
fun fromDtoActive(dto: ActiveContractDto): ActiveContract {
    return ActiveContract(
        id = ActiveContractId(dto.id),
        boardContractId = ContractId(dto.boardContractId),
        takenDay = dto.takenDay,
        daysRemaining = dto.daysRemaining,
        heroIds = dto.heroIds.map { HeroId(it) },
        status = ActiveStatus.valueOf(dto.status)
    )
}

/**
 * Map DTO [ReturnPacketDto] into domain [ReturnPacket].
 *
 * @param dto Return DTO.
 */
fun fromDtoReturn(dto: ReturnPacketDto): ReturnPacket {
    return ReturnPacket(
        activeContractId = ActiveContractId(dto.activeContractId),
        boardContractId = ContractId(dto.boardContractId),
        heroIds = dto.heroIds.map { HeroId(it) },
        resolvedDay = dto.resolvedDay,
        outcome = Outcome.valueOf(dto.outcome),
        trophiesCount = dto.trophiesCount,
        trophiesQuality = Quality.valueOf(dto.trophiesQuality),
        reasonTags = dto.reasonTags,
        requiresPlayerClose = dto.requiresPlayerClose,
        suspectedTheft = dto.suspectedTheft
    )
}

/**
 * Map DTO [HeroStateDto] into domain [HeroState].
 *
 * @param dto Hero state DTO.
 */
fun fromDto(dto: HeroStateDto): HeroState {
    return HeroState(
        roster = dto.roster.map { fromDtoHero(it) },
        arrivalsToday = emptyList() // K11 decision: reset to empty on load
    )
}

/**
 * Map DTO [HeroDto] into domain [Hero].
 *
 * @param dto Hero DTO.
 */
fun fromDtoHero(dto: HeroDto): Hero {
    return Hero(
        id = HeroId(dto.id),
        name = dto.name,
        rank = Rank.valueOf(dto.rank),
        klass = HeroClass.valueOf(dto.klass),
        traits = Traits(
            greed = dto.traits.greed,
            honesty = dto.traits.honesty,
            courage = dto.traits.courage
        ),
        status = HeroStatus.valueOf(dto.status),
        historyCompleted = dto.historyCompleted
    )
}
