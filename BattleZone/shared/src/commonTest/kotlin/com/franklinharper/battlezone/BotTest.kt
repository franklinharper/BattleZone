package com.franklinharper.battlezone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Test suite for Bot AI implementation
 */
class BotTest {

    /**
     * Helper function to create a test map with specified territories
     */
    private fun createTestMap(
        territories: List<TestTerritory>,
        seed: Long = 42L
    ): GameMap {
        val gridWidth = 28
        val gridHeight = 32
        val maxTerritories = 32

        val cells = IntArray(gridWidth * gridHeight)
        val cellNeighbors = Array(gridWidth * gridHeight) {
            CellNeighbors(IntArray(6) { -1 })
        }

        // Create territory array
        val territoryArray = Array(maxTerritories) { territoryId ->
            val testTerritory = territories.find { it.id == territoryId }
            Territory(
                id = territoryId,
                size = testTerritory?.size ?: 0,
                centerPos = 0,
                owner = testTerritory?.owner ?: -1,
                armyCount = testTerritory?.armyCount ?: 1,
                left = 0,
                right = 0,
                top = 0,
                bottom = 0,
                centerX = 0,
                centerY = 0,
                borderCells = IntArray(0),
                borderDirections = IntArray(0),
                adjacentTerritories = BooleanArray(maxTerritories) { adjacentId ->
                    testTerritory?.adjacentTo?.contains(adjacentId) ?: false
                }
            )
        }

        return GameMap(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            maxTerritories = maxTerritories,
            cells = cells,
            cellNeighbors = cellNeighbors,
            territories = territoryArray,
            playerCount = 2,
            seed = seed,
            gameRandom = GameRandom(seed)
        )
    }

    /**
     * Helper data class for defining test territories
     */
    data class TestTerritory(
        val id: Int,
        val owner: Int,
        val armyCount: Int,
        val adjacentTo: List<Int>,
        val size: Int = 1
    )

    @Test
    fun `bot does not attack stronger territories`() {
        // Setup: Player 0 has territory with 3 armies, adjacent to enemy with 5 armies
        val map = createTestMap(
            territories = listOf(
                TestTerritory(id = 1, owner = 0, armyCount = 3, adjacentTo = listOf(2)),
                TestTerritory(id = 2, owner = 1, armyCount = 5, adjacentTo = listOf(1))
            )
        )

        val bot = DefaultBot(map.gameRandom)
        val decision = bot.decide(map, playerId = 0)

        // Should skip because attacking would be unwise (enemy is stronger)
        assertIs<BotDecision.Skip>(decision)
    }

    @Test
    fun `bot attacks weaker adjacent territories`() {
        // Setup: Player 0 has territory with 5 armies, adjacent to enemy with 2 armies
        val map = createTestMap(
            territories = listOf(
                TestTerritory(id = 1, owner = 0, armyCount = 5, adjacentTo = listOf(2)),
                TestTerritory(id = 2, owner = 1, armyCount = 2, adjacentTo = listOf(1))
            )
        )

        val bot = DefaultBot(map.gameRandom)
        val decision = bot.decide(map, playerId = 0)

        // Should attack because we're stronger
        assertIs<BotDecision.Attack>(decision)
        assertEquals(1, decision.fromTerritoryId)
        assertEquals(2, decision.toTerritoryId)
    }

    @Test
    fun `bot ends turn when no valid attacks available`() {
        // Setup: Player 0 has only territories with 1 army (can't attack)
        val map = createTestMap(
            territories = listOf(
                TestTerritory(id = 1, owner = 0, armyCount = 1, adjacentTo = listOf(2)),
                TestTerritory(id = 2, owner = 1, armyCount = 3, adjacentTo = listOf(1))
            )
        )

        val bot = DefaultBot(map.gameRandom)
        val decision = bot.decide(map, playerId = 0)

        // Should skip because we can't attack (only 1 army)
        assertIs<BotDecision.Skip>(decision)
    }

    @Test
    fun `bot does not attack non-adjacent territories`() {
        // Setup: Player 0 has territory with 5 armies, enemy has weak territory but not adjacent
        val map = createTestMap(
            territories = listOf(
                TestTerritory(id = 1, owner = 0, armyCount = 5, adjacentTo = listOf()),  // Not adjacent to 2
                TestTerritory(id = 2, owner = 1, armyCount = 2, adjacentTo = listOf())
            )
        )

        val bot = DefaultBot(map.gameRandom)
        val decision = bot.decide(map, playerId = 0)

        // Should skip because no adjacent enemies
        assertIs<BotDecision.Skip>(decision)
    }

