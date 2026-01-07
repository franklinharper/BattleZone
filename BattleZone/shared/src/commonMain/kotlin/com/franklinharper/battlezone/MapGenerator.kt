package com.franklinharper.battlezone

import kotlin.random.Random

/**
 * Map generator using percolation-based territory growth algorithm
 * Based on Dice Wars implementation
 */
object MapGenerator {

    /**
     * Generate a complete game map
     */
    fun generate(seed: Long? = null, playerCount: Int = 2): GameMap {
        val resolvedSeed = seed ?: Random.nextLong()
        val gameRandom = GameRandom(resolvedSeed)

        // Build cell adjacency
        val cellNeighbors = HexGrid.buildCellNeighbors()

        // Generate territories using percolation
        val cells = IntArray(HexGrid.TOTAL_CELLS) { 0 }
        val territoryCount = generateTerritories(cells, gameRandom)

        // Create territory objects
        val territories = createTerritories(cells, territoryCount)

        // Calculate territory properties
        calculateTerritoryProperties(cells, territories, cellNeighbors)

        // Calculate adjacency
        calculateTerritoryAdjacency(cells, territories, cellNeighbors)

        // Trace borders
        traceTerritoryBorders(cells, territories, cellNeighbors)

        // Assign territories to players
        assignTerritories(territories, gameRandom, playerCount)

        // Distribute armies
        distributeStartingArmies(territories, gameRandom, playerCount)

        val map = GameMap(
            gridWidth = HexGrid.GRID_WIDTH,
            gridHeight = HexGrid.GRID_HEIGHT,
            maxTerritories = GameRules.MAX_TERRITORIES,
            cells = cells,
            cellNeighbors = cellNeighbors,
            territories = territories,
            playerCount = playerCount,
            seed = resolvedSeed,
            gameRandom = gameRandom
        )

        return map
    }

    /**
     * Generate territories using percolation algorithm
     * Returns the number of territories created
     */
    private fun generateTerritories(cells: IntArray, random: GameRandom): Int {
        // Create shuffled array for randomization
        val shuffleArray = Array(HexGrid.TOTAL_CELLS) { it }
        random.shuffle(shuffleArray)
        val shuffleOrder = IntArray(HexGrid.TOTAL_CELLS) { i ->
            shuffleArray.indexOf(i)
        }

        // Track which cells are adjacent to existing territories
        val adjacentCells = BooleanArray(HexGrid.TOTAL_CELLS) { false }

        // Start with one random cell
        val startCell = random.nextInt(HexGrid.TOTAL_CELLS)
        adjacentCells[startCell] = true

        var territoryId = 1

        // Generate territories
        while (territoryId <= GameRules.MAX_TERRITORIES) {
            // Find unassigned cell with lowest shuffle number that's adjacent
            var seedCell = -1
            var lowestShuffle = Int.MAX_VALUE

            for (i in cells.indices) {
                if (cells[i] == 0 && adjacentCells[i] && shuffleOrder[i] < lowestShuffle) {
                    seedCell = i
                    lowestShuffle = shuffleOrder[i]
                }
            }

            if (seedCell == -1) break // No more cells available

            // Grow territory from seed
            growTerritory(cells, seedCell, territoryId, shuffleOrder, adjacentCells, random)

            territoryId++
        }

        // Clean up small territories and fill water
        val finalCount = cleanupTerritories(cells, territoryId - 1)

        return finalCount
    }

