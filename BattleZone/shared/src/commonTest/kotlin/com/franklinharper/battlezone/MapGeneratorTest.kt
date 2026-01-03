package com.franklinharper.battlezone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Tests for MapGenerator to verify behavior matches legacy JavaScript implementation
 */
class MapGeneratorTest {

    @Test
    fun testMapGenerationWithSeed() {
        // Generate map with a fixed seed
        val map = MapGenerator.generate(seed = 12345L)

        assertNotNull(map, "Map should not be null")
        assertEquals(HexGrid.GRID_WIDTH, map.gridWidth)
        assertEquals(HexGrid.GRID_HEIGHT, map.gridHeight)
        assertEquals(2, map.playerCount)
    }

    @Test
    fun testDeterministicGenerationWithSameSeed() {
        // Maps generated with same seed should be identical
        val map1 = MapGenerator.generate(seed = 42L)
        val map2 = MapGenerator.generate(seed = 42L)

        assertEquals(map1.territories.size, map2.territories.size, "Same seed should produce same number of territories")

        // Verify territories are identical
        for (i in map1.territories.indices) {
            assertEquals(map1.territories[i].size, map2.territories[i].size, "Territory $i size should match")
            assertEquals(map1.territories[i].owner, map2.territories[i].owner, "Territory $i owner should match")
            assertEquals(map1.territories[i].armyCount, map2.territories[i].armyCount, "Territory $i army count should match")
        }

        // Verify cell assignments are identical
        for (i in map1.cells.indices) {
            assertEquals(map1.cells[i], map2.cells[i], "Cell $i should have same territory assignment")
        }
    }

    @Test
    fun testTerritoryCountInValidRange() {
        // Test multiple seeds to ensure territory count is always in valid range
        repeat(20) { seed ->
            val map = MapGenerator.generate(seed = seed.toLong())
            val territoryCount = map.territories.size

            assertTrue(
                territoryCount >= 18 && territoryCount <= 32,
                "Territory count $territoryCount should be between 18 and 32 (seed: $seed)"
            )
        }
    }

    @Test
    fun testNoSmallTerritories() {
        // Verify no territories smaller than MIN_TERRITORY_SIZE (6 cells) exist
        // This was a bug we fixed - matching legacy behavior
        repeat(20) { seed ->
            val map = MapGenerator.generate(seed = seed.toLong())

            for (territory in map.territories) {
                assertTrue(
                    territory.size >= 6,
                    "Territory ${territory.id} has only ${territory.size} cells, should be >= 6 (seed: $seed)"
                )
            }
        }
    }

    @Test
    fun testTerritorySizeMatchesCellCount() {
        // Verify each territory's size matches actual cell count
        val map = MapGenerator.generate(seed = 99L)

        for (territory in map.territories) {
            val actualCellCount = map.cells.count { it == territory.id + 1 }
            assertEquals(
                territory.size,
                actualCellCount,
                "Territory ${territory.id} size field (${territory.size}) should match actual cells ($actualCellCount)"
            )
        }
    }

    @Test
    fun testAllTerritoriesAssignedToPlayers() {
        // Verify all territories have valid player assignments (0 or 1)
        val map = MapGenerator.generate(seed = 123L)

        for (territory in map.territories) {
            assertTrue(
                territory.owner == 0 || territory.owner == 1,
                "Territory ${territory.id} has invalid owner ${territory.owner}, should be 0 or 1"
            )
        }
    }

    @Test
    fun testPlayerTerritoryDistribution() {
        // Verify territories are distributed fairly between players
        val map = MapGenerator.generate(seed = 456L)

        val player0Count = map.territories.count { it.owner == 0 }
        val player1Count = map.territories.count { it.owner == 1 }

        // Distribution should be roughly equal (within 1 territory)
        val diff = kotlin.math.abs(player0Count - player1Count)
        assertTrue(
            diff <= 1,
            "Players should have equal territories (±1). Player 0: $player0Count, Player 1: $player1Count"
        )
    }

    @Test
    fun testAllTerritoriesHaveArmies() {
        // Verify all territories start with at least 1 army
        val map = MapGenerator.generate(seed = 789L)

        for (territory in map.territories) {
            assertTrue(
                territory.armyCount >= 1,
                "Territory ${territory.id} should have at least 1 army, has ${territory.armyCount}"
            )
        }
    }

    @Test
    fun testArmyDistribution() {
        // Total armies should be: territoryCount + (territoryCount * 2)
        // Each territory starts with 1, then territoryCount*2 additional armies distributed
        val map = MapGenerator.generate(seed = 111L)

        val totalArmies = map.territories.sumOf { it.armyCount }
        val expectedArmies = map.territories.size + (map.territories.size * 2)

        assertEquals(
            expectedArmies,
            totalArmies,
            "Total armies should be ${map.territories.size} base + ${map.territories.size * 2} distributed = $expectedArmies"
        )
    }

    @Test
    fun testNoArmiesExceedMaximum() {
        // Verify no territory has more than 8 armies (max from distribution algorithm)
        val map = MapGenerator.generate(seed = 222L)

        for (territory in map.territories) {
            assertTrue(
                territory.armyCount <= 8,
                "Territory ${territory.id} should have max 8 armies, has ${territory.armyCount}"
            )
        }
    }

