package com.franklinharper.battlezone

data class GameSnapshot(
    val gameState: GameState,
    val uiState: GameUiState
) {
    fun deepCopy(): GameSnapshot = GameSnapshot(
        gameState = gameState.deepCopy(),
        uiState = uiState.deepCopy()
    )
}

fun GameState.deepCopy(): GameState = GameState(
    map = map.deepCopy(),
    players = Array(players.size) { index ->
        players[index].copy()
    },
    currentPlayerIndex = currentPlayerIndex,
    gamePhase = gamePhase,
    eliminatedPlayers = eliminatedPlayers.toSet(),
    skipTracker = skipTracker.toSet(),
    winner = winner
)

fun GameMap.deepCopy(): GameMap = GameMap(
    gridWidth = gridWidth,
    gridHeight = gridHeight,
    maxTerritories = maxTerritories,
    cells = cells.copyOf(),
    cellNeighbors = Array(cellNeighbors.size) { index ->
        CellNeighbors(cellNeighbors[index].directions.copyOf())
    },
    territories = Array(territories.size) { index ->
        territories[index].deepCopy()
    },
    playerCount = playerCount,
    seed = seed,
    gameRandom = GameRandom(seed)
)

fun Territory.deepCopy(): Territory = Territory(
    id = id,
    size = size,
    centerPos = centerPos,
    owner = owner,
    armyCount = armyCount,
    left = left,
    right = right,
    top = top,
    bottom = bottom,
    centerX = centerX,
    centerY = centerY,
    borderCells = borderCells.copyOf(),
    borderDirections = borderDirections.copyOf(),
    adjacentTerritories = adjacentTerritories.copyOf()
)

fun GameUiState.deepCopy(): GameUiState = GameUiState(
    currentBotDecision = currentBotDecision,
    playerCombatResults = playerCombatResults.mapValues { (_, result) -> result.deepCopy() },
    skippedPlayers = skippedPlayers.toSet(),
    message = message,
    isProcessing = isProcessing,
    selectedTerritoryId = selectedTerritoryId,
    errorMessage = errorMessage,
    attackArrows = attackArrows.toList()
)

fun CombatResult.deepCopy(): CombatResult = CombatResult(
    attackerPlayerId = attackerPlayerId,
    defenderPlayerId = defenderPlayerId,
    attackerRoll = attackerRoll.copyOf(),
    defenderRoll = defenderRoll.copyOf(),
    attackerTotal = attackerTotal,
    defenderTotal = defenderTotal,
    attackerWins = attackerWins
)
