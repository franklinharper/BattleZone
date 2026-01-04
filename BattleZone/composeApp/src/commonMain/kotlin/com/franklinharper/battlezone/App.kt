package com.franklinharper.battlezone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.franklinharper.battlezone.presentation.screens.GameScreen
import com.franklinharper.battlezone.presentation.screens.ModeSelectionScreen
import org.jetbrains.compose.ui.tooling.preview.Preview

// Constants for bot turn delays (in milliseconds)
private const val BOT_DECISION_DELAY = 800L
private const val BOT_EXECUTION_DELAY = 1200L

/**
 * Main application entry point
 */
@Composable
@Preview
fun App() {
    MaterialTheme {
        var gameMode by remember { mutableStateOf<GameMode?>(null) }
        var gameController by remember { mutableStateOf<GameController?>(null) }
        var recompositionTrigger by remember { mutableStateOf(0) }

        // Show mode selection if no mode is chosen
        if (gameMode == null) {
            ModeSelectionScreen(
                onModeSelected = { selectedMode ->
                    gameMode = selectedMode
                    val initialMap = MapGenerator.generate()
                    gameController = GameController(
                        initialMap = initialMap,
                        gameMode = selectedMode,
                        humanPlayerId = 0,
                        onStateChange = {
                            recompositionTrigger++
                        }
                    )
                }
            )
        } else {
            // Trigger recomposition when state changes
            key(recompositionTrigger) {
                gameController?.let { controller ->
                    // Auto-execute bot turns in Human vs Bot mode
                    LaunchedEffect(controller.isCurrentPlayerBot(), controller.uiState.currentBotDecision) {
                        if (gameMode == GameMode.HUMAN_VS_BOT && controller.isCurrentPlayerBot()) {
                            if (controller.gameState.gamePhase == GamePhase.ATTACK) {
                                if (controller.uiState.currentBotDecision == null) {
                                    // Small delay so user can see turn change
                                    kotlinx.coroutines.delay(BOT_DECISION_DELAY)
                                    controller.requestBotDecision()
                                } else {
                                    // Small delay so user can see the highlighted attack
                                    kotlinx.coroutines.delay(BOT_EXECUTION_DELAY)
                                    controller.executeBotDecision()
                                }
                            }
                        }
                    }

                    GameScreen(
                        controller = controller,
                        gameMode = gameMode!!,
                        onBackToMenu = {
                            gameMode = null
                            gameController = null
                        }
                    )
                }
            }
        }
    }
}
