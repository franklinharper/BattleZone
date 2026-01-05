# Attack Animations Plan

## Overview

Add visual animations to show attacks and their results that run asynchronously without blocking user input.

## Requirements

1. **Asynchronous**: Animations run in parallel with game logic
2. **Short**: Each animation completes in 1-3 seconds
3. **Non-blocking**: User can still interact with UI during animations
4. **Clear**: User understands what happened (which territories, who won)
5. **Concurrent**: Multiple animations can play simultaneously
6. **Instant Updates**: Game state updates immediately, animations are purely visual
7. **Zero Input Delay**: Human player can launch attacks with zero delay

## Architecture

### Critical Design Principle: State vs Animation Separation

**Game state updates INSTANTLY when attack executes. Animations are purely cosmetic overlays.**

```
Human clicks attack → Game state updates immediately → Animation starts playing
                   ↓
Human can click next attack immediately (no wait)
                   ↓
Second game state update → Second animation starts (plays concurrently with first)
```

### Animation State Management

```kotlin
// Animation state is completely separate from game state
data class AnimationState(
    val activeAnimations: Map<String, Animation> = emptyMap() // Key = animation ID
)

sealed class Animation {
    abstract val id: String
    abstract val startTime: Long
    abstract fun isComplete(currentTime: Long): Boolean

    data class AttackAnimation(
        override val id: String,
        override val startTime: Long,
        val fromTerritoryId: Int,
        val toTerritoryId: Int,
        val attackerRoll: IntArray,
        val defenderRoll: IntArray,
        val attackerWins: Boolean,
        val duration: Long = 2700L // Total animation duration in ms
    ) : Animation() {
        override fun isComplete(currentTime: Long) =
            (currentTime - startTime) >= duration

        fun getCurrentPhase(currentTime: Long): AnimationPhase {
            val elapsed = currentTime - startTime
            return when {
                elapsed < 400 -> AnimationPhase.HIGHLIGHTING
                elapsed < 1200 -> AnimationPhase.DICE_ROLLING
                elapsed < 2200 -> AnimationPhase.DICE_RESULT
                elapsed < 2700 -> AnimationPhase.APPLYING_RESULT
                else -> AnimationPhase.COMPLETE
            }
        }

        fun getProgress(currentTime: Long): Float =
            ((currentTime - startTime).toFloat() / duration).coerceIn(0f, 1f)
    }

    data class ReinforcementAnimation(
        override val id: String,
        override val startTime: Long,
        val territoryId: Int,
        val armyChange: Int, // Can be positive or negative
        val duration: Long = 800L
    ) : Animation() {
        override fun isComplete(currentTime: Long) =
            (currentTime - startTime) >= duration
    }
}

enum class AnimationPhase {
    HIGHLIGHTING,    // 0-400ms: Highlight attacking/defending territories
    DICE_ROLLING,    // 400-1200ms: Show dice rolling animation
    DICE_RESULT,     // 1200-2200ms: Show final dice values
    APPLYING_RESULT, // 2200-2700ms: Update army counts with animation
    COMPLETE         // Animation done
}
```

### Animation Coordinator (Concurrent Design)

```kotlin
/**
 * Manages concurrent animations without blocking.
 * Animations start immediately and run in parallel.
 */
class AnimationCoordinator(private val scope: CoroutineScope) {
    private val _animationState = MutableStateFlow(AnimationState())
    val animationState: StateFlow<AnimationState> = _animationState.asStateFlow()

    // Cleanup timer to remove completed animations
    private var cleanupJob: Job? = null

    init {
        startCleanupTimer()
    }

    /**
     * Start a new animation immediately (non-blocking).
     * Multiple animations can run concurrently.
     */
    fun startAnimation(animation: Animation) {
        // Add to active animations immediately
        _animationState.update { state ->
            state.copy(
                activeAnimations = state.activeAnimations + (animation.id to animation)
            )
        }

        // Schedule removal when animation completes
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
            state.copy(
                activeAnimations = state.activeAnimations - id
            )
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

    fun dispose() {
        cleanupJob?.cancel()
    }
}

// Extension property for animations
val Animation.duration: Long
    get() = when (this) {
        is Animation.AttackAnimation -> this.duration
        is Animation.ReinforcementAnimation -> this.duration
    }
```

