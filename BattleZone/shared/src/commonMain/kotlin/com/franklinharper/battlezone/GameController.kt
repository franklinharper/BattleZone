package com.franklinharper.battlezone

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the result of a combat action
 */
data class CombatResult(
    val attackerRoll: IntArray,
    val defenderRoll: IntArray,
    val attackerTotal: Int,
    val defenderTotal: Int,
    val attackerWins: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CombatResult

        return attackerRoll.contentEquals(other.attackerRoll) &&
                defenderRoll.contentEquals(other.defenderRoll) &&
                attackerTotal == other.attackerTotal &&
                defenderTotal == other.defenderTotal &&
                attackerWins == other.attackerWins
    }

    override fun hashCode(): Int {
        var result = attackerRoll.contentHashCode()
        result = 31 * result + defenderRoll.contentHashCode()
        result = 31 * result + attackerTotal
        result = 31 * result + defenderTotal
        result = 31 * result + attackerWins.hashCode()
        return result
    }
}

/**
 * UI state for displaying the game
 */
data class GameUiState(
    val currentBotDecision: BotDecision? = null,
    val lastCombatResult: CombatResult? = null,
    val message: String? = null,
    val isProcessing: Boolean = false,
    val selectedTerritoryId: Int? = null,
    val errorMessage: String? = null
)

/**
 * Main game controller that manages game state and turn flow
 */
