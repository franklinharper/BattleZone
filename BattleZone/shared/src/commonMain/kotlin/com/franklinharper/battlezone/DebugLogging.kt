package com.franklinharper.battlezone

object DebugFlags {
    var enableLogs = false
}

inline fun debugLog(message: () -> String) {
    if (DebugFlags.enableLogs) {
        println(message())
    }
}
