package com.franklinharper.battlezone.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (isHumanVsBot) "BattleZone - Human vs Bot" else "BattleZone - Bot vs Bot",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(8.dp)
        )

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Button(
                onClick = {
                    val newMap = MapGenerator.generate()
                    viewModel.newGame(newMap)
                }
            ) {
                Text("New Game")
            }

            Button(onClick = onBackToMenu) {
                Text("Back to Menu")
            }

            // Game phase and mode-specific buttons
            when {
                viewModel.isGameOver() -> {
                    // Game over, no action button needed
                }
                gameState.gamePhase == GamePhase.REINFORCEMENT -> {
                    Button(
                        onClick = {
                            viewModel.executeReinforcementPhase()
                        }
                    ) {
                        Text("Apply Reinforcements")
                    }
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

        // Game status display
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Current turn indicator
            if (!viewModel.isGameOver()) {
                val currentPlayerColor = if (viewModel.getCurrentPlayer() == 0)
                    GameColors.Player0 else GameColors.Player1

                // Show "Your Turn" or "Bot's Turn" for human vs bot mode
                val turnText = when {
                    viewModel.isCurrentPlayerHuman() -> "YOUR TURN"
                    isHumanVsBot -> "BOT'S TURN"
                    else -> "Current Turn: Player ${viewModel.getCurrentPlayer()}"
                }

                Text(
                    turnText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = currentPlayerColor,
                    modifier = Modifier.padding(4.dp)
                )

                Text(
                    "Phase: ${gameState.gamePhase}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(4.dp)
                )
            }

            // Error message display
            uiState.errorMessage?.let { error ->
                Text(
                    "❌ $error",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Message display
            uiState.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (viewModel.isGameOver())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Combat result display
            uiState.lastCombatResult?.let { combat ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        "🎲 Combat Result:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Attacker: [${combat.attackerRoll.joinToString(", ")}] = ${combat.attackerTotal}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Defender: [${combat.defenderRoll.joinToString(", ")}] = ${combat.defenderTotal}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (combat.attackerWins) "✅ Attacker Wins!" else "🛡️ Defender Wins!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (combat.attackerWins)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Player stats
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            PlayerStatsDisplay(
                playerIndex = 0,
                playerState = gameState.players[0],
                label = if (isHumanVsBot) "Human (Purple)" else "Player 0 (Purple)",
                color = GameColors.Player0
            )

            PlayerStatsDisplay(
                playerIndex = 1,
                playerState = gameState.players[1],
                label = if (isHumanVsBot) "Bot (Green)" else "Player 1 (Green)",
                color = GameColors.Player1
            )
        }

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

                // Bot attack arrow overlay (does not block clicks)
                uiState.botAttackArrow?.let { arrow ->
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
