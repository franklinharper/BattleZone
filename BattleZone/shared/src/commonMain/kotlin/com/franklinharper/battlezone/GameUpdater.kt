package com.franklinharper.battlezone

import kotlin.math.max

class GameUpdater(
    private val gameLogic: GameLogic = GameLogic,
) {

    data class AttackResult(
        val newState: GameState,
        val combatResult: CombatResult,
    )

    fun executeAttack(
        currentGameState: GameState,
        fromTerritoryId: Int,
        toTerritoryId: Int,
    ): AttackResult {
        val fromTerritory = currentGameState.map.territories.getOrNull(fromTerritoryId)
            ?: throw IllegalStateException("Invalid from territory ID")
        val toTerritory = currentGameState.map.territories.getOrNull(toTerritoryId)
            ?: throw IllegalStateException("Invalid to territory ID")

        // Validate attack
        require(fromTerritory.owner == currentGameState.currentPlayerIndex) { "Attacker does not own the 'from' territory" }
        require(fromTerritory.armyCount > 1) { "Attacking territory must have more than 1 army" }
        require(fromTerritory.adjacentTerritories[toTerritoryId]) { "Territories are not adjacent" }
        require(toTerritory.owner != currentGameState.currentPlayerIndex) { "Cannot attack your own territory" }

        // Roll dice
        val attackerRoll = currentGameState.map.gameRandom.rollDice(fromTerritory.armyCount)
        val defenderRoll = currentGameState.map.gameRandom.rollDice(toTerritory.armyCount)

        val attackerTotal = attackerRoll.sum()
        val defenderTotal = defenderRoll.sum()
        val attackerWins = attackerTotal > defenderTotal

        val combatResult = CombatResult(
            attackerRoll = attackerRoll,
            defenderRoll = defenderRoll,
            attackerTotal = attackerTotal,
            defenderTotal = defenderTotal,
            attackerWins = attackerWins
        )

        // Create a deep copy of the map and territories for modification
        val newMap = currentGameState.map.copy(
            territories = currentGameState.map.territories.map { it.copy() }.toTypedArray()
        )
        val newFromTerritory = newMap.territories[fromTerritoryId]
        val newToTerritory = newMap.territories[toTerritoryId]

        // Apply combat results
        if (attackerWins) {
            newToTerritory.owner = newFromTerritory.owner
            newToTerritory.armyCount = newFromTerritory.armyCount - 1
            newFromTerritory.armyCount = 1
        } else {
            newFromTerritory.armyCount = 1
        }

        // Update player states
        val newPlayers = currentGameState.players.map { it.copy() }.toTypedArray()
        gameLogic.updatePlayerState(newMap, newPlayers[0], 0)
        gameLogic.updatePlayerState(newMap, newPlayers[1], 1)

        // Check for victory
        val winner = gameLogic.checkVictory(newMap, currentGameState.currentPlayerIndex)
        val nextPlayerIndex = (currentGameState.currentPlayerIndex + 1) % newMap.playerCount

        val newGameState = currentGameState.copy(
            map = newMap,
            players = newPlayers,
            consecutiveSkips = 0,
            currentPlayerIndex = nextPlayerIndex,
            winner = winner,
            gamePhase = if (winner != null) GamePhase.GAME_OVER else currentGameState.gamePhase
        )

        return AttackResult(
            newState = newGameState,
            combatResult = combatResult
        )
    }

    fun skipTurn(currentGameState: GameState): GameState {
        val newConsecutiveSkips = currentGameState.consecutiveSkips + 1
        val gamePhase = if (newConsecutiveSkips >= 2) {
            GamePhase.REINFORCEMENT
        } else {
            currentGameState.gamePhase
        }
        val nextPlayerIndex = (currentGameState.currentPlayerIndex + 1) % currentGameState.map.playerCount

        return currentGameState.copy(
            consecutiveSkips = newConsecutiveSkips,
            currentPlayerIndex = nextPlayerIndex,
            gamePhase = gamePhase
        )
    }

    fun executeReinforcementPhase(currentGameState: GameState): GameState {
        require(currentGameState.gamePhase == GamePhase.REINFORCEMENT) { "Cannot execute reinforcement phase outside of reinforcement phase" }

        val newMap = currentGameState.map.copy(
            territories = currentGameState.map.territories.map { it.copy() }.toTypedArray()
        )
        val newPlayers = currentGameState.players.map { it.copy() }.toTypedArray()

        for (playerId in 0 until newMap.playerCount) {
            val playerState = newPlayers[playerId]
            val reinforcements = gameLogic.calculateReinforcements(newMap, playerId)

            val newReserve = gameLogic.distributeReinforcements(
                map = newMap,
                playerId = playerId,
                reinforcements = reinforcements,
                currentReserve = playerState.reserveArmies
            )

            playerState.reserveArmies = newReserve
            gameLogic.updatePlayerState(newMap, playerState, playerId)
        }

        return currentGameState.copy(
            map = newMap,
            players = newPlayers,
            gamePhase = GamePhase.ATTACK,
            consecutiveSkips = 0
        )
    }
}
