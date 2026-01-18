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

fun serialize(state: GameState): String {
    val dto = toDto(state)
    return json.encodeToString(dto)
}

fun deserialize(jsonString: String): GameState {
    val dto = json.decodeFromString<GameStateDto>(jsonString)
    
    // Validate save version
    if (dto.meta.saveVersion != SUPPORTED_SAVE_VERSION) {
        throw IllegalArgumentException("Unsupported saveVersion: ${dto.meta.saveVersion}")
    }
    
    return fromDto(dto)
}

// --- toDto mappings ---

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
        )
    )
}

fun toDto(guild: GuildState): GuildStateDto {
    return GuildStateDto(
        guildRank = guild.guildRank,
        reputation = guild.reputation
    )
}

fun toDto(region: RegionState): RegionStateDto {
    return RegionStateDto(
        stability = region.stability
    )
}

fun toDto(economy: EconomyState): EconomyStateDto {
    return EconomyStateDto(
        moneyCopper = economy.moneyCopper,
        trophiesStock = economy.trophiesStock
    )
}

fun toDto(contracts: ContractState): ContractStateDto {
    return ContractStateDto(
        inbox = contracts.inbox.map { toDto(it) },
        board = contracts.board.map { toDto(it) },
        active = contracts.active.map { toDto(it) },
        returns = contracts.returns.map { toDto(it) }
    )
}

fun toDto(draft: ContractDraft): ContractDraftDto {
    return ContractDraftDto(
        id = draft.id.value,
        createdDay = draft.createdDay,
        title = draft.title,
        rankSuggested = draft.rankSuggested.name,
        feeOffered = draft.feeOffered,
        baseDifficulty = draft.baseDifficulty,
        proofHint = draft.proofHint
    )
}

fun toDto(board: BoardContract): BoardContractDto {
    return BoardContractDto(
        id = board.id.value,
        postedDay = board.postedDay,
        title = board.title,
        rank = board.rank.name,
        fee = board.fee,
        salvage = board.salvage.name,
        status = board.status.name
    )
}

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

fun toDto(packet: ReturnPacket): ReturnPacketDto {
    return ReturnPacketDto(
        activeContractId = packet.activeContractId.value,
        resolvedDay = packet.resolvedDay,
        outcome = packet.outcome.name,
        trophiesCount = packet.trophiesCount,
        trophiesQuality = packet.trophiesQuality.name,
        reasonTags = packet.reasonTags,
        requiresPlayerClose = packet.requiresPlayerClose
    )
}

fun toDto(heroes: HeroState): HeroStateDto {
    return HeroStateDto(
        roster = heroes.roster.map { toDto(it) }
        // arrivalsToday is NOT serialized
    )
}

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
        )
    )
}

fun fromDto(dto: GuildStateDto): GuildState {
    return GuildState(
        guildRank = dto.guildRank,
        reputation = dto.reputation
    )
}

fun fromDto(dto: RegionStateDto): RegionState {
    return RegionState(
        stability = dto.stability
    )
}

fun fromDto(dto: EconomyStateDto): EconomyState {
    return EconomyState(
        moneyCopper = dto.moneyCopper,
        trophiesStock = dto.trophiesStock
    )
}

fun fromDto(dto: ContractStateDto): ContractState {
    return ContractState(
        inbox = dto.inbox.map { fromDtoDraft(it) },
        board = dto.board.map { fromDtoBoard(it) },
        active = dto.active.map { fromDtoActive(it) },
        returns = dto.returns.map { fromDtoReturn(it) }
    )
}

fun fromDtoDraft(dto: ContractDraftDto): ContractDraft {
    return ContractDraft(
        id = ContractId(dto.id),
        createdDay = dto.createdDay,
        title = dto.title,
        rankSuggested = Rank.valueOf(dto.rankSuggested),
        feeOffered = dto.feeOffered,
        baseDifficulty = dto.baseDifficulty,
        proofHint = dto.proofHint
    )
}

fun fromDtoBoard(dto: BoardContractDto): BoardContract {
    return BoardContract(
        id = ContractId(dto.id),
        postedDay = dto.postedDay,
        title = dto.title,
        rank = Rank.valueOf(dto.rank),
        fee = dto.fee,
        salvage = SalvagePolicy.valueOf(dto.salvage),
        status = BoardStatus.valueOf(dto.status)
    )
}

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

fun fromDtoReturn(dto: ReturnPacketDto): ReturnPacket {
    return ReturnPacket(
        activeContractId = ActiveContractId(dto.activeContractId),
        resolvedDay = dto.resolvedDay,
        outcome = Outcome.valueOf(dto.outcome),
        trophiesCount = dto.trophiesCount,
        trophiesQuality = Quality.valueOf(dto.trophiesQuality),
        reasonTags = dto.reasonTags,
        requiresPlayerClose = dto.requiresPlayerClose
    )
}

fun fromDto(dto: HeroStateDto): HeroState {
    return HeroState(
        roster = dto.roster.map { fromDtoHero(it) },
        arrivalsToday = emptyList() // K11 decision: reset to empty on load
    )
}

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
