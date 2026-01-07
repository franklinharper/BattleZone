package com.franklinharper.battlezone

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Action to be taken during a turn
 */
sealed class TurnCoordinatorAction {
    /** Request bot to make a decision */
    object RequestBotDecision : TurnCoordinatorAction()

    /** Execute the bot's decision */
    object ExecuteBotDecision : TurnCoordinatorAction()

    /** Execute reinforcement phase */
    object ExecuteReinforcement : TurnCoordinatorAction()
}

/**
 * Configuration for turn timing
 */
data class TurnTiming(
    val decisionDelay: Long = 0L,    // No delay - instant bot decisions
    val executionDelay: Long = 0L,   // No delay - instant execution (animations show feedback)
    val reinforcementDelay: Long = 500L
)

/**
 * Coordinates turn execution using a proper state machine instead of hard-coded delays.
 *
 * Benefits:
 * - No hard-coded delays in UI layer
 * - Proper sequencing of actions
 * - Configurable timing
 * - Testable turn logic
 */
class TurnCoordinator(
    private val scope: CoroutineScope,
    private val timing: TurnTiming = TurnTiming()
) {
    private val _actions = MutableSharedFlow<TurnCoordinatorAction>()
    val actions: SharedFlow<TurnCoordinatorAction> = _actions.asSharedFlow()

    /**
     * Start coordinating turns for the given game state
     */
    fun coordinateTurn(
        gameMode: GameMode,
        isCurrentPlayerBot: Boolean,
        gamePhase: GamePhase,
        hasBotDecision: Boolean
    ) {
        if (gamePhase == GamePhase.REINFORCEMENT) {
            scope.launch {
                delay(timing.reinforcementDelay)
                _actions.emit(TurnCoordinatorAction.ExecuteReinforcement)
            }
            return
        }

        // Only coordinate bot turns in Human vs Bot mode
        if (gameMode != GameMode.HUMAN_VS_BOT || !isCurrentPlayerBot) {
            return
        }

        scope.launch {
            when (gamePhase) {
                GamePhase.ATTACK -> {
                    if (!hasBotDecision) {
                        // Delay before requesting decision (visual feedback for turn change)
                        delay(timing.decisionDelay)
                        _actions.emit(TurnCoordinatorAction.RequestBotDecision)
                    } else {
                        // Delay before executing (show highlighted attack to user)
                        delay(timing.executionDelay)
                        _actions.emit(TurnCoordinatorAction.ExecuteBotDecision)
                    }
                }
                else -> {
                    // No action needed for other phases
                }
            }
        }
    }
}