### Integration with GameController

```kotlin
// In GameController.kt
class GameController(
    // ... existing parameters
    private val animationCoordinator: AnimationCoordinator? = null
) {
    private fun executeAttack(fromTerritoryId: Int, toTerritoryId: Int) {
        val currentGameState = _gameState.value
        val fromTerritory = currentGameState.map.territories.getOrNull(fromTerritoryId) ?: return
        val toTerritory = currentGameState.map.territories.getOrNull(toTerritoryId) ?: return

        // Validation...
        if (fromTerritory.owner != currentGameState.currentPlayerIndex) return
        if (fromTerritory.armyCount <= 1) return
        // ... etc

        // Roll dice
        val attackerRoll = currentGameState.map.gameRandom.rollDice(fromTerritory.armyCount)
        val defenderRoll = currentGameState.map.gameRandom.rollDice(toTerritory.armyCount)
        val attackerTotal = attackerRoll.sum()
        val defenderTotal = defenderRoll.sum()
        val attackerWins = attackerTotal > defenderTotal

        // CRITICAL: Start animation IMMEDIATELY (non-blocking, runs in parallel)
        animationCoordinator?.startAnimation(
            Animation.AttackAnimation(
                id = UUID.randomUUID().toString(),
                startTime = System.currentTimeMillis(),
                fromTerritoryId = fromTerritoryId,
                toTerritoryId = toTerritoryId,
                attackerRoll = attackerRoll,
                defenderRoll = defenderRoll,
                attackerWins = attackerWins
            )
        )

        // Game state updates IMMEDIATELY (does not wait for animation)
        if (attackerWins) {
            val armiesTransferred = fromTerritory.armyCount - 1
            toTerritory.owner = fromTerritory.owner
            toTerritory.armyCount = armiesTransferred
            fromTerritory.armyCount = 1
        } else {
            fromTerritory.armyCount = 1
        }

        // Update player states
        GameLogic.updatePlayerState(currentGameState.map, currentGameState.players[0], 0)
        GameLogic.updatePlayerState(currentGameState.map, currentGameState.players[1], 1)

        // Emit new state immediately
        _gameState.value = _gameState.value.copy(
            players = arrayOf(
                currentGameState.players[0].copy(),
                currentGameState.players[1].copy()
            ),
            // ... other state updates
        )

        // Check for victory
        checkVictory()

        // IMPORTANT: Function returns immediately. Animation plays asynchronously.
        // Human player can click next attack right away!
    }
}
```

### UI Integration

```kotlin
// In GameScreen.kt or similar
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    val animationState by viewModel.animationCoordinator.animationState.collectAsState()

    Box {
        // Base map (ALWAYS interactive, never blocked)
        MapRenderer(
            map = gameState.map,
            onTerritoryClick = { territoryId ->
                // Clicks are processed immediately, even during animations
                viewModel.selectTerritory(territoryId)
            }
            // ... other parameters
        )

        // Animation overlay (renders on top, does not intercept clicks)
        AnimationOverlay(
            animations = animationState.activeAnimations.values.toList(),
            gameState = gameState,
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) { /* Don't consume pointer events */ }
        )
    }
}

@Composable
fun AnimationOverlay(
    animations: List<Animation>,
    gameState: GameState,
    modifier: Modifier = Modifier
) {
    val currentTime by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(16) // ~60 FPS
        }
    }

    Canvas(modifier = modifier) {
        // Render all active animations concurrently
        animations.forEach { animation ->
            when (animation) {
                is Animation.AttackAnimation -> {
                    val phase = animation.getCurrentPhase(currentTime)
                    val progress = animation.getProgress(currentTime)

                    drawAttackAnimation(
                        animation = animation,
                        phase = phase,
                        progress = progress,
                        gameState = gameState
                    )
                }
                is Animation.ReinforcementAnimation -> {
                    val progress = animation.getProgress(currentTime)
                    drawReinforcementAnimation(animation, progress, gameState)
                }
            }
        }
    }
}
```

