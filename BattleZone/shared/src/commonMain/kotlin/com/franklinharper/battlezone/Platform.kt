package com.franklinharper.battlezone

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform