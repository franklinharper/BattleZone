# BattleZone Game Specification

## Overview

BattleZone is a 1v1 territory control game inspired by Dice Wars and Risk. Two players (human and/or AI) compete to control all territories on a procedurally generated map by attacking adjacent territories using dice-based combat.

**Goal**: Be the last player standing by conquering all territories on the board.

**Key Features:**
- Procedurally generated maps for high replayability
- Turn-based gameplay where players alternate turns
- Dice-based combat with defender advantage (ties go to defender)
- Army reinforcements based on largest connected territory
- Local play: Human vs AI or AI vs AI

## Design Goals

- **Fast-paced gameplay**: Quick turns and immediate conflicts
- **Aggressive strategy**: Encourage attacking and territorial expansion
- **High replayability**: Different map each game
- **Balanced competition**: Fair starting positions for both players
- **Simple rules**: Easy to learn, strategic depth emerges from play

## Game Concept

### The Board

The game board consists of territories (areas) that are adjacent to one another, similar to Risk or Dice Wars. Players attack adjacent territories by rolling dice, with the goal of controlling all territories on the board.

### Players

- Exactly 2 players (human and/or AI)
- Each player controls a set of territories
- Players alternate turns attacking enemy territories

### Victory Condition

The game ends when one player controls all territories on the board.

## Map Structure

### Territory Graph

**Adjacency Definition:**
- The map uses a hexagonal grid system where cells have 6 potential neighbors
- Two territories are adjacent if their cells share a border
- Each territory consists of multiple hexagonal cells grouped together
- Adjacency between territories is determined during map generation

**Connectivity Requirements:**
- The map is fully connected (every territory reachable from every other)
- No isolated regions or unreachable territories

### Map Size

The map size is fixed and uses the same map size as dice wars.js

## Territory Allocation

### Allocation Method: Random Distribution between players with an equal territory count for each
player whenever possible.

**Algorithm**:
1. Assign territories randomly to each player (alternating assignment)
2. All territories are owned at game start (no neutrals)

**No Connectivity Requirement**:
- A player's territories do NOT need to be connected to each other
- Promotes scattered positions and multi-front warfare

## Starting Army Distribution

### Armies per Player

**Army Target**: The game aims for an average of 3 armies per territory across the entire map (matching Dice Wars).

**Reference Implementation**: `dicewarsjs/game.js` - lines 351-378 (dice placement in `make_map()`)

**Distribution Algorithm**:
1. Start with 1 army on each territory (all territories, both players)
2. Calculate additional armies: `additionalArmies = totalTerritories × 2`
3. Distribute additional armies alternating between players:
   - Player 0 gets 1 army added to a random territory
   - Player 1 gets 1 army added to a random territory
   - Repeat until all additional armies are distributed
   - Cap territories at maximum 8 armies during initial distribution

**Result**: Each player receives approximately equal armies, with final counts depending on territory distribution

**Army Cap Rules**:
- **Maximum armies per territory**: 8 (hard cap throughout entire game)
- **During initial distribution**: Territories capped at 8 armies
- **During gameplay reinforcement**:
  - Armies are distributed randomly to territories with <8 armies
  - If all player's territories are at 8 armies, excess reinforcements go into that player's **Reserve Pool**
  - Reserve armies are stored and automatically deployed during the next reinforcement phase
- **Reserve Pool deployment**:
  - At the start of reinforcement phase, any stored reserve armies are added to the new reinforcement count
  - All armies (reserve + new reinforcements) are then distributed using the standard random distribution algorithm
- **Display**: Show each player's reserve army count in the UI (e.g., "Reserve: 3")

## Turn Structure

### Overview
- Game proceeds in rounds
- Each round has two phases: **Attack Phase** and **Reinforcement Phase**
- Players alternate taking turns during the attack phase
- Both players receive reinforcements simultaneously at end of each round
- Player order is randomized at game start

### Attack Phase

Players alternate turns. On each turn, the active player chooses to either:
1. **Attack**: Select one of their territories (with >1 army) and an adjacent enemy territory
2. **Skip**: Pass their turn without attacking

**Attack Phase End Condition**:
The attack phase ends when both players consecutively skip their turns.

**Example**:
```
Player 1: Attack
Player 2: Attack
Player 1: Skip
Player 2: Attack
Player 1: Attack
Player 2: Skip
Player 1: Skip
← Attack phase ends (both players skipped consecutively)
```

**Victory Check**:
After each successful attack, check if the attacker now controls all territories. If yes, game ends immediately with attacker as winner.

### Reinforcement Phase

**Trigger**: Occurs after attack phase ends (both players have skipped)

**Process**:
1. Calculate reinforcement armies for each player (largest connected component size)
2. Distribute reinforcement armies randomly to each player's territories
3. Display army count changes with animation
4. Return to attack phase (new round begins)

**Victory Check**:
No victory check during reinforcement - game can only end during attack phase.

**Diagram: Turn Flow State Machine**
```
[Diagram placeholder: State machine showing:
- START → Attack Phase
- Attack Phase → (player attacks) → Victory Check → (no winner) → Attack Phase
- Attack Phase → (both skip) → Reinforcement Phase → Attack Phase
- Victory Check → (winner) → GAME OVER]
```

### Army Reinforcements

**Calculation (Dice Wars Rule)**:
- At the end of each round, each player receives reinforcement armies
- **Reinforcement count = size of largest connected subgraph** of that player's territories
- Connected subgraph = group of player's territories where you can reach any territory from any other via adjacent territories (all owned by same player)

**Example**:
```
Player has 7 territories:
- Territory group A: 5 connected territories (biggest)
- Territory group B: 2 connected territories (isolated)
Reinforcement = 5 armies
```

**Diagram: Connected Component Example**
```
[Diagram placeholder: Visual map showing:
- Player 1 (purple) with two groups:
  - Group A: 5 connected territories (highlighted with blue outline)
  - Group B: 2 connected territories (separate from group A)
- Reinforcement = 5 (size of largest group A)
- Arrows showing territories in group A are all connected
- X marks showing no path between group A and group B]
```

**Distribution**:
- New armies are distributed **randomly** across all territories the player controls
- Algorithm:
  1. Calculate reinforcement count (largest connected component size)
  2. Add any reserve armies from previous rounds: `totalToDistribute = reinforcementCount + reserveArmies`
  3. For each army in totalToDistribute:
     - Get list of player's territories with <8 armies
     - If list is empty (all territories at 8): add army to reserve pool, continue to next army
     - Otherwise: randomly select one territory from the list
     - Add 1 army to selected territory
  4. Update player's reserveArmies count
  5. Display updated army counts and reserve count (if > 0)

**Rationale**:
- Rewards holding connected territory (strategic depth)
- Incentivizes expansion and consolidation
- Random distribution prevents purely defensive play
- Matches classic Dice Wars mechanics

## Attack Mechanics

### Attack UI

**Attack Initiation**:
1. Active player decides if they want to attack, or not.
2. If the player decides not to attack, they click on the Skip button, and the next player gets a chance to attack.
3. If the player decides to attack they click one of their own territories (selects attacking
territory) and clicks an adjacent enemy territory (selects target)
4. Combat resolution occurs immediately (see below)
5. The other player can attack again or end turn

**Constraints**:
- Can only attack from territories with >1 army (attacking territory will be reduced to 1 army after attack)
- Can only attack adjacent territories (shared border)
- Can only attack enemy-owned territories (not your own)
- One attack at a time (complete one attack before initiating another)

**Edge Cases**:
- If a player has no territories with >1 army, they cannot attack and must skip their turn
- If a player has no adjacent enemy territories from any valid attacking territory, they cannot attack and must skip
- If both players simultaneously have no valid attacks available (all territories have 1 army and/or no adjacent enemies):
  - Attack phase ends immediately
  - Proceed directly to reinforcement phase
  - After reinforcement distribution, new round begins

