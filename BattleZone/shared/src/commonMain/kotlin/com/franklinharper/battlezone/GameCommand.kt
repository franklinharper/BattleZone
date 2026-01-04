package com.franklinharper.battlezone

/**
 * Command pattern for undoable game actions
 */
interface GameCommand {
    /**
     * Execute the command
     */
    fun execute(): GameEvent

    /**
     * Undo the command, returning the game to its previous state
     */
    fun undo()

    /**
     * Get a description of this command for history/debugging
     */
    fun description(): String
}

/**
 * Command to execute an attack
 */
class AttackCommand(
    private val gameState: GameState,
    private val fromTerritoryId: Int,
    private val toTerritoryId: Int,
    private val gameRandom: GameRandom
) : GameCommand {

    private var previousFromArmyCount: Int = 0
    private var previousToArmyCount: Int = 0
    private var previousToOwner: Int = 0
    private var combatResult: CombatResult? = null

    override fun execute(): GameEvent {
        val fromTerritory = gameState.map.territories[fromTerritoryId]
        val toTerritory = gameState.map.territories[toTerritoryId]

        // Store previous state for undo
        previousFromArmyCount = fromTerritory.armyCount
        previousToArmyCount = toTerritory.armyCount
        previousToOwner = toTerritory.owner

        // Roll dice
        val attackerRoll = gameRandom.rollDice(fromTerritory.armyCount)
        val defenderRoll = gameRandom.rollDice(toTerritory.armyCount)

        val attackerTotal = attackerRoll.sum()
        val defenderTotal = defenderRoll.sum()
        val attackerWins = attackerTotal > defenderTotal

        combatResult = CombatResult(
            attackerRoll = attackerRoll,
            defenderRoll = defenderRoll,
            attackerTotal = attackerTotal,
            defenderTotal = defenderTotal,
            attackerWins = attackerWins
        )

        // Apply combat results
        if (attackerWins) {
            val armiesTransferred = fromTerritory.armyCount - 1
            toTerritory.owner = fromTerritory.owner
            toTerritory.armyCount = armiesTransferred
            fromTerritory.armyCount = 1
        } else {
            fromTerritory.armyCount = 1
        }

        return GameEvent.AttackExecuted(fromTerritoryId, toTerritoryId, combatResult!!)
    }

    override fun undo() {
        val fromTerritory = gameState.map.territories[fromTerritoryId]
        val toTerritory = gameState.map.territories[toTerritoryId]

        fromTerritory.armyCount = previousFromArmyCount
        toTerritory.armyCount = previousToArmyCount
        toTerritory.owner = previousToOwner
    }

    override fun description(): String {
        return "Attack from territory $fromTerritoryId to $toTerritoryId"
    }
}

/**
 * Command to skip a turn
 */
class SkipTurnCommand(
    private val playerId: Int
) : GameCommand {

    override fun execute(): GameEvent {
        return GameEvent.TurnSkipped(playerId)
    }

    override fun undo() {
        // Skipping is stateless, nothing to undo
    }

    override fun description(): String {
        return "Player $playerId skipped turn"
    }
}

/**
 * Command to distribute reinforcements
 */
class DistributeReinforcementsCommand(
    private val gameState: GameState,
    private val playerId: Int,
    private val reinforcements: Int,
    private val playerState: PlayerState,
    private val gameRandom: GameRandom
) : GameCommand {

    private val reinforcementHistory = mutableListOf<Pair<Int, Int>>() // (territoryId, armiesAdded)
    private var previousReserve: Int = 0

    override fun execute(): GameEvent {
        previousReserve = playerState.reserveArmies
        reinforcementHistory.clear()

        val totalToDistribute = reinforcements + playerState.reserveArmies
        var remainingReserve = 0

        repeat(totalToDistribute) {
            val eligibleTerritories = gameState.map.territories
                .filter { it.owner == playerId && it.armyCount < 8 }

            if (eligibleTerritories.isEmpty()) {
                remainingReserve++
            } else {
                val selectedTerritory = eligibleTerritories[
                    gameRandom.nextInt(eligibleTerritories.size)
                ]
                selectedTerritory.armyCount++
                reinforcementHistory.add(Pair(selectedTerritory.id, 1))
            }
        }

        playerState.reserveArmies = remainingReserve

        return GameEvent.ReinforcementPhaseCompleted(
            player0Reinforcements = if (playerId == 0) reinforcements else 0,
            player1Reinforcements = if (playerId == 1) reinforcements else 0
        )
    }

    override fun undo() {
        // Revert all reinforcements
        for ((territoryId, armiesAdded) in reinforcementHistory) {
            val territory = gameState.map.territories[territoryId]
            territory.armyCount -= armiesAdded
        }
        playerState.reserveArmies = previousReserve
    }

    override fun description(): String {
        return "Distribute $reinforcements reinforcements to player $playerId"
    }
}

/**
 * Manager for command history (undo/redo support)
 */
class CommandHistory {
    private val history = mutableListOf<GameCommand>()
    private var currentPosition = -1

    /**
     * Execute a command and add it to history
     */
    fun execute(command: GameCommand): GameEvent {
        // Remove any commands after current position (when doing new action after undo)
        while (history.size > currentPosition + 1) {
            history.removeAt(history.size - 1)
        }

        val event = command.execute()
        history.add(command)
        currentPosition++
        return event
    }

    /**
     * Undo the last command
     */
    fun undo(): Boolean {
        if (currentPosition < 0) return false

        val command = history[currentPosition]
        command.undo()
        currentPosition--
        return true
    }

    /**
     * Redo the previously undone command
     */
    fun redo(): Boolean {
        if (currentPosition >= history.size - 1) return false

        currentPosition++
        val command = history[currentPosition]
        command.execute()
        return true
    }

    /**
     * Check if undo is available
     */
    fun canUndo(): Boolean = currentPosition >= 0

    /**
     * Check if redo is available
     */
    fun canRedo(): Boolean = currentPosition < history.size - 1

    /**
     * Clear all history
     */
    fun clear() {
        history.clear()
        currentPosition = -1
    }

    /**
     * Get command history for debugging/display
     */
    fun getHistory(): List<String> {
        return history.mapIndexed { index, command ->
            val marker = if (index == currentPosition) "→ " else "  "
            "$marker${command.description()}"
        }
    }
}
