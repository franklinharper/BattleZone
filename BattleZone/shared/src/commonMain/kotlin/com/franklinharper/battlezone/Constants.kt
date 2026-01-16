package com.franklinharper.battlezone

const val SERVER_PORT = 8080
const val MIN_PLAYERS = 2
const val MAX_PLAYERS = 8
const val REALTIME_ROUND_TIMER_MIN_SECONDS = 1
const val REALTIME_ROUND_TIMER_MAX_SECONDS = 30
const val DEFAULT_REALTIME_ROUND_TIMER_SECONDS = 5
const val MILLIS_PER_SECOND = 1000L
const val UNKNOWN_PLAYER_ID = -1

object GameRules {
    const val MIN_TERRITORIES = 18
    const val MAX_TERRITORIES = 32
    const val TARGET_TERRITORY_SIZE = 8
    const val MIN_TERRITORY_SIZE = 6
    const val MAX_ARMIES_PER_TERRITORY = 8
    const val MIN_ARMIES_TO_ATTACK = 2
    const val DICE_SIDES = 6
    const val STARTING_ARMY_MULTIPLIER = 2
}