    /**
     * Grow a single territory from a seed cell using percolation
     * Matches JavaScript implementation with two phases:
     * Phase 1: Grow to target size using percolation
     * Phase 2: Add all remaining adjacent cells and mark their neighbors
     */
    private fun growTerritory(
        cells: IntArray,
        seedCell: Int,
        territoryId: Int,
        shuffleOrder: IntArray,
        adjacentCells: BooleanArray,
        random: GameRandom
    ) {
        // Track cells adjacent to this territory during growth
        val nextCells = BooleanArray(HexGrid.TOTAL_CELLS) { false }

        var currentCell = seedCell
        var size = 0

        // Phase 1: Grow to target size
        while (size < GameRules.TARGET_TERRITORY_SIZE) {
            // Assign current cell to territory
            cells[currentCell] = territoryId
            size++

            // Mark all neighbors as adjacent to this territory
            val neighbors = HexGrid.getAllNeighbors(currentCell)
            for (neighbor in neighbors) {
                if (neighbor != -1) {
                    nextCells[neighbor] = true
                }
            }

            // Find next cell to grow into (lowest shuffle number among unassigned neighbors)
            var nextCell = -1
            var lowestShuffle = Int.MAX_VALUE

            for (neighbor in neighbors) {
                if (neighbor != -1 && cells[neighbor] == 0 && shuffleOrder[neighbor] < lowestShuffle) {
                    nextCell = neighbor
                    lowestShuffle = shuffleOrder[neighbor]
                }
            }

            if (nextCell == -1) break // Can't grow anymore
            currentCell = nextCell
        }

        // Phase 2: Add all remaining adjacent cells (JavaScript lines 414-426)
        for (i in cells.indices) {
            if (nextCells[i] && cells[i] == 0) {
                // Add this cell to the territory
                cells[i] = territoryId
                size++

                // Mark all neighbors of this cell as candidates for future territories
                val neighbors = HexGrid.getAllNeighbors(i)
                for (neighbor in neighbors) {
                    if (neighbor != -1) {
                        adjacentCells[neighbor] = true
                    }
                }
            }
        }
    }

    /**
     * Clean up territories: fill water holes and remove small territories
     */
    private fun cleanupTerritories(cells: IntArray, maxTerritoryId: Int): Int {
        // Fill single-cell water spaces (isolated unassigned cells)
        fillWaterHoles(cells)

        // Count territory sizes
        val territorySizes = IntArray(maxTerritoryId + 1) { 0 }
        for (cell in cells) {
            if (cell > 0 && cell <= maxTerritoryId) {
                territorySizes[cell]++
            }
        }

        // Remove territories that are too small (JavaScript lines 250-257)
        // Mark territories with size <= 5 for deletion
        val isDeleted = BooleanArray(maxTerritoryId + 1) { false }
        for (territoryId in 1..maxTerritoryId) {
            if (territorySizes[territoryId] < GameRules.MIN_TERRITORY_SIZE) {
                isDeleted[territoryId] = true
            }
        }

        // Set all cells in deleted territories to 0 (unassigned)
        for (i in cells.indices) {
            val territoryId = cells[i]
            if (territoryId > 0 && territoryId <= maxTerritoryId && isDeleted[territoryId]) {
                cells[i] = 0
            }
        }

        // Renumber territories sequentially
        return renumberTerritories(cells, maxTerritoryId)
    }

    /**
     * Fill isolated single-cell water spaces
     * Only fills cells that are completely surrounded by territories (no unassigned neighbors)
     * Matches JavaScript behavior: if( f==0 ) this.cel[i] = a;
     */
    private fun fillWaterHoles(cells: IntArray) {
        for (i in cells.indices) {
            if (cells[i] == 0) {
                // Check if completely surrounded by territories (no unassigned neighbors)
                var hasUnassignedNeighbor = false
                var territoryToAssign = 0

                val neighbors = HexGrid.getAllNeighbors(i)
                for (neighbor in neighbors) {
                    if (neighbor == -1) continue
                    if (cells[neighbor] == 0) {
                        hasUnassignedNeighbor = true
                    } else {
                        territoryToAssign = cells[neighbor]
                    }
                }

                // Only fill if completely surrounded by territories
                if (!hasUnassignedNeighbor && territoryToAssign > 0) {
                    cells[i] = territoryToAssign
                }
            }
        }
    }

