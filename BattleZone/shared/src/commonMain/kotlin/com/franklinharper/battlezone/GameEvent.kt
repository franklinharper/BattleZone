package com.franklinharper.battlezone

/**
 * Sealed class representing all possible game events in the state machine
 */
sealed class GameEvent {
    /**
     * An attack was executed between two territories
     */
    data class AttackExecuted(
        val fromTerritoryId: Int,
        val toTerritoryId: Int,
        val result: CombatResult
    ) : GameEvent()

    /**
     * Current player skipped their turn
     */
    data class TurnSkipped(val playerId: Int) : GameEvent()

    /**
     * Reinforcement phase started
     */
    object ReinforcementPhaseStarted : GameEvent()

    /**
     * Reinforcement phase completed
     */
    data class ReinforcementPhaseCompleted(
        val player0Reinforcements: Int,
        val player1Reinforcements: Int
    ) : GameEvent()

    /**
     * A new game was started
     */
    data class GameStarted(val startingPlayer: Int) : GameEvent()

    /**
     * Game ended with a winner
     */
    data class GameEnded(val winner: Int) : GameEvent()

    /**
     * A bot made a decision
     */
    data class BotDecisionMade(val playerId: Int, val decision: BotDecision) : GameEvent()

    /**
     * Player selected a territory (for human players)
     */
    data class TerritorySelected(val territoryId: Int) : GameEvent()

    /**
     * Territory selection was cancelled
     */
    object SelectionCancelled : GameEvent()
}
