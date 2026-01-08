package com.franklinharper.battlezone

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object UiConstants {
    const val ORIGINAL_CELL_WIDTH = 27f
    const val ORIGINAL_CELL_HEIGHT = 18f
    const val MIN_CELL_WIDTH = 10f
    const val MIN_CELL_HEIGHT = 7f
    const val ORIGINAL_FONT_SIZE = 12f
    const val MIN_FONT_SIZE = 8f
    const val MAX_FONT_SIZE = 16f
    const val MIN_BOTTOM_ROW_FONT_SIZE = 10f
    const val MAX_BOTTOM_ROW_FONT_SIZE = 28f
    const val PLAYER_LABEL_FONT_DIVISOR = 6f
    const val LABEL_HORIZONTAL_PADDING_SCALE = 0.6f
    const val LABEL_VERTICAL_PADDING_SCALE = 0.3f
    const val PLAYER_LABEL_BORDER_ALPHA = 0.4f
    const val HEX_EDGE_COUNT = 6
    const val PLAYBACK_STEP_DELAY_MS = 800L
    const val PLAYBACK_SPEED_HALF = 0.5f
    const val PLAYBACK_SPEED_NORMAL = 1.0f
    const val PLAYBACK_SPEED_DOUBLE = 2.0f
    const val PLAYBACK_ELIMINATION_POPUP_DURATION_MS = 2000L

    val PLAYER_LABEL_CORNER_RADIUS = 6.dp
    val PLAYER_LABEL_SHAPE = RoundedCornerShape(PLAYER_LABEL_CORNER_RADIUS)
    val PLAYER_LABEL_BORDER_WIDTH = 1.dp
    val PLAYER_LABEL_BORDER_COLOR = GameColors.PlayerLabelBorder
    val PLAYER_LABEL_CONTENT_SPACING = 8.dp
    val ACTION_BUTTON_RESERVE_WIDTH = 190.dp
    val SEED_FIELD_WIDTH = 240.dp
}
