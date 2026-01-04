package com.franklinharper.battlezone

/**
 * Pure hexagonal geometry calculations without rendering dependencies.
 *
 * All methods return geometry data as simple data structures (Pair, List, etc.)
 * that can be used by rendering code.
 */
object HexGeometry {
    /**
     * Calculate the 6 vertices of a hexagon given its top-left corner position.
     *
     * @param x Top-left x coordinate
     * @param y Top-left y coordinate
     * @param cellWidth Width of the hex cell
     * @param cellHeight Height of the hex cell
     * @return List of 6 vertex points as Pair<Float, Float> (x, y)
     */
    fun getHexagonVertices(
        x: Float,
        y: Float,
        cellWidth: Float,
        cellHeight: Float
    ): List<Pair<Float, Float>> {
        val halfWidth = cellWidth / 2f
        val hexHeight = cellHeight * 4f / 3f
        val quarterHexHeight = hexHeight / 4f

        return listOf(
            Pair(x + halfWidth, y),                              // Top
            Pair(x + cellWidth, y + quarterHexHeight),           // Upper-right
            Pair(x + cellWidth, y + hexHeight - quarterHexHeight), // Lower-right
            Pair(x + halfWidth, y + hexHeight),                  // Bottom
            Pair(x, y + hexHeight - quarterHexHeight),           // Lower-left
            Pair(x, y + quarterHexHeight)                        // Upper-left
        )
    }

    /**
     * Get the two endpoints of a hexagon edge in a given direction.
     *
     * @param x Top-left x coordinate
     * @param y Top-left y coordinate
     * @param cellWidth Width of the hex cell
     * @param cellHeight Height of the hex cell
     * @param direction Edge direction (0-5)
     * @return Pair of endpoints: (start point, end point) where each point is Pair(x, y)
     */
    fun getHexEdgePoints(
        x: Float,
        y: Float,
        cellWidth: Float,
        cellHeight: Float,
        direction: Int
    ): Pair<Pair<Float, Float>, Pair<Float, Float>>? {
        val halfWidth = cellWidth / 2f
        val hexHeight = cellHeight * 4f / 3f
        val quarterHexHeight = hexHeight / 4f

        return when (direction) {
            0 -> Pair(
                Pair(x + halfWidth, y),
                Pair(x + cellWidth, y + quarterHexHeight)
            )
            1 -> Pair(
                Pair(x + cellWidth, y + quarterHexHeight),
                Pair(x + cellWidth, y + hexHeight - quarterHexHeight)
            )
            2 -> Pair(
                Pair(x + cellWidth, y + hexHeight - quarterHexHeight),
                Pair(x + halfWidth, y + hexHeight)
            )
            3 -> Pair(
                Pair(x + halfWidth, y + hexHeight),
                Pair(x, y + hexHeight - quarterHexHeight)
            )
            4 -> Pair(
                Pair(x, y + hexHeight - quarterHexHeight),
                Pair(x, y + quarterHexHeight)
            )
            5 -> Pair(
                Pair(x, y + quarterHexHeight),
                Pair(x + halfWidth, y)
            )
            else -> null
        }
    }

    /**
     * Calculate the center point of a hexagon.
     *
     * @param x Top-left x coordinate
     * @param y Top-left y coordinate
     * @param cellWidth Width of the hex cell
     * @param cellHeight Height of the hex cell
     * @return Center point as Pair(x, y)
     */
    fun getHexCenter(
        x: Float,
        y: Float,
        cellWidth: Float,
        cellHeight: Float
    ): Pair<Float, Float> {
        val hexHeight = cellHeight * 4f / 3f
        return Pair(x + cellWidth / 2f, y + hexHeight / 2f)
    }

    /**
     * Calculate the actual rendering height of a hexagon.
     *
     * @param cellHeight Base cell height
     * @return Actual hex height
     */
    fun getHexHeight(cellHeight: Float): Float {
        return cellHeight * 4f / 3f
    }
}
