package com.franklinharper.battlezone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
private const val ESTIMATED_FIXED_HEIGHT_DP = 204

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
    // Grid needs (GRID_WIDTH + 0.5) * cellWidth for odd row offsets
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
 * Ensures text remains readable at all scales
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
        var gameMap by remember { mutableStateOf<GameMap?>(null) }
        var player0State by remember { mutableStateOf(PlayerState(0, 0, 0, 0)) }
        var player1State by remember { mutableStateOf(PlayerState(0, 0, 0, 0)) }
        var reinforcementMessage by remember { mutableStateOf<String?>(null) }

        // Update player states when map changes
        LaunchedEffect(gameMap) {
            gameMap?.let { map ->
                GameLogic.updatePlayerState(map, player0State, 0)
                GameLogic.updatePlayerState(map, player1State, 1)
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
                "BattleZone - Phase 2: Reinforcement",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                Button(
                    onClick = {
                        val newMap = MapGenerator.generate()
                        gameMap = newMap
                        GameLogic.updatePlayerState(newMap, player0State, 0)
                        GameLogic.updatePlayerState(newMap, player1State, 1)
                        reinforcementMessage = null
                    }
                ) {
                    Text("Generate New Map")
                }

                Button(
                    onClick = {
                        gameMap?.let { map ->
                            // Calculate reinforcements
                            val p0Reinforcements = GameLogic.calculateReinforcements(map, 0)
                            val p1Reinforcements = GameLogic.calculateReinforcements(map, 1)

                            // Show reinforcement message
                            reinforcementMessage = "Reinforcing: Player 0 gets $p0Reinforcements armies, Player 1 gets $p1Reinforcements armies"

                            // Distribute reinforcements
                            player0State.reserveArmies = GameLogic.distributeReinforcements(
                                map, 0, p0Reinforcements, player0State.reserveArmies
                            )
                            player1State.reserveArmies = GameLogic.distributeReinforcements(
                                map, 1, p1Reinforcements, player1State.reserveArmies
                            )

                            // Update player states
                            GameLogic.updatePlayerState(map, player0State, 0)
                            GameLogic.updatePlayerState(map, player1State, 1)
                        }
                    },
                    enabled = gameMap != null
                ) {
                    Text("Reinforce Armies")
                }
            }

            // Display reinforcement message
            reinforcementMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }

            gameMap?.let { map ->
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Total Territories: ${map.territories.size}",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    // Player 0 stats
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            "Player 0 (Purple):",
                            style = MaterialTheme.typography.bodyLarge,
                            color = GameColors.Player0
                        )
                        Text(
                            "  Territories: ${player0State.territoryCount}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "  Total Armies: ${player0State.totalArmies}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "  Largest Connected: ${player0State.largestConnectedSize}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (player0State.reserveArmies > 0) {
                            Text(
                                "  Reserve: ${player0State.reserveArmies}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Player 1 stats
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            "Player 1 (Green):",
                            style = MaterialTheme.typography.bodyLarge,
                            color = GameColors.Player1
                        )
                        Text(
                            "  Territories: ${player1State.territoryCount}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "  Total Armies: ${player1State.totalArmies}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "  Largest Connected: ${player1State.largestConnectedSize}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (player1State.reserveArmies > 0) {
                            Text(
                                "  Reserve: ${player1State.reserveArmies}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Use BoxWithConstraints to measure actual available space for map
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val density = LocalDensity.current

                    // Calculate responsive cell dimensions based on actual available space
                    val renderParams = with(density) {
                        calculateCellDimensions(
                            maxWidth.toPx(),
                            maxHeight.toPx()
                        )
                    }

                    MapRenderer(
                        map = map,
                        cellWidth = renderParams.cellWidth,
                        cellHeight = renderParams.cellHeight,
                        fontSize = renderParams.fontSize
                    )
                }
            }
        }
    }
}

