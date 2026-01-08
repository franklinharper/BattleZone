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
data class AttackArrow(
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
    val attackArrows: List<AttackArrow> = emptyList()
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

    private val undoStack = ArrayDeque<GameSnapshot>()
    private val redoStack = ArrayDeque<GameSnapshot>()
    private val recordedEvents = ArrayDeque<RecordedEvent>()
    private var recordingStartSnapshot: GameSnapshot = GameSnapshot(_gameState.value.deepCopy(), _uiState.value.deepCopy())
    private var recordingEnabled = true

    private val _replayMode = MutableStateFlow(false)
    val replayMode: StateFlow<Boolean> = _replayMode.asStateFlow()

    private val _playbackInfo = MutableStateFlow(PlaybackInfo(index = 0, total = 1))
    val playbackInfo: StateFlow<PlaybackInfo> = _playbackInfo.asStateFlow()

    init {
        resetHistory()
    }

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
            skipTracker = emptySet(),
            winner = null
        )
    }

    /**
     * Request the current bot to make a decision
     */
    fun requestBotDecision() {
        if (!canMutateGame()) return
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
                is BotDecision.Attack -> "${playerLabel(currentPlayer, gameMode)} attacks: Territory ${decision.fromTerritoryId} → ${decision.toTerritoryId}"
                is BotDecision.Skip -> "${playerLabel(currentPlayer, gameMode)} skips their turn"
            }
        )
    }

    /**
     * Execute the current bot's decision (attack or skip)
     */
    fun executeBotDecision() {
        if (!canMutateGame()) return
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
        if (fromTerritory.armyCount < GameRules.MIN_ARMIES_TO_ATTACK) return
        if (!fromTerritory.adjacentTerritories[toTerritoryId]) return
        if (toTerritory.owner == currentGameState.currentPlayerIndex) return

        val combatResult = GameLogic.resolveAttack(fromTerritory, toTerritory, currentGameState.map.gameRandom)

        applyAttackOutcome(
            fromTerritoryId = fromTerritoryId,
            toTerritoryId = toTerritoryId,
            combatResult = combatResult,
            mutateMap = false
        )

        recordAction(
            RecordedEvent.Attack(
                fromTerritoryId = fromTerritoryId,
                toTerritoryId = toTerritoryId,
                result = combatResult.toRecordedCombatResult()
            )
        )
        recordSnapshot()
    }

    /**
     * Skip the current player's turn
     */
    fun skipTurn() {
        if (!canMutateGame()) return
        val currentState = _gameState.value
        val currentPlayer = currentState.currentPlayerIndex
        val isBotSkip = isCurrentPlayerBot()

        // Mark current player as skipped
        val updatedSkipTracker = currentState.skipTracker + currentPlayer

        // Check if all non-eliminated players have skipped
        val activePlayerCount = currentState.map.playerCount - currentState.eliminatedPlayers.size
        val skipCount = (updatedSkipTracker - currentState.eliminatedPlayers).size

        _uiState.value = _uiState.value.copy(
            message = "${playerLabel(currentPlayer, gameMode)} skipped. ($skipCount/$activePlayerCount players skipped)",
            skippedPlayers = _uiState.value.skippedPlayers + currentPlayer,  // Add to skipped set
            attackArrows = if (isCurrentPlayerHuman()) {
                emptyList()  // Human skip - clear bot arrows from previous round
            } else {
                _uiState.value.attackArrows  // Bot skip - keep existing arrows
            }
        )

        _gameState.value = currentState.copy(skipTracker = updatedSkipTracker)

        // Check if all active players have skipped → reinforcement phase
        if (skipCount >= activePlayerCount) {
            startReinforcementPhase()
        } else {
            nextPlayer()
        }
        recordAction(RecordedEvent.Skip(playerId = currentPlayer))
        recordSnapshot()
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
                message = "🎉 ${playerLabel(currentPlayer, gameMode)} wins the game! 🎉"
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
            attackArrows = emptyList(),  // Clear attack arrows for new round
            skippedPlayers = emptySet()  // Clear skipped players for new round
        )
    }

    /**
     * Execute the reinforcement phase for all players
     */
    fun executeReinforcementPhase() {
        if (!canMutateGame()) return
        val currentState = _gameState.value
        if (currentState.gamePhase != GamePhase.REINFORCEMENT) return

        val messages = mutableListOf<String>()
        val reinforcementResults = mutableListOf<RecordedReinforcementResult>()

        // Reinforce all non-eliminated players
        for (playerId in 0 until currentState.map.playerCount) {
            // Skip eliminated players
            if (playerId in currentState.eliminatedPlayers) continue

            val playerState = currentState.players[playerId]
            val reinforcements = GameLogic.calculateReinforcements(currentState.map, playerId)

            val distribution = GameLogic.distributeReinforcementsWithLog(
                currentState.map,
                playerId,
                reinforcements,
                playerState.reserveArmies
            )

            playerState.reserveArmies = distribution.reserveArmies
            GameLogic.updatePlayerState(currentState.map, playerState, playerId)

            messages.add("${playerLabel(playerId, gameMode)}: +$reinforcements armies" +
                if (distribution.reserveArmies > 0) " (Reserve: ${distribution.reserveArmies})" else "")

            reinforcementResults.add(
                RecordedReinforcementResult(
                    playerId = playerId,
                    territoryIncrements = distribution.territoryIncrements,
                    reserveArmies = distribution.reserveArmies
                )
            )
        }

        _uiState.value = _uiState.value.copy(
            message = "Reinforcements: ${messages.joinToString(" | ")}"
        )

        // Return to attack phase with new PlayerState copies and reset skip tracker
        _gameState.value = currentState.copy(
            gamePhase = GamePhase.ATTACK,
            skipTracker = emptySet(),  // Reset skip tracker for new round
            players = Array(currentState.map.playerCount) { playerId ->
                currentState.players[playerId].copy()
            }
        )
        recordAction(RecordedEvent.Reinforcement(players = reinforcementResults))
        recordSnapshot()
    }

    /**
     * Reset to a new game
     */
    fun newGame(map: GameMap) {
        _gameState.value = createInitialGameState(map)
        _uiState.value = GameUiState(message = "New game started! ${playerLabel(_gameState.value.currentPlayerIndex, gameMode)} goes first.")
        _replayMode.value = false
        recordingEnabled = true
        resetHistory()
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
     * Human player selects a territory (for attack)
     */
    fun selectTerritory(territoryId: Int) {
        if (!canMutateGame()) return
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

            if (territory.armyCount < GameRules.MIN_ARMIES_TO_ATTACK) {
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
        if (!canMutateGame()) return
        _uiState.value = _uiState.value.copy(
            selectedTerritoryId = null,
            errorMessage = null,
            message = "Selection cancelled"
        )
    }

    fun undo() {
        if (undoStack.size <= 1) return
        val current = undoStack.removeLast()
        redoStack.addLast(current)
        val previous = undoStack.last()
        applySnapshot(previous)
        updatePlaybackInfo()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(next)
        applySnapshot(next)
        updatePlaybackInfo()
    }

    fun canUndo(): Boolean = undoStack.size > 1

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun exportRecordingJson(): String {
        val recordedStart = recordingStartSnapshot.deepCopy().toRecordedSnapshot()
        val recordedEventList = recordedEvents.toList()
        val recording = RecordedGame(
            gameMode = gameMode,
            humanPlayerId = humanPlayerId,
            initialSnapshot = recordedStart,
            events = recordedEventList
        )
        return RecordingSerializer.encode(recording)
    }

    fun importRecordingJson(json: String): Boolean {
        val recording = try {
            RecordingSerializer.decode(json)
        } catch (ex: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = "Recording file is invalid.")
            return false
        }

        if (recording.gameMode != gameMode || recording.humanPlayerId != humanPlayerId) {
            _uiState.value = _uiState.value.copy(errorMessage = "Recording does not match this game mode.")
            return false
        }

        val snapshots = if (recording.initialSnapshot != null) {
            RecordingReplayer.buildSnapshots(recording)
        } else if (recording.snapshots.isNotEmpty()) {
            recording.snapshots.map { it.toGameSnapshot().deepCopy() }
        } else {
            emptyList()
        }

        if (snapshots.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Recording has no replay data.")
            return false
        }

        undoStack.clear()
        redoStack.clear()
        undoStack.addLast(snapshots.first())
        redoStack.addAll(snapshots.drop(1).asReversed())
        applySnapshot(undoStack.last())
        updatePlaybackInfo()
        _replayMode.value = true
        recordingEnabled = false
        _uiState.value = _uiState.value.copy(
            message = "Recording loaded. Use Undo/Redo to step through.",
            errorMessage = null
        )
        return true
    }

    fun seekToIndex(index: Int) {
        if (!_replayMode.value) {
            _uiState.value = _uiState.value.copy(errorMessage = "Playback controls are only available in replay mode.")
            return
        }

        val maxIndex = playbackTimelineSize() - 1
        val target = index.coerceIn(0, maxIndex)
        var current = undoStack.size - 1

        while (current < target && redoStack.isNotEmpty()) {
            undoStack.addLast(redoStack.removeLast())
            current++
        }

        while (current > target && undoStack.size > 1) {
            redoStack.addLast(undoStack.removeLast())
            current--
        }

        applySnapshot(undoStack.last())
        updatePlaybackInfo()
    }

    fun setMessage(message: String?) {
        _uiState.value = _uiState.value.copy(message = message, errorMessage = null)
    }

    fun setErrorMessage(message: String?) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    private fun resetHistory() {
        undoStack.clear()
        redoStack.clear()
        recordedEvents.clear()
        recordingStartSnapshot = captureSnapshot()
        recordSnapshot()
    }

    private fun recordSnapshot() {
        val snapshot = captureSnapshot()
        undoStack.addLast(snapshot)
        redoStack.clear()
        updatePlaybackInfo()
    }

    private fun captureSnapshot(): GameSnapshot = GameSnapshot(
        gameState = _gameState.value.deepCopy(),
        uiState = _uiState.value.deepCopy()
    )

    private fun applySnapshot(snapshot: GameSnapshot) {
        val copy = snapshot.deepCopy()
        _gameState.value = copy.gameState
        _uiState.value = copy.uiState
    }

    private fun updatePlaybackInfo() {
        val total = playbackTimelineSize()
        val index = (undoStack.size - 1).coerceIn(0, maxOf(0, total - 1))
        _playbackInfo.value = PlaybackInfo(index = index, total = total)
    }

    private fun playbackTimelineSize(): Int = undoStack.size + redoStack.size

    private fun canMutateGame(): Boolean {
        if (!_replayMode.value) {
            return true
        }
        _uiState.value = _uiState.value.copy(errorMessage = "Replay mode is read-only.")
        return false
    }

    private fun recordAction(event: RecordedEvent) {
        if (!recordingEnabled) return
        val currentIndex = undoStack.size - 1
        while (recordedEvents.size > currentIndex) {
            recordedEvents.removeLast()
        }
        recordedEvents.addLast(event)
    }

    private fun applyAttackOutcome(
        fromTerritoryId: Int,
        toTerritoryId: Int,
        combatResult: CombatResult,
        mutateMap: Boolean
    ) {
        val currentGameState = _gameState.value
        val fromTerritory = currentGameState.map.territories.getOrNull(fromTerritoryId) ?: return
        val toTerritory = currentGameState.map.territories.getOrNull(toTerritoryId) ?: return

        if (mutateMap) {
            GameLogic.applyAttackResult(fromTerritory, toTerritory, combatResult)
        }

        val attackerPlayerId = combatResult.attackerPlayerId
        val defenderPlayerId = combatResult.defenderPlayerId
        val isBotAttack = isCurrentPlayerBot()

        if (combatResult.attackerWins) {
            val updatedCombatResults = _uiState.value.playerCombatResults + (attackerPlayerId to combatResult)
            debugLog { "DEBUG: Storing combat result for attacker $attackerPlayerId. Map now has ${updatedCombatResults.size} entries" }
            _uiState.value = _uiState.value.copy(
                playerCombatResults = updatedCombatResults,
                skippedPlayers = _uiState.value.skippedPlayers - attackerPlayerId,
                message = "${playerLabel(currentGameState.currentPlayerIndex, gameMode)} wins! " +
                    "Attacker: ${combatResult.attackerRoll.joinToString("+")} = ${combatResult.attackerTotal} | " +
                    "Defender: ${combatResult.defenderRoll.joinToString("+")} = ${combatResult.defenderTotal}",
                attackArrows = if (isBotAttack) {
                    _uiState.value.attackArrows + AttackArrow(fromTerritoryId, toTerritoryId, attackSucceeded = true)
                } else {
                    emptyList()
                }
            )
        } else {
            val updatedCombatResults = _uiState.value.playerCombatResults + (attackerPlayerId to combatResult)
            debugLog { "DEBUG: Storing combat result for attacker $attackerPlayerId. Map now has ${updatedCombatResults.size} entries" }
            _uiState.value = _uiState.value.copy(
                playerCombatResults = updatedCombatResults,
                skippedPlayers = _uiState.value.skippedPlayers - attackerPlayerId,
                message = "${playerLabel(toTerritory.owner, gameMode)} defends! " +
                    "Attacker: ${combatResult.attackerRoll.joinToString("+")} = ${combatResult.attackerTotal} | " +
                    "Defender: ${combatResult.defenderRoll.joinToString("+")} = ${combatResult.defenderTotal}",
                attackArrows = if (isBotAttack) {
                    _uiState.value.attackArrows + AttackArrow(fromTerritoryId, toTerritoryId, attackSucceeded = false)
                } else {
                    emptyList()
                }
            )
        }

        for (playerId in 0 until currentGameState.map.playerCount) {
            GameLogic.updatePlayerState(currentGameState.map, currentGameState.players[playerId], playerId)
        }

        val updatedPlayers = Array(currentGameState.map.playerCount) { playerId ->
            currentGameState.players[playerId].copy()
        }

        var eliminatedPlayers = currentGameState.eliminatedPlayers
        if (updatedPlayers[defenderPlayerId].territoryCount == 0) {
            eliminatedPlayers = eliminatedPlayers + defenderPlayerId
            debugLog { "DEBUG: Player $defenderPlayerId eliminated!" }
        }

        if (gameMode == GameMode.HUMAN_VS_BOT && humanPlayerId in eliminatedPlayers) {
            _gameState.value = _gameState.value.copy(
                gamePhase = GamePhase.GAME_OVER,
                winner = null,
                players = updatedPlayers,
                eliminatedPlayers = eliminatedPlayers
            )
            _uiState.value = _uiState.value.copy(
                message = "💀 ${playerLabel(humanPlayerId, gameMode)} eliminated! Game Over."
            )
            return
        }

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
                message = "🎉 ${playerLabel(winner, gameMode)} wins the game! 🎉"
            )
            return
        }

        _gameState.value = _gameState.value.copy(
            skipTracker = emptySet(),
            eliminatedPlayers = eliminatedPlayers,
            players = updatedPlayers
        )

        nextPlayer()
    }
}
