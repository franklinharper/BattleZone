package com.franklinharper.battlezone

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
    private val onStateChange: () -> Unit = {}
) {
    private var _gameState: GameState = createInitialGameState(initialMap)
    private var _uiState: GameUiState = GameUiState()

    val gameState: GameState get() = _gameState
    val uiState: GameUiState get() = _uiState

    /**
     * Check if the current player is human
     */
    fun isCurrentPlayerHuman(): Boolean {
        return gameMode == GameMode.HUMAN_VS_BOT && _gameState.currentPlayerIndex == humanPlayerId
    }

    /**
     * Check if the current player is a bot
     */
    fun isCurrentPlayerBot(): Boolean {
        return gameMode == GameMode.BOT_VS_BOT || _gameState.currentPlayerIndex != humanPlayerId
    }

    private fun notifyStateChanged() {
        onStateChange()
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
        if (_gameState.gamePhase != GamePhase.ATTACK) return
        if (_gameState.winner != null) return

        val currentPlayer = _gameState.currentPlayerIndex
        val bot = if (currentPlayer == 0) bot0 else bot1
        val decision = bot.decide(_gameState.map, currentPlayer)

        _uiState = _uiState.copy(
            currentBotDecision = decision,
            message = when (decision) {
                is BotDecision.Attack -> "Player $currentPlayer attacks: Territory ${decision.fromTerritoryId} → ${decision.toTerritoryId}"
                is BotDecision.Skip -> "Player $currentPlayer skips their turn"
            }
        )
        notifyStateChanged()
    }

    /**
     * Execute the current bot's decision (attack or skip)
     */
    fun executeBotDecision() {
        val decision = _uiState.currentBotDecision ?: return

        when (decision) {
            is BotDecision.Attack -> executeAttack(decision.fromTerritoryId, decision.toTerritoryId)
            is BotDecision.Skip -> skipTurn()
        }

        // Clear the decision after execution
        _uiState = _uiState.copy(currentBotDecision = null)
        notifyStateChanged()
    }

    /**
     * Execute an attack from one territory to another
     */
    private fun executeAttack(fromTerritoryId: Int, toTerritoryId: Int) {
        val fromTerritory = _gameState.map.territories.getOrNull(fromTerritoryId) ?: return
        val toTerritory = _gameState.map.territories.getOrNull(toTerritoryId) ?: return

        // Validate attack
        if (fromTerritory.owner != _gameState.currentPlayerIndex) return
        if (fromTerritory.armyCount <= 1) return
        if (!fromTerritory.adjacentTerritories[toTerritoryId]) return
        if (toTerritory.owner == _gameState.currentPlayerIndex) return

        // Roll dice
        val attackerRoll = _gameState.map.gameRandom.rollDice(fromTerritory.armyCount)
        val defenderRoll = _gameState.map.gameRandom.rollDice(toTerritory.armyCount)

        val attackerTotal = attackerRoll.sum()
        val defenderTotal = defenderRoll.sum()
        val attackerWins = attackerTotal > defenderTotal

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

            _uiState = _uiState.copy(
                lastCombatResult = combatResult,
                message = "Player ${_gameState.currentPlayerIndex} wins! " +
                        "Attacker: ${attackerRoll.joinToString("+")} = $attackerTotal | " +
                        "Defender: ${defenderRoll.joinToString("+")} = $defenderTotal"
            )
        } else {
            // Defender wins: attacker loses all armies except 1
            fromTerritory.armyCount = 1

            _uiState = _uiState.copy(
                lastCombatResult = combatResult,
                message = "Player ${toTerritory.owner} defends! " +
                        "Attacker: ${attackerRoll.joinToString("+")} = $attackerTotal | " +
                        "Defender: ${defenderRoll.joinToString("+")} = $defenderTotal"
            )
        }

        // Update player states
        GameLogic.updatePlayerState(_gameState.map, _gameState.players[0], 0)
        GameLogic.updatePlayerState(_gameState.map, _gameState.players[1], 1)

        // Check for victory
        checkVictory()

        if (_gameState.winner == null) {
            // Reset consecutive skips since an attack occurred
            _gameState = _gameState.copy(consecutiveSkips = 0)

            // Switch to next player
            nextPlayer()
        }

        notifyStateChanged()
    }

    /**
     * Skip the current player's turn
     */
    fun skipTurn() {
        val newConsecutiveSkips = _gameState.consecutiveSkips + 1

        _uiState = _uiState.copy(
            message = "Player ${_gameState.currentPlayerIndex} skipped. " +
                    "Consecutive skips: $newConsecutiveSkips"
        )

        _gameState = _gameState.copy(consecutiveSkips = newConsecutiveSkips)

        // Check if both players have skipped consecutively
        if (newConsecutiveSkips >= 2) {
            startReinforcementPhase()
        } else {
            nextPlayer()
        }

        notifyStateChanged()
    }

    /**
     * Switch to the next player
     */
    private fun nextPlayer() {
        val nextPlayerIndex = (_gameState.currentPlayerIndex + 1) % _gameState.map.playerCount
        _gameState = _gameState.copy(currentPlayerIndex = nextPlayerIndex)
    }

    /**
     * Check if the current player has won (owns all territories)
     */
    private fun checkVictory() {
        val currentPlayer = _gameState.currentPlayerIndex
        val allTerritoriesOwned = _gameState.map.territories.all { territory ->
            territory.size == 0 || territory.owner == currentPlayer
        }

        if (allTerritoriesOwned) {
            _gameState = _gameState.copy(
                winner = currentPlayer,
                gamePhase = GamePhase.GAME_OVER
            )
            _uiState = _uiState.copy(
                message = "🎉 Player $currentPlayer wins the game! 🎉"
            )
        }
    }

    /**
     * Start the reinforcement phase
     */
    private fun startReinforcementPhase() {
        _gameState = _gameState.copy(gamePhase = GamePhase.REINFORCEMENT)
        _uiState = _uiState.copy(
            message = "Reinforcement Phase: Both players skipped. Distributing reinforcements..."
        )
    }

    /**
     * Execute the reinforcement phase for both players
     */
    fun executeReinforcementPhase() {
        if (_gameState.gamePhase != GamePhase.REINFORCEMENT) return

        val messages = mutableListOf<String>()

        // Reinforce both players
        for (playerId in 0 until _gameState.map.playerCount) {
            val playerState = _gameState.players[playerId]
            val reinforcements = GameLogic.calculateReinforcements(_gameState.map, playerId)

            val newReserve = GameLogic.distributeReinforcements(
                _gameState.map,
                playerId,
                reinforcements,
                playerState.reserveArmies
            )

            playerState.reserveArmies = newReserve
            GameLogic.updatePlayerState(_gameState.map, playerState, playerId)

            messages.add("Player $playerId: +$reinforcements armies" +
                if (newReserve > 0) " (Reserve: $newReserve)" else "")
        }

        _uiState = _uiState.copy(
            message = "Reinforcements: ${messages.joinToString(" | ")}"
        )

        // Return to attack phase
        _gameState = _gameState.copy(
            gamePhase = GamePhase.ATTACK,
            consecutiveSkips = 0
        )

        notifyStateChanged()
    }

    /**
     * Reset to a new game
     */
    fun newGame(map: GameMap) {
        _gameState = createInitialGameState(map)
        _uiState = GameUiState(message = "New game started! Player ${_gameState.currentPlayerIndex} goes first.")
        notifyStateChanged()
    }

    /**
     * Check if the game is over
     */
    fun isGameOver(): Boolean = _gameState.winner != null

    /**
     * Get the current player index
     */
    fun getCurrentPlayer(): Int = _gameState.currentPlayerIndex

    /**
     * Human player selects a territory (for attack)
     */
    fun selectTerritory(territoryId: Int) {
        if (!isCurrentPlayerHuman()) {
            _uiState = _uiState.copy(errorMessage = "Not your turn!")
            notifyStateChanged()
            return
        }

        if (_gameState.gamePhase != GamePhase.ATTACK) {
            _uiState = _uiState.copy(errorMessage = "Cannot attack during ${_gameState.gamePhase} phase")
            notifyStateChanged()
            return
        }

        val territory = _gameState.map.territories.getOrNull(territoryId)
        if (territory == null) {
            _uiState = _uiState.copy(errorMessage = "Invalid territory")
            notifyStateChanged()
            return
        }

        // If no territory is selected yet, select this one (if it's owned by human)
        if (_uiState.selectedTerritoryId == null) {
            if (territory.owner != humanPlayerId) {
                _uiState = _uiState.copy(errorMessage = "You don't own this territory")
                notifyStateChanged()
                return
            }

            if (territory.armyCount <= 1) {
                _uiState = _uiState.copy(errorMessage = "Territory must have more than 1 army to attack")
                notifyStateChanged()
                return
            }

            _uiState = _uiState.copy(
                selectedTerritoryId = territoryId,
                errorMessage = null,
                message = "Territory $territoryId selected. Now select an adjacent enemy territory to attack."
            )
            notifyStateChanged()
        } else {
            // A territory is already selected, so this is the target
            val fromTerritoryId = _uiState.selectedTerritoryId!!
            val fromTerritory = _gameState.map.territories[fromTerritoryId]

            // Validate the attack
            if (territory.owner == humanPlayerId) {
                _uiState = _uiState.copy(errorMessage = "Cannot attack your own territory")
                notifyStateChanged()
                return
            }

            if (!fromTerritory.adjacentTerritories[territoryId]) {
                _uiState = _uiState.copy(errorMessage = "Territories are not adjacent")
                notifyStateChanged()
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
        _uiState = _uiState.copy(
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
        _uiState = _uiState.copy(
            selectedTerritoryId = null,
            errorMessage = null,
            message = "Selection cancelled"
        )
        notifyStateChanged()
    }
}
