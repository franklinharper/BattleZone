package com.franklinharper.battlezone

import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the game that wraps GameController and provides lifecycle-aware state management.
 *
 * This class exposes StateFlows for reactive UI updates and delegates game logic to GameController.
 */
class GameViewModel(
    initialMap: GameMap,
    gameMode: GameMode = GameMode.BOT_VS_BOT,
    humanPlayerId: Int = 0
) {
    private val controller = GameController(
        initialMap = initialMap,
        gameMode = gameMode,
        humanPlayerId = humanPlayerId
    )

    /** Observable game state */
    val gameState: StateFlow<GameState> = controller.gameState

    /** Observable UI state */
    val uiState: StateFlow<GameUiState> = controller.uiState

    /** Observable game events */
    val events = controller.events

    // Game control methods

    /** Request the current bot to make a decision */
    fun requestBotDecision() = controller.requestBotDecision()

    /** Execute the current bot's pending decision */
    fun executeBotDecision() = controller.executeBotDecision()

    /** Execute the reinforcement phase */
    fun executeReinforcementPhase() = controller.executeReinforcementPhase()

    /** Start a new game with a fresh map */
    fun newGame(map: GameMap) = controller.newGame(map)

    /** Human player selects a territory */
    fun selectTerritory(territoryId: Int) = controller.selectTerritory(territoryId)

    /** Skip the current player's turn */
    fun skipTurn() = controller.skipTurn()

    /** Cancel the current territory selection */
    fun cancelSelection() = controller.cancelSelection()

    // Status methods

    /** Check if the game is over */
    fun isGameOver(): Boolean = controller.isGameOver()

    /** Get the current player index */
    fun getCurrentPlayer(): Int = controller.getCurrentPlayer()

    /** Check if the current player is human */
    fun isCurrentPlayerHuman(): Boolean = controller.isCurrentPlayerHuman()

    /** Check if the current player is a bot */
    fun isCurrentPlayerBot(): Boolean = controller.isCurrentPlayerBot()

    // Undo/Redo methods

    /** Check if undo is available */
    fun canUndo(): Boolean = controller.canUndo()

    /** Check if redo is available */
    fun canRedo(): Boolean = controller.canRedo()
}