### Handling Overlapping Animations

When multiple attacks affect the same territory rapidly, animations overlay naturally:

```kotlin
/**
 * Example scenario:
 * - Bot attacks territory A → B (animation 1 starts)
 * - 500ms later, human attacks territory B → C (animation 2 starts)
 * - Both animations render concurrently
 */

// In AnimationOverlay rendering
fun DrawScope.drawAttackAnimation(
    animation: Animation.AttackAnimation,
    phase: AnimationPhase,
    progress: Float,
    gameState: GameState
) {
    when (phase) {
        AnimationPhase.HIGHLIGHTING -> {
            // Draw pulsing borders on both territories
            // If multiple animations affect same territory, borders stack/intensify
            val attackerTerritory = gameState.map.territories[animation.fromTerritoryId]
            val defenderTerritory = gameState.map.territories[animation.toTerritoryId]

            drawTerritoryHighlight(
                territory = attackerTerritory,
                color = Color.Red.copy(alpha = 0.3f + 0.2f * sin(progress * PI * 4)),
                strokeWidth = 6f
            )

            drawTerritoryHighlight(
                territory = defenderTerritory,
                color = Color.Yellow.copy(alpha = 0.3f + 0.2f * sin(progress * PI * 4)),
                strokeWidth = 6f
            )
        }

        AnimationPhase.DICE_ROLLING -> {
            // Draw dice popup in center of screen
            // Multiple concurrent attacks = multiple dice popups
            // Offset them slightly to avoid complete overlap
            val offset = animations.indexOf(animation) * 50f
            drawDicePopup(
                animation = animation,
                progress = progress,
                yOffset = offset
            )
        }

        // ... other phases
    }
}
```

## Benefits of Concurrent Animation Design

### 1. Zero Input Latency
- Human player clicks attack → Game state updates instantly → Territory ownership changes immediately
- No waiting for animations to complete
- Feels responsive and snappy

### 2. Rapid Gameplay
- Bot attacks don't queue up
- Multiple attacks can happen while previous animations still play
- Fast-paced games don't slow down due to animation overhead

### 3. Visual Clarity Despite Overlap
- Each attack gets its own animation
- Overlapping animations are visually distinct (different positions, colors)
- User can follow multiple concurrent events

### 4. Performance Considerations
- Limit concurrent animations if performance degrades
- Option: Cap at 5 concurrent attack animations
- Older animations auto-cancel if limit reached

```kotlin
// In AnimationCoordinator
private const val MAX_CONCURRENT_ANIMATIONS = 5

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
    // ... rest of implementation
}
```

## Visual Options

## ✅ CHOSEN DESIGN: Territory Pulse + Flash

**Simple, fast, and works perfectly with concurrent animations**

### Visual Specification

**Phase 1: Pulsing Outlines (400ms)**
- **Attacking territory**: Yellow pulsing outline (3-6px stroke width oscillating)
- **Defending territory**: Red pulsing outline (3-6px stroke width oscillating)
- Pulse frequency: 2-3 pulses during phase

**Phase 2: Result Flash (250ms)**
- **If attack succeeds**:
  - Defending territory: Translucent red overlay (alpha 0.4)
  - Territory ownership changes to attacker color underneath flash
- **If attack fails**:
  - Defending territory: Translucent blue overlay (alpha 0.4)
  - Territory retains original owner color

**Total Duration**: 650ms per attack animation

**Concurrent Behavior**:
- Multiple pulsing outlines stack naturally (additive blending)
- Flash overlays appear on top of base territory colors
- Same territory attacked twice: Both flashes show (colors blend)
- Very fast = minimal visual overlap issues

