package com.franklinharper.battlezone

/**
 * Core game logic functions for BattleZone
 */
object GameLogic {
    /**
     * Resolve an attack between two territories and apply the result.
     */
    fun resolveAttack(
        fromTerritory: Territory,
        toTerritory: Territory,
        gameRandom: GameRandom
    ): CombatResult {
        val attackerRoll = gameRandom.rollDice(fromTerritory.armyCount)
        val defenderRoll = gameRandom.rollDice(toTerritory.armyCount)

        val attackerTotal = attackerRoll.sum()
        val defenderTotal = defenderRoll.sum()
        val attackerWins = attackerTotal > defenderTotal

        val combatResult = CombatResult(
            attackerPlayerId = fromTerritory.owner,
            defenderPlayerId = toTerritory.owner,
            attackerRoll = attackerRoll,
            defenderRoll = defenderRoll,
            attackerTotal = attackerTotal,
            defenderTotal = defenderTotal,
            attackerWins = attackerWins
        )

        if (attackerWins) {
            val armiesTransferred = fromTerritory.armyCount - 1
            toTerritory.owner = fromTerritory.owner
            toTerritory.armyCount = armiesTransferred
            fromTerritory.armyCount = 1
        } else {
            fromTerritory.armyCount = 1
        }

        return combatResult
    }


    /**
     * Calculate the size of the largest connected component of territories for a player
     * Uses Depth-First Search (DFS) to find connected territories
     *
     * @param map The game map containing all territories
     * @param playerId The player ID (0 or 1)
     * @return Size of largest connected group of territories
     */
    fun calculateLargestConnected(map: GameMap, playerId: Int): Int {
        require(playerId >= 0 && playerId < map.playerCount) {
            "Invalid player ID: $playerId (must be 0 to ${map.playerCount - 1})"
        }

        val playerTerritories = map.territories
            .filter { it.owner == playerId }
            .map { it.id }
            .toSet()

        if (playerTerritories.isEmpty()) {
            return 0
        }

        val visited = mutableSetOf<Int>()
        var maxComponentSize = 0

        // Try starting DFS from each unvisited territory
        for (territoryId in playerTerritories) {
            if (territoryId !in visited) {
                val componentSize = dfsCountComponent(
                    map,
                    territoryId,
                    playerId,
                    playerTerritories,
                    visited
                )
                maxComponentSize = maxOf(maxComponentSize, componentSize)
            }
        }

        return maxComponentSize
    }

    /**
     * Perform DFS to count the size of a connected component
     */
    private fun dfsCountComponent(
        map: GameMap,
        territoryId: Int,
        playerId: Int,
        playerTerritories: Set<Int>,
        visited: MutableSet<Int>
    ): Int {
        require(territoryId >= 0 && territoryId < map.territories.size) {
            "Invalid territory ID: $territoryId (must be 0 to ${map.territories.size - 1})"
        }

        if (territoryId in visited) {
            return 0
        }

        visited.add(territoryId)
        var count = 1

        // Get the territory object
        val territory = map.territories.getOrNull(territoryId)
        if (territory == null || territory.owner != playerId) {
            return count
        }

        // Visit all adjacent territories owned by the same player
        for (adjacentId in territory.adjacentTerritories.indices) {
            if (territory.adjacentTerritories[adjacentId] &&
                adjacentId in playerTerritories &&
                adjacentId !in visited) {
                count += dfsCountComponent(
                    map,
                    adjacentId,
                    playerId,
                    playerTerritories,
                    visited
                )
            }
        }

        return count
    }

    /**
     * Calculate reinforcement count for a player
     * Based on the size of their largest connected component
     */
    fun calculateReinforcements(map: GameMap, playerId: Int): Int {
        require(playerId >= 0 && playerId < map.playerCount) {
            "Invalid player ID: $playerId (must be 0 to ${map.playerCount - 1})"
        }
        return calculateLargestConnected(map, playerId)
    }

    /**
     * Distribute reinforcement armies to a player's territories
     * Follows the design spec algorithm:
     * 1. Add reserve armies to new reinforcements
     * 2. Randomly distribute to territories below the max army cap
     * 3. Excess goes to reserve pool
     *
     * @param map The game map
     * @param playerId The player receiving reinforcements
     * @param reinforcements Number of new reinforcement armies
     * @param currentReserve Current reserve army count
     * @return Updated reserve army count
     */
    fun distributeReinforcements(
        map: GameMap,
        playerId: Int,
        reinforcements: Int,
        currentReserve: Int
    ): Int {
        require(playerId >= 0 && playerId < map.playerCount) {
            "Invalid player ID: $playerId (must be 0 to ${map.playerCount - 1})"
        }
        require(reinforcements >= 0) { "Reinforcements cannot be negative: $reinforcements" }
        require(currentReserve >= 0) { "Current reserve cannot be negative: $currentReserve" }

        val totalToDistribute = reinforcements + currentReserve
        var remainingReserve = 0

        repeat(totalToDistribute) {
            // Get territories below the max army cap
            val eligibleTerritories = map.territories.filter {
                it.owner == playerId && it.armyCount < GameRules.MAX_ARMIES_PER_TERRITORY
            }

            if (eligibleTerritories.isEmpty()) {
                // All territories at max, add to reserve
                remainingReserve++
            } else {
                // Randomly select a territory and add 1 army
                val selectedTerritory = eligibleTerritories[
                    map.gameRandom.nextInt(eligibleTerritories.size)
                ]
                selectedTerritory.armyCount++
            }
        }

        return remainingReserve
    }

    /**
     * Update player state with current territory and army counts
     */
    fun updatePlayerState(map: GameMap, playerState: PlayerState, playerId: Int) {
        require(playerId >= 0 && playerId < map.playerCount) {
            "Invalid player ID: $playerId (must be 0 to ${map.playerCount - 1})"
        }

        val playerTerritories = map.territories.filter { it.owner == playerId }

        playerState.territoryCount = playerTerritories.size
        playerState.totalArmies = playerTerritories.sumOf { it.armyCount }
        playerState.largestConnectedSize = calculateLargestConnected(map, playerId)
    }
}
