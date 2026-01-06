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

    val bottomRowContentHeight = DEFAULT_BOTTOM_ROW_HEIGHT

    val contentWidth = mapWidth + WINDOW_HORIZONTAL_PADDING * 2
    val contentHeight = WINDOW_VERTICAL_PADDING * 2 +
        TITLE_BLOCK_HEIGHT +
        CONTROL_ROW_HEIGHT +
        MAP_ROW_VERTICAL_PADDING +
        mapHeight +
        BOTTOM_ROW_VERTICAL_PADDING +
        bottomRowContentHeight

    return DpSize(contentWidth, contentHeight)
}

private val WINDOW_HORIZONTAL_PADDING = 24.dp
private val WINDOW_VERTICAL_PADDING = 16.dp
private val MAP_ROW_VERTICAL_PADDING = 16.dp
private val BOTTOM_ROW_VERTICAL_PADDING = 40.dp
private val TITLE_BLOCK_HEIGHT = 72.dp
private val CONTROL_ROW_HEIGHT = 64.dp
private val DEFAULT_BOTTOM_ROW_HEIGHT = 40.dp
