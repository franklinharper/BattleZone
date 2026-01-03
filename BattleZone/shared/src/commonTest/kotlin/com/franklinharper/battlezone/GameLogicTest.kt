package com.franklinharper.battlezone

import kotlin.test.*

class GameLogicTest {

    /**
     * Helper function to create a test map with specific territory configuration
     */
    private fun createTestMap(territoryCount: Int): GameMap {
        val cells = IntArray(HexGrid.TOTAL_CELLS) { 0 }
        val cellNeighbors = HexGrid.buildCellNeighbors()

        val territories = Array(territoryCount) { index ->
            Territory(
                id = index,
                size = 1,
                centerPos = index,
                owner = -1,
                armyCount = 1,
                left = 0,
                right = 0,
                top = 0,
                bottom = 0,
                centerX = 0,
                centerY = 0,
                borderCells = intArrayOf(),
                borderDirections = intArrayOf(),
                adjacentTerritories = BooleanArray(territoryCount) { false }
            )
        }

        return GameMap(
            cells = cells,
            cellNeighbors = cellNeighbors,
            territories = territories,
            gameRandom = GameRandom(42L)
        )
    }

    @Test
    fun `single territory returns size 1`() {
        val map = createTestMap(1)
        map.territories[0].owner = 0

        val result = GameLogic.calculateLargestConnected(map, 0)

        assertEquals(1, result)
    }

    @Test
    fun `no territories returns size 0`() {
        val map = createTestMap(3)
        map.territories[0].owner = 1
        map.territories[1].owner = 1
        map.territories[2].owner = 1

        val result = GameLogic.calculateLargestConnected(map, 0)

        assertEquals(0, result)
    }

    @Test
    fun `fully disconnected territories return size 1`() {
        val map = createTestMap(5)
        // Assign all territories to player 0, but no adjacencies
        for (i in 0 until 5) {
            map.territories[i].owner = 0
        }

        val result = GameLogic.calculateLargestConnected(map, 0)

        assertEquals(1, result, "With no adjacencies, largest component should be 1")
    }

    @Test
    fun `multiple groups returns largest component`() {
        val map = createTestMap(10)

        // Player 0 has two groups:
        // Group 1: territories 0, 1, 2, 3, 4 (size 5)
        // Group 2: territories 5, 6 (size 2)
        // Group 3: territory 7 (size 1)
        for (i in 0..7) {
            map.territories[i].owner = 0
        }

        // Other territories belong to player 1
        map.territories[8].owner = 1
        map.territories[9].owner = 1

        // Set up adjacencies for group 1 (territories 0-4)
        map.territories[0].adjacentTerritories[1] = true
        map.territories[1].adjacentTerritories[0] = true
        map.territories[1].adjacentTerritories[2] = true
        map.territories[2].adjacentTerritories[1] = true
        map.territories[2].adjacentTerritories[3] = true
        map.territories[3].adjacentTerritories[2] = true
        map.territories[3].adjacentTerritories[4] = true
        map.territories[4].adjacentTerritories[3] = true

        // Set up adjacencies for group 2 (territories 5-6)
        map.territories[5].adjacentTerritories[6] = true
        map.territories[6].adjacentTerritories[5] = true

        // Territory 7 is isolated (no adjacencies)

        val result = GameLogic.calculateLargestConnected(map, 0)

        assertEquals(5, result, "Largest connected component should be 5")
    }

    @Test
    fun `complex connected graph returns correct size`() {
        val map = createTestMap(8)

        // Player 0 has territories 0-5
        for (i in 0..5) {
            map.territories[i].owner = 0
        }

        // Player 1 has territories 6-7
        map.territories[6].owner = 1
        map.territories[7].owner = 1

        // Create a complex connected graph for player 0
        // 0 - 1 - 2
        // |   |
        // 3 - 4 - 5
        map.territories[0].adjacentTerritories[1] = true
        map.territories[0].adjacentTerritories[3] = true
        map.territories[1].adjacentTerritories[0] = true
        map.territories[1].adjacentTerritories[2] = true
        map.territories[1].adjacentTerritories[4] = true
        map.territories[2].adjacentTerritories[1] = true
        map.territories[3].adjacentTerritories[0] = true
        map.territories[3].adjacentTerritories[4] = true
        map.territories[4].adjacentTerritories[1] = true
        map.territories[4].adjacentTerritories[3] = true
        map.territories[4].adjacentTerritories[5] = true
        map.territories[5].adjacentTerritories[4] = true

        val result = GameLogic.calculateLargestConnected(map, 0)

        assertEquals(6, result, "All 6 territories should be connected")
    }

    @Test
    fun `reinforcement distribution respects 8 army cap`() {
        val map = createTestMap(2)
        map.territories[0].owner = 0
        map.territories[1].owner = 0
        map.territories[0].armyCount = 7
        map.territories[1].armyCount = 7

        val reserve = GameLogic.distributeReinforcements(map, 0, 5, 0)

        // Both territories can take 1 army each, remaining 3 go to reserve
        assertTrue(map.territories[0].armyCount <= 8)
        assertTrue(map.territories[1].armyCount <= 8)
        assertEquals(3, reserve, "3 armies should go to reserve")
    }

