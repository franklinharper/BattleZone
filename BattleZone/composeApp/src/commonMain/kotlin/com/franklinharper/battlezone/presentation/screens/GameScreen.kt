package com.franklinharper.battlezone.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import com.franklinharper.battlezone.*
import com.franklinharper.battlezone.presentation.components.BotAttackArrowOverlay
import com.franklinharper.battlezone.presentation.components.MapRenderer
import com.franklinharper.battlezone.presentation.components.PlayerStatsDisplay
import com.franklinharper.battlezone.playerLabel
import com.franklinharper.battlezone.debugLog
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Main game screen showing the map and controls
 */
enum class GameScreenMode {
    PLAY,
    PLAYBACK
}

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    gameMode: GameMode,
    onBackToMenu: () -> Unit,
    screenMode: GameScreenMode = GameScreenMode.PLAY
) {
    // Collect state from StateFlows
    val gameState by viewModel.gameState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val replayMode by viewModel.replayMode.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()

    val isHumanVsBot = gameMode == GameMode.HUMAN_VS_BOT
    val allowInput = !replayMode
    val filePicker = rememberRecordingFilePicker()
    val coroutineScope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(UiConstants.PLAYBACK_SPEED_NORMAL) }

    // Show overlay while in reinforcement phase
    val showReinforcementOverlay = gameState.gamePhase == GamePhase.REINFORCEMENT

    // Game over overlay state
    var showGameOverOverlay by remember { mutableStateOf(false) }

    // Show game over overlay when game ends
    LaunchedEffect(viewModel.isGameOver()) {
        if (viewModel.isGameOver()) {
            showGameOverOverlay = true
        }
    }

    LaunchedEffect(isPlaying, playbackInfo.index, playbackInfo.total, playbackSpeed) {
        if (screenMode != GameScreenMode.PLAYBACK) return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect
        if (playbackInfo.total <= 1) return@LaunchedEffect

        val lastIndex = playbackInfo.total - 1
        if (playbackInfo.index >= lastIndex) {
            isPlaying = false
            return@LaunchedEffect
        }

        kotlinx.coroutines.delay((UiConstants.PLAYBACK_STEP_DELAY_MS / playbackSpeed).toLong())
        viewModel.seekToPlaybackIndex(playbackInfo.index + 1)
    }

    // Create a stable click handler
    val territoryClickHandler = remember(gameMode, allowInput) {
        if (gameMode == GameMode.HUMAN_VS_BOT && allowInput) {
            { territoryId: Int ->
                debugLog { "Territory clicked: $territoryId, Current player: ${viewModel.getCurrentPlayer()}, Is human turn: ${viewModel.isCurrentPlayerHuman()}" }
                viewModel.selectTerritory(territoryId)
            }
        } else {
            null
        }
    }

    var mapWidth by remember { mutableStateOf(0.dp) }
    var mapWidthPx by remember { mutableStateOf(0f) }
    var debugModeEnabled by remember { mutableStateOf(DebugFlags.enableLogs) }
    var seedText by remember { mutableStateOf("") }
    var debugCellIndex by remember { mutableStateOf<Int?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val applySeed: () -> Unit = applySeed@{
        val parsedSeed = seedText.trim().toLongOrNull() ?: return@applySeed
        val newMap = MapGenerator.generate(
            seed = parsedSeed,
            playerCount = gameState.map.playerCount
        )
        viewModel.newGame(newMap)
    }

    LaunchedEffect(gameState.map.seed) {
        seedText = gameState.map.seed?.toString().orEmpty()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .background(GameColors.ScreenBackground)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        val titleText = when {
            screenMode == GameScreenMode.PLAYBACK && isHumanVsBot -> "BattleZone Playback - Human vs Bot"
            screenMode == GameScreenMode.PLAYBACK -> "BattleZone Playback - Bot vs Bot"
            isHumanVsBot -> "BattleZone - Human vs Bot"
            else -> "BattleZone - Bot vs Bot"
        }
        Text(
            titleText,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(8.dp)
        )

        // Control buttons (left-aligned)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            when (screenMode) {
                GameScreenMode.PLAY -> {
                    Button(onClick = onBackToMenu) {
                        Text("← Back to Menu")
                    }

                    Button(
                        onClick = {
                            val newMap = MapGenerator.generate(playerCount = gameState.map.playerCount)
                            viewModel.newGame(newMap)
                        }
                    ) {
                        Text("New Game")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Debug")
                        Switch(
                            checked = debugModeEnabled,
                            onCheckedChange = { enabled ->
                                debugModeEnabled = enabled
                                DebugFlags.enableLogs = enabled
                                if (!enabled) {
                                    debugCellIndex = null
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    val undoLabel = if (replayMode) "Previous" else "Undo"
                    val redoLabel = if (replayMode) "Next" else "Redo"

                    Button(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo()
                    ) {
                        Text(undoLabel)
                    }

                    Button(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo()
                    ) {
                        Text(redoLabel)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val json = viewModel.exportRecordingJson()
                                val success = filePicker.saveRecording(json)
                                if (success) {
                                    viewModel.setMessage("Recording saved.")
                                } else {
                                    viewModel.setMessage("Recording save cancelled.")
                                }
                            }
                        }
                    ) {
                        Text("Save Recording")
                    }
                }
                GameScreenMode.PLAYBACK -> {
                    Button(onClick = onBackToMenu) {
                        Text("← Back to Menu")
                    }

                    Button(
                        onClick = { isPlaying = !isPlaying },
                        enabled = playbackInfo.total > 1
                    ) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }

                    Button(
                        onClick = { viewModel.seekToPlaybackIndex(playbackInfo.index - 1) },
                        enabled = playbackInfo.index > 0
                    ) {
                        Text("Step")
                    }

                    Button(
                        onClick = { viewModel.seekToPlaybackIndex(playbackInfo.index + 1) },
                        enabled = playbackInfo.index < playbackInfo.total - 1
                    ) {
                        Text("Next")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text("Speed")

                    val speedOptions = listOf(
                        UiConstants.PLAYBACK_SPEED_HALF to "0.5x",
                        UiConstants.PLAYBACK_SPEED_NORMAL to "1x",
                        UiConstants.PLAYBACK_SPEED_DOUBLE to "2x"
                    )
                    speedOptions.forEach { (speed, label) ->
                        val selected = playbackSpeed == speed
                        val buttonModifier = Modifier.padding(start = 4.dp)
                        if (selected) {
                            Button(
                                onClick = { playbackSpeed = speed },
                                modifier = buttonModifier
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { playbackSpeed = speed },
                                modifier = buttonModifier
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            if (debugModeEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Seed:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextField(
                        value = seedText,
                        onValueChange = { seedText = it },
                        singleLine = true,
                        modifier = Modifier.width(UiConstants.SEED_FIELD_WIDTH)
                    )
                    Button(onClick = applySeed) {
                        Text("Apply")
                    }
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(seedText)) }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy seed"
                        )
                    }
                    debugCellIndex?.let { cellIndex ->
                        Text(
                            text = "Cell: $cellIndex",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        val statusMessage = uiState.errorMessage ?: uiState.message
        if (statusMessage != null) {
            Text(
                text = statusMessage,
                color = if (uiState.errorMessage != null) GameColors.UiTextError else GameColors.UiTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (screenMode == GameScreenMode.PLAYBACK) {
            val maxIndex = (playbackInfo.total - 1).coerceAtLeast(0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${(playbackInfo.index + 1).coerceAtMost(playbackInfo.total)}/${playbackInfo.total}",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = playbackInfo.index.toFloat().coerceIn(0f, maxIndex.toFloat()),
                    valueRange = 0f..maxIndex.toFloat(),
                    onValueChange = { value ->
                        isPlaying = false
                        viewModel.seekToPlaybackIndex(value.roundToInt())
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Map and action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Map and action buttons column
            Column(
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Single BoxWithConstraints to measure available space and calculate map dimensions
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    val density = LocalDensity.current

                    // Calculate render parameters based on available space
                    val renderParams = with(density) {
                        calculateCellDimensions(maxWidth.toPx(), maxHeight.toPx())
                    }

                    // Calculate actual map width in pixels: formula from hex grid rendering
                    val calculatedMapWidthPx = ((HexGrid.GRID_WIDTH * 2 + 1) * renderParams.cellWidth) / 2
                    val calculatedMapWidth = with(density) { calculatedMapWidthPx.toDp() }
                    val calculatedMapHeight = with(density) {
                        (HexGrid.GRID_HEIGHT * renderParams.cellHeight).toDp()
                    }

                    if (calculatedMapWidth != mapWidth) {
                        mapWidth = calculatedMapWidth
                    }
                    if (calculatedMapWidthPx != mapWidthPx) {
                        mapWidthPx = calculatedMapWidthPx
                    }

                    Box(
                        modifier = Modifier.size(calculatedMapWidth, calculatedMapHeight),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Map with exact calculated width
                        Box(modifier = Modifier.width(calculatedMapWidth)) {
                            // Base map layer (always interactive)
                            MapRenderer(
                                map = gameState.map,
                                cellWidth = renderParams.cellWidth,
                                cellHeight = renderParams.cellHeight,
                                fontSize = renderParams.fontSize,
                                showTerritoryIds = debugModeEnabled,
                                showCellOutlines = debugModeEnabled,
                                highlightedTerritories = when {
                                    uiState.currentBotDecision is BotDecision.Attack -> {
                                        val decision = uiState.currentBotDecision as BotDecision.Attack
                                        setOf(decision.fromTerritoryId, decision.toTerritoryId)
                                    }
                                    uiState.selectedTerritoryId != null -> {
                                        setOf(uiState.selectedTerritoryId!!)
                                    }
                                    else -> emptySet()
                                },
                                attackFromTerritory = when (val decision = uiState.currentBotDecision) {
                                    is BotDecision.Attack -> decision.fromTerritoryId
                                    else -> uiState.selectedTerritoryId
                                },
                                onTerritoryClick = territoryClickHandler,
                                onCellClick = if (debugModeEnabled) {
                                    { cellIndex -> debugCellIndex = cellIndex }
                                } else {
                                    null
                                }
                            )

                            // Bot attack arrows overlay (does not block clicks) - show all bot attacks
                            uiState.botAttackArrows.forEach { arrow ->
                                BotAttackArrowOverlay(
                                    arrow = arrow,
                                    gameMap = gameState.map,
                                    cellWidth = renderParams.cellWidth,
                                    cellHeight = renderParams.cellHeight,
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                        }
                    }
                }

                val bottomRowModifier = if (mapWidthPx > 0f) {
                    Modifier.width(mapWidth)
                } else {
                    Modifier.fillMaxWidth()
                }

                val showActionButton = when {
                    viewModel.isGameOver() -> false
                    gameState.gamePhase == GamePhase.REINFORCEMENT -> false
                    replayMode -> false
                    viewModel.isCurrentPlayerHuman() -> true
                    viewModel.isCurrentPlayerBot() && gameMode == GameMode.BOT_VS_BOT -> true
                    else -> false
                }

                // Bottom row: exact same width as map
                Row(
                    modifier = bottomRowModifier.padding(top = 32.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val density = LocalDensity.current

                    // Calculate font sizes based on map width (not window width)
                    val baseFontSize = if (mapWidthPx > 0f) {
                        val actionReservePx = if (showActionButton) {
                            with(density) { UiConstants.ACTION_BUTTON_RESERVE_WIDTH.toPx() }
                        } else {
                            0f
                        }
                        val availableWidthPx = (mapWidthPx - actionReservePx).coerceAtLeast(0f)
                        val perPlayerWidthPx = availableWidthPx / gameState.map.playerCount.coerceAtLeast(1)
                        (perPlayerWidthPx / UiConstants.PLAYER_LABEL_FONT_DIVISOR).coerceIn(
                            UiConstants.MIN_BOTTOM_ROW_FONT_SIZE,
                            UiConstants.MAX_BOTTOM_ROW_FONT_SIZE
                        )
                    } else {
                        12f
                    }
                    val labelFontSize = baseFontSize.sp
                    val numberFontSize = (baseFontSize * 1.2f).sp
                    val buttonFontSize = baseFontSize.sp
                    val labelPaddingHorizontal = (baseFontSize * UiConstants.LABEL_HORIZONTAL_PADDING_SCALE).dp
                    val labelPaddingVertical = (baseFontSize * UiConstants.LABEL_VERTICAL_PADDING_SCALE).dp

                    // Connected territories (left side, can wrap)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        for (playerId in 0 until gameState.map.playerCount) {
                            val playerState = gameState.players[playerId]
                            val isEliminated = playerId in gameState.eliminatedPlayers
                            if (isEliminated) continue
                            val playerColor = GameColors.getPlayerColor(playerId)

                            val label = playerLabel(playerId, gameMode)

                            // Player entry (kept together as a unit)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Colored box with player name + reinforcements count.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(UiConstants.PLAYER_LABEL_CONTENT_SPACING),
                                    modifier = Modifier
                                        .background(
                                            color = playerColor,
                                            shape = UiConstants.PLAYER_LABEL_SHAPE
                                        )
                                        .border(
                                            width = UiConstants.PLAYER_LABEL_BORDER_WIDTH,
                                            color = UiConstants.PLAYER_LABEL_BORDER_COLOR,
                                            shape = UiConstants.PLAYER_LABEL_SHAPE
                                        )
                                        .padding(
                                            horizontal = labelPaddingHorizontal,
                                            vertical = labelPaddingVertical
                                        )
                                ) {
                                    Text(
                                        text = label,
                            color = GameColors.UiTextPrimary,
                            fontSize = labelFontSize
                        )
                        Text(
                            text = playerState.largestConnectedSize.toString(),
                            color = GameColors.UiTextPrimary,
                            fontSize = numberFontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                        }
                    }

                    // Action button (right side)
                    when {
                        !showActionButton -> {
                            // No action button needed
                        }
                        viewModel.isCurrentPlayerHuman() -> {
                            // Human player's turn - show Skip button
                            Button(
                                onClick = { viewModel.skipTurn() },
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Text("Skip Turn", fontSize = buttonFontSize)
                            }
                        }
                        viewModel.isCurrentPlayerBot() -> {
                            // Bot's turn - show manual controls only in Bot vs Bot mode
                            if (gameMode == GameMode.BOT_VS_BOT) {
                                when {
                                    uiState.currentBotDecision == null -> {
                                        Button(
                                            onClick = { viewModel.requestBotDecision() },
                                            modifier = Modifier.padding(start = 12.dp)
                                        ) {
                                            Text(
                                                "${playerLabel(viewModel.getCurrentPlayer(), gameMode)}: Make Decision",
                                                fontSize = buttonFontSize
                                            )
                                        }
                                    }
                                    uiState.currentBotDecision is BotDecision.Attack -> {
                                        Button(
                                            onClick = { viewModel.executeBotDecision() },
                                            modifier = Modifier.padding(start = 12.dp)
                                        ) {
                                            Text("Execute Attack", fontSize = buttonFontSize)
                                        }
                                    }
                                    uiState.currentBotDecision is BotDecision.Skip -> {
                                        Button(
                                            onClick = { viewModel.executeBotDecision() },
                                            modifier = Modifier.padding(start = 12.dp)
                                        ) {
                                            Text("Skip Turn", fontSize = buttonFontSize)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // Game over overlay
        if (showGameOverOverlay && viewModel.isGameOver()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GameColors.OverlayScrimStrong),
                contentAlignment = Alignment.Center
            ) {
                val winner = gameState.winner
                val humanWon = winner == 0 && gameMode == GameMode.HUMAN_VS_BOT
                val isHumanGame = gameMode == GameMode.HUMAN_VS_BOT

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Large emoji
                        Text(
                            text = if (humanWon) "🏆" else if (isHumanGame) "💀" else "🎮",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.dp.value.sp)
                        )

                        // Victory or Defeat text
                        Text(
                            text = if (humanWon) "VICTORY!" else if (isHumanGame) "DEFEAT" else "GAME OVER",
                            style = MaterialTheme.typography.displayLarge,
                            color = if (humanWon) GameColors.GameOverWin else GameColors.GameOverLoss
                        )
                    }

                    // OK button
                    Button(onClick = { showGameOverOverlay = false }) {
                        Text("OK")
                    }
                }
            }
        }

        // Reinforcement overlay
        if (showReinforcementOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GameColors.OverlayScrim),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Applying Reinforcements",
                    style = MaterialTheme.typography.displayMedium,
                    color = GameColors.UiTextInverted
                )
            }
        }
    }
}

/**
 * Data class to hold calculated rendering parameters
 */
data class MapRenderingParams(
    val cellWidth: Float,
    val cellHeight: Float,
    val fontSize: Float
)

/**
 * Calculate optimal cell dimensions to fit the map within available space
 * while maintaining the hexagon aspect ratio
 */
fun calculateCellDimensions(
    availableWidthPx: Float,
    availableHeightPx: Float
): MapRenderingParams {
    // Calculate scale factors for both dimensions
    val scaleFromWidth = (availableWidthPx * 2f) / ((HexGrid.GRID_WIDTH * 2 + 1) * UiConstants.ORIGINAL_CELL_WIDTH)
    val scaleFromHeight = availableHeightPx / (HexGrid.GRID_HEIGHT * UiConstants.ORIGINAL_CELL_HEIGHT)

    // Use the smaller scale factor to ensure map fits in both dimensions
    val scale = kotlin.math.min(scaleFromWidth, scaleFromHeight)

    // Apply scale to original dimensions
    val cellWidth = UiConstants.ORIGINAL_CELL_WIDTH * scale
    val cellHeight = UiConstants.ORIGINAL_CELL_HEIGHT * scale

    // Apply minimum size constraints
    val finalCellWidth = kotlin.math.max(cellWidth, UiConstants.MIN_CELL_WIDTH)
    val finalCellHeight = kotlin.math.max(cellHeight, UiConstants.MIN_CELL_HEIGHT)

    // Calculate scaled font size
    val fontSize = calculateFontSize(finalCellWidth)

    return MapRenderingParams(finalCellWidth, finalCellHeight, fontSize)
}

/**
 * Calculate font size based on cell scaling factor
 */
fun calculateFontSize(cellWidth: Float): Float {
    val scaleFactor = cellWidth / UiConstants.ORIGINAL_CELL_WIDTH
    val scaledSize = UiConstants.ORIGINAL_FONT_SIZE * scaleFactor
    return scaledSize.coerceIn(UiConstants.MIN_FONT_SIZE, UiConstants.MAX_FONT_SIZE)
}
