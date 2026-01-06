package com.franklinharper.battlezone.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .background(Color.LightGray)
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

        // Player stats and map side-by-side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Player stats - show all players
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (playerId in 0 until gameState.map.playerCount) {
                    val playerState = gameState.players[playerId]
                    val isEliminated = playerId in gameState.eliminatedPlayers
                    val isCurrentPlayer = playerId == gameState.currentPlayerIndex

                    val label = when (gameMode) {
                        GameMode.HUMAN_VS_BOT -> if (playerId == 0) "Human" else "Bot $playerId"
                        GameMode.BOT_VS_BOT -> "Bot ${playerId + 1}"
                    }

                    PlayerStatsDisplay(
                        playerIndex = playerId,
                        playerState = playerState,
                        label = label,
                        color = GameColors.getPlayerColor(playerId),
                        isEliminated = isEliminated,
                        isCurrentPlayer = isCurrentPlayer,
                        combatResult = uiState.playerCombatResults[playerId],
                        hasSkipped = playerId in uiState.skippedPlayers,
                        gameMode = gameMode
                    )
                }
            }

            // Map and action buttons column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Map rendering with animation overlay
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
            val density = LocalDensity.current
            val renderParams = with(density) {
                calculateCellDimensions(maxWidth.toPx(), maxHeight.toPx())
            }

            Box {
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

                // Action buttons (below map, right-aligned)
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    when {
                        viewModel.isGameOver() -> {
                            // Game over, no action button needed
                        }
                        gameState.gamePhase == GamePhase.REINFORCEMENT -> {
                            // Reinforcements applied automatically - no button needed
                        }
                        viewModel.isCurrentPlayerHuman() -> {
                            // Human player's turn - show Skip button
                            Button(
                                onClick = {
                                    viewModel.skipTurn()
                                }
                            ) {
                                Text("Skip Turn")
                            }
                        }
                        viewModel.isCurrentPlayerBot() -> {
                            // Bot's turn - show manual controls only in Bot vs Bot mode
                            if (gameMode == GameMode.BOT_VS_BOT) {
                                when {
                                    uiState.currentBotDecision == null -> {
                                        Button(
                                            onClick = {
                                                viewModel.requestBotDecision()
                                            }
                                        ) {
                                            Text("Player ${viewModel.getCurrentPlayer()}: Make Decision")
                                        }
                                    }
                                    uiState.currentBotDecision is BotDecision.Attack -> {
                                        Button(
                                            onClick = {
                                                viewModel.executeBotDecision()
                                            }
                                        ) {
                                            Text("Execute Attack")
                                        }
                                    }
                                    uiState.currentBotDecision is BotDecision.Skip -> {
                                        Button(
                                            onClick = {
                                                viewModel.executeBotDecision()
                                            }
                                        ) {
                                            Text("Skip Turn")
                                        }
                                    }
                                }
                            }
                            // In Human vs Bot mode, bot turns execute automatically
                        }
                    }
                }

                // Connected territories row (below action buttons)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    for (playerId in 0 until gameState.map.playerCount) {
                        val playerState = gameState.players[playerId]
                        val isEliminated = playerId in gameState.eliminatedPlayers
                        val playerColor = GameColors.getPlayerColor(playerId)

                        val label = when (gameMode) {
                            GameMode.HUMAN_VS_BOT -> if (playerId == 0) "Human" else "Bot $playerId"
                            GameMode.BOT_VS_BOT -> "Bot ${playerId + 1}"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Colored box with player name
                            Box(
                                modifier = Modifier
                                    .background(playerColor)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = Color.Black,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            // Connected count (outside the box)
                            Text(
                                text = playerState.largestConnectedSize.toString(),
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyMedium
                            )
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
