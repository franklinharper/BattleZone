package com.franklinharper.battlezone

object RecordingReplayer {
    fun buildSnapshots(recording: RecordedGame): List<GameSnapshot> {
        val initialSnapshot = recording.initialSnapshot?.toGameSnapshot()?.deepCopy()
            ?: return emptyList()

        val snapshots = mutableListOf(initialSnapshot)
        var currentState = initialSnapshot.gameState
        var currentUi = initialSnapshot.uiState

        for (event in recording.events) {
            val next = applyRecordedEvent(
                gameState = currentState,
                uiState = currentUi,
                event = event,
                gameMode = recording.gameMode,
                humanPlayerId = recording.humanPlayerId
            )
            snapshots.add(next)
            currentState = next.gameState
            currentUi = next.uiState
        }

        return snapshots
    }

    private fun applyRecordedEvent(
        gameState: GameState,
        uiState: GameUiState,
        event: RecordedEvent,
        gameMode: GameMode,
        humanPlayerId: Int
    ): GameSnapshot {
        return when (event) {
            is RecordedEvent.Attack -> applyAttack(gameState, uiState, event, gameMode, humanPlayerId)
            is RecordedEvent.Skip -> applySkip(gameState, uiState, event, gameMode, humanPlayerId)
            is RecordedEvent.Reinforcement -> applyReinforcement(gameState, uiState, event, gameMode)
        }
    }

    private fun applyAttack(
        gameState: GameState,
        uiState: GameUiState,
        event: RecordedEvent.Attack,
        gameMode: GameMode,
        humanPlayerId: Int
    ): GameSnapshot {
        val updatedState = copyGameStateForUpdate(gameState)
        val updatedUi = uiState.deepCopy()

        val map = updatedState.map
        val fromTerritory = map.territories.getOrNull(event.fromTerritoryId) ?: return GameSnapshot(updatedState, updatedUi)
        val toTerritory = map.territories.getOrNull(event.toTerritoryId) ?: return GameSnapshot(updatedState, updatedUi)
        val combatResult = event.result.toCombatResult()

        GameLogic.applyAttackResult(fromTerritory, toTerritory, combatResult)

        val attackerPlayerId = combatResult.attackerPlayerId
        val defenderPlayerId = combatResult.defenderPlayerId
        val isBotAttack = isBotPlayer(gameMode, humanPlayerId, updatedState.currentPlayerIndex)

        val updatedCombatResults = updatedUi.playerCombatResults + (attackerPlayerId to combatResult)
        val updatedSkippedPlayers = updatedUi.skippedPlayers - attackerPlayerId
        val botArrows = if (isBotAttack) {
            updatedUi.botAttackArrows + BotAttackArrow(
                fromTerritoryId = event.fromTerritoryId,
                toTerritoryId = event.toTerritoryId,
                attackSucceeded = combatResult.attackerWins
            )
        } else {
            emptyList()
        }

        val message = if (combatResult.attackerWins) {
            "${playerLabel(updatedState.currentPlayerIndex, gameMode)} wins! " +
                "Attacker: ${combatResult.attackerRoll.joinToString("+")} = ${combatResult.attackerTotal} | " +
                "Defender: ${combatResult.defenderRoll.joinToString("+")} = ${combatResult.defenderTotal}"
        } else {
            "${playerLabel(toTerritory.owner, gameMode)} defends! " +
                "Attacker: ${combatResult.attackerRoll.joinToString("+")} = ${combatResult.attackerTotal} | " +
                "Defender: ${combatResult.defenderRoll.joinToString("+")} = ${combatResult.defenderTotal}"
        }

        val combatUiState = updatedUi.copy(
            playerCombatResults = updatedCombatResults,
            skippedPlayers = updatedSkippedPlayers,
            message = message,
            botAttackArrows = botArrows
        )

        for (playerId in 0 until map.playerCount) {
            GameLogic.updatePlayerState(map, updatedState.players[playerId], playerId)
        }

        val updatedPlayers = Array(map.playerCount) { playerId ->
            updatedState.players[playerId].copy()
        }

        var eliminatedPlayers = updatedState.eliminatedPlayers
        if (updatedPlayers[defenderPlayerId].territoryCount == 0) {
            eliminatedPlayers = eliminatedPlayers + defenderPlayerId
        }

        if (gameMode == GameMode.HUMAN_VS_BOT && humanPlayerId in eliminatedPlayers) {
            val finalState = updatedState.copy(
                gamePhase = GamePhase.GAME_OVER,
                winner = null,
                players = updatedPlayers,
                eliminatedPlayers = eliminatedPlayers
            )
            val finalUi = combatUiState.copy(
                message = "💀 ${playerLabel(humanPlayerId, gameMode)} eliminated! Game Over."
            )
            return GameSnapshot(finalState, finalUi)
        }

        val remainingPlayers = (0 until map.playerCount).filter { it !in eliminatedPlayers }
        if (remainingPlayers.size == 1) {
            val winner = remainingPlayers[0]
            val finalState = updatedState.copy(
                winner = winner,
                gamePhase = GamePhase.GAME_OVER,
                players = updatedPlayers,
                eliminatedPlayers = eliminatedPlayers
            )
            val finalUi = combatUiState.copy(
                message = "🎉 ${playerLabel(winner, gameMode)} wins the game! 🎉"
            )
            return GameSnapshot(finalState, finalUi)
        }

        val nextPlayerIndex = nextPlayerIndex(updatedState, eliminatedPlayers)
        val nextState = updatedState.copy(
            skipTracker = emptySet(),
            eliminatedPlayers = eliminatedPlayers,
            players = updatedPlayers,
            currentPlayerIndex = nextPlayerIndex
        )

        return GameSnapshot(nextState, combatUiState)
    }