    /**
     * Find a neighboring territory for a cell
     */
    private fun findNeighboringTerritory(cells: IntArray, cellIndex: Int): Int? {
        val neighbors = HexGrid.getAllNeighbors(cellIndex)
        for (neighbor in neighbors) {
            if (neighbor != -1 && cells[neighbor] > 0) {
                return cells[neighbor]
            }
        }
        return null
    }

    /**
     * Renumber territories sequentially (remove gaps)
     */
    private fun renumberTerritories(cells: IntArray, maxTerritoryId: Int): Int {
        val mapping = mutableMapOf<Int, Int>()
        var newId = 1

        for (oldId in 1..maxTerritoryId) {
            // Check if this territory still exists
            if (cells.any { it == oldId }) {
                mapping[oldId] = newId
                newId++
            }
        }

        // Apply renumbering
        for (i in cells.indices) {
            if (cells[i] > 0) {
                cells[i] = mapping[cells[i]] ?: 0
            }
        }

        return newId - 1
    }

    /**
     * Create territory objects from cell data
     */
    private fun createTerritories(cells: IntArray, territoryCount: Int): Array<Territory> {
        return Array(territoryCount) { index ->
            val id = index
            Territory(
                id = id,
                size = 0,
                centerPos = 0,
                owner = -1,
                armyCount = 0,
                left = Int.MAX_VALUE,
                right = Int.MIN_VALUE,
                top = Int.MAX_VALUE,
                bottom = Int.MIN_VALUE,
                centerX = 0,
                centerY = 0,
                borderCells = intArrayOf(),
                borderDirections = intArrayOf(),
                adjacentTerritories = BooleanArray(territoryCount) { false }
            )
        }
    }

