package com.franklinharper.battlezone

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class RecordedGame(
    val version: Int = 2,
    val gameMode: GameMode,
    val humanPlayerId: Int,
    val initialSnapshot: RecordedSnapshot? = null,
    val events: List<RecordedEvent> = emptyList(),
    val snapshots: List<RecordedSnapshot> = emptyList()
)

@Serializable
sealed class RecordedEvent {
    @Serializable
    @SerialName("attack")
    data class Attack(
        val fromTerritoryId: Int,
        val toTerritoryId: Int,
        val result: RecordedCombatResult
    ) : RecordedEvent()

    @Serializable
    @SerialName("skip")
    data class Skip(val playerId: Int) : RecordedEvent()

    @Serializable
    @SerialName("reinforcement")
    data class Reinforcement(
        val players: List<RecordedReinforcementResult>
    ) : RecordedEvent()
}

@Serializable
data class RecordedReinforcementResult(
    val playerId: Int,
    val territoryIncrements: List<Int>,
    val reserveArmies: Int
)

@Serializable
data class RecordedSnapshot(
    val gameState: RecordedGameState,
    val uiState: RecordedGameUiState
)

@Serializable
data class RecordedGameState(
    val map: RecordedGameMap,
    val players: List<RecordedPlayerState>,
    val currentPlayerIndex: Int,
    val gamePhase: GamePhase,
    val eliminatedPlayers: List<Int>,
    val skipTracker: List<Int>,
    val winner: Int?
)

@Serializable
data class RecordedGameMap(
    val gridWidth: Int,
    val gridHeight: Int,
    val maxTerritories: Int,
    val cells: List<Int>,
    val cellNeighbors: List<RecordedCellNeighbors>,
    val territories: List<RecordedTerritory>,
    val playerCount: Int,
    val seed: Long?
)

@Serializable
data class RecordedCellNeighbors(
    val directions: List<Int>
)

@Serializable
data class RecordedTerritory(
    val id: Int,
    val size: Int,
    val centerPos: Int,
    val owner: Int,
    val armyCount: Int,
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int,
    val centerX: Int,
    val centerY: Int,
    val borderCells: List<Int>,
    val borderDirections: List<Int>,
    val adjacentTerritories: List<Boolean>
)

@Serializable
data class RecordedPlayerState(
    val territoryCount: Int,
    val largestConnectedSize: Int,
    val totalArmies: Int,
    val reserveArmies: Int
)

@Serializable
data class RecordedGameUiState(
    val currentBotDecision: RecordedBotDecision? = null,
    val playerCombatResults: List<RecordedCombatEntry> = emptyList(),
    val skippedPlayers: List<Int> = emptyList(),
    val message: String? = null,
    val isProcessing: Boolean = false,
    val selectedTerritoryId: Int? = null,
    val errorMessage: String? = null,
    val attackArrows: List<RecordedAttackArrow> = emptyList()
)

@Serializable
data class RecordedCombatEntry(
    val playerId: Int,
    val result: RecordedCombatResult
)

@Serializable
data class RecordedCombatResult(
    val attackerPlayerId: Int,
    val defenderPlayerId: Int,
    val attackerRoll: List<Int>,
    val defenderRoll: List<Int>,
    val attackerTotal: Int,
    val defenderTotal: Int,
    val attackerWins: Boolean
)

@Serializable
data class RecordedAttackArrow(
    val fromTerritoryId: Int,
    val toTerritoryId: Int,
    val attackSucceeded: Boolean
)

@Serializable
data class RecordedBotDecision(
    val type: RecordedBotDecisionType,
    val fromTerritoryId: Int? = null,
    val toTerritoryId: Int? = null
)

@Serializable
enum class RecordedBotDecisionType {
    ATTACK,
    SKIP
}

object RecordingSerializer {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun encode(recording: RecordedGame): String = json.encodeToString(recording)

    fun decode(jsonString: String): RecordedGame = json.decodeFromString(jsonString)
}

fun GameSnapshot.toRecordedSnapshot(): RecordedSnapshot = RecordedSnapshot(
    gameState = gameState.toRecordedGameState(),
    uiState = uiState.toRecordedGameUiState()
)

fun RecordedSnapshot.toGameSnapshot(): GameSnapshot = GameSnapshot(
    gameState = gameState.toGameState(),
    uiState = uiState.toGameUiState()
)

private fun GameState.toRecordedGameState(): RecordedGameState = RecordedGameState(
    map = map.toRecordedGameMap(),
    players = players.map { player ->
        RecordedPlayerState(
            territoryCount = player.territoryCount,
            largestConnectedSize = player.largestConnectedSize,
            totalArmies = player.totalArmies,
            reserveArmies = player.reserveArmies
        )
    },
    currentPlayerIndex = currentPlayerIndex,
    gamePhase = gamePhase,
    eliminatedPlayers = eliminatedPlayers.toList(),
    skipTracker = skipTracker.toList(),
    winner = winner
)

private fun RecordedGameState.toGameState(): GameState = GameState(
    map = map.toGameMap(),
    players = players.map { player ->
        PlayerState(
            territoryCount = player.territoryCount,
            largestConnectedSize = player.largestConnectedSize,
            totalArmies = player.totalArmies,
            reserveArmies = player.reserveArmies
        )
    }.toTypedArray(),
    currentPlayerIndex = currentPlayerIndex,
    gamePhase = gamePhase,
    eliminatedPlayers = eliminatedPlayers.toSet(),
    skipTracker = skipTracker.toSet(),
    winner = winner
)

