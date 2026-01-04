package com.franklinharper.battlezone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
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
        var viewModel by remember { mutableStateOf<GameViewModel?>(null) }

        // Show mode selection if no mode is chosen
        if (gameMode == null) {
            ModeSelectionScreen(
                onModeSelected = { selectedMode ->
                    gameMode = selectedMode
                    val initialMap = MapGenerator.generate()
                    viewModel = GameViewModel(
                        initialMap = initialMap,
                        gameMode = selectedMode,
                        humanPlayerId = 0
                    )
                }
            )
        } else {
            viewModel?.let { vm ->
                // Collect state from StateFlows
                val gameState by vm.gameState.collectAsState()
                val uiState by vm.uiState.collectAsState()

                // Auto-execute bot turns in Human vs Bot mode
                LaunchedEffect(vm.isCurrentPlayerBot(), uiState.currentBotDecision) {
                    if (gameMode == GameMode.HUMAN_VS_BOT && vm.isCurrentPlayerBot()) {
                        if (gameState.gamePhase == GamePhase.ATTACK) {
                            if (uiState.currentBotDecision == null) {
                                // Small delay so user can see turn change
                                kotlinx.coroutines.delay(BOT_DECISION_DELAY)
                                vm.requestBotDecision()
                            } else {
                                // Small delay so user can see the highlighted attack
                                kotlinx.coroutines.delay(BOT_EXECUTION_DELAY)
                                vm.executeBotDecision()
                            }
                        }
                    }
                }

                GameScreen(
                    viewModel = vm,
                    gameMode = gameMode!!,
                    onBackToMenu = {
                        gameMode = null
                        viewModel = null
                    }
                )
            }
        }
    }
}