### Implementation Details

```kotlin
data class AttackAnimation(
    override val id: String,
    override val startTime: Long,
    val fromTerritoryId: Int,
    val toTerritoryId: Int,
    val attackerWins: Boolean,
    val duration: Long = 650L
) : Animation() {

    fun getCurrentPhase(currentTime: Long): AnimationPhase {
        val elapsed = currentTime - startTime
        return when {
            elapsed < 400 -> AnimationPhase.PULSING
            elapsed < 650 -> AnimationPhase.FLASH
            else -> AnimationPhase.COMPLETE
        }
    }

    fun getPulseProgress(currentTime: Long): Float {
        val elapsed = (currentTime - startTime).coerceAtMost(400)
        return (elapsed.toFloat() / 400f)
    }

    fun getFlashProgress(currentTime: Long): Float {
        val elapsed = (currentTime - startTime - 400).coerceIn(0, 250)
        return (elapsed.toFloat() / 250f)
    }
}

enum class AnimationPhase {
    PULSING,   // 0-400ms: Pulsing outlines
    FLASH,     // 400-650ms: Result flash
    COMPLETE   // Animation done
}
```

### Rendering Implementation

```kotlin
fun DrawScope.drawAttackAnimation(
    animation: Animation.AttackAnimation,
    currentTime: Long,
    map: GameMap
) {
    val phase = animation.getCurrentPhase(currentTime)

    when (phase) {
        AnimationPhase.PULSING -> {
            val progress = animation.getPulseProgress(currentTime)

            // Oscillating stroke width (3px to 6px)
            val pulseValue = sin(progress * PI * 3).toFloat() // 3 pulses
            val strokeWidth = 3f + (pulseValue + 1f) * 1.5f // Maps [-1,1] to [3,6]

            // Yellow outline on attacking territory
            drawTerritoryOutline(
                territory = map.territories[animation.fromTerritoryId],
                color = Color.Yellow,
                strokeWidth = strokeWidth
            )

            // Red outline on defending territory
            drawTerritoryOutline(
                territory = map.territories[animation.toTerritoryId],
                color = Color.Red,
                strokeWidth = strokeWidth
            )
        }

        AnimationPhase.FLASH -> {
            val progress = animation.getFlashProgress(currentTime)

            // Flash overlay on defending territory
            val flashColor = if (animation.attackerWins) {
                Color.Red.copy(alpha = 0.4f * (1f - progress)) // Fade out
            } else {
                Color.Blue.copy(alpha = 0.4f * (1f - progress)) // Fade out
            }

            drawTerritoryFill(
                territory = map.territories[animation.toTerritoryId],
                color = flashColor
            )
        }

        AnimationPhase.COMPLETE -> {
            // Animation done, will be removed by coordinator
        }
    }
}
```

### Color Specifications

```kotlin
object AttackAnimationColors {
    val ATTACKING_OUTLINE = Color(0xFFFFD700) // Gold/Yellow
    val DEFENDING_OUTLINE = Color(0xFFFF3333) // Bright Red
    val SUCCESS_FLASH = Color(0xFFFF0000)     // Pure Red
    val FAILURE_FLASH = Color(0xFF4444FF)     // Bright Blue
}
```

### Benefits of This Design

✅ **Fast**: 650ms total (4x faster than dice popup options)
✅ **Clean**: No screen clutter, focuses on the map
✅ **Concurrent-friendly**: Outlines and flashes stack naturally
✅ **Clear feedback**: Yellow/Red outlines → Flash shows result instantly
✅ **No complex graphics**: Just outlines and color overlays
✅ **Performant**: Simple rendering, scales to 10+ concurrent animations
✅ **Responsive**: Quick animations = rapid-fire attacks feel snappy

---

## Alternative Options (Not Chosen)

### Option 1: Territory Pulse + Dice Popup

**Description**: Territories pulse/glow during attack, dice appear in center of screen

