package com.franklinharper.battlezone.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.franklinharper.battlezone.BotAttackArrow
import com.franklinharper.battlezone.GameColors
import com.franklinharper.battlezone.GameMap
import com.franklinharper.battlezone.HexGeometry
import com.franklinharper.battlezone.HexGrid
import com.franklinharper.battlezone.Territory
import kotlin.math.sqrt

/**
 * Renders a static arrow showing the most recent bot attack.
 * Arrow starts near the border on attacking side and ends shortly after crossing into defending territory.
 */
@Composable
fun BotAttackArrowOverlay(
    arrow: BotAttackArrow,
    gameMap: GameMap,
    cellWidth: Float,
    cellHeight: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val fromTerritory = gameMap.territories.getOrNull(arrow.fromTerritoryId) ?: return@Canvas
        val toTerritory = gameMap.territories.getOrNull(arrow.toTerritoryId) ?: return@Canvas

        if (fromTerritory.size == 0 || toTerritory.size == 0) return@Canvas

        // Find the border crossing point between territories
        val borderPoint = findBorderCrossingPoint(
            fromTerritory,
            toTerritory,
            gameMap,
            cellWidth,
            cellHeight
        ) ?: return@Canvas

        // Arrow starts slightly before border (on attacking side)
        // Arrow ends slightly after border (on defending side)
        val startOffset = 20f  // Distance before border
        val endOffset = 25f    // Distance after border

        val start = Offset(
            borderPoint.x - borderPoint.normalX * startOffset,
            borderPoint.y - borderPoint.normalY * startOffset
        )

        val end = Offset(
            borderPoint.x + borderPoint.normalX * endOffset,
            borderPoint.y + borderPoint.normalY * endOffset
        )

        // Draw arrow crossing the border
        drawArrowWithOutline(
            start = start,
            end = end,
            color = if (arrow.attackSucceeded) GameColors.BotArrowSuccess else GameColors.BotArrowFailure,
            outlineColor = GameColors.BotArrowOutline,
            strokeWidth = 4f,
            outlineWidth = 2f
        )
    }
}

/**
 * Border crossing point with normal vector
 */
private data class BorderPoint(
    val x: Float,        // Border crossing point X
    val y: Float,        // Border crossing point Y
    val normalX: Float,  // Normal vector X (points from attacker to defender)
    val normalY: Float   // Normal vector Y
)

/**
 * Find the midpoint of the shared border between two adjacent territories
 * and calculate the normal vector pointing from attacker to defender
 */
private fun findBorderCrossingPoint(
    fromTerritory: Territory,
    toTerritory: Territory,
    gameMap: GameMap,
    cellWidth: Float,
    cellHeight: Float
): BorderPoint? {
    // Find all border edges between the two territories
    val borderEdges = mutableListOf<Pair<Offset, Offset>>()

    for (cellIdx in gameMap.cells.indices) {
        if (gameMap.cells[cellIdx] == fromTerritory.id + 1) {
            val (cellX, cellY) = HexGrid.getCellPosition(cellIdx, cellWidth, cellHeight)
            val neighbors = gameMap.cellNeighbors[cellIdx].directions

            for (dir in neighbors.indices) {
                val neighborCell = neighbors[dir]
                if (neighborCell != -1 && gameMap.cells[neighborCell] == toTerritory.id + 1) {
                    // Found a shared edge
                    val edgePoints = HexGeometry.getHexEdgePoints(cellX, cellY, cellWidth, cellHeight, dir)
                    if (edgePoints != null) {
                        val (start, end) = edgePoints
                        borderEdges.add(
                            Offset(start.first, start.second) to Offset(end.first, end.second)
                        )
                    }
                }
            }
        }
    }

    if (borderEdges.isEmpty()) return null

    // Calculate average midpoint of all border edges
    var sumX = 0f
    var sumY = 0f
    borderEdges.forEach { (start, end) ->
        sumX += (start.x + end.x) / 2
        sumY += (start.y + end.y) / 2
    }
    val borderX = sumX / borderEdges.size
    val borderY = sumY / borderEdges.size

    // Calculate normal vector (from attacker center to defender center)
    val fromCenter = getTerritoryCenter(fromTerritory, gameMap, cellWidth, cellHeight)
    val toCenter = getTerritoryCenter(toTerritory, gameMap, cellWidth, cellHeight)

    val dx = toCenter.first - fromCenter.first
    val dy = toCenter.second - fromCenter.second
    val length = sqrt(dx * dx + dy * dy)

    val normalX = dx / length
    val normalY = dy / length

    return BorderPoint(borderX, borderY, normalX, normalY)
}

/**
 * Get the center point of a territory
 */
private fun getTerritoryCenter(
    territory: Territory,
    gameMap: GameMap,
    cellWidth: Float,
    cellHeight: Float
): Pair<Float, Float> {
    val centerCellIdx = territory.centerPos
    val (cellX, cellY) = HexGrid.getCellPosition(centerCellIdx, cellWidth, cellHeight)
    return Pair(cellX + cellWidth / 2, cellY + cellHeight / 2)
}

/**
 * Draw an arrow with white outline for visibility
 */
private fun DrawScope.drawArrowWithOutline(
    start: Offset,
    end: Offset,
    color: Color,
    outlineColor: Color,
    strokeWidth: Float,
    outlineWidth: Float
) {
    // Calculate arrow direction and arrowhead
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)

    if (length < 1f) return // Too short to draw

    val unitX = dx / length
    val unitY = dy / length

    // Arrowhead size
    val headLength = 12f
    val headWidth = 8f

    // Arrowhead points
    val tipX = end.x
    val tipY = end.y
    val baseX = end.x - unitX * headLength
    val baseY = end.y - unitY * headLength

    // Perpendicular for arrowhead width
    val perpX = -unitY * headWidth
    val perpY = unitX * headWidth

    val arrowPoint1 = Offset(baseX + perpX, baseY + perpY)
    val arrowPoint2 = Offset(baseX - perpX, baseY - perpY)

    // Draw outline (white border)
    drawLine(
        color = outlineColor,
        start = start,
        end = end,
        strokeWidth = strokeWidth + outlineWidth * 2
    )

    // Draw arrowhead outline
    val outlinePath = Path().apply {
        moveTo(tipX, tipY)
        lineTo(arrowPoint1.x, arrowPoint1.y)
        lineTo(arrowPoint2.x, arrowPoint2.y)
        close()
    }
    drawPath(outlinePath, color = outlineColor)

    // Draw main arrow line
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth
    )

    // Draw arrowhead
    val arrowPath = Path().apply {
        moveTo(tipX, tipY)
        lineTo(arrowPoint1.x, arrowPoint1.y)
        lineTo(arrowPoint2.x, arrowPoint2.y)
        close()
    }
    drawPath(arrowPath, color = color)
}