    private fun applySkip(
        gameState: GameState,
        uiState: GameUiState,
        event: RecordedEvent.Skip,
        gameMode: GameMode,
        humanPlayerId: Int
    ): GameSnapshot {
        val updatedState = copyGameStateForUpdate(gameState)
        val updatedUi = uiState.deepCopy()
        val currentPlayer = event.playerId
        val normalizedState = if (updatedState.currentPlayerIndex != currentPlayer) {
            updatedState.copy(currentPlayerIndex = currentPlayer)
        } else {
            updatedState
        }
        val updatedSkipTracker = normalizedState.skipTracker + currentPlayer
        val activePlayerCount = normalizedState.map.playerCount - normalizedState.eliminatedPlayers.size
        val skipCount = (updatedSkipTracker - normalizedState.eliminatedPlayers).size
        val isHumanTurn = !isBotPlayer(gameMode, humanPlayerId, currentPlayer)

        val skipUiState = updatedUi.copy(
            message = "${playerLabel(currentPlayer, gameMode)} skipped. ($skipCount/$activePlayerCount players skipped)",
            skippedPlayers = updatedUi.skippedPlayers + currentPlayer,
            botAttackArrows = if (isHumanTurn) emptyList() else updatedUi.botAttackArrows
        )

        val skipGameState = normalizedState.copy(skipTracker = updatedSkipTracker)

        return if (skipCount >= activePlayerCount) {
            val reinforcementState = skipGameState.copy(gamePhase = GamePhase.REINFORCEMENT)
            val reinforcementUi = skipUiState.copy(
                message = "Reinforcement Phase: All players skipped. Distributing reinforcements...",
                botAttackArrows = emptyList(),
                skippedPlayers = emptySet()
            )
            GameSnapshot(reinforcementState, reinforcementUi)
        } else {
            val nextIndex = nextPlayerIndex(skipGameState, skipGameState.eliminatedPlayers)
            GameSnapshot(skipGameState.copy(currentPlayerIndex = nextIndex), skipUiState)
        }
    }

    private fun applyReinforcement(
        gameState: GameState,
        uiState: GameUiState,
        event: RecordedEvent.Reinforcement,
        gameMode: GameMode
    ): GameSnapshot {
        val updatedState = copyGameStateForUpdate(gameState)
        val updatedUi = uiState.deepCopy()
        val map = updatedState.map
        val messages = mutableListOf<String>()

        for (result in event.players) {
            if (result.playerId in updatedState.eliminatedPlayers) continue

            for (territoryId in result.territoryIncrements) {
                val territory = map.territories.getOrNull(territoryId)
                if (territory != null) {
                    territory.armyCount++
                }
            }

            val playerState = updatedState.players[result.playerId]
            playerState.reserveArmies = result.reserveArmies
            GameLogic.updatePlayerState(map, playerState, result.playerId)

            val reinforcements = GameLogic.calculateReinforcements(map, result.playerId)
            messages.add("${playerLabel(result.playerId, gameMode)}: +$reinforcements armies" +
                if (result.reserveArmies > 0) " (Reserve: ${result.reserveArmies})" else "")
        }

        val updatedPlayers = Array(map.playerCount) { playerId ->
            updatedState.players[playerId].copy()
        }

        val finalState = updatedState.copy(
            gamePhase = GamePhase.ATTACK,
            skipTracker = emptySet(),
            players = updatedPlayers
        )

        val finalUi = updatedUi.copy(
            message = "Reinforcements: ${messages.joinToString(" | ")}"
        )

        return GameSnapshot(finalState, finalUi)
    }

    private fun copyGameStateForUpdate(gameState: GameState): GameState {
        val mapCopy = gameState.map.deepCopy()
        val playersCopy = Array(mapCopy.playerCount) { index ->
            gameState.players[index].copy()
        }
        return gameState.copy(map = mapCopy, players = playersCopy)
    }

    private fun isBotPlayer(gameMode: GameMode, humanPlayerId: Int, playerId: Int): Boolean {
        return gameMode == GameMode.BOT_VS_BOT || playerId != humanPlayerId
    }

    private fun nextPlayerIndex(gameState: GameState, eliminatedPlayers: Set<Int>): Int {
        var nextPlayerIndex = (gameState.currentPlayerIndex + 1) % gameState.map.playerCount
        while (nextPlayerIndex in eliminatedPlayers) {
            nextPlayerIndex = (nextPlayerIndex + 1) % gameState.map.playerCount
        }
        return nextPlayerIndex
    }
}