**Visual Flow**:
1. **Highlighting (400ms)**:
   - Attacking territory: Red pulsing border (thick, 6px)
   - Defending territory: Yellow pulsing border (thick, 6px)
   - Both territories: Slight brightness increase (+20%)

2. **Dice Rolling (800ms)**:
   - Center of screen: Two dice groups appear
   - Each die rotates randomly (3D tumble effect)
   - Background: Semi-transparent dark overlay (0.3 opacity)
   - Label above each group: "Attacker" / "Defender"

3. **Dice Result (1000ms)**:
   - Dice stop tumbling, show final values
   - Numbers appear below each group showing totals
   - Winner's dice group: Green glow
   - Loser's dice group: Red glow
   - Text banner: "Attacker Wins!" or "Defender Wins!"

4. **Applying Result (500ms)**:
   - Army counts on territories animate to new values
   - If territory captured: Color transition from defender to attacker
   - Fade out dice popup
   - Remove territory highlights

**Concurrent Animation Handling**:
- Multiple dice popups stack vertically with 60px spacing
- Territory highlights overlay (multiple borders visible if territory attacked multiple times)
- Semi-transparent overlays blend naturally
- Z-order: Newer animations render on top

**Example with 2 concurrent attacks**:
```
Screen center:
┌─────────────────────┐
│ Attack 1: A → B     │  ← First attack (dice popup at y=200)
│ Dice: [4][3] vs [2] │
│ Attacker Wins!      │
└─────────────────────┘

┌─────────────────────┐
│ Attack 2: C → D     │  ← Second attack (dice popup at y=260)
│ Dice: [6][5] vs [3] │
│ Rolling...          │
└─────────────────────┘

Map: Territories A, B, C, D all have highlights (different phases)
```

**Pros**:
- Clear and focused (dice are center of attention)
- Doesn't interfere with map visibility
- Easy to understand what's happening
- Stacked dice popups keep all attacks visible
- Works well with concurrent animations

**Cons**:
- Requires creating dice graphics/animations
- Screen can get crowded with 4+ concurrent attacks (cap at 3 dice popups)

---

### Option 2: Projectile + Territory Flash

**Description**: Visual "projectile" travels from attacker to defender

**Visual Flow**:
1. **Highlighting (300ms)**:
   - Attacking territory: Red border
   - Defending territory: Yellow border

2. **Projectile Launch (600ms)**:
   - Animated arrow/bolt/energy beam travels from attacker center to defender center
   - Attacker dice float above territory showing roll values
   - Trail effect follows projectile

3. **Impact (400ms)**:
   - Projectile reaches defender
   - Defender dice appear showing roll values
   - Flash effect on defending territory
   - Color: Green if attacker wins, Red if defender wins

4. **Result Display (700ms)**:
   - Text popup above defending territory: "15 vs 10" with winner highlighted
   - If captured: Territory color morphs to attacker color
   - Army counts update with number animation

5. **Cleanup (200ms)**:
   - Fade out projectile, dice, and highlights

**Pros**:
- Very visual and game-like
- Shows directionality of attack clearly
- Engaging to watch

**Cons**:
- More complex to implement
- Might feel cluttered on maps with many territories

---

### Option 3: Minimalist Numbers + Highlights

**Description**: Simple number popups with territory highlights, no dice graphics

**Visual Flow**:
1. **Attack Initiation (200ms)**:
   - Attacking territory: Thick red outline with subtle pulse
   - Defending territory: Thick yellow outline with subtle pulse
   - Arrow appears connecting the two territories

2. **Roll Display (800ms)**:
   - Numbers appear above each territory showing individual die rolls
   - Example above attacker: "3 + 6 + 4 + 2"
   - Example above defender: "5 + 4"
   - Numbers cascade in one at a time (fast)

3. **Total Display (600ms)**:
   - Dice values fade out
   - Totals appear larger: "15" above attacker, "9" above defender
   - Winner's number: Green color + slight grow animation
   - Loser's number: Red color + slight shrink animation

