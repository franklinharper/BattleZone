package com.franklinharper.battlezone

import kotlin.test.*

class GameUpdaterTest {

    private fun createInitialState(): GameState {
        val map = MapGenerator.generate(seed = 42)
        return GameState(
            map = map,
            players = Array(2) { PlayerState(0, 0, 0, 0) },
            currentPlayerIndex = 0,
            gamePhase = GamePhase.ATTACK,
            consecutiveSkips = 0,
            winner = null
        )
    }

    @Test
    fun `successful attack transfers territory`() {
        val initialState = createInitialState()
        val updater = GameUpdater()

        // Ensure territory 0 has enough armies and an adjacent enemy
        initialState.map.territories[0].owner = 0
        initialState.map.territories[0].armyCount = 5
        initialState.map.territories[1].owner = 1
        initialState.map.territories[0].adjacentTerritories[1] = true
        initialState.map.territories[1].adjacentTerritories[0] = true

        val result = updater.executeAttack(initialState, 0, 1)

        val newTerritory0 = result.newState.map.territories[0]
        val newTerritory1 = result.newState.map.territories[1]

        // With a fixed seed, the roll should be deterministic
        assertTrue(result.combatResult.attackerWins)
        assertEquals(1, newTerritory0.armyCount)
        assertEquals(4, newTerritory1.armyCount)
        assertEquals(0, newTerritory1.owner)
    }

    @Test
    fun `failed attack reduces attacker's armies`() {
        val initialState = createInitialState()
        val updater = GameUpdater()

        // Force a losing scenario
        initialState.map.territories[0].owner = 0
        initialState.map.territories[0].armyCount = 2 // Low army count
        initialState.map.territories[1].owner = 1
        initialState.map.territories[1].armyCount = 8 // High army count
        initialState.map.territories[0].adjacentTerritories[1] = true
        initialState.map.territories[1].adjacentTerritories[0] = true

        val result = updater.executeAttack(initialState, 0, 1)

        val newTerritory0 = result.newState.map.territories[0]
        val newTerritory1 = result.newState.map.territories[1]

        assertFalse(result.combatResult.attackerWins)
        assertEquals(1, newTerritory0.armyCount)
        assertEquals(8, newTerritory1.armyCount) // Unchanged
        assertEquals(1, newTerritory1.owner) // Unchanged
    }

    @Test
    fun `skip turn advances player and increases skip count`() {
        val initialState = createInitialState()
        val updater = GameUpdater()

        val newState = updater.skipTurn(initialState)

        assertEquals(1, newState.currentPlayerIndex)
        assertEquals(1, newState.consecutiveSkips)
        assertEquals(GamePhase.ATTACK, newState.gamePhase)
    }

    @Test
    fun `two consecutive skips trigger reinforcement phase`() {
        val initialState = createInitialState().copy(
            currentPlayerIndex = 1,
            consecutiveSkips = 1
        )
        val updater = GameUpdater()

        val newState = updater.skipTurn(initialState)

        assertEquals(0, newState.currentPlayerIndex)
        assertEquals(2, newState.consecutiveSkips)
        assertEquals(GamePhase.REINFORCEMENT, newState.gamePhase)
    }

    @Test
    fun `reinforcement phase distributes armies`() {
        val initialState = createInitialState().copy(gamePhase = GamePhase.REINFORCEMENT)
        val updater = GameUpdater()

        val initialArmies = initialState.players.sumOf { it.totalArmies }
        val newState = updater.executeReinforcementPhase(initialState)

        assertTrue(newState.players.sumOf { it.totalArmies } > initialArmies)
        assertEquals(GamePhase.ATTACK, newState.gamePhase)
        assertEquals(0, newState.consecutiveSkips)
    }

    @Test
    fun `attacking own territory throws exception`() {
        val initialState = createInitialState()
        val updater = GameUpdater()
        initialState.map.territories[0].owner = 0
        initialState.map.territories[1].owner = 0
        initialState.map.territories[0].adjacentTerritories[1] = true
        initialState.map.territories[1].adjacentTerritories[0] = true


        assertFailsWith<IllegalArgumentException> {
            updater.executeAttack(initialState, 0, 1)
        }
    }
}
