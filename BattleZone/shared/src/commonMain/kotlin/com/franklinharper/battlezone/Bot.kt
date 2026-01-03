package com.franklinharper.battlezone

/**
 * Represents a bot's decision for their turn
 */
sealed class BotDecision {
    /**
     * Bot decides to attack
     */
    data class Attack(
        val fromTerritoryId: Int,
        val toTerritoryId: Int
    ) : BotDecision()

    /**
     * Bot decides to skip their turn
     */
    object Skip : BotDecision()
}

/**
 * Interface for bot AI strategies
 */
interface Bot {
    /**
     * Decide the next action for the given player
     *
     * @param map The current game map
     * @param playerId The player ID making the decision
     * @return The bot's decision (Attack or Skip)
     */
    fun decide(map: GameMap, playerId: Int): BotDecision
}

/**
 * Player statistics for AI decision-making
 */
data class PlayerStats(
    val playerId: Int,
    var territoryCount: Int = 0,
    var totalArmies: Int = 0,
    var diceRanking: Int = 0  // 0 = top player, 1 = second, etc.
)

/**
 * Default AI implementation based on the original Dice Wars AI
 * Reference: dicewarsjs/ai_default.js
 */
class DefaultBot(private val random: GameRandom) : Bot {

    override fun decide(map: GameMap, playerId: Int): BotDecision {
        // Step 1: Analyze game state
        val playerStats = analyzeGameState(map)
        val currentPlayerStats = playerStats.find { it.playerId == playerId }
            ?: return BotDecision.Skip

        // Find dominant player (>40% of total armies)
        val totalArmies = playerStats.sumOf { it.totalArmies }
        val dominantPlayer = playerStats.find {
            it.totalArmies > totalArmies * 2 / 5
        }

        // Step 2: Generate attack options
        val attackOptions = generateAttackOptions(
            map,
            playerId,
            currentPlayerStats,
            dominantPlayer,
            playerStats
        )

        // Step 3: Select attack
        if (attackOptions.isEmpty()) {
            return BotDecision.Skip
        }

        val selectedIndex = random.nextInt(attackOptions.size)
        val selectedAttack = attackOptions[selectedIndex]

        return BotDecision.Attack(
            fromTerritoryId = selectedAttack.first,
            toTerritoryId = selectedAttack.second
        )
    }

    /**
     * Analyze the current game state and calculate statistics for each player
     */
    private fun analyzeGameState(map: GameMap): List<PlayerStats> {
        // Initialize stats for all players
        val stats = (0 until map.playerCount).map { PlayerStats(playerId = it) }

        // Count territories and armies for each player
        for (territory in map.territories) {
            if (territory.size == 0 || territory.owner < 0) continue

            val playerStats = stats[territory.owner]
            playerStats.territoryCount++
            playerStats.totalArmies += territory.armyCount
        }

        // Calculate dice rankings (sort by total armies descending)
        val sortedStats = stats.sortedByDescending { it.totalArmies }
        sortedStats.forEachIndexed { index, playerStats ->
            playerStats.diceRanking = index
        }

        return stats
    }

    /**
     * Generate list of valid attack options as (fromTerritoryId, toTerritoryId) pairs
     */
    private fun generateAttackOptions(
        map: GameMap,
        playerId: Int,
        currentPlayerStats: PlayerStats,
        dominantPlayer: PlayerStats?,
        allPlayerStats: List<PlayerStats>
    ): List<Pair<Int, Int>> {
        val attackOptions = mutableListOf<Pair<Int, Int>>()

        // Iterate through all territories owned by current player
        for (fromTerritory in map.territories) {
            if (fromTerritory.size == 0) continue
            if (fromTerritory.owner != playerId) continue
            if (fromTerritory.armyCount <= 1) continue  // Can't attack with 1 army

            // Check each adjacent territory
            for (toTerritoryId in fromTerritory.adjacentTerritories.indices) {
                if (!fromTerritory.adjacentTerritories[toTerritoryId]) continue

                val toTerritory = map.territories.getOrNull(toTerritoryId) ?: continue
                if (toTerritory.size == 0) continue
                if (toTerritory.owner == playerId) continue  // Don't attack own territory

                // Filter rule 1: Don't attack if enemy has more armies
                if (toTerritory.armyCount > fromTerritory.armyCount) continue

                // Filter rule 2: If dominant player exists, prioritize attacking/defending against them
                if (dominantPlayer != null) {
                    val fromPlayerIsNotDominant = fromTerritory.owner != dominantPlayer.playerId
                    val toPlayerIsNotDominant = toTerritory.owner != dominantPlayer.playerId

                    // Skip if both attacker and defender are non-dominant players
                    if (fromPlayerIsNotDominant && toPlayerIsNotDominant) continue
                }

                // Filter rule 3: If enemy has equal armies, decide strategically
                if (toTerritory.armyCount == fromTerritory.armyCount) {
                    val enemyStats = allPlayerStats.find { it.playerId == toTerritory.owner }
                        ?: continue

                    var shouldAttack = false

                    // Attack if we're the top-ranked player
                    if (currentPlayerStats.diceRanking == 0) {
                        shouldAttack = true
                    }

                    // Attack if opponent is the top-ranked player
                    if (enemyStats.diceRanking == 0) {
                        shouldAttack = true
                    }

                    // Otherwise attack with 90% probability (Math.random()*10 > 1)
                    if (random.nextInt(10) > 0) {  // 90% chance (9 out of 10)
                        shouldAttack = true
                    }

                    if (!shouldAttack) continue
                }

                // Add this attack to valid options
                attackOptions.add(Pair(fromTerritory.id, toTerritoryId))
            }
        }

        return attackOptions
    }
}