4. **Result Application (400ms)**:
   - If captured: Territory smoothly transitions to attacker color
   - Army counts morph to new values
   - Highlights and numbers fade out

**Pros**:
- Lightweight (no complex graphics needed)
- Fast and clean
- Still clear about what happened

**Cons**:
- Less exciting than dice/projectile options
- Might feel "dry" for a dice game

---

### Option 4: Side Panel Theater

**Description**: Attacks play out in a dedicated side panel, map shows minimal highlights

**Visual Flow**:
1. **Theater Panel Appears (200ms)**:
   - Right side of screen: Panel slides in (25% width)
   - Map dims slightly but remains interactive

2. **Attack Visualization in Panel (1500ms)**:
   - Top: "Territory 5 attacks Territory 8"
   - Middle: Animated dice rolling (3D effect)
   - Shows: [Attacker rolls] → [3][6][2][5] = 16
   - Shows: [Defender rolls] → [4][6] = 10
   - Bottom: "Attacker Wins!"
   - Color-coded: Winner in green, loser in red

3. **Map Updates (300ms)**:
   - Territories on map: Quick flash on affected territories
   - Army counts update
   - If captured: Color change

4. **Panel Dismissal (200ms)**:
   - Panel slides out
   - Map returns to full brightness

**Pros**:
- Keeps map uncluttered
- Can show detailed information
- User can watch or ignore

**Cons**:
- Takes up screen space
- Divides attention between panel and map
- May feel disconnected from map action

---

### Option 5: Augmented Reality Style

**Description**: Holographic-style overlays appear above territories

**Visual Flow**:
1. **Territory Activation (300ms)**:
   - Attacking territory: Glowing red aura expands outward
   - Defending territory: Glowing yellow shield appears
   - Connection line: Animated dashed line between territories

2. **Dice Projection (800ms)**:
   - 3D dice appear floating above each territory
   - Dice tumble in place (perspective projection)
   - Slight shadow beneath dice for depth

3. **Result Reveal (600ms)**:
   - Dice settle showing final values
   - Totals appear as floating numbers
   - Winner: Victory particles burst from winning territory
   - Loser: Territory shakes slightly

4. **State Update (500ms)**:
   - Army numbers count up/down to new values
   - If captured: Color wave sweeps across territory (attacker → defender direction)
   - All effects fade out

**Pros**:
- Very polished and modern look
- Feels premium and engaging
- Clear spatial relationship

**Cons**:
- Most complex to implement
- Requires 3D rendering or good 2D perspective tricks
- May be performance-intensive

---

## Recommended Implementation Order

### Phase 1: Foundation (1-2 days)
- Implement `AnimationCoordinator` class with concurrent support
- Add animation state to `GameViewModel`
- Create basic animation overlay layer in UI
- Test concurrent animation management (start multiple, cleanup)

### Phase 2: Territory Pulse Animation (1-2 days)
- Implement pulsing outline rendering
  - Yellow outline on attacking territory
  - Red outline on defending territory
  - Oscillating stroke width (sin wave)
- Test with single attack
- Test with concurrent attacks (verify overlays stack correctly)

### Phase 3: Flash Overlay (1 day)
- Implement result flash overlay
  - Red flash for successful attacks
  - Blue flash for failed attacks
  - Fade-out animation
- Integration with GameController (pass attackerWins flag)
- Test full animation sequence (pulse → flash)

### Phase 4: Polish & Testing (1 day)
- Fine-tune timing (adjust pulse frequency, flash duration)
- Test rapid-fire attacks (human clicking fast)
- Test bot vs bot (many concurrent animations)
- Performance testing (10+ simultaneous attacks)
- Test on all platforms

**Total Estimated Time**: 4-6 days

## Technical Considerations

### Performance
- Use `remember` and `derivedStateOf` to avoid unnecessary recompositions
- Limit active animations to 3-5 concurrent max
- Use hardware acceleration for transformations (translate, rotate, scale)

