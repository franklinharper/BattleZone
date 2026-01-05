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
     * Player 2 territory color - Red
     */
    val Player2 = Color(0xFFF44336)

    /**
     * Player 3 territory color - Gold/Yellow
     */
    val Player3 = Color(0xFFFFEB3B)

    /**
     * Player 4 territory color - Orange
     */
    val Player4 = Color(0xFFFF9800)

    /**
     * Player 5 territory color - Blue
     */
    val Player5 = Color(0xFF2196F3)

    /**
     * Player 6 territory color - Cyan
     */
    val Player6 = Color(0xFF00BCD4)

    /**
     * Player 7 territory color - Pink
     */
    val Player7 = Color(0xFFE91E63)

    /**
     * Territory border color - Dark blue-gray
     */
    val TerritoryBorder = Color(0xFF222244)

    /**
     * Text color for army counts and territory info
     */
    val TerritoryText = Color.Black

    /**
     * Get the color for a specific player ID
     */
    fun getPlayerColor(playerId: Int): Color = when (playerId) {
        0 -> Player0
        1 -> Player1
        2 -> Player2
        3 -> Player3
        4 -> Player4
        5 -> Player5
        6 -> Player6
        7 -> Player7
        else -> Color.Gray
    }
}