@Composable
fun MapRenderer(
    map: GameMap,
    cellWidth: Float,
    cellHeight: Float,
    fontSize: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Calculate actual map size based on grid dimensions and cell sizes
    val mapWidth = with(density) {
        ((HexGrid.GRID_WIDTH + 0.5f) * cellWidth).toDp()
    }
    val mapHeight = with(density) {
        (HexGrid.GRID_HEIGHT * cellHeight).toDp()
    }

    Canvas(
        modifier = modifier
            .width(mapWidth)
            .height(mapHeight)
    ) {

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

                // Draw filled hexagon for this cell
                val hexPath = buildHexagonPath(cellPos.first, cellPos.second, cellWidth, cellHeight)
                drawPath(
                    path = hexPath,
                    color = fillColor,
                    style = Fill
                )
            }
        }

        // Second pass: Draw borders only between different territories
        for (i in map.cells.indices) {
            val territoryId = map.cells[i]
            if (territoryId == 0) continue

            val neighbors = map.cellNeighbors[i].directions

            // Check each direction for a border
            for (dir in neighbors.indices) {
                val neighborCell = neighbors[dir]
                val neighborTerritoryId = if (neighborCell != -1) map.cells[neighborCell] else -1

                // Draw border if neighbor is different territory or edge
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

        // Third pass: Draw army counts and cell sizes
        for (territory in map.territories) {
            if (territory.size == 0) continue

            val centerPos = HexGrid.getCellPosition(territory.centerPos, cellWidth, cellHeight)

            // Display format: "armies (cells)"
            val displayText = "${territory.armyCount} (${territory.size})"

            val textLayoutResult = textMeasurer.measure(
                text = displayText,
                style = TextStyle(
                    color = GameColors.TerritoryText,
                    fontSize = fontSize.sp
                )
            )

            // Center the text better
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
 * Creates a pointy-top hexagon that tiles seamlessly
 *
 * For proper vertical tiling, hexagons must be taller than the cellHeight spacing.
 * The hexagon height is cellHeight * 4/3 to ensure vertical overlap between rows.
 */
private fun buildHexagonPath(
    x: Float,
    y: Float,
    cellWidth: Float,
    cellHeight: Float
): Path {
    val path = Path()
    val halfWidth = cellWidth / 2f

    // Hexagon needs to be taller than cellHeight for proper vertical tiling
    // Height = cellHeight * 4/3 ensures rows overlap by 25%
    val hexHeight = cellHeight * 4f / 3f
    val quarterHexHeight = hexHeight / 4f

    // Small overlap to prevent anti-aliasing gaps
    val overlap = 0.6f

    // Pointy-top hexagon vertices with slight expansion
    path.moveTo(x + halfWidth, y - overlap)                                // top
    path.lineTo(x + cellWidth + overlap, y + quarterHexHeight)             // top-right
    path.lineTo(x + cellWidth + overlap, y + hexHeight - quarterHexHeight) // bottom-right
    path.lineTo(x + halfWidth, y + hexHeight + overlap)                    // bottom
    path.lineTo(x - overlap, y + hexHeight - quarterHexHeight)             // bottom-left
    path.lineTo(x - overlap, y + quarterHexHeight)                         // top-left
    path.close()

    return path
}

/**
 * Draw a single edge of a hexagon
 * Direction: 0=upper-right, 1=right, 2=lower-right, 3=lower-left, 4=left, 5=upper-left
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

    // Match the taller hexagon height from buildHexagonPath
    val hexHeight = cellHeight * 4f / 3f
    val quarterHexHeight = hexHeight / 4f

    // Define edge endpoints based on direction (matches buildHexagonPath vertices)
    val (x1, y1, x2, y2) = when (direction) {
        0 -> listOf(x + halfWidth, y, x + cellWidth, y + quarterHexHeight) // top to top-right
        1 -> listOf(x + cellWidth, y + quarterHexHeight, x + cellWidth, y + hexHeight - quarterHexHeight) // top-right to bottom-right
        2 -> listOf(x + cellWidth, y + hexHeight - quarterHexHeight, x + halfWidth, y + hexHeight) // bottom-right to bottom
        3 -> listOf(x + halfWidth, y + hexHeight, x, y + hexHeight - quarterHexHeight) // bottom to bottom-left
        4 -> listOf(x, y + hexHeight - quarterHexHeight, x, y + quarterHexHeight) // bottom-left to top-left
        5 -> listOf(x, y + quarterHexHeight, x + halfWidth, y) // top-left to top
        else -> return
    }

    drawLine(
        color = color,
        start = Offset(x1, y1),
        end = Offset(x2, y2),
        strokeWidth = strokeWidth
    )
}