### Accessibility
- Ensure animations can be disabled (settings)
- Provide text fallbacks for screen readers
- Respect system "reduce motion" preferences

### Testing

**Concurrent Animation Tests**:
- ✅ Start 3 attacks rapidly (within 500ms) → All 3 animations play concurrently
- ✅ Human attacks while bot animation playing → Both animations visible
- ✅ Attack same territory twice in quick succession → Both animations render
- ✅ Exceed animation limit (6+ concurrent) → Oldest animations removed gracefully

**Zero-Delay Input Tests**:
- ✅ Click attack → Can immediately click next attack (no delay)
- ✅ Measure input latency: Click → Territory selection < 16ms
- ✅ Game state updates before animation completes
- ✅ Territory ownership reflects immediately in UI (army counts update)

**Visual Overlap Tests**:
- ✅ Multiple dice popups offset correctly (don't completely overlap)
- ✅ Territory highlights stack visually (multiple attacks on same territory)
- ✅ Animations don't obscure critical game information

**Performance Tests**:
- ✅ 10 rapid attacks → Frame rate stays above 30 FPS
- ✅ Animation cleanup removes completed animations properly
- ✅ Memory usage stable with many consecutive animations

## Success Criteria

- ✅ Attacks are visually clear and understandable
- ✅ Animations complete in **650ms** (fast and responsive)
- ✅ User can click/interact with map during animations with ZERO delay
- ✅ **Multiple attacks play concurrently** (not sequentially)
- ✅ **Human can launch next attack instantly** without waiting
- ✅ Game state updates immediately, animations are purely visual overlay
- ✅ Animations don't cause performance issues (10+ concurrent attacks = smooth)
- ✅ Works on all target platforms (Android, iOS, Desktop, Web)
- ✅ Pulsing outlines are visible and distinct (yellow = attacker, red = defender)
- ✅ Flash overlays clearly indicate result (red = success, blue = failure)

## Example Scenarios

### Scenario 1: Human Rapid Fire
```
Time 0ms:    Human attacks A → B (animation 1 starts, state updates)
             Yellow outline on A, red outline on B (pulsing)
Time 100ms:  Human attacks C → D (animation 2 starts, state updates)
             Both animations playing concurrently
             4 territories now have pulsing outlines
Time 400ms:  Animation 1 enters flash phase (red or blue overlay on B)
Time 500ms:  Animation 2 enters flash phase (red or blue overlay on D)
Time 650ms:  Animation 1 completes, removed from active list
Time 750ms:  Animation 2 completes, removed from active list
```

### Scenario 2: Bot + Human Overlap
```
Time 0ms:    Bot attacks X → Y (animation 1 starts)
             Yellow outline on X, red outline on Y (pulsing)
Time 300ms:  Human attacks Z → W (animation 2 starts immediately)
             Animation 1 still in pulsing phase
             Animation 2 starts pulsing phase
             All 4 territories have pulsing outlines
Time 400ms:  Animation 1 enters flash phase (Y gets colored overlay)
Time 650ms:  Animation 1 completes
Time 700ms:  Animation 2 enters flash phase (W gets colored overlay)
Time 950ms:  Animation 2 completes
```

### Scenario 3: Same Territory Chain
```
Time 0ms:    Bot attacks A → B (attacker wins, B becomes bot's)
             Yellow outline on A, red outline on B (pulsing)
Time 400ms:  Animation enters flash phase (red overlay on B)
             Territory B ownership changes underneath flash
Time 500ms:  Bot attacks B → C (valid attack, animation starts)
             Territory B has overlapping animations:
             - First animation: Fading red flash (almost done)
             - Second animation: Yellow pulsing outline (just started)
             Territory C: Red pulsing outline (new attack target)
Time 650ms:  First animation completes
Time 900ms:  Second animation enters flash phase
Time 1150ms: Second animation completes
```
