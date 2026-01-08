package com.franklinharper.battlezone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RealTimeModeTest {
    @Test
    fun `real-time allows human attack out of turn`() {
        val map = createAttackableMap()
        val controller = GameController(
            initialMap = map,
            gameMode = GameMode.HUMAN_VS_BOT,
            humanPlayerId = 0,
            bots = arrayOf(DefaultBot(GameRandom(42L))),
            turnMode = TurnMode.REAL_TIME,
            roundTimerSeconds = 5
        )

        assertEquals(1, controller.getCurrentPlayer())

        val attackerId = map.territories.indexOfFirst { it.owner == 0 }
        val defenderId = map.territories.indexOfFirst { it.owner == 1 && map.territories[attackerId].adjacentTerritories[it.id] }

        controller.selectTerritory(attackerId)
        controller.selectTerritory(defenderId)

        val combatResult = controller.uiState.value.playerCombatResults[0]
        assertNotNull(combatResult)
    }

    @Test
    fun `real-time skips are rejected`() {
        val map = createAttackableMap()
        val controller = GameController(
            initialMap = map,
            gameMode = GameMode.HUMAN_VS_BOT,
            humanPlayerId = 0,
            bots = arrayOf(DefaultBot(GameRandom(42L))),
            turnMode = TurnMode.REAL_TIME,
            roundTimerSeconds = 5
        )

        val currentPlayer = controller.getCurrentPlayer()
        controller.skipTurn()

        assertEquals(currentPlayer, controller.getCurrentPlayer())
        assertEquals("Skipping is unavailable in real-time mode.", controller.uiState.value.errorMessage)
    }

    private fun createAttackableMap(): GameMap {
        val map = MapGenerator.generate(seed = 123L, playerCount = 2)
        val attackerIndex = map.territories.indexOfFirst { territory ->
            territory.adjacentTerritories.any { it }
        }
        require(attackerIndex >= 0) { "Expected at least one adjacent territory pair." }
        val defenderIndex = map.territories[attackerIndex].adjacentTerritories.indexOfFirst { it }
        require(defenderIndex >= 0) { "Expected at least one adjacent territory pair." }

        map.territories[attackerIndex].owner = 0
        map.territories[attackerIndex].armyCount = 3
        map.territories[defenderIndex].owner = 1
        map.territories[defenderIndex].armyCount = 2

        return map
    }
}
