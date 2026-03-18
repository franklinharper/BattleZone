package com.franklinharper.battlezone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.franklinharper.battlezone.presentation.screens.GameScreen
import com.franklinharper.battlezone.presentation.screens.GameScreenMode
import com.franklinharper.battlezone.presentation.screens.ModeSelectionScreen
import com.franklinharper.battlezone.presentation.screens.PlayerCountSelectionScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Main application entry point
 */
@Composable
@Preview
fun App() {
    App(onGameEvent = {})
}

@Composable
fun App(
    onGameEvent: (GameEvent) -> Unit
) {
    MaterialTheme {
        var selectedMode by remember { mutableStateOf<GameMode?>(null) }
        var selectedTurnMode by remember { mutableStateOf(TurnMode.REAL_TIME) }
        var selectedRoundTimerSeconds by remember { mutableStateOf(DEFAULT_REALTIME_ROUND_TIMER_SECONDS) }
        var selectedPlayerCount by remember { mutableStateOf<Int?>(7) }
        var selectedAttackArrowRenderingOption by remember {
            mutableStateOf(AttackArrowRenderingOption.MIDPOINT_BADGE_CURRENT_COLORS)
        }
        var botDelayBaseSeconds by remember { mutableStateOf(UiConstants.DEFAULT_BOT_DELAY_BASE_SECONDS) }
        var botDelayDeltaSeconds by remember { mutableStateOf(UiConstants.DEFAULT_BOT_DELAY_DELTA_SECONDS) }
        var botDelayBaseText by remember { mutableStateOf(UiConstants.DEFAULT_BOT_DELAY_BASE_SECONDS.toString()) }
        var botDelayDeltaText by remember { mutableStateOf(UiConstants.DEFAULT_BOT_DELAY_DELTA_SECONDS.toString()) }
        var viewModel by remember { mutableStateOf<GameViewModel?>(null) }
        var playbackMode by remember { mutableStateOf<GameMode?>(null) }
        var playbackTurnMode by remember { mutableStateOf<TurnMode?>(null) }
        var menuStatusMessage by remember { mutableStateOf<String?>(null) }
        val filePicker = rememberRecordingFilePicker()
        val scope = rememberCoroutineScope()

        when {
            playbackMode != null && playbackTurnMode != null && viewModel != null -> {
                val playbackGameMode = playbackMode!!
                val playbackModeSelection = playbackTurnMode!!
                val vm = viewModel!!

                PlaybackCoordinator(
                    viewModel = vm,
                    gameMode = playbackGameMode,
                    turnMode = playbackModeSelection,
                    botDelayBaseSeconds = botDelayBaseSeconds,
                    botDelayDeltaText = botDelayDeltaText,
                    onBackToMenu = {
                        playbackMode = null
                        playbackTurnMode = null
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
                                bots = bots,
                                turnMode = recording.turnMode,
                                roundTimerSeconds = recording.roundTimerSeconds
                            )

                            if (!newViewModel.importRecordingJson(json)) {
                                menuStatusMessage = "Failed to load recording."
                                return@launch
                            }

                            viewModel = newViewModel
                            playbackMode = recording.gameMode
                            playbackTurnMode = recording.turnMode
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
                    turnMode = selectedTurnMode,
                    roundTimerSeconds = selectedRoundTimerSeconds,
                    onTurnModeChanged = { selectedTurnMode = it },
                    onRoundTimerSecondsChanged = { selectedRoundTimerSeconds = it },
                    attackArrowRenderingOption = selectedAttackArrowRenderingOption,
                    onAttackArrowRenderingOptionChanged = { selectedAttackArrowRenderingOption = it },
                    botDelayBaseText = botDelayBaseText,
                    onBotDelayBaseTextChanged = { text ->
                        botDelayBaseText = text
                        val parsed = text.toFloatOrNull()
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(
                                UiConstants.BOT_DELAY_BASE_MIN_SECONDS,
                                UiConstants.BOT_DELAY_BASE_MAX_SECONDS
                            )
                            botDelayBaseSeconds = clamped
                            if (clamped != parsed) {
                                botDelayBaseText = clamped.toString()
                            }
                        }
                    },
                    botDelayDeltaText = botDelayDeltaText,
                    onBotDelayDeltaTextChanged = { text ->
                        botDelayDeltaText = text
                        val parsed = text.toFloatOrNull()
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(
                                UiConstants.BOT_DELAY_DELTA_MIN_SECONDS,
                                UiConstants.BOT_DELAY_DELTA_MAX_SECONDS
                            )
                            botDelayDeltaSeconds = clamped
                            if (clamped != parsed) {
                                botDelayDeltaText = clamped.toString()
                            }
                        }
                    },
                    selectedPlayerCount = selectedPlayerCount,
                    onPlayerCountChanged = { playerCount ->
                        selectedPlayerCount = playerCount
                    },
                    onStartGame = {
                        selectedPlayerCount?.let { playerCount ->
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
                                bots = bots,
                                turnMode = selectedTurnMode,
                                roundTimerSeconds = selectedRoundTimerSeconds
                            )
                        }
                    },
                    onBack = {
                        selectedMode = null
                        selectedPlayerCount = null
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
                    val realTimePaused by vm.realTimePaused.collectAsState()

                    // Turn coordinator for bot moves
                    val scope = rememberCoroutineScope()
                    val turnCoordinator = remember { TurnCoordinator(scope) }

                    // Coordinate bot turns using proper state machine
                    LaunchedEffect(
                        vm.isCurrentPlayerBot(),
                        gameState.currentPlayerIndex,
                        gameState.gamePhase,
                        uiState.currentBotDecision,
                        replayMode,
                        realTimePaused,
                        selectedTurnMode,
                        botDelayBaseSeconds,
                        botDelayDeltaSeconds
                    ) {
                        if (!replayMode) {
                            if (selectedTurnMode == TurnMode.REAL_TIME && realTimePaused) {
                                return@LaunchedEffect
                            }
                            turnCoordinator.coordinateTurn(
                                gameMode = selectedMode!!,
                                turnMode = selectedTurnMode,
                                isCurrentPlayerBot = vm.isCurrentPlayerBot(),
                                gamePhase = gameState.gamePhase,
                            hasBotDecision = uiState.currentBotDecision != null,
                            botDelayBaseSeconds = botDelayBaseSeconds,
                            botDelayDeltaSeconds = botDelayDeltaSeconds
                        )
                    }
                    }

                    // Handle turn actions from coordinator
                    LaunchedEffect(Unit) {
                        turnCoordinator.actions.collectLatest { action ->
                            if (!replayMode) {
                                if (selectedTurnMode == TurnMode.REAL_TIME && realTimePaused) {
                                    return@collectLatest
                                }
                                when (action) {
                                    TurnCoordinatorAction.RequestBotDecision -> vm.requestBotDecision()
                                    TurnCoordinatorAction.ExecuteBotDecision -> vm.executeBotDecision()
                                    TurnCoordinatorAction.ExecuteReinforcement -> vm.executeReinforcementPhase()
                                }
                            }
                        }
                    }

                    LaunchedEffect(vm) {
                        vm.events.collect { event ->
                            onGameEvent(event)
                        }
                    }

                    GameScreen(
                        viewModel = vm,
                        gameMode = selectedMode!!,
                        turnMode = selectedTurnMode,
                        attackArrowRenderingOption = selectedAttackArrowRenderingOption,
                        realTimePaused = realTimePaused,
                        botDelayBaseText = botDelayBaseText,
                        onBotDelayBaseTextChanged = { text ->
                            botDelayBaseText = text
                            val parsed = text.toFloatOrNull()
                            if (parsed != null) {
                                val clamped = parsed.coerceIn(
                                    UiConstants.BOT_DELAY_BASE_MIN_SECONDS,
                                    UiConstants.BOT_DELAY_BASE_MAX_SECONDS
                                )
                                botDelayBaseSeconds = clamped
                                if (clamped != parsed) {
                                    botDelayBaseText = clamped.toString()
                                }
                            }
                        },
                        botDelayDeltaText = botDelayDeltaText,
                        onBotDelayDeltaTextChanged = { text ->
                            botDelayDeltaText = text
                            val parsed = text.toFloatOrNull()
                            if (parsed != null) {
                                val clamped = parsed.coerceIn(
                                    UiConstants.BOT_DELAY_DELTA_MIN_SECONDS,
                                    UiConstants.BOT_DELAY_DELTA_MAX_SECONDS
                                )
                                botDelayDeltaSeconds = clamped
                                if (clamped != parsed) {
                                    botDelayDeltaText = clamped.toString()
                                }
                            }
                        },
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
    turnMode: TurnMode,
    botDelayBaseSeconds: Float,
    botDelayDeltaText: String,
    onBackToMenu: () -> Unit
) {
    GameScreen(
        viewModel = viewModel,
        gameMode = gameMode,
        turnMode = turnMode,
        attackArrowRenderingOption = AttackArrowRenderingOption.MIDPOINT_BADGE_CURRENT_COLORS,
        realTimePaused = false,
        botDelayBaseText = botDelayBaseSeconds.toString(),
        onBotDelayBaseTextChanged = {},
        botDelayDeltaText = botDelayDeltaText,
        onBotDelayDeltaTextChanged = {},
        onBackToMenu = onBackToMenu,
        screenMode = GameScreenMode.PLAYBACK
    )
}
