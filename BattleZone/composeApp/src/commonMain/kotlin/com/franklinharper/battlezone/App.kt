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

@Composable
@Preview
fun App() {
    MaterialTheme {
        var gameMap by remember { mutableStateOf<GameMap?>(null) }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "BattleZone - Map Generation",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )

            Button(
                onClick = {
                    gameMap = MapGenerator.generate()
                },
                modifier = Modifier.padding(8.dp)
            ) {
                Text("Generate New Map")
            }

            gameMap?.let { map ->
                // Calculate player statistics
                val player0Territories = map.territories.count { it.owner == 0 }
                val player1Territories = map.territories.count { it.owner == 1 }

                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Total Territories: ${map.territories.size}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Player 0 (Purple): $player0Territories",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GameColors.Player0
                        )
                        Text(
                            "Player 1 (Green): $player1Territories",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GameColors.Player1
                        )
                    }
                }

                MapRenderer(map = map, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun MapRenderer(map: GameMap, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val cellWidth = 27f
        val cellHeight = 18f

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
                    drawHexEdge(cellPos.first, cellPos.second, cellWidth, cellHeight, dir, GameColors.TerritoryBorder)
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
                    fontSize = 12.sp
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
    color: Color
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
        strokeWidth = 3f
    )
}