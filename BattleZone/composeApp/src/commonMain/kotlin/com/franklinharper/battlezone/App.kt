package com.franklinharper.battlezone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max
import kotlin.math.min

// Constants for responsive layout
private const val ORIGINAL_CELL_WIDTH = 27f
private const val ORIGINAL_CELL_HEIGHT = 18f
private const val MIN_CELL_WIDTH = 10f
private const val MIN_CELL_HEIGHT = 7f
private const val ORIGINAL_FONT_SIZE = 12f
private const val MIN_FONT_SIZE = 8f
private const val MAX_FONT_SIZE = 16f

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
    val scaleFromWidth = (availableWidthPx * 2f) / ((HexGrid.GRID_WIDTH * 2 + 1) * ORIGINAL_CELL_WIDTH)
    val scaleFromHeight = availableHeightPx / (HexGrid.GRID_HEIGHT * ORIGINAL_CELL_HEIGHT)

    // Use the smaller scale factor to ensure map fits in both dimensions
    val scale = min(scaleFromWidth, scaleFromHeight)

    // Apply scale to original dimensions
    val cellWidth = ORIGINAL_CELL_WIDTH * scale
    val cellHeight = ORIGINAL_CELL_HEIGHT * scale

    // Apply minimum size constraints
    val finalCellWidth = max(cellWidth, MIN_CELL_WIDTH)
    val finalCellHeight = max(cellHeight, MIN_CELL_HEIGHT)

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
                                    kotlinx.coroutines.delay(800)
                                    controller.requestBotDecision()
                                } else {
                                    // Small delay so user can see the highlighted attack
                                    kotlinx.coroutines.delay(1200)
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

@Composable
fun ModeSelectionScreen(onModeSelected: (GameMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "BattleZone",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        Button(
            onClick = { onModeSelected(GameMode.HUMAN_VS_BOT) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Human vs Bot", style = MaterialTheme.typography.headlineSmall)
        }

        Button(
            onClick = { onModeSelected(GameMode.BOT_VS_BOT) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Bot vs Bot", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun GameScreen(controller: GameController, gameMode: GameMode, onBackToMenu: () -> Unit) {
    // Access state - Compose will read these during composition
    val gameState = controller.gameState
    val uiState = controller.uiState

    val isHumanVsBot = gameMode == GameMode.HUMAN_VS_BOT

    // Create a stable click handler
    val territoryClickHandler = remember(gameMode) {
        if (gameMode == GameMode.HUMAN_VS_BOT) {
            { territoryId: Int ->
                println("Territory clicked: $territoryId, Current player: ${controller.getCurrentPlayer()}, Is human turn: ${controller.isCurrentPlayerHuman()}")
                controller.selectTerritory(territoryId)
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
                    controller.newGame(newMap)
                }
            ) {
                Text("New Game")
            }

            Button(onClick = onBackToMenu) {
                Text("Back to Menu")
            }

            // Game phase and mode-specific buttons
            when {
                controller.isGameOver() -> {
                    // Game over, no action button needed
                }
                gameState.gamePhase == GamePhase.REINFORCEMENT -> {
                    Button(
                        onClick = {
                            controller.executeReinforcementPhase()
                        }
                    ) {
                        Text("Apply Reinforcements")
                    }
                }
                controller.isCurrentPlayerHuman() -> {
                    // Human player's turn - show Skip button
                    Button(
                        onClick = {
                            controller.skipTurn()
                        }
                    ) {
                        Text("Skip Turn")
                    }
                }
                controller.isCurrentPlayerBot() -> {
                    // Bot's turn - show manual controls only in Bot vs Bot mode
                    if (gameMode == GameMode.BOT_VS_BOT) {
                        when {
                            uiState.currentBotDecision == null -> {
                                Button(
                                    onClick = {
                                        controller.requestBotDecision()
                                    }
                                ) {
                                    Text("Player ${controller.getCurrentPlayer()}: Make Decision")
                                }
                            }
                            uiState.currentBotDecision is BotDecision.Attack -> {
                                Button(
                                    onClick = {
                                        controller.executeBotDecision()
                                    }
                                ) {
                                    Text("Execute Attack")
                                }
                            }
                            uiState.currentBotDecision is BotDecision.Skip -> {
                                Button(
                                    onClick = {
                                        controller.executeBotDecision()
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
            if (!controller.isGameOver()) {
                val currentPlayerColor = if (controller.getCurrentPlayer() == 0)
                    GameColors.Player0 else GameColors.Player1

                // Show "Your Turn" or "Bot's Turn" for human vs bot mode
                val turnText = when {
                    controller.isCurrentPlayerHuman() -> "YOUR TURN"
                    isHumanVsBot -> "BOT'S TURN"
                    else -> "Current Turn: Player ${controller.getCurrentPlayer()}"
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
                    color = if (controller.isGameOver())
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
            // Player 0 stats
            Column(horizontalAlignment = Alignment.Start) {
                val player0Label = if (isHumanVsBot) "Human (Purple)" else "Player 0 (Purple)"
                Text(
                    player0Label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = GameColors.Player0
                )
                Text(
                    "Territories: ${gameState.players[0].territoryCount}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Armies: ${gameState.players[0].totalArmies}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Connected: ${gameState.players[0].largestConnectedSize}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (gameState.players[0].reserveArmies > 0) {
                    Text(
                        "Reserve: ${gameState.players[0].reserveArmies}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Player 1 stats
            Column(horizontalAlignment = Alignment.Start) {
                val player1Label = if (isHumanVsBot) "Bot (Green)" else "Player 1 (Green)"
                Text(
                    player1Label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = GameColors.Player1
                )
                Text(
                    "Territories: ${gameState.players[1].territoryCount}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Armies: ${gameState.players[1].totalArmies}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Connected: ${gameState.players[1].largestConnectedSize}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (gameState.players[1].reserveArmies > 0) {
                    Text(
                        "Reserve: ${gameState.players[1].reserveArmies}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Map rendering
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
        }
    }
}

@Composable
fun MapRenderer(
    map: GameMap,
    cellWidth: Float,
    cellHeight: Float,
    fontSize: Float,
    highlightedTerritories: Set<Int> = emptySet(),
    attackFromTerritory: Int? = null,
    onTerritoryClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val mapWidth = with(density) {
        ((HexGrid.GRID_WIDTH + 0.5f) * cellWidth).toDp()
    }
    val mapHeight = with(density) {
        (HexGrid.GRID_HEIGHT * cellHeight).toDp()
    }

    val canvasModifier = modifier
        .width(mapWidth)
        .height(mapHeight)
        .then(
            if (onTerritoryClick != null) {
                Modifier.pointerInput(cellWidth, cellHeight) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val position = down.position
                        val clickedTerritory = findTerritoryAtPosition(
                            position.x,
                            position.y,
                            map,
                            cellWidth,
                            cellHeight
                        )
                        clickedTerritory?.let { onTerritoryClick(it) }
                    }
                }
            } else {
                Modifier
            }
        )

    Canvas(modifier = canvasModifier) {
        // First pass: Fill all cells with territory colors
        for (i in map.cells.indices) {
            val territoryId = map.cells[i]
            if (territoryId > 0 && territoryId <= map.territories.size) {
                val territory = map.territories[territoryId - 1]
                val cellPos = HexGrid.getCellPosition(i, cellWidth, cellHeight)

                val fillColor = when (territory.owner) {
                    0 -> GameColors.Player0
                    1 -> GameColors.Player1
                    else -> Color.Gray
                }

                val hexPath = buildHexagonPath(cellPos.first, cellPos.second, cellWidth, cellHeight)
                drawPath(
                    path = hexPath,
                    color = fillColor,
                    style = Fill
                )
            }
        }

        // Second pass: Draw borders between territories
        for (i in map.cells.indices) {
            val territoryId = map.cells[i]
            if (territoryId == 0) continue

            val neighbors = map.cellNeighbors[i].directions

            for (dir in neighbors.indices) {
                val neighborCell = neighbors[dir]
                val neighborTerritoryId = if (neighborCell != -1) map.cells[neighborCell] else -1

                if (neighborTerritoryId != territoryId) {
                    val cellPos = HexGrid.getCellPosition(i, cellWidth, cellHeight)
                    drawHexEdge(
                        cellPos.first,
                        cellPos.second,
                        cellWidth,
                        cellHeight,
                        dir,
                        GameColors.TerritoryBorder,
                        max(1f, 3f * (cellWidth / ORIGINAL_CELL_WIDTH))
                    )
                }
            }
        }

        // Highlight selected territories - only draw outer edges
        for (territoryId in highlightedTerritories) {
            val territory = map.territories.getOrNull(territoryId) ?: continue
            if (territory.size == 0) continue

            val highlightColor = if (territoryId == attackFromTerritory) {
                Color.Red // Attacking territory
            } else {
                Color.Yellow // Defending territory
            }

            // Draw highlight border around territory outline only
            // Note: map.cells uses 1-based IDs, so we compare with territoryId + 1
            for (cellIdx in map.cells.indices) {
                if (map.cells[cellIdx] == territoryId + 1) {
                    val cellPos = HexGrid.getCellPosition(cellIdx, cellWidth, cellHeight)
                    val neighbors = map.cellNeighbors[cellIdx].directions

                    // Only draw edges that border a different territory (outer edges)
                    for (dir in neighbors.indices) {
                        val neighborCell = neighbors[dir]
                        val neighborTerritoryId = if (neighborCell != -1) map.cells[neighborCell] else -1

                        // Draw edge if it's a boundary (different territory or edge of map)
                        if (neighborTerritoryId != territoryId + 1) {
                            drawHexEdge(
                                cellPos.first,
                                cellPos.second,
                                cellWidth,
                                cellHeight,
                                dir,
                                highlightColor,
                                max(4f, 6f * (cellWidth / ORIGINAL_CELL_WIDTH))
                            )
                        }
                    }
                }
            }
        }

        // Third pass: Draw army counts and territory IDs
        for (territory in map.territories) {
            if (territory.size == 0) continue

            val centerPos = HexGrid.getCellPosition(territory.centerPos, cellWidth, cellHeight)
            val displayText = "${territory.armyCount}"

            val textLayoutResult = textMeasurer.measure(
                text = displayText,
                style = TextStyle(
                    color = GameColors.TerritoryText,
                    fontSize = fontSize.sp
                )
            )

            val textX = centerPos.first + cellWidth / 2 - textLayoutResult.size.width / 2
            val textY = centerPos.second + cellHeight / 2 - textLayoutResult.size.height / 2

            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(textX, textY)
            )
        }
    }
}

/**
 * Build a path for a single hexagon cell
 */
private fun buildHexagonPath(
    x: Float,
    y: Float,
    cellWidth: Float,
    cellHeight: Float
): Path {
    val path = Path()
    val halfWidth = cellWidth / 2f
    val hexHeight = cellHeight * 4f / 3f
    val quarterHexHeight = hexHeight / 4f
    val overlap = 0.6f

    path.moveTo(x + halfWidth, y - overlap)
    path.lineTo(x + cellWidth + overlap, y + quarterHexHeight)
    path.lineTo(x + cellWidth + overlap, y + hexHeight - quarterHexHeight)
    path.lineTo(x + halfWidth, y + hexHeight + overlap)
    path.lineTo(x - overlap, y + hexHeight - quarterHexHeight)
    path.lineTo(x - overlap, y + quarterHexHeight)
    path.close()

    return path
}

/**
 * Draw a single edge of a hexagon
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexEdge(
    x: Float,
    y: Float,
    cellWidth: Float,
    cellHeight: Float,
    direction: Int,
    color: Color,
    strokeWidth: Float = 3f
) {
    val halfWidth = cellWidth / 2f
    val hexHeight = cellHeight * 4f / 3f
    val quarterHexHeight = hexHeight / 4f

    val (x1, y1, x2, y2) = when (direction) {
        0 -> listOf(x + halfWidth, y, x + cellWidth, y + quarterHexHeight)
        1 -> listOf(x + cellWidth, y + quarterHexHeight, x + cellWidth, y + hexHeight - quarterHexHeight)
        2 -> listOf(x + cellWidth, y + hexHeight - quarterHexHeight, x + halfWidth, y + hexHeight)
        3 -> listOf(x + halfWidth, y + hexHeight, x, y + hexHeight - quarterHexHeight)
        4 -> listOf(x, y + hexHeight - quarterHexHeight, x, y + quarterHexHeight)
        5 -> listOf(x, y + quarterHexHeight, x + halfWidth, y)
        else -> return
    }

    drawLine(
        color = color,
        start = Offset(x1, y1),
        end = Offset(x2, y2),
        strokeWidth = strokeWidth
    )
}

/**
 * Find which territory was clicked based on screen coordinates
 */
private fun findTerritoryAtPosition(
    x: Float,
    y: Float,
    map: GameMap,
    cellWidth: Float,
    cellHeight: Float
): Int? {
    // Find which cell was clicked
    val clickedCell = findCellAtPosition(x, y, cellWidth, cellHeight, map.gridWidth, map.gridHeight)
        ?: return null

    // Get the territory ID from the cell (1-based)
    val territoryId = map.cells[clickedCell]
    if (territoryId <= 0 || territoryId > map.territories.size) return null

    // Return 0-based territory ID
    return territoryId - 1
}

/**
 * Find which cell (in hex grid) was clicked
 */
private fun findCellAtPosition(
    x: Float,
    y: Float,
    cellWidth: Float,
    cellHeight: Float,
    gridWidth: Int,
    gridHeight: Int
): Int? {
    // Rough estimation based on row and column
    val estimatedRow = (y / cellHeight).toInt()
    val estimatedCol = if (estimatedRow % 2 == 1) {
        ((x - cellWidth / 2) / cellWidth).toInt()
    } else {
        (x / cellWidth).toInt()
    }

    // Check the estimated cell and its neighbors
    val cellsToCheck = mutableListOf<Int>()

    for (row in maxOf(0, estimatedRow - 1)..minOf(gridHeight - 1, estimatedRow + 1)) {
        for (col in maxOf(0, estimatedCol - 1)..minOf(gridWidth - 1, estimatedCol + 1)) {
            val cellIndex = row * gridWidth + col
            cellsToCheck.add(cellIndex)
        }
    }

    // Find the closest cell center
    var closestCell: Int? = null
    var closestDistance = Float.MAX_VALUE

    for (cellIndex in cellsToCheck) {
        if (cellIndex < 0 || cellIndex >= gridWidth * gridHeight) continue

        val cellPos = HexGrid.getCellPosition(cellIndex, cellWidth, cellHeight)
        val centerX = cellPos.first + cellWidth / 2
        val centerY = cellPos.second + cellHeight / 2

        val dx = x - centerX
        val dy = y - centerY
        val distance = dx * dx + dy * dy

        if (distance < closestDistance) {
            closestDistance = distance
            closestCell = cellIndex
        }
    }

    return closestCell
}