### Combat Resolution (Dice Wars Rules)

**Dice Rolling**:
- Attacker rolls dice equal to number of armies on attacking territory
- Defender rolls dice equal to number of armies on defending territory
- Each die is a standard 6-sided die (1-6)
- Sum the results for each side

**Victory Conditions**:
- **Attacker wins** if attacker's total > defender's total
  - All attacking armies (minus 1) move to conquered territory
  - Defender loses the territory
  - Attacking territory reduced to 1 army
- **Defender wins** if defender's total ≥ attacker's total (ties go to defender)
  - Attacking territory reduced to 1 army (all attacking armies lost)
  - Defender keeps territory with original army count

**Example**:
```
Attack: Territory A (5 armies) → Territory B (3 armies)
Attacker rolls 5 dice: [3,6,2,4,5] = 20
Defender rolls 3 dice: [6,5,4] = 15
Result: Attacker wins (20 > 15)
→ Territory B now owned by attacker with 4 armies
→ Territory A reduced to 1 army
```

**Tie Example**:
```
Attack: Territory A (4 armies) → Territory B (2 armies)
Attacker rolls 4 dice: [2,3,4,1] = 10
Defender rolls 2 dice: [5,5] = 10
Result: Defender wins (tie)
→ Territory A reduced to 1 army (lost 3 armies)
→ Territory B unchanged (still 2 armies, same owner)
```

**Diagram: Attack Sequence Example**
```
[Diagram placeholder: 4-panel comic-style sequence showing:
1. Before: Territory A (5 armies) selecting Territory B (3 armies)
2. Dice Roll: Attacker dice showing [3,6,2,4,5]=20, Defender [6,5,4]=15
3. Resolution: Attacker wins (20>15)
4. After: Territory A (1 army), Territory B now owned by attacker (4 armies)]
```

## Map Generation

### Algorithm Overview

The map generation is based on the Dice Wars algorithm using a hexagonal cell-based percolation method.

**Reference Implementation**: `dicewarsjs/game.js` - `make_map()` function (lines 194-380)

### Hexagonal Grid System

**Grid Dimensions**:
- Grid size: 28 columns × 32 rows (896 total hexagonal cells)
- Each cell has up to 6 neighbors (hexagonal adjacency)
- Neighbor directions: 0=upper-right, 1=right, 2=lower-right, 3=lower-left, 4=left, 5=upper-left
- Odd rows are offset by half a cell width for hexagonal tiling

**Cell Adjacency Calculation**:
- Cells in even rows (y%2 == 0): standard hex neighbors
- Cells in odd rows (y%2 == 1): neighbors shifted due to offset
- Edge cells have fewer than 6 neighbors (border of map)

**Diagram: Hexagonal Neighbor Directions**
```
[Diagram placeholder: Hexagon showing 6 directions numbered 0-5]
- 0: Upper-right
- 1: Right
- 2: Lower-right
- 3: Lower-left
- 4: Left
- 5: Upper-left
```

### Territory Generation Algorithm

**Step 1: Initialize**
1. Create shuffled array of cell numbers (for randomization)
2. Initialize all cells to 0 (not assigned to any territory)
3. Mark one random cell as adjacent (rcel array) to start

**Step 2: Percolation Loop**

**Territory Count Target**: 18-32 territories
- **Minimum 18**: Ensures both players start with 9+ territories, providing strategic depth and multiple fronts
- **Maximum 32**: Keeps game duration reasonable (typically 15-30 minutes) and map visually manageable
- **Actual count**: Depends on percolation randomness and cleanup steps
- **Validation**: If final territory count is <18, regenerate map (should be rare with proper percolation parameters)
- **Distribution**: Most maps will have 20-28 territories due to cleanup removing small territories

For each territory (until target count reached):
1. Find unassigned cell with lowest shuffle number that's adjacent to existing territories
2. Start "percolating" from that cell:
   - Assign cell to current territory number
   - Mark all 6 neighbors as adjacent candidates
   - Expand to lowest-numbered adjacent unassigned cell
   - Continue until territory reaches target size (~8 cells)
3. Mark neighbors of final territory as candidates for next territory
4. Increment territory number

**Diagram: Territory Percolation Growth Animation**
```
[Diagram placeholder: 6-frame animation showing:
1. Empty hex grid with one cell marked as seed
2. Territory grows to 2 cells
3. Territory grows to 4 cells
4. Territory grows to 7 cells
5. Territory grows to 8 cells (target size reached)
6. New seed starts for next territory]
```

**Step 3: Clean Up**
1. Fill single-cell "water" spaces (isolated unassigned cells surrounded by territories)
2. Remove territories with ≤5 cells (too small)
3. Renumber remaining territories sequentially

**Step 4: Calculate Territory Properties**
For each territory:
1. **Size**: Count of cells in territory
2. **Bounding box**: leftmost, rightmost, topmost, bottommost cells
3. **Center position**:
   - Calculate midpoint of bounding box (cx, cy)
   - Find cell closest to midpoint that's not on territory border
   - This becomes the center position (cpos) for displaying dice count
4. **Adjacency**: Build join array
   - Territory A is adjacent to Territory B if any cell of A neighbors any cell of B
   - Stored as boolean array: `territory[A].join[B] = 1`

**Step 5: Border Line Tracing**
For each territory, trace its perimeter for rendering:
1. Find a cell on territory border
2. Walk clockwise around border by following edge rules
3. Store sequence of (cell, direction) pairs defining the outline
4. Used for drawing territory boundaries

### Territory Assignment to Players

**Algorithm**:
1. Create list of all valid territories (size > 0)
2. Shuffle territory list
3. Assign territories alternating between Player 0 and Player 1

### Army Placement

**Algorithm**: Use the army distribution algorithm specified in "Starting Army Distribution" section above:
1. Set all territories to 1 army (minimum)
2. Calculate additional armies: `totalTerritories × 2`
3. Distribute additional armies alternating between players randomly to their territories (max 8 per territory)

## Map Data Structures

### Core Data Classes

```kotlin
// Represents a single territory on the map
data class Territory(
    val id: Int,                        // Territory number (1..AREA_MAX)
    var size: Int,                      // Number of cells in this territory
    var centerPos: Int,                 // Cell index of center (for dice display)
    var owner: Int,                     // Player ID (0 or 1), -1 for unowned
    var armyCount: Int,                 // Number of armies (dice) on territory

    // Bounding box for center calculation
    var left: Int,
    var right: Int,
    var top: Int,
    var bottom: Int,
    var centerX: Int,
    var centerY: Int,

    // Border drawing data
    val borderCells: IntArray,          // Cell positions along border
    val borderDirections: IntArray,     // Edge directions at each position

    // Adjacency
    val adjacentTerritories: BooleanArray  // adjacentTerritories[j] = true if territory j is adjacent
)

// Represents the hex grid cell adjacency
data class CellNeighbors(
    val directions: IntArray            // 6 cell indices for each direction (or -1 if edge)
)

// Represents the complete game map
data class GameMap(
    val gridWidth: Int = 28,
    val gridHeight: Int = 32,
    val maxTerritories: Int = 32,

    val cells: IntArray,                // Cell grid: cells[cellIndex] = territory ID
    val cellNeighbors: Array<CellNeighbors>,  // Precomputed adjacency for each cell
    val territories: Array<Territory>,  // All territory data

    val playerCount: Int = 2,
    val seed: Long? = null,             // Random seed for reproducible maps
    val gameRandom: GameRandom          // Random number generator instance
)

// Player state
data class PlayerState(
    var territoryCount: Int,            // Number of territories owned
    var largestConnectedSize: Int,      // Size of largest connected territory group
    var totalArmies: Int,               // Total armies across all territories
    var reserveArmies: Int = 0          // Armies in reserve (couldn't be placed due to 8-army cap)
)

// Represents the overall game state
data class GameState(
    val map: GameMap,
    val players: Array<PlayerState>,
    val currentPlayerIndex: Int,        // Index of player whose turn it is (0 or 1)
    val gamePhase: GamePhase,            // Current phase of the game
    val consecutiveSkips: Int,           // Count of consecutive skips (0-2)
    val winner: Int? = null,             // Player ID of winner, or null if game ongoing
    val turnHistory: List<Turn> = emptyList()  // History for replay/undo (optional)
)

enum class GamePhase {
    ATTACK,          // Players can attack
    REINFORCEMENT,   // Calculating and distributing reinforcements
    GAME_OVER        // One player has won
}

// Represents a single turn action
data class Turn(
    val playerId: Int,
    val action: TurnAction
)

sealed class TurnAction {
    data class Attack(
        val fromTerritoryId: Int,
        val toTerritoryId: Int,
        val attackerRoll: IntArray,
        val defenderRoll: IntArray,
        val attackerTotal: Int,
        val defenderTotal: Int,
        val success: Boolean
    ) : TurnAction()

    object Skip : TurnAction()
}
```