class GameController(
    initialMap: GameMap,
    private val gameMode: GameMode = GameMode.BOT_VS_BOT,
    private val humanPlayerId: Int = 0,
    private val bot0: Bot = DefaultBot(initialMap.gameRandom),
    private val bot1: Bot = DefaultBot(initialMap.gameRandom),
    private val gameUpdater: GameUpdater = GameUpdater(),
) {
    private val _gameState = MutableStateFlow(createInitialGameState(initialMap))
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>()
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private val commandHistory = CommandHistory()

    /**
     * Check if the current player is human
     */
    fun isCurrentPlayerHuman(): Boolean {
        return gameMode == GameMode.HUMAN_VS_BOT && _gameState.value.currentPlayerIndex == humanPlayerId
    }

    /**
     * Check if the current player is a bot
     */
    fun isCurrentPlayerBot(): Boolean {
        return gameMode == GameMode.BOT_VS_BOT || _gameState.value.currentPlayerIndex != humanPlayerId
    }

    /**
     * Get player label for display
     */
    private fun getPlayerLabel(playerId: Int): String {
        return if (gameMode == GameMode.HUMAN_VS_BOT) {
            if (playerId == humanPlayerId) "Human" else "Bot"
        } else {
            "Player $playerId"
        }
    }

    /**
     * Create initial game state from a generated map
     */
    private fun createInitialGameState(map: GameMap): GameState {
        val players = Array(map.playerCount) { playerId ->
            val playerState = PlayerState(0, 0, 0, 0)
            GameLogic.updatePlayerState(map, playerState, playerId)
            playerState
        }

        // Randomly choose starting player
        val startingPlayer = map.gameRandom.nextInt(2)

        return GameState(
            map = map,
            players = players,
            currentPlayerIndex = startingPlayer,
            gamePhase = GamePhase.ATTACK,
            consecutiveSkips = 0,
            winner = null
        )
    }

    /**
     * Request the current bot to make a decision
     */
    fun requestBotDecision() {
        if (_gameState.value.gamePhase != GamePhase.ATTACK) return
        if (_gameState.value.winner != null) return

        val currentPlayer = _gameState.value.currentPlayerIndex
        val bot = if (currentPlayer == 0) bot0 else bot1
        val decision = bot.decide(_gameState.value.map, currentPlayer)

        _uiState.value = _uiState.value.copy(
            currentBotDecision = decision,
            message = when (decision) {
                is BotDecision.Attack -> "${getPlayerLabel(currentPlayer)} attacks: Territory ${decision.fromTerritoryId} → ${decision.toTerritoryId}"
                is BotDecision.Skip -> "${getPlayerLabel(currentPlayer)} skips their turn"
            }
        )
    }

    /**
     * Execute the current bot's decision (attack or skip)
     */
    fun executeBotDecision() {
        val decision = _uiState.value.currentBotDecision ?: return

        when (decision) {
            is BotDecision.Attack -> executeAttack(decision.fromTerritoryId, decision.toTerritoryId)
            is BotDecision.Skip -> skipTurn()
        }

        // Clear the decision after execution
        _uiState.value = _uiState.value.copy(currentBotDecision = null)
    }

    /**
     * Execute an attack from one territory to another
     */
    private fun executeAttack(fromTerritoryId: Int, toTerritoryId: Int) {
        val currentGameState = _gameState.value

        try {
            val result = gameUpdater.executeAttack(
                currentGameState = currentGameState,
                fromTerritoryId = fromTerritoryId,
                toTerritoryId = toTerritoryId
            )

            // Update state with new game state from the updater
            _gameState.value = result.newState

            // Update UI with combat results and messages
            val (attackerRoll, defenderRoll, attackerTotal, defenderTotal, attackerWins) = result.combatResult
            val message = if (attackerWins) {
                "${getPlayerLabel(currentGameState.currentPlayerIndex)} wins! " +
                        "Attacker: ${attackerRoll.joinToString("+")} = $attackerTotal | " +
                        "Defender: ${defenderRoll.joinToString("+")} = $defenderTotal"
            } else {
                "${getPlayerLabel(result.newState.map.territories[toTerritoryId].owner)} defends! " +
                        "Attacker: ${attackerRoll.joinToString("+")} = $attackerTotal | " +
                        "Defender: ${defenderRoll.joinToString("+")} = $defenderTotal"
            }

            _uiState.value = _uiState.value.copy(
                lastCombatResult = result.combatResult,
                message = message
            )

            // If game is over, show winner message
            if (result.newState.winner != null) {
                _uiState.value = _uiState.value.copy(
                    message = "🎉 ${getPlayerLabel(result.newState.winner)} wins the game! 🎉"
                )
            }

        } catch (e: IllegalArgumentException) {
            // Handle illegal moves (e.g., attacking own territory)
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        } catch (e: IllegalStateException) {
            // Handle invalid state (e.g., invalid territory ID)
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    /**
     * Skip the current player's turn
     */
    fun skipTurn() {
        val oldState = _gameState.value
        val newState = gameUpdater.skipTurn(oldState)
        _gameState.value = newState

        _uiState.value = _uiState.value.copy(
            message = "${getPlayerLabel(oldState.currentPlayerIndex)} skipped. " +
                    "Consecutive skips: ${newState.consecutiveSkips}"
        )

        if (newState.gamePhase == GamePhase.REINFORCEMENT) {
            _uiState.value = _uiState.value.copy(
                message = "Reinforcement Phase: Both players skipped. Distributing reinforcements..."
            )
        }
    }

    /**
     * Execute the reinforcement phase for both players
     */
    fun executeReinforcementPhase() {
        try {
            val oldState = _gameState.value
            val newState = gameUpdater.executeReinforcementPhase(oldState)
            _gameState.value = newState

            // Create a summary message for reinforcements
            val messages = newState.players.mapIndexed { index, playerState ->
                val oldPlayerState = oldState.players[index]
                val newArmies = playerState.totalArmies - oldPlayerState.totalArmies
                val reinforcementCount = newArmies + (playerState.reserveArmies - oldPlayerState.reserveArmies)
                "${getPlayerLabel(index)}: +$reinforcementCount armies" +
                        if (playerState.reserveArmies > 0) " (Reserve: ${playerState.reserveArmies})" else ""
            }

            _uiState.value = _uiState.value.copy(
                message = "Reinforcements: ${messages.joinToString(" | ")}"
            )
        } catch (e: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    /**
     * Reset to a new game
     */
    fun newGame(map: GameMap) {
        _gameState.value = createInitialGameState(map)
        _uiState.value = GameUiState(message = "New game started! ${getPlayerLabel(_gameState.value.currentPlayerIndex)} goes first.")
        commandHistory.clear()
    }

    /**
     * Check if the game is over
     */
    fun isGameOver(): Boolean = _gameState.value.winner != null

    /**
     * Get the current player index
     */
    fun getCurrentPlayer(): Int = _gameState.value.currentPlayerIndex

    /**
     * Check if undo is available
     */
    fun canUndo(): Boolean = commandHistory.canUndo()

    /**
     * Check if redo is available
     */
    fun canRedo(): Boolean = commandHistory.canRedo()

    /**
     * Human player selects a territory (for attack)
     */
    fun selectTerritory(territoryId: Int) {
        val currentState = _gameState.value
        val currentUiState = _uiState.value

        if (!isCurrentPlayerHuman()) {
            _uiState.value = currentUiState.copy(errorMessage = "Not your turn!")
            return
        }

        if (currentState.gamePhase != GamePhase.ATTACK) {
            _uiState.value = currentUiState.copy(errorMessage = "Cannot attack during ${currentState.gamePhase} phase")
            return
        }

        val territory = currentState.map.territories.getOrNull(territoryId)
        if (territory == null) {
            _uiState.value = currentUiState.copy(errorMessage = "Invalid territory")
            return
        }

        // If no territory is selected yet, select this one (if it's owned by human)
        if (currentUiState.selectedTerritoryId == null) {
            if (territory.owner != humanPlayerId) {
                _uiState.value = currentUiState.copy(errorMessage = "You don't own this territory")
                return
            }

            if (territory.armyCount <= 1) {
                _uiState.value = currentUiState.copy(errorMessage = "Territory must have more than 1 army to attack")
                return
            }

            _uiState.value = currentUiState.copy(
                selectedTerritoryId = territoryId,
                errorMessage = null,
                message = "Territory $territoryId selected. Now select an adjacent enemy territory to attack."
            )
        } else {
            // A territory is already selected
            val fromTerritoryId = currentUiState.selectedTerritoryId

            // If clicking the same territory again, cancel the selection
            if (territoryId == fromTerritoryId) {
                _uiState.value = currentUiState.copy(
                    selectedTerritoryId = null,
                    errorMessage = null,
                    message = "Selection cancelled"
                )
                return
            }

            val fromTerritory = currentState.map.territories[fromTerritoryId]

            // Validate the attack
            if (territory.owner == humanPlayerId) {
                _uiState.value = currentUiState.copy(errorMessage = "Cannot attack your own territory")
                return
            }

            if (!fromTerritory.adjacentTerritories[territoryId]) {
                _uiState.value = currentUiState.copy(errorMessage = "Territories are not adjacent")
                return
            }

            // Execute the attack
            executeHumanAttack(fromTerritoryId, territoryId)
        }
    }

    /**
     * Execute a human player's attack
     */
    private fun executeHumanAttack(fromTerritoryId: Int, toTerritoryId: Int) {
        // Clear selection
        _uiState.value = _uiState.value.copy(
            selectedTerritoryId = null,
            errorMessage = null
        )

        // Execute the attack using the existing attack logic
        executeAttack(fromTerritoryId, toTerritoryId)
    }

    /**
     * Cancel the current territory selection
     */
    fun cancelSelection() {
        _uiState.value = _uiState.value.copy(
            selectedTerritoryId = null,
            errorMessage = null,
            message = "Selection cancelled"
        )
    }
}
