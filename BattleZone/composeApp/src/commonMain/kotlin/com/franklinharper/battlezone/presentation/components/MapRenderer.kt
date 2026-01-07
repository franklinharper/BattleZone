package com.franklinharper.battlezone.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.franklinharper.battlezone.*
import kotlin.math.sqrt

/**
 * Renders the game map with hexagonal territories.
 *
 * This component handles:
 * - Canvas composition and sizing
 * - Territory click detection
 * - Text rendering (army counts)
 * - Delegating actual drawing to TerritoryDrawer
 */
@Composable
fun MapRenderer(
    map: GameMap,
    cellWidth: Float,
    cellHeight: Float,
    fontSize: Float,
    showTerritoryIds: Boolean = false,
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
        // Helper function to get cell position
        val getCellPosition: (Int) -> Pair<Float, Float> = { cellIndex ->
            HexGrid.getCellPosition(cellIndex, cellWidth, cellHeight)
        }

        // Pass 1: Fill all territories
        with(TerritoryDrawer) {
            drawTerritoryFills(map, cellWidth, cellHeight, getCellPosition)
        }

        // Pass 2: Draw borders between territories
        with(TerritoryDrawer) {
            drawTerritoryBorders(map, cellWidth, cellHeight, getCellPosition)
        }

        // Pass 3: Draw highlights for selected territories
        if (highlightedTerritories.isNotEmpty()) {
            with(TerritoryDrawer) {
                drawHighlightedTerritories(
                    map,
                    highlightedTerritories,
                    attackFromTerritory,
                    cellWidth,
                    cellHeight,
                    getCellPosition
                )
            }
        }

        // Pass 4: Draw army counts
        for (territory in map.territories) {
            if (territory.size == 0) continue

            val (centerX, centerY) = getCellPosition(territory.centerPos)
            val displayText = if (showTerritoryIds) {
                "${territory.armyCount} (${territory.id})"
            } else {
                "${territory.armyCount}"
            }

            val textLayoutResult = textMeasurer.measure(
                text = displayText,
                style = TextStyle(
                    color = GameColors.TerritoryText,
                    fontSize = fontSize.sp
                )
            )

            val textX = centerX + cellWidth / 2 - textLayoutResult.size.width / 2
            val textY = centerY + cellHeight / 2 - textLayoutResult.size.height / 2

            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(textX, textY)
            )
        }
    }
}

/**
 * Find which territory was clicked based on screen coordinates.
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
 * Find which cell (in hex grid) was clicked.
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
