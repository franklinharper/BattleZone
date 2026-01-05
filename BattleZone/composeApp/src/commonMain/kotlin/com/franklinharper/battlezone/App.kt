package com.franklinharper.battlezone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.franklinharper.battlezone.presentation.screens.GameScreen
import com.franklinharper.battlezone.presentation.screens.ModeSelectionScreen
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.ui.tooling.preview.Preview

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

                // Turn coordinator for bot moves
                val scope = rememberCoroutineScope()
                val turnCoordinator = remember { TurnCoordinator(scope) }

                // Coordinate bot turns using proper state machine
                LaunchedEffect(vm.isCurrentPlayerBot(), gameState.gamePhase, uiState.currentBotDecision) {
                    turnCoordinator.coordinateTurn(
                        gameMode = gameMode!!,
                        isCurrentPlayerBot = vm.isCurrentPlayerBot(),
                        gamePhase = gameState.gamePhase,
                        hasBotDecision = uiState.currentBotDecision != null
                    )
                }

                // Handle turn actions from coordinator
                LaunchedEffect(Unit) {
                    turnCoordinator.actions.collectLatest { action ->
                        when (action) {
                            TurnAction.RequestBotDecision -> vm.requestBotDecision()
                            TurnAction.ExecuteBotDecision -> vm.executeBotDecision()
                            TurnAction.ExecuteReinforcement -> vm.executeReinforcementPhase()
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
