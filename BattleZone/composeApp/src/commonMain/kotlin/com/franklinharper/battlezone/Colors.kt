package com.franklinharper.battlezone

import androidx.compose.ui.graphics.Color

/**
 * Color constants for the BattleZone game
 */
object GameColors {
    /**
     * Player 0 territory color - Purple
     */
    val Player0 = Color(0xFFB37FFE)

    /**
     * Player 1 territory color - Dark Green
     * Darker than the original to provide better contrast with white text
     */
    val Player1 = Color(0xFF4CAF50)

    /**
     * Territory border color - Dark blue-gray
     */
    val TerritoryBorder = Color(0xFF222244)

    /**
     * Text color for army counts and territory info
     */
    val TerritoryText = Color.White
}
