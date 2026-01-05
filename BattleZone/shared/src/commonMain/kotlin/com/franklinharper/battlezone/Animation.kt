package com.franklinharper.battlezone

/**
 * Represents visual animations that play during game events.
 * Animations are purely cosmetic and don't affect game state.
 */
sealed class Animation {
    abstract val id: String
    abstract val startTime: Long
    abstract val duration: Long

    /**
     * Check if animation has completed
     */
    fun isComplete(currentTime: Long): Boolean {
        return (currentTime - startTime) >= duration
    }

    /**
     * Get overall progress (0.0 to 1.0)
     */
    fun getProgress(currentTime: Long): Float {
        return ((currentTime - startTime).toFloat() / duration).coerceIn(0f, 1f)
    }

    /**
     * Attack animation showing territory pulse and result flash
     */
    data class AttackAnimation(
        override val id: String,
        override val startTime: Long,
        val fromTerritoryId: Int,
        val toTerritoryId: Int,
        val attackerWins: Boolean,
        override val duration: Long = 900L
    ) : Animation() {

        /**
         * Get current animation phase
         */
        fun getCurrentPhase(currentTime: Long): AnimationPhase {
            val elapsed = currentTime - startTime
            return when {
                elapsed < 400 -> AnimationPhase.PULSING
                elapsed < 900 -> AnimationPhase.FLASH
                else -> AnimationPhase.COMPLETE
            }
        }

        /**
         * Get pulse animation progress (0.0 to 1.0 over 400ms)
         */
        fun getPulseProgress(currentTime: Long): Float {
            val elapsed = (currentTime - startTime).coerceAtMost(400)
            return (elapsed.toFloat() / 400f)
        }

        /**
         * Get flash animation progress (0.0 to 1.0 over 500ms)
         */
        fun getFlashProgress(currentTime: Long): Float {
            val elapsed = (currentTime - startTime - 400).coerceIn(0, 500)
            return (elapsed.toFloat() / 500f)
        }
    }

    /**
     * Reinforcement animation showing army count changes
     */
    data class ReinforcementAnimation(
        override val id: String,
        override val startTime: Long,
        val territoryId: Int,
        val armyChange: Int,
        override val duration: Long = 800L
    ) : Animation()
}

/**
 * Animation phases for attack animations
 */
enum class AnimationPhase {
    PULSING,   // 0-400ms: Territory outlines pulse
    FLASH,     // 400-650ms: Result flash overlay
    COMPLETE   // Animation finished
}

/**
 * State container for all active animations
 */
data class AnimationState(
    val activeAnimations: Map<String, Animation> = emptyMap()
)