    @Test
    fun testAllCellsValid() {
        // Verify all cells either belong to a territory or are unassigned (0)
        val map = MapGenerator.generate(seed = 333L)

        for (i in map.cells.indices) {
            val cellValue = map.cells[i]
            assertTrue(
                cellValue >= 0 && cellValue <= map.territories.size,
                "Cell $i has invalid value $cellValue, should be 0 or 1-${map.territories.size}"
            )
        }
    }

    @Test
    fun testTerritoryAdjacency() {
        // Verify territory adjacency is calculated correctly
        val map = MapGenerator.generate(seed = 444L)

        for (i in map.cells.indices) {
            val territoryId = map.cells[i]
            if (territoryId == 0) continue

            val neighbors = map.cellNeighbors[i].directions
            for (neighborCell in neighbors) {
                if (neighborCell == -1) continue

                val neighborTerritoryId = map.cells[neighborCell]
                if (neighborTerritoryId > 0 && neighborTerritoryId != territoryId) {
                    // These territories should be marked as adjacent
                    assertTrue(
                        map.territories[territoryId - 1].adjacentTerritories[neighborTerritoryId - 1],
                        "Territory ${territoryId - 1} should be adjacent to ${neighborTerritoryId - 1}"
                    )
                }
            }
        }
    }

    @Test
    fun testTerritoryBoundingBox() {
        // Verify each territory's bounding box contains all its cells
        val map = MapGenerator.generate(seed = 555L)

        for (territory in map.territories) {
            for (i in map.cells.indices) {
                if (map.cells[i] == territory.id + 1) {
                    val x = HexGrid.cellX(i)
                    val y = HexGrid.cellY(i)

                    assertTrue(
                        x >= territory.left && x <= territory.right,
                        "Cell $i at x=$x should be within territory ${territory.id} bounding box [${territory.left}, ${territory.right}]"
                    )
                    assertTrue(
                        y >= territory.top && y <= territory.bottom,
                        "Cell $i at y=$y should be within territory ${territory.id} bounding box [${territory.top}, ${territory.bottom}]"
                    )
                }
            }
        }
    }

    @Test
    fun testTerritoryCenterWithinBounds() {
        // Verify each territory's center position is actually within that territory
        val map = MapGenerator.generate(seed = 666L)

        for (territory in map.territories) {
            val centerCellValue = map.cells[territory.centerPos]
            assertEquals(
                territory.id + 1,
                centerCellValue,
                "Territory ${territory.id} center at cell ${territory.centerPos} should belong to this territory"
            )
        }
    }

    @Test
    fun testHasBorderCells() {
        // Verify territories have border cells (used for rendering)
        val map = MapGenerator.generate(seed = 777L)

        for (territory in map.territories) {
            assertTrue(
                territory.borderCells.isNotEmpty(),
                "Territory ${territory.id} should have border cells"
            )
            assertEquals(
                territory.borderCells.size,
                territory.borderDirections.size,
                "Territory ${territory.id} should have matching border cells and directions"
            )
        }
    }

    @Test
    fun testUnassignedCellsExist() {
        // Verify there are some unassigned cells (water)
        // This matches legacy behavior after fixing water hole filling
        val map = MapGenerator.generate(seed = 888L)

        val unassignedCount = map.cells.count { it == 0 }
        assertTrue(
            unassignedCount > 0,
            "Map should have some unassigned cells (water), found $unassignedCount"
        )
    }

    @Test
    fun testWaterHolesProperlyFilled() {
        // Verify that single cells completely surrounded by same territory don't exist
        // (they should be filled by fillWaterHoles)
        val map = MapGenerator.generate(seed = 999L)

        for (i in map.cells.indices) {
            if (map.cells[i] == 0) {
                // Check if this unassigned cell has at least one unassigned neighbor
                // (if all neighbors are territories, it should have been filled)
                val neighbors = HexGrid.getAllNeighbors(i)
                var hasUnassignedNeighbor = false
                var hasTerritoryNeighbor = false

                for (neighbor in neighbors) {
                    if (neighbor == -1) continue
                    if (map.cells[neighbor] == 0) {
                        hasUnassignedNeighbor = true
                    } else {
                        hasTerritoryNeighbor = true
                    }
                }

                if (hasTerritoryNeighbor) {
                    assertTrue(
                        hasUnassignedNeighbor,
                        "Unassigned cell $i is completely surrounded by territories, should have been filled"
                    )
                }
            }
        }
    }

    @Test
    fun testStatisticalPropertiesAcrossMultipleSeeds() {
        // Generate multiple maps and verify statistical properties match expected ranges
        val maps = (1..50).map { seed ->
            MapGenerator.generate(seed = seed.toLong())
        }

        // Average territory count should be around 20-32 (matching MIN/MAX constants)
        val avgTerritoryCount = maps.map { it.territories.size }.average()
        assertTrue(
            avgTerritoryCount in 18.0..32.0,
            "Average territory count $avgTerritoryCount should be in reasonable range (18-32)"
        )

        // Average territory size should be around 15-35 cells
        // (896 total cells / ~30 territories = ~30, but varies with water and territory count)
        val avgTerritorySize = maps.flatMap { map ->
            map.territories.map { it.size }
        }.average()
        assertTrue(
            avgTerritorySize in 15.0..35.0,
            "Average territory size $avgTerritorySize should be in reasonable range"
        )

        // Most maps should have unassigned cells
        val mapsWithWater = maps.count { it.cells.any { cell -> cell == 0 } }
        assertTrue(
            mapsWithWater >= 40,
            "Most maps ($mapsWithWater/50) should have unassigned cells"
        )
    }
}
