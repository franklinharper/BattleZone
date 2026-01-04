package com.franklinharper.battlezone

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Utilities for rendering hexagonal grid elements
 */
object HexRenderingUtils {
    /**
     * Build a path for a single hexagon cell
     *
     * @param x The x coordinate of the hexagon
     * @param y The y coordinate of the hexagon
     * @param cellWidth The width of a hex cell
     * @param cellHeight The height of a hex cell
     * @return Path representing the hexagon shape
     */
    fun buildHexagonPath(
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
     *
     * @param x The x coordinate of the hexagon
     * @param y The y coordinate of the hexagon
     * @param cellWidth The width of a hex cell
     * @param cellHeight The height of a hex cell
     * @param direction The direction of the edge (0-5)
     * @param color The color of the edge
     * @param strokeWidth The width of the stroke
     */
    fun DrawScope.drawHexEdge(
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
}