    @Test
    fun `bot does not attack own territories`() {
        // Setup: Player 0 has two adjacent territories
        val map = createTestMap(
            territories = listOf(
                TestTerritory(id = 1, owner = 0, armyCount = 5, adjacentTo = listOf(2)),
                TestTerritory(id = 2, owner = 0, armyCount = 2, adjacentTo = listOf(1))
            )
        )

        val bot = DefaultBot(map.gameRandom)
        val decision = bot.decide(map, playerId = 0)

        // Should skip because all adjacent territories are owned by same player
        assertIs<BotDecision.Skip>(decision)
    }

    @Test
    fun `bot prioritizes attacking dominant player`() {
        // Setup: Player 2 is dominant (>40% of armies)
        // Player 0 has choice: attack weak player 1, or slightly stronger dominant player 2
        val map = createTestMap(
            territories = listOf(
                // Player 0: 5 armies total
                TestTerritory(id = 1, owner = 0, armyCount = 5, adjacentTo = listOf(2, 3)),

                // Player 1: 3 armies total (weak, non-dominant)
                TestTerritory(id = 2, owner = 1, armyCount = 3, adjacentTo = listOf(1)),

                // Player 2: 20 armies total (dominant player - has >40% of total)
                TestTerritory(id = 3, owner = 2, armyCount = 4, adjacentTo = listOf(1)),
                TestTerritory(id = 4, owner = 2, armyCount = 8, adjacentTo = listOf()),
                TestTerritory(id = 5, owner = 2, armyCount = 8, adjacentTo = listOf())
            ),
            seed = 12345L
        )

        // Update player count to 3 for this test
        val updatedMap = map.copy(playerCount = 3)

        val bot = DefaultBot(updatedMap.gameRandom)
        val decision = bot.decide(updatedMap, playerId = 0)

        // Bot should attack the dominant player (territory 3) even though territory 2 is weaker
        // because of the dominant player filtering rule
        assertIs<BotDecision.Attack>(decision)
        assertEquals(1, decision.fromTerritoryId)
        assertEquals(3, decision.toTerritoryId)
    }

    @Test
    fun `bot attacks with equal armies when top-ranked player`() {
        // Setup: Player 0 is top-ranked, has equal armies to enemy
        val map = createTestMap(
            territories = listOf(
                // Player 0: 10 armies total (top player)
                TestTerritory(id = 1, owner = 0, armyCount = 4, adjacentTo = listOf(3)),
                TestTerritory(id = 2, owner = 0, armyCount = 6, adjacentTo = listOf()),

                // Player 1: 4 armies total
                TestTerritory(id = 3, owner = 1, armyCount = 4, adjacentTo = listOf(1))
            )
        )

        val bot = DefaultBot(map.gameRandom)
        val decision = bot.decide(map, playerId = 0)

        // Should attack because we're top-ranked even though armies are equal
        assertIs<BotDecision.Attack>(decision)
        assertEquals(1, decision.fromTerritoryId)
        assertEquals(3, decision.toTerritoryId)
    }

    @Test
    fun `bot attacks top-ranked player even with equal armies`() {
        // Setup: Player 1 is top-ranked, player 0 has equal armies on adjacent territory
        val map = createTestMap(
            territories = listOf(
                // Player 0: 4 armies total (weaker)
                TestTerritory(id = 1, owner = 0, armyCount = 4, adjacentTo = listOf(2)),

                // Player 1: 10 armies total (top-ranked)
                TestTerritory(id = 2, owner = 1, armyCount = 4, adjacentTo = listOf(1)),
                TestTerritory(id = 3, owner = 1, armyCount = 6, adjacentTo = listOf())
            )
        )

        val bot = DefaultBot(map.gameRandom)
        val decision = bot.decide(map, playerId = 0)

        // Should attack because opponent is top-ranked even though armies are equal
        assertIs<BotDecision.Attack>(decision)
        assertEquals(1, decision.fromTerritoryId)
        assertEquals(2, decision.toTerritoryId)
    }

    @Test
    fun `bot selects randomly from multiple valid attacks`() {
        // Setup: Player 0 has multiple valid attack options
        val map = createTestMap(
            territories = listOf(
                TestTerritory(id = 1, owner = 0, armyCount = 5, adjacentTo = listOf(2, 3)),
                TestTerritory(id = 2, owner = 1, armyCount = 2, adjacentTo = listOf(1)),
                TestTerritory(id = 3, owner = 1, armyCount = 3, adjacentTo = listOf(1))
            )
        )

        val bot = DefaultBot(map.gameRandom)
        val decision = bot.decide(map, playerId = 0)

        // Should attack one of the enemy territories
        assertIs<BotDecision.Attack>(decision)
        assertEquals(1, decision.fromTerritoryId)
        assertTrue(decision.toTerritoryId == 2 || decision.toTerritoryId == 3)
    }