private fun GameMap.toRecordedGameMap(): RecordedGameMap = RecordedGameMap(
    gridWidth = gridWidth,
    gridHeight = gridHeight,
    maxTerritories = maxTerritories,
    cells = cells.toList(),
    cellNeighbors = cellNeighbors.map { neighbor ->
        RecordedCellNeighbors(neighbor.directions.toList())
    },
    territories = territories.map { territory ->
        RecordedTerritory(
            id = territory.id,
            size = territory.size,
            centerPos = territory.centerPos,
            owner = territory.owner,
            armyCount = territory.armyCount,
            left = territory.left,
            right = territory.right,
            top = territory.top,
            bottom = territory.bottom,
            centerX = territory.centerX,
            centerY = territory.centerY,
            borderCells = territory.borderCells.toList(),
            borderDirections = territory.borderDirections.toList(),
            adjacentTerritories = territory.adjacentTerritories.toList()
        )
    },
    playerCount = playerCount,
    seed = seed
)

private fun RecordedGameMap.toGameMap(): GameMap = GameMap(
    gridWidth = gridWidth,
    gridHeight = gridHeight,
    maxTerritories = maxTerritories,
    cells = cells.toIntArray(),
    cellNeighbors = cellNeighbors.map { neighbor ->
        CellNeighbors(neighbor.directions.toIntArray())
    }.toTypedArray(),
    territories = territories.map { territory ->
        Territory(
            id = territory.id,
            size = territory.size,
            centerPos = territory.centerPos,
            owner = territory.owner,
            armyCount = territory.armyCount,
            left = territory.left,
            right = territory.right,
            top = territory.top,
            bottom = territory.bottom,
            centerX = territory.centerX,
            centerY = territory.centerY,
            borderCells = territory.borderCells.toIntArray(),
            borderDirections = territory.borderDirections.toIntArray(),
            adjacentTerritories = territory.adjacentTerritories.toBooleanArray()
        )
    }.toTypedArray(),
    playerCount = playerCount,
    seed = seed,
    gameRandom = GameRandom(seed)
)

private fun GameUiState.toRecordedGameUiState(): RecordedGameUiState = RecordedGameUiState(
    currentBotDecision = currentBotDecision?.toRecordedBotDecision(),
    playerCombatResults = playerCombatResults.map { (playerId, result) ->
        RecordedCombatEntry(
            playerId = playerId,
            result = RecordedCombatResult(
                attackerPlayerId = result.attackerPlayerId,
                defenderPlayerId = result.defenderPlayerId,
                attackerRoll = result.attackerRoll.toList(),
                defenderRoll = result.defenderRoll.toList(),
                attackerTotal = result.attackerTotal,
                defenderTotal = result.defenderTotal,
                attackerWins = result.attackerWins
            )
        )
    },
    skippedPlayers = skippedPlayers.toList(),
    message = message,
    isProcessing = isProcessing,
    selectedTerritoryId = selectedTerritoryId,
    errorMessage = errorMessage,
    attackArrows = attackArrows.map { arrow ->
        RecordedAttackArrow(
            fromTerritoryId = arrow.fromTerritoryId,
            toTerritoryId = arrow.toTerritoryId,
            attackSucceeded = arrow.attackSucceeded
        )
    }
)

private fun RecordedGameUiState.toGameUiState(): GameUiState = GameUiState(
    currentBotDecision = currentBotDecision?.toBotDecision(),
    playerCombatResults = playerCombatResults.associate { entry ->
        entry.playerId to CombatResult(
            attackerPlayerId = entry.result.attackerPlayerId,
            defenderPlayerId = entry.result.defenderPlayerId,
            attackerRoll = entry.result.attackerRoll.toIntArray(),
            defenderRoll = entry.result.defenderRoll.toIntArray(),
            attackerTotal = entry.result.attackerTotal,
            defenderTotal = entry.result.defenderTotal,
            attackerWins = entry.result.attackerWins
        )
    },
    skippedPlayers = skippedPlayers.toSet(),
    message = message,
    isProcessing = isProcessing,
    selectedTerritoryId = selectedTerritoryId,
    errorMessage = errorMessage,
    attackArrows = attackArrows.map { arrow ->
        AttackArrow(
            fromTerritoryId = arrow.fromTerritoryId,
            toTerritoryId = arrow.toTerritoryId,
            attackSucceeded = arrow.attackSucceeded
        )
    }
)

private fun BotDecision.toRecordedBotDecision(): RecordedBotDecision = when (this) {
    is BotDecision.Attack -> RecordedBotDecision(
        type = RecordedBotDecisionType.ATTACK,
        fromTerritoryId = fromTerritoryId,
        toTerritoryId = toTerritoryId
    )
    BotDecision.Skip -> RecordedBotDecision(
        type = RecordedBotDecisionType.SKIP
    )
}

private fun RecordedBotDecision.toBotDecision(): BotDecision = when (type) {
    RecordedBotDecisionType.ATTACK -> BotDecision.Attack(
        fromTerritoryId = fromTerritoryId ?: 0,
        toTerritoryId = toTerritoryId ?: 0
    )
    RecordedBotDecisionType.SKIP -> BotDecision.Skip
}

fun CombatResult.toRecordedCombatResult(): RecordedCombatResult = RecordedCombatResult(
    attackerPlayerId = attackerPlayerId,
    defenderPlayerId = defenderPlayerId,
    attackerRoll = attackerRoll.toList(),
    defenderRoll = defenderRoll.toList(),
    attackerTotal = attackerTotal,
    defenderTotal = defenderTotal,
    attackerWins = attackerWins
)

fun RecordedCombatResult.toCombatResult(): CombatResult = CombatResult(
    attackerPlayerId = attackerPlayerId,
    defenderPlayerId = defenderPlayerId,
    attackerRoll = attackerRoll.toIntArray(),
    defenderRoll = defenderRoll.toIntArray(),
    attackerTotal = attackerTotal,
    defenderTotal = defenderTotal,
    attackerWins = attackerWins
)
