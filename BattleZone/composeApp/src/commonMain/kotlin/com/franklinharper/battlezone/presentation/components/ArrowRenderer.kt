package com.franklinharper.battlezone.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.franklinharper.battlezone.AttackArrow
import com.franklinharper.battlezone.AttackArrowRenderingOption
import com.franklinharper.battlezone.GameColors
import com.franklinharper.battlezone.GameMap
import com.franklinharper.battlezone.HexGeometry
import com.franklinharper.battlezone.HexGrid
import com.franklinharper.battlezone.Territory
import com.franklinharper.battlezone.UNKNOWN_PLAYER_ID
import kotlin.math.sqrt

private const val ARROW_START_OFFSET_PX = 20f
private const val ARROW_END_OFFSET_PX = 25f
private const val ARROW_STROKE_WIDTH_PX = 4f
private const val ARROW_OUTLINE_WIDTH_PX = 2f
private const val ARROW_HEAD_LENGTH_PX = 12f
private const val ARROW_HEAD_WIDTH_PX = 8f
private const val BADGE_RADIUS_PX = 10f
private const val BADGE_OUTLINE_WIDTH_PX = 2f

/**
 * Renders a static arrow showing a recorded attack.
 * Arrow starts near the border on attacking side and ends shortly after crossing into defending territory.
 */
@Composable
fun AttackArrowOverlay(
    arrow: AttackArrow,
    gameMap: GameMap,
    cellWidth: Float,
    cellHeight: Float,
    renderingOption: AttackArrowRenderingOption,
    showBadge: Boolean = true,
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
        val start = Offset(
            borderPoint.x - borderPoint.normalX * ARROW_START_OFFSET_PX,
            borderPoint.y - borderPoint.normalY * ARROW_START_OFFSET_PX
        )

        val end = Offset(
            borderPoint.x + borderPoint.normalX * ARROW_END_OFFSET_PX,
            borderPoint.y + borderPoint.normalY * ARROW_END_OFFSET_PX
        )

        val attackerPlayerId = if (arrow.attackerPlayerId != UNKNOWN_PLAYER_ID) {
            arrow.attackerPlayerId
        } else {
            fromTerritory.owner
        }
        val attackerColor = GameColors.getPlayerColor(attackerPlayerId)

        when (renderingOption) {
            AttackArrowRenderingOption.ATTACKER_COLOR_SHAFT_RESULT_HEAD -> {
                val headColor = if (arrow.attackSucceeded) {
                    GameColors.AttackArrowHeadSuccess
                } else {
                    GameColors.AttackArrowHeadFailure
                }
                drawArrowWithOutline(
                    start = start,
                    end = end,
                    lineColor = attackerColor,
                    headColor = headColor,
                    outlineColor = GameColors.BotArrowOutline,
                    strokeWidth = ARROW_STROKE_WIDTH_PX,
                    outlineWidth = ARROW_OUTLINE_WIDTH_PX
                )
            }
            AttackArrowRenderingOption.MIDPOINT_BADGE_CURRENT_COLORS -> {
                val arrowColor = if (arrow.attackSucceeded) {
                    GameColors.BotArrowSuccess
                } else {
                    GameColors.BotArrowFailure
                }
                drawArrowWithOutline(
                    start = start,
                    end = end,
                    lineColor = arrowColor,
                    headColor = arrowColor,
                    outlineColor = GameColors.BotArrowOutline,
                    strokeWidth = ARROW_STROKE_WIDTH_PX,
                    outlineWidth = ARROW_OUTLINE_WIDTH_PX
                )
                if (showBadge) {
                    drawMidpointBadge(
                        start = start,
                        end = end,
                        fillColor = attackerColor,
                        outlineColor = GameColors.BotArrowOutline
                    )
                }
            }
        }
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
    lineColor: Color,
    headColor: Color,
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
    val headLength = ARROW_HEAD_LENGTH_PX
    val headWidth = ARROW_HEAD_WIDTH_PX

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
        color = lineColor,
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
    drawPath(arrowPath, color = headColor)
}

private fun DrawScope.drawMidpointBadge(
    start: Offset,
    end: Offset,
    fillColor: Color,
    outlineColor: Color
) {
    val midpoint = Offset(
        (start.x + end.x) / 2f,
        (start.y + end.y) / 2f
    )
    drawCircle(
        color = outlineColor,
        radius = BADGE_RADIUS_PX + BADGE_OUTLINE_WIDTH_PX,
        center = midpoint
    )
    drawCircle(
        color = fillColor,
        radius = BADGE_RADIUS_PX,
        center = midpoint
    )
    drawCircle(
        color = outlineColor,
        radius = BADGE_RADIUS_PX,
        center = midpoint,
        style = Stroke(width = BADGE_OUTLINE_WIDTH_PX)
    )
}
