package com.franklinharper.battlezone.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.franklinharper.battlezone.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Constants for rendering
private const val ORIGINAL_CELL_WIDTH = 27f

/**
 * Renders the game map with hexagonal territories
 */
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

                val hexPath = HexRenderingUtils.buildHexagonPath(cellPos.first, cellPos.second, cellWidth, cellHeight)
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
                    with(HexRenderingUtils) {
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
                            with(HexRenderingUtils) {
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
        }

        // Third pass: Draw army counts
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

    // Find the closest cell center to the click
    var closestCell: Int? = null
    var closestDistance = Float.MAX_VALUE

    for (cellIndex in cellsToCheck) {
        val (cellX, cellY) = HexGrid.getCellPosition(cellIndex, cellWidth, cellHeight)
        val centerX = cellX + cellWidth / 2
        val centerY = cellY + cellHeight / 2

        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < closestDistance) {
            closestDistance = distance
            closestCell = cellIndex
        }
    }

    return closestCell
}