### Helper Functions

```kotlin
// Calculate cell index from x, y coordinates
fun cellIndex(x: Int, y: Int, gridWidth: Int): Int = y * gridWidth + x

// Get neighboring cell index for a given direction (0-5)
fun getNeighborCell(cellIndex: Int, direction: Int, gridWidth: Int, gridHeight: Int): Int

// Calculate largest connected component for a player
fun calculateLargestConnected(map: GameMap, playerId: Int): Int

// Check if two territories are adjacent
fun areTerritoriesAdjacent(map: GameMap, territoryA: Int, territoryB: Int): Boolean

// Manages all randomness for reproducible games
class GameRandom(seed: Long? = null) {
    private val random = Random(seed ?: System.currentTimeMillis())

    // Roll N dice (1-6 each)
    fun rollDice(count: Int): IntArray {
        return IntArray(count) { random.nextInt(1, 7) }
    }

    // Select random territory from list
    fun selectRandomTerritory(territoryIds: List<Int>): Int {
        return territoryIds[random.nextInt(territoryIds.size)]
    }

    // Select random cell
    fun selectRandomCell(cellIndices: List<Int>): Int {
        return cellIndices[random.nextInt(cellIndices.size)]
    }

    // Shuffle array (for map generation)
    fun <T> shuffle(array: Array<T>) {
        array.shuffle(random)
    }
}
```

## State Management Architecture

### Overview

The application uses a **unidirectional data flow** pattern based on the Model-View-Intent (MVI) architecture, well-suited for Compose Multiplatform.

### Core Principles

1. **Single Source of Truth**: GameState is the single source of truth for the entire game
2. **Immutable State**: All state objects are immutable data classes
3. **Unidirectional Flow**: User actions → Intent → State update → UI recomposition
4. **Platform Agnostic**: State management logic lives in `shared/commonMain`

### State Flow

```kotlin
// State container using Kotlin StateFlow
class GameViewModel {
    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun handleIntent(intent: GameIntent) {
        when (intent) {
            is GameIntent.GenerateMap -> generateNewMap()
            is GameIntent.SelectTerritory -> selectTerritory(intent.territoryId)
            is GameIntent.AttackTerritory -> executeAttack(intent.fromId, intent.toId)
            is GameIntent.SkipTurn -> skipTurn()
            is GameIntent.ExecuteBotTurn -> executeBotTurn()
        }
    }
}

// Intent represents user actions
sealed class GameIntent {
    object GenerateMap : GameIntent()
    data class SelectTerritory(val territoryId: Int) : GameIntent()
    data class AttackTerritory(val fromId: Int, val toId: Int) : GameIntent()
    object SkipTurn : GameIntent()
    object ExecuteBotTurn : GameIntent()
}

// UI state separate from game logic state
data class GameUiState(
    val selectedTerritoryId: Int? = null,
    val highlightedTerritories: Set<Int> = emptySet(),
    val isAnimating: Boolean = false,
    val showingDiceRoll: DiceRollAnimation? = null,
    val errorMessage: String? = null
)
```

### State Persistence

```kotlin
// State serialization for save/load
interface GameStateRepository {
    suspend fun saveGame(gameState: GameState)
    suspend fun loadGame(): GameState?
    suspend fun hasAutosave(): Boolean
}

// Platform-specific implementations in androidMain, iosMain, etc.
expect class GameStateRepositoryImpl() : GameStateRepository
```

### Compose Integration

```kotlin
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    gameState?.let { state ->
        GameBoard(
            gameState = state,
            uiState = uiState,
            onIntent = { intent -> viewModel.handleIntent(intent) }
        )
    }
}
```

### Benefits

- **Testability**: Pure functions for state transitions, easy to unit test
- **Predictability**: State changes are explicit and traceable
- **Time Travel**: Can implement replay/undo by storing state history
- **Platform Consistency**: Same logic across all platforms

---

## Map Rendering

### Rendering Approach

The rendering is based on the Dice Wars approach using hexagonal cell visualization.

**Reference Implementation**: `dicewarsjs/main.js` - `draw_areashape()` function (lines 586-623)

### Hexagonal Cell Rendering

**Cell Dimensions**:
- Cell width: 27 pixels (configurable)
- Cell height: 18 pixels (configurable)
- Cells in odd rows are offset by cellWidth/2 for hex tiling

**Cell Position Calculation**:
```kotlin
fun getCellPosition(cellIndex: Int, cellWidth: Float, cellHeight: Float): Pair<Float, Float> {
    val x = cellIndex % gridWidth
    val y = cellIndex / gridWidth
    val posX = x * cellWidth + if (y % 2 == 1) cellWidth / 2 else 0f
    val posY = y * cellHeight
    return Pair(posX, posY)
}
```

### Territory Rendering

**Border Drawing**:
1. Use traced border line data (stored in Territory)
2. Walk through borderCells and borderDirections arrays
3. For each segment, draw line from edge to edge
4. Vertex positions based on hexagonal edge offsets:
   ```kotlin
   val hexEdgeX = floatArrayOf(cellWidth/2, cellWidth, cellWidth, cellWidth/2, 0f, 0f)
   val hexEdgeY = floatArrayOf(3f, 3f, cellHeight-3, cellHeight+3, cellHeight-3, 3f)
   ```
5. Close the polygon to create filled area