    @Test
    fun `bot decision is deterministic with same seed`() {
        // Setup: Create two identical maps with same seed
        val territories = listOf(
            TestTerritory(id = 1, owner = 0, armyCount = 5, adjacentTo = listOf(2, 3)),
            TestTerritory(id = 2, owner = 1, armyCount = 2, adjacentTo = listOf(1)),
            TestTerritory(id = 3, owner = 1, armyCount = 3, adjacentTo = listOf(1))
        )

        val map1 = createTestMap(territories, seed = 999L)
        val map2 = createTestMap(territories, seed = 999L)

        val bot1 = DefaultBot(map1.gameRandom)
        val bot2 = DefaultBot(map2.gameRandom)

        val decision1 = bot1.decide(map1, playerId = 0)
        val decision2 = bot2.decide(map2, playerId = 0)

        // Decisions should be identical with same seed
        assertIs<BotDecision.Attack>(decision1)
        assertIs<BotDecision.Attack>(decision2)
        assertEquals(decision1.fromTerritoryId, decision2.fromTerritoryId)
        assertEquals(decision1.toTerritoryId, decision2.toTerritoryId)
    }

    @Test
    fun `bot handles player with no territories gracefully`() {
        // Setup: Player 0 has no territories
        val map = createTestMap(
            territories = listOf(
                TestTerritory(id = 1, owner = 1, armyCount = 5, adjacentTo = listOf())
            )
        )

        val bot = DefaultBot(map.gameRandom)
        val decision = bot.decide(map, playerId = 0)

        // Should skip because player has no territories
        assertIs<BotDecision.Skip>(decision)
    }

    @Test
    fun `bot correctly identifies dominant player threshold`() {
        // Setup: Test the >40% threshold (exactly 40% should NOT be dominant)
        // Total armies: 25 (player 0: 10, player 1: 10, player 2: 5)
        // 40% of 25 = 10, so >10 armies needed to be dominant
        val map = createTestMap(
            territories = listOf(
                TestTerritory(id = 1, owner = 0, armyCount = 10, adjacentTo = listOf(2)),
                TestTerritory(id = 2, owner = 1, armyCount = 10, adjacentTo = listOf(1, 3)),
                TestTerritory(id = 3, owner = 2, armyCount = 5, adjacentTo = listOf(2))
            )
        )

        // Update player count to 3
        val updatedMap = map.copy(playerCount = 3)

        val bot = DefaultBot(updatedMap.gameRandom)

        // Player 0 should be able to attack player 1 even though neither is dominant
        val decision = bot.decide(updatedMap, playerId = 0)
        assertIs<BotDecision.Attack>(decision)
    }

    @Test
    fun `bot only attacks from territories with more than 1 army`() {
        // Setup: Player has mix of territories with 1 and >1 armies
        val map = createTestMap(
            territories = listOf(
                TestTerritory(id = 1, owner = 0, armyCount = 1, adjacentTo = listOf(3)),  // Can't attack
                TestTerritory(id = 2, owner = 0, armyCount = 5, adjacentTo = listOf(4)),  // Can attack
                TestTerritory(id = 3, owner = 1, armyCount = 1, adjacentTo = listOf(1)),
                TestTerritory(id = 4, owner = 1, armyCount = 2, adjacentTo = listOf(2))
            )
        )

        val bot = DefaultBot(map.gameRandom)
        val decision = bot.decide(map, playerId = 0)

        // Should attack from territory 2 only (territory 1 has only 1 army)
        assertIs<BotDecision.Attack>(decision)
        assertEquals(2, decision.fromTerritoryId)
        assertEquals(4, decision.toTerritoryId)
    }

    @Test
    fun `bot handles complex multi-player scenario`() {
        // Setup: 3 players with complex territory arrangement
        val map = createTestMap(
            territories = listOf(
                // Player 0
                TestTerritory(id = 1, owner = 0, armyCount = 4, adjacentTo = listOf(2, 3)),

                // Player 1
                TestTerritory(id = 2, owner = 1, armyCount = 2, adjacentTo = listOf(1, 4)),

                // Player 2
                TestTerritory(id = 3, owner = 2, armyCount = 3, adjacentTo = listOf(1)),
                TestTerritory(id = 4, owner = 2, armyCount = 5, adjacentTo = listOf(2))
            )
        )

        // Update player count to 3
        val updatedMap = map.copy(playerCount = 3)

        val bot = DefaultBot(updatedMap.gameRandom)
        val decision = bot.decide(updatedMap, playerId = 0)

        // Should attack one of the adjacent weaker territories
        assertIs<BotDecision.Attack>(decision)
        assertEquals(1, decision.fromTerritoryId)
        assertTrue(decision.toTerritoryId == 2 || decision.toTerritoryId == 3)
    }
}
