package com.franklinharper.battlezone

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Maximum number of concurrent animations to prevent performance issues
 */
private const val MAX_CONCURRENT_ANIMATIONS = 10

/**
 * Coordinates attack and reinforcement animations.
 * Supports multiple concurrent animations without blocking game state updates.
 */
class AnimationCoordinator(private val scope: CoroutineScope) {
    private val _animationState = MutableStateFlow(AnimationState())
    val animationState: StateFlow<AnimationState> = _animationState.asStateFlow()

    private var cleanupJob: Job? = null

    init {
        startCleanupTimer()
    }

    /**
     * Start a new animation immediately (non-blocking).
     * Multiple animations can run concurrently.
     */
    fun startAnimation(animation: Animation) {
        _animationState.update { state ->
            var animations = state.activeAnimations

            // If at limit, remove oldest animation of same type
            if (animations.size >= MAX_CONCURRENT_ANIMATIONS) {
                val oldestId = animations.values
                    .filter { it::class == animation::class }
                    .minByOrNull { it.startTime }
                    ?.id

                if (oldestId != null) {
                    animations = animations - oldestId
                }
            }

            state.copy(activeAnimations = animations + (animation.id to animation))
        }

        // Schedule automatic removal when animation completes
        scope.launch {
            delay(animation.duration)
            removeAnimation(animation.id)
        }
    }

    /**
     * Remove a completed animation
     */
    private fun removeAnimation(id: String) {
        _animationState.update { state ->
            state.copy(activeAnimations = state.activeAnimations - id)
        }
    }

    /**
     * Periodic cleanup of expired animations (backup mechanism)
     */
    private fun startCleanupTimer() {
        cleanupJob = scope.launch {
            while (isActive) {
                delay(100) // Check every 100ms
                val currentTime = System.currentTimeMillis()
                _animationState.update { state ->
                    state.copy(
                        activeAnimations = state.activeAnimations.filterValues { animation ->
                            !animation.isComplete(currentTime)
                        }
                    )
                }
            }
        }
    }

    /**
     * Get all animations affecting a specific territory
     */
    fun getAnimationsForTerritory(territoryId: Int): List<Animation> {
        return _animationState.value.activeAnimations.values.filter { animation ->
            when (animation) {
                is Animation.AttackAnimation ->
                    animation.fromTerritoryId == territoryId ||
                    animation.toTerritoryId == territoryId
                is Animation.ReinforcementAnimation ->
                    animation.territoryId == territoryId
            }
        }
    }

    /**
     * Clear all animations (useful for game reset)
     */
    fun clearAll() {
        _animationState.value = AnimationState()
    }

    /**
     * Dispose of coordinator resources
     */
    fun dispose() {
        cleanupJob?.cancel()
    }
}
