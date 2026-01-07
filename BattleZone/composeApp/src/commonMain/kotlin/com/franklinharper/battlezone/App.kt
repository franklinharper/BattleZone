package com.franklinharper.battlezone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.franklinharper.battlezone.presentation.screens.GameScreen
import com.franklinharper.battlezone.presentation.screens.ModeSelectionScreen
import com.franklinharper.battlezone.presentation.screens.PlayerCountSelectionScreen
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Main application entry point
 */
@Composable
@Preview
fun App() {
    MaterialTheme {
        var selectedMode by remember { mutableStateOf<GameMode?>(null) }
        var gameConfig by remember { mutableStateOf<GameConfig?>(null) }
        var viewModel by remember { mutableStateOf<GameViewModel?>(null) }

        when {
            // Level 1: Mode selection (Human vs Bot, Bot vs Bot)
            selectedMode == null -> {
                ModeSelectionScreen(
                    onModeSelected = { mode ->
                        selectedMode = mode
                    }
                )
            }

            // Level 2: Player count selection (2-8 players)
            gameConfig == null -> {
                PlayerCountSelectionScreen(
                    gameMode = selectedMode!!,
                    onPlayerCountSelected = { playerCount ->
                        gameConfig = GameConfig(selectedMode!!, playerCount)

                        // Generate map with the selected player count
                        val initialMap = MapGenerator.generate(playerCount = playerCount)

                        // Create bots array
                        val bots: Array<Bot> = Array(
                            if (selectedMode == GameMode.HUMAN_VS_BOT)
                                playerCount - 1  // Bots for players 1-N
                            else
                                playerCount      // All players are bots
                        ) { DefaultBot(initialMap.gameRandom) }

                        // Create view model with new configuration
                        viewModel = GameViewModel(
                            initialMap = initialMap,
                            gameMode = selectedMode!!,
                            humanPlayerId = 0,
                            bots = bots
                        )
                    },
                    onBack = {
                        selectedMode = null
                    }
                )
            }

            // Level 3: Game screen
            else -> {
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
                        gameMode = selectedMode!!,
                        isCurrentPlayerBot = vm.isCurrentPlayerBot(),
                        gamePhase = gameState.gamePhase,
                        hasBotDecision = uiState.currentBotDecision != null
                    )
                }

                // Handle turn actions from coordinator
                LaunchedEffect(Unit) {
                    turnCoordinator.actions.collectLatest { action ->
                        when (action) {
                            TurnCoordinatorAction.RequestBotDecision -> vm.requestBotDecision()
                            TurnCoordinatorAction.ExecuteBotDecision -> vm.executeBotDecision()
                            TurnCoordinatorAction.ExecuteReinforcement -> vm.executeReinforcementPhase()
                        }
                    }
                }

                GameScreen(
                    viewModel = vm,
                    gameMode = selectedMode!!,
                    onBackToMenu = {
                        selectedMode = null
                        gameConfig = null
                        viewModel = null
                    }
                )
                }
            }
        }
    }
}
