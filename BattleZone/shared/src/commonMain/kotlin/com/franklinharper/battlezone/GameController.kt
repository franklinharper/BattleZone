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
    val attackerPlayerId: Int,
    val defenderPlayerId: Int,
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

        return attackerPlayerId == other.attackerPlayerId &&
                defenderPlayerId == other.defenderPlayerId &&
                attackerRoll.contentEquals(other.attackerRoll) &&
                defenderRoll.contentEquals(other.defenderRoll) &&
                attackerTotal == other.attackerTotal &&
                defenderTotal == other.defenderTotal &&
                attackerWins == other.attackerWins
    }

    override fun hashCode(): Int {
        var result = attackerPlayerId
        result = 31 * result + defenderPlayerId
        result = 31 * result + attackerRoll.contentHashCode()
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
    val playerCombatResults: Map<Int, CombatResult> = emptyMap(),
    val skippedPlayers: Set<Int> = emptySet(),
    val message: String? = null,
    val isProcessing: Boolean = false,
    val selectedTerritoryId: Int? = null,
    val errorMessage: String? = null,
    val botAttackArrows: List<BotAttackArrow> = emptyList()
)

/**
 * Main game controller that manages game state and turn flow
 */
class GameController(
    initialMap: GameMap,
    private val gameMode: GameMode = GameMode.BOT_VS_BOT,
    private val humanPlayerId: Int = 0,
    private val bots: Array<Bot>
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

        // Choose starting player based on game mode
        val startingPlayer = when (gameMode) {
            GameMode.HUMAN_VS_BOT -> 0  // Human always starts
            GameMode.BOT_VS_BOT -> map.gameRandom.nextInt(map.playerCount)
        }

        return GameState(
            map = map,
            players = players,
            currentPlayerIndex = startingPlayer,
            gamePhase = GamePhase.ATTACK,
            eliminatedPlayers = emptySet(),
            skipTracker = emptyMap(),
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
        val botIndex = if (gameMode == GameMode.HUMAN_VS_BOT) {
            currentPlayer - 1  // bots[0] is player 1
        } else {
            currentPlayer  // bots array matches player indices
        }
        val bot = bots[botIndex]
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

        val attackerPlayerId = fromTerritory.owner
        val defenderPlayerId = toTerritory.owner

        // Store combat result for UI
        val combatResult = CombatResult(
            attackerPlayerId = attackerPlayerId,
            defenderPlayerId = defenderPlayerId,
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

            val updatedCombatResults = _uiState.value.playerCombatResults + (attackerPlayerId to combatResult)
            println("DEBUG: Storing combat result for attacker $attackerPlayerId. Map now has ${updatedCombatResults.size} entries")
            _uiState.value = _uiState.value.copy(
                playerCombatResults = updatedCombatResults,
                skippedPlayers = _uiState.value.skippedPlayers - attackerPlayerId,  // Remove from skipped when attacking
                message = "${getPlayerLabel(currentGameState.currentPlayerIndex)} wins! " +
                        "Attacker: ${attackerRoll.joinToString("+")} = $attackerTotal | " +
                        "Defender: ${defenderRoll.joinToString("+")} = $defenderTotal",
                botAttackArrows = if (isBotAttack) {
                    // Add this bot's attack arrow to the list
                    _uiState.value.botAttackArrows + BotAttackArrow(fromTerritoryId, toTerritoryId, attackSucceeded = true)
                } else {
                    emptyList()  // Human attack - clear all bot arrows
                }
            )
        } else {
            // Defender wins: attacker loses all armies except 1
            fromTerritory.armyCount = 1

            val updatedCombatResults = _uiState.value.playerCombatResults + (attackerPlayerId to combatResult)
            println("DEBUG: Storing combat result for attacker $attackerPlayerId. Map now has ${updatedCombatResults.size} entries")
            _uiState.value = _uiState.value.copy(
                playerCombatResults = updatedCombatResults,
                skippedPlayers = _uiState.value.skippedPlayers - attackerPlayerId,  // Remove from skipped when attacking
                message = "${getPlayerLabel(toTerritory.owner)} defends! " +
                        "Attacker: ${attackerRoll.joinToString("+")} = $attackerTotal | " +
                        "Defender: ${defenderRoll.joinToString("+")} = $defenderTotal",
                botAttackArrows = if (isBotAttack) {
                    // Add this bot's attack arrow to the list
                    _uiState.value.botAttackArrows + BotAttackArrow(fromTerritoryId, toTerritoryId, attackSucceeded = false)
                } else {
                    emptyList()  // Human attack - clear all bot arrows
                }
            )
        }

        // Update all player states
        for (playerId in 0 until currentGameState.map.playerCount) {
            GameLogic.updatePlayerState(currentGameState.map, currentGameState.players[playerId], playerId)
        }

        // Create new PlayerState copies with updated values
        val updatedPlayers = Array(currentGameState.map.playerCount) { playerId ->
            currentGameState.players[playerId].copy()
        }

        // Check if defender is eliminated (use defenderPlayerId saved before ownership change)
        var eliminatedPlayers = currentGameState.eliminatedPlayers
        if (updatedPlayers[defenderPlayerId].territoryCount == 0) {
            eliminatedPlayers = eliminatedPlayers + defenderPlayerId
            println("DEBUG: Player $defenderPlayerId eliminated!")
        }

        // Check game end conditions
        val currentPlayer = currentGameState.currentPlayerIndex

        // Human eliminated → human loses immediately
        if (gameMode == GameMode.HUMAN_VS_BOT && humanPlayerId in eliminatedPlayers) {
            _gameState.value = _gameState.value.copy(
                gamePhase = GamePhase.GAME_OVER,
                winner = null,
                players = updatedPlayers,
                eliminatedPlayers = eliminatedPlayers
            )
            _uiState.value = _uiState.value.copy(
                message = "💀 Human eliminated! Game Over."
            )
            return
        }

        // Count remaining players
        val remainingPlayers = (0 until currentGameState.map.playerCount).filter { it !in eliminatedPlayers }
        if (remainingPlayers.size == 1) {
            val winner = remainingPlayers[0]
            _gameState.value = _gameState.value.copy(
                winner = winner,
                gamePhase = GamePhase.GAME_OVER,
                players = updatedPlayers,
                eliminatedPlayers = eliminatedPlayers
            )
            _uiState.value = _uiState.value.copy(
                message = "🎉 ${getPlayerLabel(winner)} wins the game! 🎉"
            )
            return
        }

        // Update state: reset skip tracker and advance to next player
        _gameState.value = _gameState.value.copy(
            skipTracker = emptyMap(),  // Reset skip tracker after attack
            eliminatedPlayers = eliminatedPlayers,
            players = updatedPlayers
        )

        nextPlayer()
    }

    /**
     * Skip the current player's turn
     */
    fun skipTurn() {
        val currentState = _gameState.value
        val currentPlayer = currentState.currentPlayerIndex
        val isBotSkip = isCurrentPlayerBot()

        // Mark current player as skipped
        val updatedSkipTracker = currentState.skipTracker + (currentPlayer to true)

        // Check if all non-eliminated players have skipped
        val activePlayerCount = currentState.map.playerCount - currentState.eliminatedPlayers.size
        val skipCount = (0 until currentState.map.playerCount)
            .filter { it !in currentState.eliminatedPlayers }
            .count { updatedSkipTracker[it] == true }

        _uiState.value = _uiState.value.copy(
            message = "${getPlayerLabel(currentPlayer)} skipped. ($skipCount/$activePlayerCount players skipped)",
            skippedPlayers = _uiState.value.skippedPlayers + currentPlayer,  // Add to skipped set
            botAttackArrows = if (isCurrentPlayerHuman()) {
                emptyList()  // Human skip - clear bot arrows from previous round
            } else {
                _uiState.value.botAttackArrows  // Bot skip - keep existing arrows
            }
        )

        _gameState.value = currentState.copy(skipTracker = updatedSkipTracker)

        // Check if all active players have skipped → reinforcement phase
        if (skipCount >= activePlayerCount) {
            startReinforcementPhase()
        } else {
            nextPlayer()
        }
    }

    /**
     * Switch to the next player (skip eliminated players)
     */
    private fun nextPlayer() {
        val currentState = _gameState.value
        var nextPlayerIndex = (currentState.currentPlayerIndex + 1) % currentState.map.playerCount

        // Skip eliminated players
        while (nextPlayerIndex in currentState.eliminatedPlayers) {
            nextPlayerIndex = (nextPlayerIndex + 1) % currentState.map.playerCount
        }

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
            message = "Reinforcement Phase: All players skipped. Distributing reinforcements...",
            botAttackArrows = emptyList(),  // Clear bot attack arrows for new round
            skippedPlayers = emptySet()  // Clear skipped players for new round
        )
    }

    /**
     * Execute the reinforcement phase for all players
     */
    fun executeReinforcementPhase() {
        val currentState = _gameState.value
        if (currentState.gamePhase != GamePhase.REINFORCEMENT) return

        val messages = mutableListOf<String>()

        // Reinforce all non-eliminated players
        for (playerId in 0 until currentState.map.playerCount) {
            // Skip eliminated players
            if (playerId in currentState.eliminatedPlayers) continue

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

        // Return to attack phase with new PlayerState copies and reset skip tracker
        _gameState.value = currentState.copy(
            gamePhase = GamePhase.ATTACK,
            skipTracker = emptyMap(),  // Reset skip tracker for new round
            players = Array(currentState.map.playerCount) { playerId ->
                currentState.players[playerId].copy()
            }
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
    fun isGameOver(): Boolean = _gameState.value.gamePhase == GamePhase.GAME_OVER

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
