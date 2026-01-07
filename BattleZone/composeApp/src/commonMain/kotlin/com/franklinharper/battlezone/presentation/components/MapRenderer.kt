package com.franklinharper.battlezone.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    showCellOutlines: Boolean = false,
    highlightedTerritories: Set<Int> = emptySet(),
    attackFromTerritory: Int? = null,
    onTerritoryClick: ((Int) -> Unit)? = null,
    onCellClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelPositions = remember(map, cellWidth, cellHeight) {
        computeTerritoryLabelPositions(map, cellWidth, cellHeight)
    }

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
            if (onTerritoryClick != null || onCellClick != null) {
                Modifier.pointerInput(cellWidth, cellHeight) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val position = down.position
                        val clickedCell = findCellAtPosition(
                            position.x,
                            position.y,
                            cellWidth,
                            cellHeight,
                            map.gridWidth,
                            map.gridHeight
                        )
                        clickedCell?.let { cellIndex ->
                            onCellClick?.invoke(cellIndex)
                            val clickedTerritory = findTerritoryAtPosition(cellIndex, map)
                            clickedTerritory?.let { onTerritoryClick?.invoke(it) }
                        }
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

        // Pass 2: Draw optional debug outlines for each cell
        if (showCellOutlines) {
            with(TerritoryDrawer) {
                drawCellOutlines(map, cellWidth, cellHeight, getCellPosition)
            }
        }

        // Pass 3: Draw borders between territories
        with(TerritoryDrawer) {
            drawTerritoryBorders(map, cellWidth, cellHeight, getCellPosition)
        }

        // Pass 4: Draw highlights for selected territories
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

        // Pass 5: Draw army counts
        for (territory in map.territories) {
            if (territory.size == 0) continue

            val labelPosition = labelPositions[territory.id]
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

            val textX = labelPosition.x
            val textY = labelPosition.y - textLayoutResult.size.height / 2

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
    cellIndex: Int,
    map: GameMap
): Int? {
    // Get the territory ID from the cell (1-based)
    val territoryId = map.cells[cellIndex]
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

private data class Edge(val start: Offset, val end: Offset)

private fun computeTerritoryLabelPositions(
    map: GameMap,
    cellWidth: Float,
    cellHeight: Float
): Array<Offset> {
    val territoryCells = Array(map.territories.size) { mutableListOf<Int>() }
    for (cellIndex in map.cells.indices) {
        val territoryId = map.cells[cellIndex]
        if (territoryId > 0 && territoryId <= map.territories.size) {
            territoryCells[territoryId - 1].add(cellIndex)
        }
    }

    return Array(map.territories.size) { territoryId ->
        val cellsInTerritory = territoryCells[territoryId]
        if (cellsInTerritory.isEmpty()) {
            return@Array Offset.Zero
        }

        val baseCellIndex = map.territories[territoryId].centerPos
        val (baseCellX, baseCellY) = HexGrid.getCellPosition(baseCellIndex, cellWidth, cellHeight)
        val baseCenter = HexGeometry.getHexCenter(baseCellX, baseCellY, cellWidth, cellHeight)
        val boundaryEdges = buildBoundaryEdges(map, cellsInTerritory, cellWidth, cellHeight)

        var bestPoint = Offset(baseCenter.first, baseCenter.second)
        var bestDistance = minDistanceToEdges(bestPoint, boundaryEdges)

        val stepX = cellWidth * LABEL_SEARCH_STEP_SCALE
        val stepY = cellHeight * LABEL_SEARCH_STEP_SCALE

        for (dx in -LABEL_SEARCH_STEP_COUNT..LABEL_SEARCH_STEP_COUNT) {
            for (dy in -LABEL_SEARCH_STEP_COUNT..LABEL_SEARCH_STEP_COUNT) {
                val candidate = Offset(
                    baseCenter.first + dx * stepX,
                    baseCenter.second + dy * stepY
                )
                if (!isPointInsideTerritory(candidate, territoryId, map, cellWidth, cellHeight)) {
                    continue
                }
                val distance = minDistanceToEdges(candidate, boundaryEdges)
                if (distance > bestDistance) {
                    bestDistance = distance
                    bestPoint = candidate
                }
            }
        }

        bestPoint
    }
}

private fun buildBoundaryEdges(
    map: GameMap,
    cellsInTerritory: List<Int>,
    cellWidth: Float,
    cellHeight: Float
): List<Edge> {
    val edges = mutableListOf<Edge>()
    val territoryCellValue = map.cells[cellsInTerritory.first()]

    for (cellIndex in cellsInTerritory) {
        val neighbors = map.cellNeighbors[cellIndex].directions
        val (cellX, cellY) = HexGrid.getCellPosition(cellIndex, cellWidth, cellHeight)
        for (dir in 0 until HEX_EDGE_COUNT) {
            val neighborCell = neighbors[dir]
            val neighborTerritoryId = if (neighborCell != -1) map.cells[neighborCell] else -1
            if (neighborTerritoryId != territoryCellValue) {
                val edgePoints = HexGeometry.getHexEdgePoints(cellX, cellY, cellWidth, cellHeight, dir)
                    ?: continue
                val (start, end) = edgePoints
                edges.add(Edge(Offset(start.first, start.second), Offset(end.first, end.second)))
            }
        }
    }

    return edges
}

private fun minDistanceToEdges(point: Offset, edges: List<Edge>): Float {
    if (edges.isEmpty()) return 0f
    var minDistance = Float.MAX_VALUE
    for (edge in edges) {
        val distance = distancePointToSegment(point, edge.start, edge.end)
        if (distance < minDistance) {
            minDistance = distance
        }
    }
    return minDistance
}

private fun distancePointToSegment(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0f && dy == 0f) {
        val px = point.x - start.x
        val py = point.y - start.y
        return kotlin.math.sqrt(px * px + py * py)
    }
    val t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)
    val clampedT = t.coerceIn(0f, 1f)
    val closestX = start.x + clampedT * dx
    val closestY = start.y + clampedT * dy
    val cx = point.x - closestX
    val cy = point.y - closestY
    return kotlin.math.sqrt(cx * cx + cy * cy)
}

private fun isPointInsideTerritory(
    point: Offset,
    territoryId: Int,
    map: GameMap,
    cellWidth: Float,
    cellHeight: Float
): Boolean {
    val cellIndex = findCellAtPosition(
        point.x,
        point.y,
        cellWidth,
        cellHeight,
        map.gridWidth,
        map.gridHeight
    ) ?: return false

    if (map.cells[cellIndex] != territoryId + 1) {
        return false
    }

    val (cellX, cellY) = HexGrid.getCellPosition(cellIndex, cellWidth, cellHeight)
    return isPointInHex(point, cellX, cellY, cellWidth, cellHeight)
}

private fun isPointInHex(
    point: Offset,
    cellX: Float,
    cellY: Float,
    cellWidth: Float,
    cellHeight: Float
): Boolean {
    val vertices = HexGeometry.getHexagonVertices(cellX, cellY, cellWidth, cellHeight)
    var inside = false
    var j = vertices.lastIndex
    for (i in vertices.indices) {
        val xi = vertices[i].first
        val yi = vertices[i].second
        val xj = vertices[j].first
        val yj = vertices[j].second
        val intersects = (yi > point.y) != (yj > point.y) &&
            (point.x < (xj - xi) * (point.y - yi) / (yj - yi + 0.000001f) + xi)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}

private const val LABEL_SEARCH_STEP_COUNT = 2
private const val LABEL_SEARCH_STEP_SCALE = 0.2f
private const val HEX_EDGE_COUNT = 6