    /**
     * Calculate territory properties (size, bounding box, center)
     */
    private fun calculateTerritoryProperties(
        cells: IntArray,
        territories: Array<Territory>,
        cellNeighbors: Array<CellNeighbors>
    ) {
        val territoryCells = Array(territories.size) { mutableListOf<Int>() }

        // Calculate size, bounding box, and cell lists.
        for (i in cells.indices) {
            val territoryId = cells[i]
            if (territoryId > 0 && territoryId <= territories.size) {
                val territory = territories[territoryId - 1]
                territory.size++
                territoryCells[territory.id].add(i)

                val x = HexGrid.cellX(i)
                val y = HexGrid.cellY(i)

                territory.left = minOf(territory.left, x)
                territory.right = maxOf(territory.right, x)
                territory.top = minOf(territory.top, y)
                territory.bottom = maxOf(territory.bottom, y)
            }
        }

        // Calculate center position using max distance to border.
        for (territory in territories) {
            val cx = (territory.left + territory.right) / 2
            val cy = (territory.top + territory.bottom) / 2
            territory.centerX = cx
            territory.centerY = cy

            val cellsInTerritory = territoryCells[territory.id]
            if (cellsInTerritory.isEmpty()) {
                territory.centerPos = 0
                continue
            }

            val distanceToBorder = IntArray(cells.size) { -1 }
            val queue = ArrayDeque<Int>()

            for (cellIndex in cellsInTerritory) {
                val neighbors = cellNeighbors[cellIndex].directions
                var isBorder = false
                for (neighbor in neighbors) {
                    if (neighbor == -1 || cells[neighbor] != territory.id + 1) {
                        isBorder = true
                        break
                    }
                }
                if (isBorder) {
                    distanceToBorder[cellIndex] = 0
                    queue.add(cellIndex)
                }
            }

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val currentDistance = distanceToBorder[current]
                val neighbors = cellNeighbors[current].directions
                for (neighbor in neighbors) {
                    if (neighbor != -1 && cells[neighbor] == territory.id + 1 && distanceToBorder[neighbor] == -1) {
                        distanceToBorder[neighbor] = currentDistance + 1
                        queue.add(neighbor)
                    }
                }
            }

            var bestCell = cellsInTerritory.first()
            var bestDistance = distanceToBorder[bestCell]
            var bestCenterDistance = Double.MAX_VALUE

            for (cellIndex in cellsInTerritory) {
                val distance = distanceToBorder[cellIndex]
                val x = HexGrid.cellX(cellIndex)
                val y = HexGrid.cellY(cellIndex)
                val centerDistance = (x - cx) * (x - cx) + (y - cy) * (y - cy).toDouble()

                if (distance > bestDistance || (distance == bestDistance && centerDistance < bestCenterDistance)) {
                    bestDistance = distance
                    bestCenterDistance = centerDistance
                    bestCell = cellIndex
                }
            }

            territory.centerPos = bestCell
        }
    }

    /**
     * Calculate territory adjacency
     */
    private fun calculateTerritoryAdjacency(
        cells: IntArray,
        territories: Array<Territory>,
        cellNeighbors: Array<CellNeighbors>
    ) {
        for (i in cells.indices) {
            val territoryId = cells[i]
            if (territoryId == 0) continue

            val neighbors = cellNeighbors[i].directions
            for (neighbor in neighbors) {
                if (neighbor != -1) {
                    val neighborTerritoryId = cells[neighbor]
                    if (neighborTerritoryId > 0 && neighborTerritoryId != territoryId) {
                        territories[territoryId - 1].adjacentTerritories[neighborTerritoryId - 1] = true
                        territories[neighborTerritoryId - 1].adjacentTerritories[territoryId - 1] = true
                    }
                }
            }
        }
    }

    /**
     * Trace territory borders (simplified version)
     * For Phase 1, we'll use a simple approach
     */
    private fun traceTerritoryBorders(
        cells: IntArray,
        territories: Array<Territory>,
        cellNeighbors: Array<CellNeighbors>
    ) {
        for (territory in territories) {
            // Find all cells on the border of this territory
            val borderCells = mutableListOf<Int>()
            val borderDirections = mutableListOf<Int>()

            for (i in cells.indices) {
                if (cells[i] == territory.id + 1) {
                    // Check if this cell is on the border
                    val neighbors = cellNeighbors[i].directions
                    for (dir in neighbors.indices) {
                        val neighbor = neighbors[dir]
                        if (neighbor == -1 || cells[neighbor] != cells[i]) {
                            borderCells.add(i)
                            borderDirections.add(dir)
                        }
                    }
                }
            }

            // Store border data (mutable arrays)
            val newBorderCells = borderCells.toIntArray()
            val newBorderDirections = borderDirections.toIntArray()

            // Create new territory with border data
            territories[territory.id] = territory.copy(
                borderCells = newBorderCells,
                borderDirections = newBorderDirections
            )
        }
    }

    /**
     * Assign territories to players
     */
    private fun assignTerritories(territories: Array<Territory>, random: GameRandom, playerCount: Int) {
        val territoryIndices = territories.indices.toList().toTypedArray()
        random.shuffle(territoryIndices)

        for (i in territoryIndices.indices) {
            val territory = territories[territoryIndices[i]]
            territory.owner = i % playerCount // Cycle through all players
        }
    }

    /**
     * Distribute starting armies
     */
    private fun distributeStartingArmies(territories: Array<Territory>, random: GameRandom, playerCount: Int) {
        // Set all territories to 1 army
        for (territory in territories) {
            territory.armyCount = 1
        }

        // Calculate additional armies
        val additionalArmies = territories.size * GameRules.STARTING_ARMY_MULTIPLIER

        // Distribute additional armies alternating between players
        repeat(additionalArmies) { index ->
            val playerId = index % playerCount

            // Get all territories for this player that are below the max army cap
            val playerTerritories = territories.filter {
                it.owner == playerId && it.armyCount < GameRules.MAX_ARMIES_PER_TERRITORY
            }

            if (playerTerritories.isNotEmpty()) {
                val territory = playerTerritories[random.nextInt(playerTerritories.size)]
                territory.armyCount++
            }
        }
    }
}
