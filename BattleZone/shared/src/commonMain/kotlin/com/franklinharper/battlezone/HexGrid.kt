package com.franklinharper.battlezone

/**
 * Hexagonal grid utilities for map generation and rendering
 *
 * Grid uses hexagonal tiling with:
 * - 28 columns × 32 rows (896 total cells)
 * - Odd rows (y % 2 == 1) offset by half a cell width
 * - 6 neighbor directions: 0=upper-right, 1=right, 2=lower-right,
 *   3=lower-left, 4=left, 5=upper-left
 */
object HexGrid {
    const val GRID_WIDTH = 28
    const val GRID_HEIGHT = 32
    const val MAX_TERRITORIES = GameRules.MAX_TERRITORIES
    const val TOTAL_CELLS = GRID_WIDTH * GRID_HEIGHT

    /**
     * Calculate cell index from x, y coordinates
     */
    fun cellIndex(x: Int, y: Int): Int = y * GRID_WIDTH + x

    /**
     * Get x coordinate from cell index
     */
    fun cellX(cellIndex: Int): Int = cellIndex % GRID_WIDTH

    /**
     * Get y coordinate from cell index
     */
    fun cellY(cellIndex: Int): Int = cellIndex / GRID_WIDTH

    /**
     * Check if coordinates are valid
     */
    fun isValidCell(x: Int, y: Int): Boolean {
        return x in 0 until GRID_WIDTH && y in 0 until GRID_HEIGHT
    }

    /**
     * Get neighboring cell index for a given direction (0-5)
     *
     * Hexagonal neighbor directions:
     * - 0: Upper-right
     * - 1: Right
     * - 2: Lower-right
     * - 3: Lower-left
     * - 4: Left
     * - 5: Upper-left
     *
     * Returns -1 if neighbor is out of bounds
     */
    fun getNeighborCell(cellIndex: Int, direction: Int): Int {
        val x = cellX(cellIndex)
        val y = cellY(cellIndex)
        val isOddRow = (y % 2) == 1

        val (nx, ny) = when (direction) {
            0 -> if (isOddRow) Pair(x + 1, y - 1) else Pair(x, y - 1) // upper-right
            1 -> Pair(x + 1, y) // right
            2 -> if (isOddRow) Pair(x + 1, y + 1) else Pair(x, y + 1) // lower-right
            3 -> if (isOddRow) Pair(x, y + 1) else Pair(x - 1, y + 1) // lower-left
            4 -> Pair(x - 1, y) // left
            5 -> if (isOddRow) Pair(x, y - 1) else Pair(x - 1, y - 1) // upper-left
            else -> return -1
        }

        return if (isValidCell(nx, ny)) cellIndex(nx, ny) else -1
    }

    /**
     * Build neighbor arrays for all cells in the grid
     */
    fun buildCellNeighbors(): Array<CellNeighbors> {
        return Array(TOTAL_CELLS) { cellIndex ->
            val directions = IntArray(6) { direction ->
                getNeighborCell(cellIndex, direction)
            }
            CellNeighbors(directions)
        }
    }

    /**
     * Calculate cell position for rendering
     * Returns Pair(x, y) in pixel coordinates
     */
    fun getCellPosition(cellIndex: Int, cellWidth: Float, cellHeight: Float): Pair<Float, Float> {
        val x = cellX(cellIndex)
        val y = cellY(cellIndex)
        val posX = x * cellWidth + if (y % 2 == 1) cellWidth / 2 else 0f
        val posY = y * cellHeight
        return Pair(posX, posY)
    }

    /**
     * Get all 6 neighbors of a cell (includes -1 for out-of-bounds)
     */
    fun getAllNeighbors(cellIndex: Int): IntArray {
        return IntArray(6) { direction ->
            getNeighborCell(cellIndex, direction)
        }
    }

    /**
     * Get valid neighbors of a cell (excludes out-of-bounds)
     */
    fun getValidNeighbors(cellIndex: Int): List<Int> {
        return getAllNeighbors(cellIndex).filter { it != -1 }
    }

    /**
     * Check if two cells are adjacent
     */
    fun areCellsAdjacent(cell1: Int, cell2: Int): Boolean {
        return getAllNeighbors(cell1).contains(cell2)
    }
}
