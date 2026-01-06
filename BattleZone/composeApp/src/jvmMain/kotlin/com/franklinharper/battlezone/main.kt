package com.franklinharper.battlezone

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1200.dp, 1500.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "BattleZone",
        state = windowState,
    ) {
        App()
    }
}
