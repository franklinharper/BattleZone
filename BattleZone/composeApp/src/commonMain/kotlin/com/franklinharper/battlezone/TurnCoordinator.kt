package com.franklinharper.battlezone

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.random.Random

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
    val reinforcementDelay: Long = UiConstants.REINFORCEMENT_POPUP_DURATION_MS
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
        turnMode: TurnMode,
        isCurrentPlayerBot: Boolean,
        gamePhase: GamePhase,
        hasBotDecision: Boolean,
        botDelayBaseSeconds: Float,
        botDelayDeltaSeconds: Float
    ) {
        if (gamePhase == GamePhase.REINFORCEMENT) {
            scope.launch {
                delay(timing.reinforcementDelay)
                _actions.emit(TurnCoordinatorAction.ExecuteReinforcement)
            }
            return
        }

        val shouldCoordinateBots = when (turnMode) {
            TurnMode.REAL_TIME -> isCurrentPlayerBot
            TurnMode.TURN_BASED -> gameMode == GameMode.HUMAN_VS_BOT && isCurrentPlayerBot
        }

        if (!shouldCoordinateBots) {
            return
        }

        scope.launch {
            when (gamePhase) {
                GamePhase.ATTACK -> {
                    if (!hasBotDecision) {
                        val delayMs = if (turnMode == TurnMode.REAL_TIME) {
                            randomBotDelayMs(
                                baseSeconds = botDelayBaseSeconds,
                                deltaSeconds = botDelayDeltaSeconds
                            )
                        } else {
                            timing.decisionDelay
                        }
                        delay(delayMs)
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

    private fun randomBotDelayMs(baseSeconds: Float, deltaSeconds: Float): Long {
        val clampedBase = baseSeconds.coerceIn(
            UiConstants.BOT_DELAY_BASE_MIN_SECONDS,
            UiConstants.BOT_DELAY_BASE_MAX_SECONDS
        )
        val clampedDelta = deltaSeconds.coerceIn(
            UiConstants.BOT_DELAY_DELTA_MIN_SECONDS,
            UiConstants.BOT_DELAY_DELTA_MAX_SECONDS
        )
        val minSeconds = (clampedBase - clampedDelta).coerceAtLeast(0f)
        val maxSeconds = clampedBase + clampedDelta
        if (minSeconds >= maxSeconds) {
            return (minSeconds * MILLIS_PER_SECOND).roundToLong()
        }
        val delaySeconds = Random.nextDouble(minSeconds.toDouble(), maxSeconds.toDouble())
        return (delaySeconds * MILLIS_PER_SECOND).roundToLong()
    }
}
