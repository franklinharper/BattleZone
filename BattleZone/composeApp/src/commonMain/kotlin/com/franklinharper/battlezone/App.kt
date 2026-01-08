package com.franklinharper.battlezone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.franklinharper.battlezone.presentation.screens.GameScreen
import com.franklinharper.battlezone.presentation.screens.GameScreenMode
import com.franklinharper.battlezone.presentation.screens.ModeSelectionScreen
import com.franklinharper.battlezone.presentation.screens.PlayerCountSelectionScreen
import kotlinx.coroutines.launch
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
        var viewModel by remember { mutableStateOf<GameViewModel?>(null) }
        var playbackMode by remember { mutableStateOf<GameMode?>(null) }
        var menuStatusMessage by remember { mutableStateOf<String?>(null) }
        val filePicker = rememberRecordingFilePicker()
        val scope = rememberCoroutineScope()

        when {
            playbackMode != null && viewModel != null -> {
                val playbackGameMode = playbackMode!!
                val vm = viewModel!!

                PlaybackCoordinator(
                    viewModel = vm,
                    gameMode = playbackGameMode,
                    onBackToMenu = {
                        playbackMode = null
                        viewModel = null
                        menuStatusMessage = null
                    }
                )
            }

            // Level 1: Mode selection (Human vs Bot, Bot vs Bot)
            selectedMode == null -> {
                ModeSelectionScreen(
                    onModeSelected = { mode ->
                        selectedMode = mode
                        menuStatusMessage = null
                    },
                    onLoadRecording = {
                        scope.launch {
                            val bytes = filePicker.loadRecording()
                            if (bytes == null) {
                                menuStatusMessage = "Recording load cancelled."
                                return@launch
                            }

                            val json = try {
                                RecordingCompression.decompressToJson(bytes)
                            } catch (ex: Exception) {
                                menuStatusMessage = "Recording file is invalid."
                                return@launch
                            }

                            val recording = try {
                                RecordingSerializer.decode(json)
                            } catch (ex: Exception) {
                                menuStatusMessage = "Recording file is invalid."
                                return@launch
                            }

                            val firstSnapshot = recording.initialSnapshot ?: recording.snapshots.firstOrNull()
                            if (firstSnapshot == null) {
                                menuStatusMessage = "Recording has no snapshots."
                                return@launch
                            }

                            val snapshot = firstSnapshot.toGameSnapshot()
                            val playerCount = snapshot.gameState.map.playerCount
                            val bots: Array<Bot> = Array(
                                if (recording.gameMode == GameMode.HUMAN_VS_BOT)
                                    playerCount - 1
                                else
                                    playerCount
                            ) { DefaultBot(GameRandom(snapshot.gameState.map.seed ?: 0L)) }

                            val newViewModel = GameViewModel(
                                initialMap = snapshot.gameState.map,
                                gameMode = recording.gameMode,
                                humanPlayerId = recording.humanPlayerId,
                                bots = bots
                            )

                            if (!newViewModel.importRecordingJson(json)) {
                                menuStatusMessage = "Failed to load recording."
                                return@launch
                            }

                            viewModel = newViewModel
                            playbackMode = recording.gameMode
                            menuStatusMessage = null
                        }
                    },
                    statusMessage = menuStatusMessage
                )
            }

            // Level 2: Player count selection (2-8 players)
            viewModel == null -> {
                PlayerCountSelectionScreen(
                    gameMode = selectedMode!!,
                    onPlayerCountSelected = { playerCount ->
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
                    val replayMode by vm.replayMode.collectAsState()

                    // Turn coordinator for bot moves
                    val scope = rememberCoroutineScope()
                    val turnCoordinator = remember { TurnCoordinator(scope) }

                    // Coordinate bot turns using proper state machine
                    LaunchedEffect(vm.isCurrentPlayerBot(), gameState.gamePhase, uiState.currentBotDecision, replayMode) {
                        if (!replayMode) {
                            turnCoordinator.coordinateTurn(
                                gameMode = selectedMode!!,
                                isCurrentPlayerBot = vm.isCurrentPlayerBot(),
                                gamePhase = gameState.gamePhase,
                                hasBotDecision = uiState.currentBotDecision != null
                            )
                        }
                    }

                    // Handle turn actions from coordinator
                    LaunchedEffect(Unit) {
                        turnCoordinator.actions.collectLatest { action ->
                            if (!replayMode) {
                                when (action) {
                                    TurnCoordinatorAction.RequestBotDecision -> vm.requestBotDecision()
                                    TurnCoordinatorAction.ExecuteBotDecision -> vm.executeBotDecision()
                                    TurnCoordinatorAction.ExecuteReinforcement -> vm.executeReinforcementPhase()
                                }
                            }
                        }
                    }

                    GameScreen(
                        viewModel = vm,
                        gameMode = selectedMode!!,
                        onBackToMenu = {
                            selectedMode = null
                            viewModel = null
                        },
                        screenMode = GameScreenMode.PLAY
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackCoordinator(
    viewModel: GameViewModel,
    gameMode: GameMode,
    onBackToMenu: () -> Unit
) {
    GameScreen(
        viewModel = viewModel,
        gameMode = gameMode,
        onBackToMenu = onBackToMenu,
        screenMode = GameScreenMode.PLAYBACK
    )
}
