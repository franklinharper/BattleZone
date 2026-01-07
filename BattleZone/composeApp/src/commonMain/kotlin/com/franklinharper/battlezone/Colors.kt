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
     * Debug outline color for individual hex cells
     */
    val DebugCellOutline = Color(0xFFCCCCCC)

    /**
     * UI colors
     */
    val ScreenBackground = Color(0xFFFFFFFF)
    val UiTextPrimary = Color(0xFF000000)
    val UiTextMuted = Color(0xFF808080)
    val UiTextInverted = Color(0xFFFFFFFF)
    val UiTextError = Color(0xFFD32F2F)
    val PanelEliminatedBackground = Color(0x4DD3D3D3)
    val PlayerLabelBorder = Color(0x66000000)
    val OverlayScrimStrong = Color(0xB3000000)
    val OverlayScrim = Color(0x80000000)
    val GameOverWin = Color(0xFF4CAF50)
    val GameOverLoss = Color(0xFFFF0000)

    /**
     * Highlight colors
     */
    val HighlightAttack = Color(0xFFFF0000)
    val HighlightDefend = Color(0xFFFFFF00)

    /**
     * Bot attack arrow colors
     */
    val BotArrowSuccess = Color(0xFFFF0000)
    val BotArrowFailure = Color(0xFF0000FF)
    val BotArrowOutline = Color(0xFFFFFFFF)

    /**
     * Fallback color for unknown player ID
     */
    val UnknownPlayer = Color(0xFF808080)

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
        else -> UnknownPlayer
    }
}
