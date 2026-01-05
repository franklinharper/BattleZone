package com.franklinharper.battlezone.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.franklinharper.battlezone.*
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.math.PI

/**
 * Color specifications for attack animations
 */
object AttackAnimationColors {
    val ATTACKING_OUTLINE = Color(0xFFFFD700) // Gold/Yellow
    val DEFENDING_OUTLINE = Color(0xFFFF3333) // Bright Red
    val SUCCESS_FLASH = Color(0xFFFF0000)     // Pure Red
    val FAILURE_FLASH = Color(0xFF4444FF)     // Bright Blue
}

/**
 * Renders attack and reinforcement animations on top of the game map.
 * Does not intercept pointer events - map remains fully interactive.
 */
@Composable
fun AnimationOverlay(
    animations: List<Animation>,
    gameState: GameState,
    cellWidth: Float,
    cellHeight: Float,
    modifier: Modifier = Modifier
) {
    // Update current time at ~60 FPS
    val currentTime by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(16) // ~60 FPS
        }
    }

    Canvas(modifier = modifier) {
        // Render all active animations concurrently
        animations.forEach { animation ->
            when (animation) {
                is Animation.AttackAnimation -> {
                    drawAttackAnimation(
                        animation = animation,
                        currentTime = currentTime,
                        gameState = gameState,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight
                    )
                }
                is Animation.ReinforcementAnimation -> {
                    // Future: render reinforcement animations
                }
            }
        }
    }
}

/**
 * Draw a single attack animation
 */
private fun DrawScope.drawAttackAnimation(
    animation: Animation.AttackAnimation,
    currentTime: Long,
    gameState: GameState,
    cellWidth: Float,
    cellHeight: Float
) {
    val phase = animation.getCurrentPhase(currentTime)

    when (phase) {
        AnimationPhase.PULSING -> {
            val progress = animation.getPulseProgress(currentTime)

            // Oscillating stroke width (3px to 6px) using sine wave
            val pulseValue = sin(progress * PI * 3).toFloat() // 3 pulses
            val strokeWidth = 3f + (pulseValue + 1f) * 1.5f // Maps [-1,1] to [3,6]

            // Yellow outline on attacking territory
            val attackingTerritory = gameState.map.territories.getOrNull(animation.fromTerritoryId)
            if (attackingTerritory != null && attackingTerritory.size > 0) {
                drawTerritoryOutline(
                    territory = attackingTerritory,
                    map = gameState.map,
                    color = AttackAnimationColors.ATTACKING_OUTLINE,
                    strokeWidth = strokeWidth,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight
                )
            }

            // Red outline on defending territory
            val defendingTerritory = gameState.map.territories.getOrNull(animation.toTerritoryId)
            if (defendingTerritory != null && defendingTerritory.size > 0) {
                drawTerritoryOutline(
                    territory = defendingTerritory,
                    map = gameState.map,
                    color = AttackAnimationColors.DEFENDING_OUTLINE,
                    strokeWidth = strokeWidth,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight
                )
            }
        }

        AnimationPhase.FLASH -> {
            val progress = animation.getFlashProgress(currentTime)

            // Flash overlay on defending territory (solid color, fades out at end)
            val flashColor = if (animation.attackerWins) {
                // Red flash for success - fade out in last 20% of animation
                if (progress < 0.8f) {
                    AttackAnimationColors.SUCCESS_FLASH
                } else {
                    val fadeProgress = (progress - 0.8f) / 0.2f
                    AttackAnimationColors.SUCCESS_FLASH.copy(alpha = 1f - fadeProgress)
                }
            } else {
                // Blue flash for failure - fade out in last 20% of animation
                if (progress < 0.8f) {
                    AttackAnimationColors.FAILURE_FLASH
                } else {
                    val fadeProgress = (progress - 0.8f) / 0.2f
                    AttackAnimationColors.FAILURE_FLASH.copy(alpha = 1f - fadeProgress)
                }
            }

            val defendingTerritory = gameState.map.territories.getOrNull(animation.toTerritoryId)
            if (defendingTerritory != null && defendingTerritory.size > 0) {
                drawTerritoryFill(
                    territory = defendingTerritory,
                    map = gameState.map,
                    color = flashColor,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight
                )
            }
        }

        AnimationPhase.COMPLETE -> {
            // Animation done, will be removed by coordinator
        }
    }
}

/**
 * Draw territory outline with specified color and stroke width
 */
private fun DrawScope.drawTerritoryOutline(
    territory: Territory,
    map: GameMap,
    color: Color,
    strokeWidth: Float,
    cellWidth: Float,
    cellHeight: Float
) {
    // Draw outline for each cell in the territory
    for (cellIdx in map.cells.indices) {
        if (map.cells[cellIdx] == territory.id + 1) { // cells use 1-based IDs
            val (cellX, cellY) = HexGrid.getCellPosition(cellIdx, cellWidth, cellHeight)
            val neighbors = map.cellNeighbors[cellIdx].directions

            // Draw edges that border different territories (outline)
            for (dir in neighbors.indices) {
                val neighborCell = neighbors[dir]
                val neighborTerritoryId = if (neighborCell != -1) map.cells[neighborCell] else -1

                // Draw edge if it's a boundary
                if (neighborTerritoryId != territory.id + 1) {
                    val edgePoints = HexGeometry.getHexEdgePoints(
                        cellX, cellY, cellWidth, cellHeight, dir
                    )
                    if (edgePoints != null) {
                        val (start, end) = edgePoints
                        drawLine(
                            color = color,
                            start = Offset(start.first, start.second),
                            end = Offset(end.first, end.second),
                            strokeWidth = strokeWidth
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draw filled territory with specified color
 */
private fun DrawScope.drawTerritoryFill(
    territory: Territory,
    map: GameMap,
    color: Color,
    cellWidth: Float,
    cellHeight: Float
) {
    // Draw filled hexagon for each cell in the territory
    for (cellIdx in map.cells.indices) {
        if (map.cells[cellIdx] == territory.id + 1) { // cells use 1-based IDs
            val (cellX, cellY) = HexGrid.getCellPosition(cellIdx, cellWidth, cellHeight)
            val hexPath = buildHexagonPath(cellX, cellY, cellWidth, cellHeight)
            drawPath(
                path = hexPath,
                color = color
            )
        }
    }
}

/**
 * Build a hexagon path for a cell
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
