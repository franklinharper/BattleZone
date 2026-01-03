package com.franklinharper.battlezone

import kotlin.random.Random

/**
 * Represents the hex grid cell adjacency
 */
data class CellNeighbors(
    val directions: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CellNeighbors
        return directions.contentEquals(other.directions)
    }

    override fun hashCode(): Int = directions.contentHashCode()
}

/**
 * Represents a single territory on the map
 */
data class Territory(
    val id: Int,
    var size: Int,
    var centerPos: Int,
    var owner: Int,
    var armyCount: Int,

    // Bounding box for center calculation
    var left: Int,
    var right: Int,
    var top: Int,
    var bottom: Int,
    var centerX: Int,
    var centerY: Int,

    // Border drawing data
    val borderCells: IntArray,
    val borderDirections: IntArray,

    // Adjacency
    val adjacentTerritories: BooleanArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Territory

        return id == other.id &&
                size == other.size &&
                centerPos == other.centerPos &&
                owner == other.owner &&
                armyCount == other.armyCount &&
                left == other.left &&
                right == other.right &&
                top == other.top &&
                bottom == other.bottom &&
                centerX == other.centerX &&
                centerY == other.centerY &&
                borderCells.contentEquals(other.borderCells) &&
                borderDirections.contentEquals(other.borderDirections) &&
                adjacentTerritories.contentEquals(other.adjacentTerritories)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + size
        result = 31 * result + centerPos
        result = 31 * result + owner
        result = 31 * result + armyCount
        result = 31 * result + left
        result = 31 * result + right
        result = 31 * result + top
        result = 31 * result + bottom
        result = 31 * result + centerX
        result = 31 * result + centerY
        result = 31 * result + borderCells.contentHashCode()
        result = 31 * result + borderDirections.contentHashCode()
        result = 31 * result + adjacentTerritories.contentHashCode()
        return result
    }
}

/**
 * Represents the complete game map
 */
data class GameMap(
    val gridWidth: Int = 28,
    val gridHeight: Int = 32,
    val maxTerritories: Int = 32,

    val cells: IntArray,
    val cellNeighbors: Array<CellNeighbors>,
    val territories: Array<Territory>,

    val playerCount: Int = 2,
    val seed: Long? = null,
    val gameRandom: GameRandom
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as GameMap

        return gridWidth == other.gridWidth &&
                gridHeight == other.gridHeight &&
                maxTerritories == other.maxTerritories &&
                cells.contentEquals(other.cells) &&
                cellNeighbors.contentDeepEquals(other.cellNeighbors) &&
                territories.contentEquals(other.territories) &&
                playerCount == other.playerCount &&
                seed == other.seed
    }

    override fun hashCode(): Int {
        var result = gridWidth
        result = 31 * result + gridHeight
        result = 31 * result + maxTerritories
        result = 31 * result + cells.contentHashCode()
        result = 31 * result + cellNeighbors.contentDeepHashCode()
        result = 31 * result + territories.contentHashCode()
        result = 31 * result + playerCount
        result = 31 * result + (seed?.hashCode() ?: 0)
        return result
    }
}

/**
 * Player state
 */
data class PlayerState(
    var territoryCount: Int,
    var largestConnectedSize: Int,
    var totalArmies: Int,
    var reserveArmies: Int = 0
)

/**
 * Represents the overall game state
 */
data class GameState(
    val map: GameMap,
    val players: Array<PlayerState>,
    val currentPlayerIndex: Int,
    val gamePhase: GamePhase,
    val consecutiveSkips: Int,
    val winner: Int? = null,
    val turnHistory: List<Turn> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as GameState

        return map == other.map &&
                players.contentEquals(other.players) &&
                currentPlayerIndex == other.currentPlayerIndex &&
                gamePhase == other.gamePhase &&
                consecutiveSkips == other.consecutiveSkips &&
                winner == other.winner &&
                turnHistory == other.turnHistory
    }

    override fun hashCode(): Int {
        var result = map.hashCode()
        result = 31 * result + players.contentHashCode()
        result = 31 * result + currentPlayerIndex
        result = 31 * result + gamePhase.hashCode()
        result = 31 * result + consecutiveSkips
        result = 31 * result + (winner ?: 0)
        result = 31 * result + turnHistory.hashCode()
        return result
    }
}

enum class GamePhase {
    ATTACK,
    REINFORCEMENT,
    GAME_OVER
}

/**
 * Represents a single turn action
 */
data class Turn(
    val playerId: Int,
    val action: TurnAction
)

sealed class TurnAction {
    data class Attack(
        val fromTerritoryId: Int,
        val toTerritoryId: Int,
        val attackerRoll: IntArray,
        val defenderRoll: IntArray,
        val attackerTotal: Int,
        val defenderTotal: Int,
        val success: Boolean
    ) : TurnAction() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Attack

            return fromTerritoryId == other.fromTerritoryId &&
                    toTerritoryId == other.toTerritoryId &&
                    attackerRoll.contentEquals(other.attackerRoll) &&
                    defenderRoll.contentEquals(other.defenderRoll) &&
                    attackerTotal == other.attackerTotal &&
                    defenderTotal == other.defenderTotal &&
                    success == other.success
        }

        override fun hashCode(): Int {
            var result = fromTerritoryId
            result = 31 * result + toTerritoryId
            result = 31 * result + attackerRoll.contentHashCode()
            result = 31 * result + defenderRoll.contentHashCode()
            result = 31 * result + attackerTotal
            result = 31 * result + defenderTotal
            result = 31 * result + success.hashCode()
            return result
        }
    }

    object Skip : TurnAction()
}

/**
 * Manages all randomness for reproducible games
 */
class GameRandom(seed: Long? = null) {
    private val random = if (seed != null) Random(seed) else Random.Default

    fun rollDice(count: Int): IntArray {
        return IntArray(count) { random.nextInt(1, 7) }
    }

    fun selectRandomTerritory(territoryIds: List<Int>): Int {
        return territoryIds[random.nextInt(territoryIds.size)]
    }

    fun selectRandomCell(cellIndices: List<Int>): Int {
        return cellIndices[random.nextInt(cellIndices.size)]
    }

    fun <T> shuffle(array: Array<T>) {
        array.shuffle(random)
    }

    fun nextInt(bound: Int): Int {
        return random.nextInt(bound)
    }

    fun nextInt(from: Int, until: Int): Int {
        return random.nextInt(from, until)
    }
}
