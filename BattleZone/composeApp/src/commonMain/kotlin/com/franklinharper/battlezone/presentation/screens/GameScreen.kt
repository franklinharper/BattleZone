package com.franklinharper.battlezone.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franklinharper.battlezone.*
import com.franklinharper.battlezone.presentation.components.BotAttackArrowOverlay
import com.franklinharper.battlezone.presentation.components.MapRenderer
import com.franklinharper.battlezone.presentation.components.PlayerStatsDisplay

/**
 * Main game screen showing the map and controls
 */
@Composable
fun GameScreen(viewModel: GameViewModel, gameMode: GameMode, onBackToMenu: () -> Unit) {
    // Collect state from StateFlows
    val gameState by viewModel.gameState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val isHumanVsBot = gameMode == GameMode.HUMAN_VS_BOT

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

    // Auto-execute reinforcements after 2 seconds when phase changes to REINFORCEMENT
    LaunchedEffect(gameState.gamePhase) {
        if (gameState.gamePhase == GamePhase.REINFORCEMENT) {
            delay(2000)
            viewModel.executeReinforcementPhase()
        }
    }

    // Create a stable click handler
    val territoryClickHandler = remember(gameMode) {
        if (gameMode == GameMode.HUMAN_VS_BOT) {
            { territoryId: Int ->
                println("Territory clicked: $territoryId, Current player: ${viewModel.getCurrentPlayer()}, Is human turn: ${viewModel.isCurrentPlayerHuman()}")
                viewModel.selectTerritory(territoryId)
            }
        } else {
            null
        }
    }

    var mapWidth by remember { mutableStateOf(0.dp) }
    var mapWidthPx by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .background(Color.White)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Text(
            if (isHumanVsBot) "BattleZone - Human vs Bot" else "BattleZone - Bot vs Bot",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(8.dp)
        )

        // Control buttons (left-aligned)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Button(onClick = onBackToMenu) {
                Text("← Back to Menu")
            }

            Button(
                onClick = {
                    val newMap = MapGenerator.generate()
                    viewModel.newGame(newMap)
                }
            ) {
                Text("New Game")
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
                                onTerritoryClick = territoryClickHandler
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
                            with(density) { ACTION_BUTTON_RESERVE_WIDTH.toPx() }
                        } else {
                            0f
                        }
                        val availableWidthPx = (mapWidthPx - actionReservePx).coerceAtLeast(0f)
                        val perPlayerWidthPx = availableWidthPx / gameState.map.playerCount.coerceAtLeast(1)
                        (perPlayerWidthPx / PLAYER_LABEL_FONT_DIVISOR).coerceIn(
                            MIN_BOTTOM_ROW_FONT_SIZE,
                            MAX_BOTTOM_ROW_FONT_SIZE
                        )
                    } else {
                        12f
                    }
                    val labelFontSize = baseFontSize.sp
                    val numberFontSize = (baseFontSize * 1.2f).sp
                    val buttonFontSize = baseFontSize.sp
                    val labelPaddingHorizontal = (baseFontSize * LABEL_HORIZONTAL_PADDING_SCALE).dp
                    val labelPaddingVertical = (baseFontSize * LABEL_VERTICAL_PADDING_SCALE).dp

                    // Connected territories (left side, can wrap)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        for (playerId in 0 until gameState.map.playerCount) {
                            val playerState = gameState.players[playerId]
                            val isEliminated = playerId in gameState.eliminatedPlayers
                            val playerColor = GameColors.getPlayerColor(playerId)

                            val label = when (gameMode) {
                                GameMode.HUMAN_VS_BOT -> if (playerId == 0) "Human" else "Bot $playerId"
                                GameMode.BOT_VS_BOT -> "Bot ${playerId + 1}"
                            }

                            // Player entry (kept together as a unit)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Colored box with player name
                                Box(
                                    modifier = Modifier
                                        .background(playerColor)
                                        .padding(
                                            horizontal = labelPaddingHorizontal,
                                            vertical = labelPaddingVertical
                                        )
                                ) {
                                    Text(
                                        text = label,
                                        color = Color.Black,
                                        fontSize = labelFontSize
                                    )
                                }

                                // Connected count (outside the box)
                                Text(
                                    text = playerState.largestConnectedSize.toString(),
                                    color = Color.Black,
                                    fontSize = numberFontSize
                                )
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
                                            Text("Player ${viewModel.getCurrentPlayer()}: Make Decision", fontSize = buttonFontSize)
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
                    .background(Color.Black.copy(alpha = 0.7f)),
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
                            color = if (humanWon) Color.Green else Color.Red
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
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Applying Reinforcements",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
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

// Constants for responsive layout
private const val ORIGINAL_CELL_WIDTH = 27f
private const val ORIGINAL_CELL_HEIGHT = 18f
private const val MIN_CELL_WIDTH = 10f
private const val MIN_CELL_HEIGHT = 7f
private const val ORIGINAL_FONT_SIZE = 12f
private const val MIN_FONT_SIZE = 8f
private const val MAX_FONT_SIZE = 16f
private const val MIN_BOTTOM_ROW_FONT_SIZE = 10f
private const val MAX_BOTTOM_ROW_FONT_SIZE = 28f
private const val PLAYER_LABEL_FONT_DIVISOR = 6f
private const val LABEL_HORIZONTAL_PADDING_SCALE = 0.6f
private const val LABEL_VERTICAL_PADDING_SCALE = 0.3f
private val ACTION_BUTTON_RESERVE_WIDTH = 190.dp

/**
 * Calculate optimal cell dimensions to fit the map within available space
 * while maintaining the hexagon aspect ratio
 */
fun calculateCellDimensions(
    availableWidthPx: Float,
    availableHeightPx: Float
): MapRenderingParams {
    // Calculate scale factors for both dimensions
    val scaleFromWidth = (availableWidthPx * 2f) / ((HexGrid.GRID_WIDTH * 2 + 1) * ORIGINAL_CELL_WIDTH)
    val scaleFromHeight = availableHeightPx / (HexGrid.GRID_HEIGHT * ORIGINAL_CELL_HEIGHT)

    // Use the smaller scale factor to ensure map fits in both dimensions
    val scale = kotlin.math.min(scaleFromWidth, scaleFromHeight)

    // Apply scale to original dimensions
    val cellWidth = ORIGINAL_CELL_WIDTH * scale
    val cellHeight = ORIGINAL_CELL_HEIGHT * scale

    // Apply minimum size constraints
    val finalCellWidth = kotlin.math.max(cellWidth, MIN_CELL_WIDTH)
    val finalCellHeight = kotlin.math.max(cellHeight, MIN_CELL_HEIGHT)

    // Calculate scaled font size
    val fontSize = calculateFontSize(finalCellWidth)

    return MapRenderingParams(finalCellWidth, finalCellHeight, fontSize)
}

/**
 * Calculate font size based on cell scaling factor
 */
fun calculateFontSize(cellWidth: Float): Float {
    val scaleFactor = cellWidth / ORIGINAL_CELL_WIDTH
    val scaledSize = ORIGINAL_FONT_SIZE * scaleFactor
    return scaledSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
}
