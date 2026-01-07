package com.franklinharper.battlezone

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.franklinharper.battlezone.presentation.screens.ORIGINAL_CELL_HEIGHT
import com.franklinharper.battlezone.presentation.screens.ORIGINAL_CELL_WIDTH

fun main() = application {
    val windowState = rememberWindowState(size = calculateInitialWindowSize())
    Window(
        onCloseRequest = ::exitApplication,
        title = "BattleZone",
        state = windowState,
    ) {
        App()
    }
}

private fun calculateInitialWindowSize(): DpSize {
    val mapWidth = (((HexGrid.GRID_WIDTH * 2 + 1) * ORIGINAL_CELL_WIDTH) / 2).dp
    val mapHeight = (HexGrid.GRID_HEIGHT * ORIGINAL_CELL_HEIGHT).dp

    val availableWidth = (mapWidth - ACTION_BUTTON_RESERVE_WIDTH).coerceAtLeast(0.dp)
    val perPlayerWidth = availableWidth / MIN_PLAYERS.toFloat()
    val baseFontSize = (perPlayerWidth.value / PLAYER_LABEL_FONT_DIVISOR).coerceIn(
        MIN_BOTTOM_ROW_FONT_SIZE,
        MAX_BOTTOM_ROW_FONT_SIZE
    )
    val labelPaddingVertical = baseFontSize * LABEL_VERTICAL_PADDING_SCALE
    val labelHeight = baseFontSize * 1.2f + labelPaddingVertical * 2
    val bottomRowContentHeight = maxOf(labelHeight, DEFAULT_BUTTON_HEIGHT.value).dp

    val contentWidth = mapWidth + WINDOW_HORIZONTAL_PADDING * 2
    val contentHeight = WINDOW_VERTICAL_PADDING * 2 +
        TITLE_BLOCK_HEIGHT +
        CONTROL_ROW_HEIGHT +
        MAP_ROW_VERTICAL_PADDING +
        mapHeight +
        BOTTOM_ROW_VERTICAL_PADDING +
        bottomRowContentHeight +
        EXTRA_WINDOW_VERTICAL_BUFFER

    return DpSize(contentWidth, contentHeight)
}

private val WINDOW_HORIZONTAL_PADDING = 24.dp
private val WINDOW_VERTICAL_PADDING = 16.dp
private val MAP_ROW_VERTICAL_PADDING = 16.dp
private val BOTTOM_ROW_VERTICAL_PADDING = 40.dp
private val TITLE_BLOCK_HEIGHT = 88.dp
private val CONTROL_ROW_HEIGHT = 72.dp
private val DEFAULT_BUTTON_HEIGHT = 40.dp
private val ACTION_BUTTON_RESERVE_WIDTH = 190.dp
private val EXTRA_WINDOW_VERTICAL_BUFFER = 16.dp
private const val MIN_BOTTOM_ROW_FONT_SIZE = 10f
private const val MAX_BOTTOM_ROW_FONT_SIZE = 28f
private const val PLAYER_LABEL_FONT_DIVISOR = 6f
private const val LABEL_VERTICAL_PADDING_SCALE = 0.3f
