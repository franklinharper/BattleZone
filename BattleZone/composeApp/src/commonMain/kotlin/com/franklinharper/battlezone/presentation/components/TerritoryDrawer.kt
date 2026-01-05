package com.franklinharper.battlezone.presentation.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import com.franklinharper.battlezone.GameColors
import com.franklinharper.battlezone.GameMap
import com.franklinharper.battlezone.HexGeometry
import kotlin.math.max

// Constants for rendering
private const val ORIGINAL_CELL_WIDTH = 27f

/**
 * Handles all territory-specific rendering operations.
 *
 * Responsibilities:
 * - Drawing territory fills
 * - Drawing territory borders
 * - Drawing highlight overlays
 */
object TerritoryDrawer {
    /**
     * Draw all territory cells with their respective colors.
     */
    fun DrawScope.drawTerritoryFills(
        map: GameMap,
        cellWidth: Float,
        cellHeight: Float,
        getCellPosition: (Int) -> Pair<Float, Float>
    ) {
        for (i in map.cells.indices) {
            val territoryId = map.cells[i]
            if (territoryId > 0 && territoryId <= map.territories.size) {
                val territory = map.territories[territoryId - 1]
                val (cellX, cellY) = getCellPosition(i)

                val fillColor = GameColors.getPlayerColor(territory.owner)

                val hexPath = buildHexagonPath(cellX, cellY, cellWidth, cellHeight)
                drawPath(
                    path = hexPath,
                    color = fillColor,
                    style = Fill
                )
            }
        }
    }

    /**
     * Draw borders between different territories.
     */
    fun DrawScope.drawTerritoryBorders(
        map: GameMap,
        cellWidth: Float,
        cellHeight: Float,
        getCellPosition: (Int) -> Pair<Float, Float>
    ) {
        for (i in map.cells.indices) {
            val territoryId = map.cells[i]
            if (territoryId == 0) continue

            val neighbors = map.cellNeighbors[i].directions

            for (dir in neighbors.indices) {
                val neighborCell = neighbors[dir]
                val neighborTerritoryId = if (neighborCell != -1) map.cells[neighborCell] else -1

                if (neighborTerritoryId != territoryId) {
                    val (cellX, cellY) = getCellPosition(i)
                    drawHexEdge(
                        cellX,
                        cellY,
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

    /**
     * Draw highlight borders for selected territories.
     */
    fun DrawScope.drawHighlightedTerritories(
        map: GameMap,
        highlightedTerritories: Set<Int>,
        attackFromTerritory: Int?,
        cellWidth: Float,
        cellHeight: Float,
        getCellPosition: (Int) -> Pair<Float, Float>
    ) {
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
                    val (cellX, cellY) = getCellPosition(cellIdx)
                    val neighbors = map.cellNeighbors[cellIdx].directions

                    // Only draw edges that border a different territory (outer edges)
                    for (dir in neighbors.indices) {
                        val neighborCell = neighbors[dir]
                        val neighborTerritoryId = if (neighborCell != -1) map.cells[neighborCell] else -1

                        // Draw edge if it's a boundary (different territory or edge of map)
                        if (neighborTerritoryId != territoryId + 1) {
                            drawHexEdge(
                                cellX,
                                cellY,
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

    /**
     * Build a path for a hexagon using pure geometry calculations.
     */
    private fun buildHexagonPath(
        x: Float,
        y: Float,
        cellWidth: Float,
        cellHeight: Float
    ): Path {
        val path = Path()
        val overlap = 0.6f
        val vertices = HexGeometry.getHexagonVertices(x, y, cellWidth, cellHeight)

        // Apply overlap adjustment
        path.moveTo(vertices[0].first, vertices[0].second - overlap)
        path.lineTo(vertices[1].first + overlap, vertices[1].second)
        path.lineTo(vertices[2].first + overlap, vertices[2].second)
        path.lineTo(vertices[3].first, vertices[3].second + overlap)
        path.lineTo(vertices[4].first - overlap, vertices[4].second)
        path.lineTo(vertices[5].first - overlap, vertices[5].second)
        path.close()

        return path
    }

    /**
     * Draw a single edge of a hexagon using pure geometry calculations.
     */
    private fun DrawScope.drawHexEdge(
        x: Float,
        y: Float,
        cellWidth: Float,
        cellHeight: Float,
        direction: Int,
        color: Color,
        strokeWidth: Float
    ) {
        val edgePoints = HexGeometry.getHexEdgePoints(x, y, cellWidth, cellHeight, direction)
            ?: return

        val (start, end) = edgePoints
        drawLine(
            color = color,
            start = Offset(start.first, start.second),
            end = Offset(end.first, end.second),
            strokeWidth = strokeWidth
        )
    }
}