**Fill Colors**:
- Player 0: Purple shade (#B37FFE or similar)
- Player 1: Light green (#B3FF01 or similar)
- Border stroke: Dark color (#222244)

**Dice Display**:
1. Position army count at territory centerPos
2. Render as 3D isometric dice (or simple number)
3. Color matches player color
4. Display army count from 1-8 (territories are capped at maximum 8 armies)

### Canvas/Compose Rendering

For Compose Multiplatform:
```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    // Draw each territory
    territories.forEach { territory ->
        // Draw filled polygon
        drawPath(
            path = buildTerritoryPath(territory),
            color = getPlayerColor(territory.owner),
            style = Fill
        )
        // Draw border
        drawPath(
            path = buildTerritoryPath(territory),
            color = Color.DarkGray,
            style = Stroke(width = 4f)
        )
        // Draw army count
        drawText(
            text = territory.armyCount.toString(),
            x = getCellX(territory.centerPos),
            y = getCellY(territory.centerPos)
        )
    }
}
```

### Platform-Specific Considerations

**Web (WASM/JS)**:
- Primary: Canvas API in Compose for Web
- Performance: WASM target preferred for better rendering performance
- Fallback: Pre-render territory shapes to images if performance issues on JS target

**iOS**:
- Test on older devices (iPhone 8+ / iOS 14+)
- Use Metal rendering backend (default for Compose Multiplatform)
- Monitor memory usage with large maps

**Android**:
- Support screen densities: mdpi (1x), hdpi (1.5x), xhdpi (2x), xxhdpi (3x), xxxhdpi (4x)
- Use density-independent pixels (dp) for cell sizes
- Test on various screen sizes: phones (4.5"-7"), tablets (8"-12")
- Minimum: Android 7.0 (API 24) as specified in CLAUDE.md

**Desktop/JVM**:
- Support window resizing: maintain map aspect ratio or allow scrolling
- Default window size: 1024×768 or larger
- Enable high-DPI support for Retina/4K displays

### Animation Timing Guidelines

**User Input Feedback** (immediate response):
- Territory hover highlight: 0ms (immediate on pointer enter)
- Territory selection: 0ms (immediate on click)
- Button click feedback: 100ms press animation

**Attack Sequence** (total ~2.5 seconds):
- Attack initiation: 200ms fade-in for territory highlights
- Dice roll animation: 800ms (simulated tumble with random rotation)
- Dice result display: 1000ms hold (show final values and totals)
- Combat result application: 500ms (army counts animate to new values)
- Highlight removal: 200ms fade-out

**Bot Turn Delays** (for human observation):
- Delay before bot decision: 500ms (gives human time to process previous action)
- Delay between bot attacks: 1500ms (total time for one bot attack + delays)
- Bot skip decision: 800ms display "Player X skips"

**Reinforcement Phase** (~3-5 seconds total):
- Phase transition: 300ms fade to reinforcement overlay
- Calculate reinforcements: Display immediately
- Army distribution: 300ms per army placed (with visual indicator)
- Phase completion: 500ms hold before returning to attack phase

### Visual Feedback States

**Territory States During Human Turn**:
- **Own territory (idle)**: Base player color
- **Own territory (can attack)**: Subtle pulsing glow on hover (indicates >1 army)
- **Own territory (cannot attack)**: Dimmed/greyed 50% opacity on hover (indicates 1 army)
- **Own territory (selected)**: Thick glowing border (3-4px), base color + 30% brightness
- **Enemy territory (valid target)**: Yellow/gold highlight border on hover when own territory selected
- **Enemy territory (invalid target)**: Red border with X icon on hover (not adjacent or wrong owner)
- **Enemy territory (idle)**: Base enemy color, no interaction

**Territory States During Bot Turn**:
- **Bot attacking from**: Red thick border (4px), pulsing
- **Bot targeting**: Yellow thick border (4px), pulsing
- **Other territories**: Normal display, no interaction

**Loading/Waiting States**:
- **AI thinking**: Spinner or "AI deciding..." overlay with semi-transparent background
- **Dice rolling**: Animated dice in center of screen, territories dimmed
- **Reinforcement calculating**: Progress indicator "Reinforcing armies..."

**Color Palette Recommendations**:
- Player 1: Purple (#B37FFE)
- Player 2: Light Green (#B3FF01)
- Border: Dark (#222244)
- Selection glow: White (#FFFFFF)
- Valid target: Gold (#FFD700)
- Invalid target: Red (#FF4444)
- Disabled: 50% opacity overlay

## Sound and Audio Design

### Audio Requirements

The game uses sound effects to enhance the gameplay experience while maintaining accessibility for users who prefer audio-free play.

### Sound Effects

**Combat Sounds**:
- **Dice Roll**: Tumbling/rolling sound effect (800ms duration, matches dice animation)
- **Attack Success**: Triumphant sound when attacker wins (e.g., sword clash + victory chime)
- **Attack Failure**: Defensive sound when defender wins (e.g., shield block)

**UI Interaction Sounds**:
- **Territory Selection**: Soft click when selecting own territory
- **Invalid Action**: Error beep for invalid selections
- **Button Click**: Standard UI button press sound
- **Turn Change**: Audio cue when turn passes to other player

**Game Flow Sounds**:
- **Round End**: Bell or gong to signal end of attack phase
- **Victory**: Extended victory fanfare (3-5 seconds)
- **Game Start**: Brief startup sound

### Audio Settings

```kotlin
data class AudioSettings(
    val soundEffectsEnabled: Boolean = true,
    val soundEffectsVolume: Float = 0.8f, // 0.0 to 1.0
    val masterVolume: Float = 1.0f // 0.0 to 1.0
)
```

### Platform-Specific Audio APIs

**Kotlin Multiplatform Audio Implementation**:

```kotlin
// Common interface in shared/commonMain
interface AudioPlayer {
    fun playSound(sound: GameSound)
    fun setVolume(category: AudioCategory, volume: Float)
}

enum class GameSound {
    DICE_ROLL,
    ATTACK_SUCCESS,
    ATTACK_FAILURE,
    TERRITORY_SELECT,
    INVALID_ACTION,
    BUTTON_CLICK,
    TURN_CHANGE,
    ROUND_END,
    VICTORY,
    GAME_START
}

enum class AudioCategory {
    SOUND_EFFECTS,
    MASTER
}

// Platform implementations
// androidMain: Use MediaPlayer or SoundPool
// iosMain: Use AVAudioPlayer
// jvmMain: Use javax.sound.sampled
// jsMain/wasmJsMain: Use Web Audio API
expect class AudioPlayerImpl() : AudioPlayer
```

### Audio Accessibility

**For Deaf/Hard of Hearing Users**:
- All audio cues have visual equivalents:
  - Dice roll: Animated dice visualization
  - Attack result: Color-coded result banner (green=success, red=failure)
  - Turn change: Visual banner "Player X's Turn"
  - Reinforcement: Animated army count increase
- Screen reader announces all game events (see Accessibility section)

**Visual Indicators**:
- Audio icon in settings shows ON/OFF state
- Mute button in game UI (optional, quick access)
- Visual feedback for every sound effect event

### Audio File Organization

```
shared/src/commonMain/resources/audio/
└── sfx/
    ├── dice_roll.mp3
    ├── attack_success.mp3
    ├── attack_failure.mp3
    ├── territory_select.mp3
    ├── invalid_action.mp3
    ├── button_click.mp3
    ├── turn_change.mp3
    ├── round_end.mp3
    ├── victory.mp3
    └── game_start.mp3
```

### Performance Considerations

- Preload all sound effects at game start (total <1MB)
- Use compressed audio formats (MP3/OGG) for web, native formats for mobile
- Limit simultaneous sound playback (max 3 concurrent sounds)
- Release audio resources when app goes to background

### Implementation Priority

- **Phase 4**: Basic sound effects (dice roll, attack success/failure)
- **Phase 5**: Full sound effect suite

---

## Accessibility Requirements

### Color Blindness Support
**Color Palette Requirements**:
- Player colors must have >4.5:1 contrast ratio (WCAG AA standard)
- Territory borders must be visible to users with deuteranopia, protanopia, and tritanopia
- Test with color blindness simulators during implementation

**Visual Differentiation** (beyond color):
- Player 1 territories: Solid fill
- Player 2 territories: Diagonal stripe pattern or dot pattern overlay
- Selected territory: Animated border (motion + color)
- This ensures players are distinguishable even in grayscale

### Screen Reader Support
**Announcements** (for accessibility services):
- Map state: "Map loaded. Player 1 has 12 territories, Player 2 has 11 territories"
- Territory selection: "Territory 5 selected. Player 1. 3 armies. Adjacent to enemy territories: 7, 9"
- Combat: "Attacking territory 9 from territory 5. Attacker rolled 3 dice: total 15. Defender rolled 2 dice: total 10. Attacker wins. Territory 9 now belongs to Player 1 with 2 armies."
- Turn changes: "Player 2's turn"
- Reinforcement: "Reinforcement phase. Player 1 receives 5 armies"

### Keyboard Navigation
**Required keyboard controls**:
- Tab/Shift+Tab: Cycle through interactive territories
- Arrow keys: Navigate between adjacent territories
- Enter/Space: Select territory or confirm action
- Escape: Deselect or cancel action
- S key: Skip turn (when it's player's turn)
- H key: Toggle help overlay

**Focus indicators**:
- Focused territory: Distinct outline (different from selection)
- Focus order: Player's territories first, then enemy territories, then buttons

### Reduced Motion
**For users with motion sensitivity**:
- Disable or reduce pulsing/glowing animations
- Use fade transitions instead of sliding/bouncing
- Instant dice results (no tumble animation)
- Note: This can be detected via system preferences (prefers-reduced-motion in web, accessibility settings on mobile)

## AI Implementation

### AI Algorithm

The AI is based on the default AI from Dice Wars.

**Reference Implementation**: `dicewarsjs/ai_default.js` (lines 1-71)

### Decision-Making Process

**Step 1: Analyze Game State**
1. Count territories and armies for each player
2. Calculate dice rankings (who has most armies)
3. Determine if there's a dominant player (>40% of total armies)

**Step 2: Generate Attack Options**
For each owned territory:
1. Check if it has >1 army (can attack)
2. For each adjacent enemy territory:
   - **Filter rule 1**: Don't attack if enemy has more armies
   - **Filter rule 2**: If dominant player exists, prioritize attacking/defending against them
   - **Filter rule 3**: If enemy has equal armies:
     - Attack if we're the leading player
     - Attack if opponent is the leading player
     - Otherwise attack with 90% probability
3. Add valid attacks to list: (fromTerritory, toTerritory)

**Step 3: Select Attack**
1. If no valid attacks, end turn (return 0)
2. Randomly select one attack from valid options
3. Execute attack

**Rationale**:
- Avoids suicidal attacks (attacking stronger territories)
- Balances random play with strategic targeting
- Creates pressure on leading player
- Maintains aggressive playstyle

### AI Difficulty Levels (Future)

For phase 3 implementation, different AI personalities:
- **Aggressive**: Attacks even with equal dice, ignores dominant player logic
- **Defensive**: Only attacks with 2+ army advantage, focuses on consolidation
- **Balanced**: Default AI behavior (current implementation)

## Map Performance Requirements

- **Generation time**: <500ms for 18-territory map
- **Memory**: <5MB for map data
- **Rendering**: 60 FPS on all target platforms
- **Platform**: Must run efficiently in shared/commonMain (all platforms)

## Testing Strategy

### Overview

Comprehensive testing ensures code quality, prevents regressions, and validates game mechanics across all platforms.

### Testing Pyramid

```
         /\
        /  \  E2E Tests (5%)
       /____\
      /      \  Integration Tests (15%)
     /________\
    /          \  Unit Tests (80%)
   /____________\
```

**Target Coverage**:
- **Unit Tests**: 80% coverage for business logic (shared module)
- **Integration Tests**: Critical paths (map generation → game flow → victory)
- **E2E Tests**: One complete game scenario per platform

### Unit Tests

**Location**: `shared/src/commonTest/`

**Test Categories**:

1. **Map Generation Tests**
   ```kotlin
   class MapGenerationTest {
       @Test
       fun `generated map has valid territory count`() {
           val map = MapGenerator.generate()
           assertTrue(map.territories.size in 18..32)
       }

       @Test
       fun `all territories are connected`() {
           val map = MapGenerator.generate()
           assertTrue(MapValidator.isFullyConnected(map))
       }

       @Test
       fun `territory assignment is balanced`() {
           val map = MapGenerator.generate()
           val player0Count = map.territories.count { it.owner == 0 }
           val player1Count = map.territories.count { it.owner == 1 }
           assertTrue(abs(player0Count - player1Count) <= 1)
       }

       @Test
       fun `territories have valid army counts`() {
           val map = MapGenerator.generate()
           map.territories.forEach { territory ->
               assertTrue(territory.armyCount in 1..8)
           }
       }

       @Test
       fun `map generation with seed is deterministic`() {
           val map1 = MapGenerator.generate(seed = 12345L)
           val map2 = MapGenerator.generate(seed = 12345L)
           assertEquals(map1, map2)
       }
   }
   ```

2. **Combat Resolution Tests**
   ```kotlin
   class CombatResolutionTest {
       @Test
       fun `attacker wins when total is higher`() {
           val result = resolveCombat(
               attackerDice = intArrayOf(6, 6, 6), // total 18
               defenderDice = intArrayOf(5, 5)     // total 10
           )
           assertTrue(result.attackerWins)
       }

       @Test
       fun `defender wins on tie`() {
           val result = resolveCombat(
               attackerDice = intArrayOf(5, 5),    // total 10
               defenderDice = intArrayOf(4, 6)     // total 10
           )
           assertFalse(result.attackerWins)
       }

       @Test
       fun `dice rolls are within valid range`() {
           val random = GameRandom(seed = 42L)
           repeat(100) {
               val dice = random.rollDice(5)
               dice.forEach { value ->
                   assertTrue(value in 1..6)
               }
           }
       }
   }
   ```

3. **Connected Component Tests**
   ```kotlin
   class ConnectedComponentTest {
       @Test
       fun `single territory returns size 1`() {
           val map = createTestMap(territoryCounts = mapOf(0 to 1))
           assertEquals(1, calculateLargestConnected(map, playerId = 0))
       }

       @Test
       fun `fully disconnected territories return size 1`() {
           val map = createIsolatedTerritories(count = 5, playerId = 0)
           assertEquals(1, calculateLargestConnected(map, playerId = 0))
       }

       @Test
       fun `complex connected graph returns correct size`() {
           // Predefined test map with known largest component
           val map = loadTestMap("complex_adjacency.json")
           assertEquals(5, calculateLargestConnected(map, playerId = 0))
       }

       @Test
       fun `multiple groups returns largest component`() {
           val map = createTestMapWithGroups(
               player0Groups = listOf(5, 3, 2) // sizes
           )
           assertEquals(5, calculateLargestConnected(map, playerId = 0))
       }
   }
   ```

4. **Reinforcement Tests**
   ```kotlin
   class ReinforcementTest {
       @Test
       fun `reinforcements distributed randomly`() {
           val map = createTestMap(territoryCount = 5, playerId = 0)
           distributeReinforcements(map, playerId = 0, armyCount = 10)

           // Verify total armies increased by 10
           val totalArmies = map.territories
               .filter { it.owner == 0 }
               .sumOf { it.armyCount }
           assertEquals(10, totalArmies - 5) // 5 starting armies
       }

       @Test
       fun `reinforcements respect 8-army cap`() {
           val map = createTestMap(territoryCount = 2, playerId = 0)
           map.territories.forEach { it.armyCount = 7 }

           val reserve = distributeReinforcements(map, playerId = 0, armyCount = 5)

           map.territories.forEach {
               assertTrue(it.armyCount <= 8)
           }
           assertTrue(reserve > 0) // Excess goes to reserve
       }

       @Test
       fun `reserve armies deployed in next reinforcement`() {
           val gameState = createGameState(reserveArmies = 3)
           val newReinforcements = 2

           reinforcePlayer(gameState, playerId = 0, newReinforcements)

           // Should distribute 3 + 2 = 5 armies total
           verify(gameState).distributedArmiesCount(5)
       }
   }
   ```

5. **AI Decision Tests**
   ```kotlin
   class AIBotTest {
       @Test
       fun `AI does not attack stronger territories`() {
           val map = createTestMap(
               ownTerritory = Territory(id = 1, armyCount = 3),
               enemyTerritory = Territory(id = 2, armyCount = 5, adjacent = listOf(1))
           )
           val decision = defaultAI.decide(map, playerId = 0)

           assertNull(decision.attack) // Should not attack
       }

       @Test
       fun `AI attacks weaker adjacent territories`() {
           val map = createTestMap(
               ownTerritory = Territory(id = 1, armyCount = 5),
               enemyTerritory = Territory(id = 2, armyCount = 2, adjacent = listOf(1))
           )
           val decision = defaultAI.decide(map, playerId = 0)

           assertNotNull(decision.attack)
           assertEquals(1, decision.attack?.fromTerritoryId)
           assertEquals(2, decision.attack?.toTerritoryId)
       }

       @Test
       fun `AI ends turn when no valid attacks`() {
           val map = createTestMapAllOwnTerritoriesHave1Army()
           val decision = defaultAI.decide(map, playerId = 0)

           assertTrue(decision.isSkip)
       }
   }
   ```

6. **Game State Tests**
   ```kotlin
   class GameStateTest {
       @Test
       fun `victory detected when player owns all territories`() {
           val gameState = createGameState()
           // Simulate player 0 capturing all territories
           gameState.map.territories.forEach { it.owner = 0 }

           assertTrue(checkVictory(gameState) == 0)
       }

       @Test
       fun `consecutive skips trigger reinforcement phase`() {
           val gameState = createGameState(consecutiveSkips = 1)
           gameState.skipTurn()

           assertEquals(GamePhase.REINFORCEMENT, gameState.gamePhase)
       }

       @Test
       fun `turn alternates between players`() {
           val gameState = createGameState(currentPlayer = 0)
           gameState.endTurn()

           assertEquals(1, gameState.currentPlayerIndex)
       }
   }
   ```

### Integration Tests

**Location**: `shared/src/commonTest/integration/`

**Test Scenarios**:

1. **Complete Game Flow**
   ```kotlin
   @Test
   fun `full game from start to victory`() {
       val game = GameController.newGame()

       // Verify map generated
       assertNotNull(game.state.map)
       assertTrue(game.state.map.territories.size in 18..32)

       // Simulate complete game
       var turnCount = 0
       while (game.state.winner == null && turnCount < 1000) {
           if (game.canAttack()) {
               game.executeRandomAttack()
           } else {
               game.skipTurn()
           }
           turnCount++
       }

       // Verify game completed
       assertNotNull(game.state.winner)
       assertTrue(game.state.winner in 0..1)
   }
   ```

2. **Map Generation to Army Distribution**
   ```kotlin
   @Test
   fun `generated map has properly distributed armies`() {
       val map = MapGenerator.generate()
       assignTerritories(map)
       distributeStartingArmies(map)

       val totalArmies = map.territories.sumOf { it.armyCount }
       val expectedAverage = map.territories.size * 3

       assertTrue(abs(totalArmies - expectedAverage) < map.territories.size)
   }
   ```

3. **Attack Phase to Reinforcement Phase**
   ```kotlin
   @Test
   fun `consecutive skips trigger reinforcement correctly`() {
       val game = GameController.newGame()

       game.skipTurn() // Player 0 skips
       assertEquals(GamePhase.ATTACK, game.state.gamePhase)

       game.skipTurn() // Player 1 skips
       assertEquals(GamePhase.REINFORCEMENT, game.state.gamePhase)

       // Verify reinforcements calculated and distributed
       val player0Armies = game.state.players[0].totalArmies
       game.processReinforcement()
       assertTrue(game.state.players[0].totalArmies > player0Armies)
   }
   ```

### UI Tests (Compose)

**Location**: `composeApp/src/commonTest/`

**Test Framework**: Compose Testing API

```kotlin
class GameScreenTest {
    @Test
    fun `clicking own territory selects it`() = runComposeUiTest {
        setContent {
            GameScreen(viewModel = testViewModel)
        }

        onNodeWithTag("territory_1").performClick()

        // Verify territory is selected in UI state
        assertEquals(1, testViewModel.uiState.value.selectedTerritoryId)
    }

    @Test
    fun `invalid attack shows error message`() = runComposeUiTest {
        setContent {
            GameScreen(viewModel = testViewModel)
        }

        // Try to attack from territory with 1 army
        onNodeWithTag("territory_1").performClick() // 1 army
        onNodeWithTag("territory_2").performClick() // enemy territory

        onNodeWithText("Cannot attack from territory with 1 army")
            .assertIsDisplayed()
    }
}
```

### Platform-Specific Tests

**Android**: `composeApp/src/androidInstrumentedTest/`
- Test on multiple screen sizes and densities
- Test state preservation during configuration changes

**iOS**: `composeApp/src/iosTest/`
- Test on iPhone and iPad simulators
- Test memory usage

**Desktop**: `composeApp/src/jvmTest/`
- Test window resizing
- Test keyboard navigation

**Web**: `composeApp/src/jsTest/` and `wasmJsTest/`
- Test in headless browser (Karma/Selenium)
- Test canvas rendering

### Test Utilities

```kotlin
// shared/src/commonTest/TestHelpers.kt
object TestMapFactory {
    fun createTestMap(
        territoryCount: Int = 20,
        seed: Long = 42L
    ): GameMap = MapGenerator.generate(seed)

    fun createTestMapWithLayout(
        territories: List<Territory>
    ): GameMap { /* ... */ }
}

object TestGameStateFactory {
    fun createGameState(
        currentPlayer: Int = 0,
        phase: GamePhase = GamePhase.ATTACK,
        consecutiveSkips: Int = 0
    ): GameState { /* ... */ }
}
```

### Continuous Integration

**CI Pipeline** (GitHub Actions / GitLab CI):
1. Run all tests on each commit
2. Generate coverage report (target: 80%)
3. Fail build if tests fail or coverage drops below threshold
4. Run tests on multiple platforms in parallel

**Gradle Configuration**:
```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
            }
        }
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
```

### Test Execution

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :shared:test
./gradlew :composeApp:test

# Run tests for specific platform
./gradlew :shared:jvmTest
./gradlew :shared:jsTest

# Generate coverage report
./gradlew koverHtmlReport
```

### Performance Testing

**Benchmarks** (using `kotlinx-benchmark`):
```kotlin
@Benchmark
fun mapGeneration() {
    MapGenerator.generate()
}

@Benchmark
fun largestConnectedComponent() {
    calculateLargestConnected(testMap, playerId = 0)
}
```

**Performance Targets**:
- Map generation: <500ms (verified in CI)
- Combat resolution: <10ms
- Largest connected calculation: <50ms

---

## Implementation Phases

### Phase 1: Map Generation & Rendering ✅ COMPLETE
**Goal**: Generate and display a playable map

**Status**: ✅ Completed 2026-01-03

**Tasks**:
1. Port hexagonal grid system from dicewarsjs
   - Implement cell adjacency calculation
   - Create HexGrid class with 28×32 grid
2. Port percolation-based map generation
   - Implement territory growth algorithm
   - Territory cleanup (remove small areas)
   - Calculate territory centers and bounding boxes
3. Implement territory adjacency calculation
   - Build join arrays between territories
4. Port border line tracing algorithm
   - Trace territory perimeters for rendering
5. Create Map data class hierarchy
   - Territory, GameMap, CellNeighbors classes
6. Implement random territory assignment
7. Implement random army distribution
9. Port hexagonal rendering to Compose
   - Calculate cell positions with hex offset
   - Render territory polygons with borders
   - Display army counts at territory centers
10. Create UI with "Generate New Map" button
11. Display generated map with territories colored by owner

**Success Criteria**:
- Generate valid maps
- Map renders correctly with hex-based territories
- Can generate multiple random maps

---

### Phase 2: Army Reinforcement Algorithm ✅ COMPLETE
**Goal**: Implement and visualize reinforcement mechanics

**Status**: ✅ Completed 2026-01-03

**Tasks**:
1. Port largest connected component algorithm
   - Implement union-find or DFS-based connectivity check
   - Calculate largestConnectedSize for each player
2. Implement reinforcement distribution
   - Calculate reinforcement count
   - Randomly distribute armies to player territories
3. Add "Reinforce Armies" button to UI
4. Display reinforcement count before distribution
5. Animate army count changes on territories
6. Add unit tests for connectivity algorithm
   - Test various territory configurations
   - Verify correct counting of largest connected group

**Success Criteria**:
- Correctly identifies largest connected territory group
- Distributes reinforcements randomly across all player territories
- UI clearly shows before/after army counts
- Algorithm handles edge cases (single territory, fully disconnected)

**Test Scenarios**:
1. **Single connected territory**:
   - Setup: Player has 1 territory
   - Expected: Reinforcement = 1
2. **Fully disconnected territories**:
   - Setup: Player has 5 territories, all isolated (no adjacencies)
   - Expected: Reinforcement = 1 (largest component is size 1)
3. **Multiple groups**:
   - Setup: Player has territory groups of sizes [5, 3, 2]
   - Expected: Reinforcement = 5 (largest component)
4. **Complex connected graph**:
   - Setup: Player has 10 territories with complex adjacency (predefined test map)
   - Expected: Verify correct largest component calculation
5. **All territories maxed**:
   - Setup: Player has 3 territories all at 8 armies, receives 5 reinforcements
   - Expected: All 5 armies go to reserve pool, reserve count = 5
6. **Reserve deployment**:
   - Setup: Player has 3 reserve armies, 2 territories at 7 armies, 1 at 8, receives 2 new reinforcements
   - Expected: 5 total armies distributed to the 2 territories with <8 armies

---

### Phase 3: AI Bot Implementation ✅ COMPLETE
**Goal**: Port and test AI decision-making

**Status**: ✅ Completed 2026-01-03

**Tasks**:
1. Port ai_default algorithm from dicewarsjs
   - Implement game state analysis (dice counts, rankings)
   - Implement attack option generation
   - Implement attack filtering rules
2. Create Bot interface/class
3. Implement bot decision logic
   - Evaluate all possible attacks
   - Filter based on army counts
   - Select attack randomly from valid options
4. Add bot testing harness
   - Given a specific map state, verify bot generates expected moves
   - Test edge cases: no valid attacks, only weak attacks available
5. Create deterministic test maps for validation

**Success Criteria**:
- Bot generates sensible attacks (doesn't attack stronger territories)
- Bot can decide to end turn when no good attacks exist
- Passes unit tests with predefined map scenarios
- Bot behavior matches dicewarsjs AI

---

### Phase 4: Bot vs Bot Turn-Based Mode ✅ COMPLETE
**Goal**: Two AIs playing a complete game

**Status**: ✅ Completed 2026-01-03

**UX Flow**:
1. User selects "Bot vs Bot" mode
2. App generates a map and assigns territories to each bot
3. Bot 1 generates an attack or decides to skip:
   - If attacking: Attacking territory is highlighted (e.g., red border)
   - If attacking: Defending territory is highlighted (e.g., yellow border)
4. User clicks "Execute Attack" button (if bot is attacking) or "Skip" button
   - If attacking: Dice are rolled and displayed
   - If attacking: Combat result shown (attacker wins/loses)
   - If attacking: Army counts updated
   - If attacking: Highlights removed
   - Check for victory (go to step 8 if bot won)
5. Bot 2's turn begins (similar to step 3-4 for Bot 2)
   - Check for victory after each attack
6. If both bots consecutively skip, reinforcement phase begins:
   - Calculate and display reinforcement count for both players
   - Distribute armies with animation
   - New round begins (go to step 3)
7. Otherwise, continue alternating between bots (go to step 3)
8. Display end-game screen showing winner

**Tasks**:
1. Create game mode selection screen
2. Implement turn management system
   - Track active player
   - Handle turn transitions
3. Implement attack highlighting
   - Highlight selected territories with distinct colors
4. Add "Execute Attack" button
5. Implement combat resolution
   - Dice rolling logic
   - Display dice and totals
   - Apply combat results
6. Add "End Round" button
7. Implement Round end sequence
   - Calculate reinforcements for both players
   - Distribute armies to both players
   - start a new round
8. Add victory detection
   - Check after each attack if one player owns all territories
9. Create end-game screen

**Success Criteria**:
- Bots can play a complete game from start to finish
- User can observe each attack before it executes
- Combat resolution is clear and visible
- Game correctly detects victory condition
- UI is clear about whose turn it is

---

### Phase 5: Human vs Bot Turn-Based Mode
**Goal**: Human player can compete against AI

**UX Flow**:
1. Game start: User chooses "Human vs Bot" mode
2. User's turn:
   - User can attack or click on the Skip button.
   - when User skips, then the Bot can attack (go to step 3. below).
   - User attacks clicking on one of their territories (shows as selected)
   - User clicks adjacent enemy territory (shows as target)
   - Combat automatically executes
3. Bot's turn:
   - Bot automatically selects attack
   - Highlights attacking and defending territories
   - Combat executes automatically (no button needed)
   - Bot continues attacking or ends turn
4. Reinforcement phase for human and for bot
5. Repeat until victory

**Tasks**:
1. Add mode selection: "Human vs Bot" or "Bot vs Bot"
2. Implement human player input
   - Add Skip button
   - Click to select own territory
   - Click to select enemy target
   - Validate attack legality (adjacent, >1 army)
   - Show selected state visually
3. Modify bot turn handling
   - Auto-execute attacks (no "Execute Attack" button needed)
   - Add small delay between attacks for visibility
4. Add turn indicator UI
   - Clearly show "Your Turn" vs "Bot's Turn"
5. Implement attack validation and error feedback
   - Can't attack from territory with 1 army
   - Can't attack non-adjacent territory
   - Can't attack own territory
6. Reinforce armies for both players at the end of a round
7. Polish UI/UX
   - Hover effects on valid targets
   - Disabled state for invalid selections
   - Clear visual feedback

**Success Criteria**:
- Human can successfully attack enemy territories
- Invalid moves are prevented and explained
- Bot plays automatically without user interaction
- Armies are reinforced at the end of each round
- Game flow is smooth and intuitive
- Human can win or lose against bot

---

### Phase 6: Save/Load Game State
**Goal**: Players can save game progress and resume later

**Features**:
- Save current game state to persistent storage
- Auto-save on app background/close
- Resume game from main menu
- Manual save/load via game menu
- Multiple save slots (optional)

**Data Persistence Requirements**:

**Game State Serialization**:
```kotlin
// Serialize GameState to JSON
fun GameState.toJson(): String {
    return Json.encodeToString(GameState.serializer(), this)
}

// Deserialize from JSON
fun GameState.Companion.fromJson(json: String): GameState {
    return Json.decodeFromString(GameState.serializer(), json)
}

// Add @Serializable annotations to data classes
@Serializable
data class GameState(
    val map: GameMap,
    val players: Array<PlayerState>,
    val currentPlayerIndex: Int,
    val gamePhase: GamePhase,
    val consecutiveSkips: Int,
    val winner: Int? = null,
    val turnHistory: List<Turn> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
```

**Platform-Specific Storage**:

```kotlin
// Common interface in shared/commonMain
interface GameStorage {
    suspend fun saveGame(gameState: GameState, slot: String = "autosave")
    suspend fun loadGame(slot: String = "autosave"): GameState?
    suspend fun hasSavedGame(slot: String = "autosave"): Boolean
    suspend fun deleteSave(slot: String)
    suspend fun listSaves(): List<SaveInfo>
}

data class SaveInfo(
    val slot: String,
    val timestamp: Long,
    val playerNames: List<String>,
    val turnCount: Int
)

// Platform implementations
expect class GameStorageImpl() : GameStorage
```

**Android Implementation** (`androidMain`):
```kotlin
actual class GameStorageImpl(private val context: Context) : GameStorage {
    private val prefs = context.getSharedPreferences("battlezone_saves", Context.MODE_PRIVATE)

    actual override suspend fun saveGame(gameState: GameState, slot: String) {
        withContext(Dispatchers.IO) {
            val json = gameState.toJson()
            prefs.edit().putString("save_$slot", json).apply()
        }
    }

    actual override suspend fun loadGame(slot: String): GameState? {
        return withContext(Dispatchers.IO) {
            val json = prefs.getString("save_$slot", null)
            json?.let { GameState.fromJson(it) }
        }
    }
}
```

**iOS Implementation** (`iosMain`):
```kotlin
actual class GameStorageImpl() : GameStorage {
    private val userDefaults = NSUserDefaults.standardUserDefaults

    actual override suspend fun saveGame(gameState: GameState, slot: String) {
        val json = gameState.toJson()
        userDefaults.setObject(json, forKey = "save_$slot")
    }

    actual override suspend fun loadGame(slot: String): GameState? {
        val json = userDefaults.stringForKey("save_$slot")
        return json?.let { GameState.fromJson(it) }
    }
}
```

**Desktop/JVM Implementation** (`jvmMain`):
```kotlin
actual class GameStorageImpl() : GameStorage {
    private val saveDir = File(System.getProperty("user.home"), ".battlezone/saves")

    init {
        saveDir.mkdirs()
    }

    actual override suspend fun saveGame(gameState: GameState, slot: String) {
        withContext(Dispatchers.IO) {
            val file = File(saveDir, "$slot.json")
            file.writeText(gameState.toJson())
        }
    }

    actual override suspend fun loadGame(slot: String): GameState? {
        return withContext(Dispatchers.IO) {
            val file = File(saveDir, "$slot.json")
            if (file.exists()) GameState.fromJson(file.readText()) else null
        }
    }
}
```

**Web Implementation** (`jsMain`/`wasmJsMain`):
```kotlin
actual class GameStorageImpl() : GameStorage {
    actual override suspend fun saveGame(gameState: GameState, slot: String) {
        val json = gameState.toJson()
        localStorage.setItem("battlezone_save_$slot", json)
    }

    actual override suspend fun loadGame(slot: String): GameState? {
        val json = localStorage.getItem("battlezone_save_$slot")
        return json?.let { GameState.fromJson(it) }
    }
}
```

**Auto-Save Strategy**:

1. **Auto-save triggers**:
   - After each completed attack
   - After reinforcement phase
   - On app going to background (mobile)
   - On window close (desktop)
   - On page unload (web)

2. **Auto-save implementation**:
   ```kotlin
   class GameViewModel(private val storage: GameStorage) {
       private var autoSaveJob: Job? = null

       fun handleIntent(intent: GameIntent) {
           // ... handle intent ...

           // Auto-save after state changes
           scheduleAutoSave()
       }

       private fun scheduleAutoSave() {
           autoSaveJob?.cancel()
           autoSaveJob = viewModelScope.launch {
               delay(1000) // Debounce: save 1 second after last action
               storage.saveGame(_gameState.value, slot = "autosave")
           }
       }
   }
   ```

**UI Components**:

1. **Main Menu**:
   - "Resume Game" button (visible only if autosave exists)
   - "New Game" button
   - "Load Game" button (shows save slots)

2. **In-Game Menu**:
   - "Save Game" button
   - "Return to Main Menu" (warns about losing unsaved progress)
   - "Settings" button

3. **Save Slots Screen** (optional enhancement):
   - List of saved games with metadata (timestamp, turn count)
   - Delete save option
   - Maximum 3-5 save slots

**Tasks**:

1. Add `@Serializable` annotations to all game state data classes
2. Implement `GameStorage` interface
3. Create platform-specific implementations
4. Add auto-save logic to GameViewModel
5. Create "Resume Game" button on main menu
6. Add "Save Game" to in-game menu
7. Implement save slot management UI (optional)
8. Add lifecycle handlers for auto-save:
   - Android: `onPause()`, `onStop()`
   - iOS: `applicationDidEnterBackground`
   - Desktop: Window close listener
   - Web: `beforeunload` event
9. Add error handling for storage failures
10. Test save/load on all platforms

**Success Criteria**:
- Game state correctly serializes and deserializes
- Auto-save triggers at appropriate times
- Resume game loads the exact state where player left off
- Save/load works reliably on all platforms
- No data loss on app close/crash
- User can continue an unfinished game after closing the app

**Edge Cases to Handle**:
- Corrupted save data (show error, delete corrupted save)
- Incompatible save format after app update (version migration)
- Storage quota exceeded (web browsers)
- Insufficient disk space (mobile/desktop)
- Concurrent saves (debouncing prevents this)

---

## Appendix A: Glossary

**Adjacent**: Two territories are adjacent if any of their hexagonal cells share a border. Players can only attack adjacent territories.

**Army**: A unit on a territory, represented visually as dice. Each army contributes one die roll during combat.

**Attack Phase**: The phase of each round where players alternate attacking or skipping turns until both skip consecutively.

**Connected Component**: A group of territories owned by the same player where you can travel from any territory to any other territory via adjacent territories (all owned by the same player). Used to calculate reinforcements.

**Hex Grid**: The underlying map structure using hexagonal cells arranged in 28 columns × 32 rows. Each cell has up to 6 neighbors.

**Hexagonal Offset**: Odd-numbered rows (y%2 == 1) are shifted right by half a cell width to create proper hexagonal tiling.

**Percolation**: Map generation technique where territories "grow" cell-by-cell from random seed points, expanding to neighboring cells until reaching target size.

**Reinforcement Phase**: The phase occurring after both players skip attacking, where each player receives new armies based on their largest connected component.

**Reserve Pool**: Storage for armies that cannot be placed on territories due to the 8-army cap. Automatically deployed during next reinforcement phase.

**Territory**: A contiguous group of hexagonal cells owned by a player. The basic unit of control in the game.

**Territory Join**: Two territories are "joined" (adjacent) if any cell of one territory neighbors any cell of the other territory.

---

**Document Version**: 2.4
**Last Updated**: 2026-01-03
**Status**: Phase 1, 2, 3 & 4 Complete, Phase 5+ In Progress
**Scope**: 1v1 turn-based gameplay (Human vs Bot, or Bot vs Bot)

**Recent Changes in v2.4**:
- Marked Phase 4 (Bot vs Bot Turn-Based Mode) as complete
- Implementation includes: GameController for turn management and combat resolution, full game loop with attack and reinforcement phases, victory detection, bot decision display with territory highlighting, combat results with dice visualization, and complete UI for bot vs bot gameplay
- Players can now watch two AI bots play a complete game from start to finish

**Recent Changes in v2.3**:
- Marked Phase 3 (AI Bot Implementation) as complete
- Implementation includes: Default AI bot with game state analysis, attack option generation with filtering rules, dominant player detection, comprehensive unit tests (15 test cases covering all edge cases)
- Bot correctly implements the Dice Wars AI algorithm with strategic decision-making

**Recent Changes in v2.2**:
- Marked Phase 1 (Map Generation & Rendering) as complete
- Marked Phase 2 (Army Reinforcement Algorithm) as complete
- Implementation includes: DFS-based connected component algorithm, reinforcement distribution with reserve pool, comprehensive unit tests (13 test cases), and enhanced UI with player statistics

**Recent Changes in v2.1**:
- Added State Management Architecture section (MVI pattern with StateFlow)
- Added comprehensive Testing Strategy section (unit, integration, UI tests)
- Added Sound and Audio Design section (platform-specific audio implementation)
- Added Phase 6: Save/Load Game State (cross-platform persistence)