    @Test
    fun `all territories maxed sends all to reserve`() {
        val map = createTestMap(2)
        map.territories[0].owner = 0
        map.territories[1].owner = 0
        map.territories[0].armyCount = 8
        map.territories[1].armyCount = 8

        val reserve = GameLogic.distributeReinforcements(map, 0, 5, 0)

        // All territories at max, all 5 armies go to reserve
        assertEquals(8, map.territories[0].armyCount)
        assertEquals(8, map.territories[1].armyCount)
        assertEquals(5, reserve)
    }

    @Test
    fun `reserve armies deployed in next reinforcement`() {
        val map = createTestMap(2)
        map.territories[0].owner = 0
        map.territories[1].owner = 0
        map.territories[0].armyCount = 1
        map.territories[1].armyCount = 1

        val currentReserve = 3
        val newReinforcements = 2

        val newReserve = GameLogic.distributeReinforcements(
            map, 0, newReinforcements, currentReserve
        )

        // Should distribute 3 + 2 = 5 armies total
        val totalArmies = map.territories[0].armyCount + map.territories[1].armyCount
        assertEquals(7, totalArmies, "Should distribute 5 armies (3 reserve + 2 new)")
        assertEquals(0, newReserve, "All armies should be distributed")
    }

    @Test
    fun `reinforcement count matches largest connected component`() {
        val map = createTestMap(6)

        // Player 0 has territories 0-4, with 0-3 connected (size 4)
        for (i in 0..4) {
            map.territories[i].owner = 0
        }

        // Set up adjacencies: 0-1-2-3 connected, 4 isolated
        map.territories[0].adjacentTerritories[1] = true
        map.territories[1].adjacentTerritories[0] = true
        map.territories[1].adjacentTerritories[2] = true
        map.territories[2].adjacentTerritories[1] = true
        map.territories[2].adjacentTerritories[3] = true
        map.territories[3].adjacentTerritories[2] = true

        val reinforcements = GameLogic.calculateReinforcements(map, 0)

        assertEquals(4, reinforcements)
    }

    @Test
    fun `updatePlayerState calculates correct values`() {
        val map = createTestMap(5)

        // Player 0 has territories 0-2
        map.territories[0].owner = 0
        map.territories[0].armyCount = 3
        map.territories[1].owner = 0
        map.territories[1].armyCount = 5
        map.territories[2].owner = 0
        map.territories[2].armyCount = 2

        // Player 1 has territories 3-4
        map.territories[3].owner = 1
        map.territories[4].owner = 1

        // Connect territories 0-1 for player 0
        map.territories[0].adjacentTerritories[1] = true
        map.territories[1].adjacentTerritories[0] = true

        val playerState = PlayerState(0, 0, 0, 0)
        GameLogic.updatePlayerState(map, playerState, 0)

        assertEquals(3, playerState.territoryCount)
        assertEquals(10, playerState.totalArmies)
        assertEquals(2, playerState.largestConnectedSize)
    }

    @Test
    fun `distribution is random but deterministic with seed`() {
        val map1 = createTestMap(3)
        val map2 = createTestMap(3)

        for (i in 0..2) {
            map1.territories[i].owner = 0
            map1.territories[i].armyCount = 1
            map2.territories[i].owner = 0
            map2.territories[i].armyCount = 1
        }

        // Use same random seed
        map1.gameRandom.nextInt(100) // consume same random values
        map2.gameRandom.nextInt(100)

        GameLogic.distributeReinforcements(map1, 0, 5, 0)
        GameLogic.distributeReinforcements(map2, 0, 5, 0)

        // With same seed, distribution should be identical
        for (i in 0..2) {
            assertEquals(
                map1.territories[i].armyCount,
                map2.territories[i].armyCount,
                "Territory $i should have same army count with same seed"
            )
        }
    }

    @Test
    fun `generated map has valid largest connected component`() {
        // Test with actual generated map
        val map = MapGenerator.generate(seed = 12345L)

        val player0Connected = GameLogic.calculateLargestConnected(map, 0)
        val player1Connected = GameLogic.calculateLargestConnected(map, 1)

        // Both should have at least 1 territory
        assertTrue(player0Connected >= 1, "Player 0 should have at least 1 connected territory")
        assertTrue(player1Connected >= 1, "Player 1 should have at least 1 connected territory")

        // Should not exceed total territories for that player
        val player0Total = map.territories.count { it.owner == 0 }
        val player1Total = map.territories.count { it.owner == 1 }

        assertTrue(player0Connected <= player0Total, "Connected size should not exceed total territories")
        assertTrue(player1Connected <= player1Total, "Connected size should not exceed total territories")
    }
}
