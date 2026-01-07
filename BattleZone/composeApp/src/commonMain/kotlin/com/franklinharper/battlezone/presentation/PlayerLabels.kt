package com.franklinharper.battlezone.presentation

import com.franklinharper.battlezone.GameMode

fun playerLabel(playerId: Int, gameMode: GameMode): String =
    when (gameMode) {
        GameMode.HUMAN_VS_BOT -> if (playerId == 0) "Human" else "Bot$playerId"
        GameMode.BOT_VS_BOT -> "Bot${playerId + 1}"
    }
