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
 * Bot attack arrow display information
 */
data class BotAttackArrow(
    val fromTerritoryId: Int,
    val toTerritoryId: Int,
    val attackSucceeded: Boolean
)

/**
 * UI state for displaying the game
 */
data class GameUiState(
    val currentBotDecision: BotDecision? = null,
    val lastCombatResult: CombatResult? = null,
    val message: String? = null,
    val isProcessing: Boolean = false,
    val selectedTerritoryId: Int? = null,
    val errorMessage: String? = null,
    val botAttackArrow: BotAttackArrow? = null
)

/**
 * Main game controller that manages game state and turn flow
 */
class GameController(
    initialMap: GameMap,
    private val gameMode: GameMode = GameMode.BOT_VS_BOT,
    private val humanPlayerId: Int = 0,
    private val bot0: Bot = DefaultBot(initialMap.gameRandom),
    private val bot1: Bot = DefaultBot(initialMap.gameRandom)
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
        val fromTerritory = currentGameState.map.territories.getOrNull(fromTerritoryId) ?: return
        val toTerritory = currentGameState.map.territories.getOrNull(toTerritoryId) ?: return

        // Validate attack
        if (fromTerritory.owner != currentGameState.currentPlayerIndex) return
        if (fromTerritory.armyCount <= 1) return
        if (!fromTerritory.adjacentTerritories[toTerritoryId]) return
        if (toTerritory.owner == currentGameState.currentPlayerIndex) return

        // Roll dice
        val attackerRoll = currentGameState.map.gameRandom.rollDice(fromTerritory.armyCount)
        val defenderRoll = currentGameState.map.gameRandom.rollDice(toTerritory.armyCount)

        val attackerTotal = attackerRoll.sum()
        val defenderTotal = defenderRoll.sum()
        val attackerWins = attackerTotal > defenderTotal

        // Determine if this is a bot attack
        val isBotAttack = isCurrentPlayerBot()

        // Store combat result for UI
        val combatResult = CombatResult(
            attackerRoll = attackerRoll,
            defenderRoll = defenderRoll,
            attackerTotal = attackerTotal,
            defenderTotal = defenderTotal,
            attackerWins = attackerWins
        )

        // Apply combat results
        if (attackerWins) {
            // Attacker wins: transfer armies and change ownership
            val armiesTransferred = fromTerritory.armyCount - 1
            toTerritory.owner = fromTerritory.owner
            toTerritory.armyCount = armiesTransferred
            fromTerritory.armyCount = 1

            _uiState.value = _uiState.value.copy(
                lastCombatResult = combatResult,
                message = "${getPlayerLabel(currentGameState.currentPlayerIndex)} wins! " +
                        "Attacker: ${attackerRoll.joinToString("+")} = $attackerTotal | " +
                        "Defender: ${defenderRoll.joinToString("+")} = $defenderTotal",
                botAttackArrow = if (isBotAttack) {
                    BotAttackArrow(fromTerritoryId, toTerritoryId, attackSucceeded = true)
                } else {
                    null  // Human attack - clear bot arrow
                }
            )
        } else {
            // Defender wins: attacker loses all armies except 1
            fromTerritory.armyCount = 1

            _uiState.value = _uiState.value.copy(
                lastCombatResult = combatResult,
                message = "${getPlayerLabel(toTerritory.owner)} defends! " +
                        "Attacker: ${attackerRoll.joinToString("+")} = $attackerTotal | " +
                        "Defender: ${defenderRoll.joinToString("+")} = $defenderTotal",
                botAttackArrow = if (isBotAttack) {
                    BotAttackArrow(fromTerritoryId, toTerritoryId, attackSucceeded = false)
                } else {
                    null  // Human attack - clear bot arrow
                }
            )
        }

        // Update player states
        GameLogic.updatePlayerState(currentGameState.map, currentGameState.players[0], 0)
        GameLogic.updatePlayerState(currentGameState.map, currentGameState.players[1], 1)

        // Create new PlayerState copies with updated values
        val updatedPlayers = arrayOf(
            currentGameState.players[0].copy(),
            currentGameState.players[1].copy()
        )

        // Check for victory
        val currentPlayer = currentGameState.currentPlayerIndex
        val allTerritoriesOwned = currentGameState.map.territories.all { territory ->
            territory.size == 0 || territory.owner == currentPlayer
        }

        if (allTerritoriesOwned) {
            _gameState.value = _gameState.value.copy(
                winner = currentPlayer,
                gamePhase = GamePhase.GAME_OVER,
                players = updatedPlayers
            )
            _uiState.value = _uiState.value.copy(
                message = "🎉 ${getPlayerLabel(currentPlayer)} wins the game! 🎉"
            )
        } else {
            // Update state with new players, reset skips, and advance to next player
            val nextPlayerIndex = (currentGameState.currentPlayerIndex + 1) % currentGameState.map.playerCount
            _gameState.value = _gameState.value.copy(
                consecutiveSkips = 0,
                currentPlayerIndex = nextPlayerIndex,
                players = updatedPlayers
            )
        }
    }

    /**
     * Skip the current player's turn
     */
    fun skipTurn() {
        val newConsecutiveSkips = _gameState.value.consecutiveSkips + 1
        val isBotSkip = isCurrentPlayerBot()

        _uiState.value = _uiState.value.copy(
            message = "${getPlayerLabel(_gameState.value.currentPlayerIndex)} skipped. " +
                    "Consecutive skips: $newConsecutiveSkips",
            botAttackArrow = if (isBotSkip) null else _uiState.value.botAttackArrow
        )

        _gameState.value = _gameState.value.copy(consecutiveSkips = newConsecutiveSkips)

        // Check if both players have skipped consecutively
        if (newConsecutiveSkips >= 2) {
            startReinforcementPhase()
        } else {
            nextPlayer()
        }
    }

    /**
     * Switch to the next player
     */
    private fun nextPlayer() {
        val currentState = _gameState.value
        val nextPlayerIndex = (currentState.currentPlayerIndex + 1) % currentState.map.playerCount
        _gameState.value = currentState.copy(currentPlayerIndex = nextPlayerIndex)
    }

    /**
     * Check if the current player has won (owns all territories)
     */
    private fun checkVictory() {
        val currentState = _gameState.value
        val currentPlayer = currentState.currentPlayerIndex
        val allTerritoriesOwned = currentState.map.territories.all { territory ->
            territory.size == 0 || territory.owner == currentPlayer
        }

        if (allTerritoriesOwned) {
            _gameState.value = currentState.copy(
                winner = currentPlayer,
                gamePhase = GamePhase.GAME_OVER
            )
            _uiState.value = _uiState.value.copy(
                message = "🎉 ${getPlayerLabel(currentPlayer)} wins the game! 🎉"
            )
        }
    }

    /**
     * Start the reinforcement phase
     */
    private fun startReinforcementPhase() {
        _gameState.value = _gameState.value.copy(gamePhase = GamePhase.REINFORCEMENT)
        _uiState.value = _uiState.value.copy(
            message = "Reinforcement Phase: Both players skipped. Distributing reinforcements..."
        )
    }

    /**
     * Execute the reinforcement phase for both players
     */
    fun executeReinforcementPhase() {
        val currentState = _gameState.value
        if (currentState.gamePhase != GamePhase.REINFORCEMENT) return

        val messages = mutableListOf<String>()

        // Reinforce both players
        for (playerId in 0 until currentState.map.playerCount) {
            val playerState = currentState.players[playerId]
            val reinforcements = GameLogic.calculateReinforcements(currentState.map, playerId)

            val newReserve = GameLogic.distributeReinforcements(
                currentState.map,
                playerId,
                reinforcements,
                playerState.reserveArmies
            )

            playerState.reserveArmies = newReserve
            GameLogic.updatePlayerState(currentState.map, playerState, playerId)

            messages.add("${getPlayerLabel(playerId)}: +$reinforcements armies" +
                if (newReserve > 0) " (Reserve: $newReserve)" else "")
        }

        _uiState.value = _uiState.value.copy(
            message = "Reinforcements: ${messages.joinToString(" | ")}"
        )

        // Return to attack phase with new PlayerState copies so UI sees the changes
        _gameState.value = currentState.copy(
            gamePhase = GamePhase.ATTACK,
            consecutiveSkips = 0,
            players = arrayOf(
                currentState.players[0].copy(),
                currentState.players[1].copy()
            )
        )
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
