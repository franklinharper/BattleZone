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

## Turn Structure

### Turn-Based Play

**Turn Order**:
- Player order is randomized at game start
- Each round consists of an attack phase and a reinforcement phase
- The round ends when both players have decided that they are done attacking.

**Attack Phase**:
- Players alternate turns. When it's their turn, they can either decide to attack or to skip
  attacking.
- When both players consecutively skip attacking, then it is the end of the attack phase.

Example:

Player 1: Attack
Player 2: Attack
Player 1: Skip
Player 2: Attack
Player 1: Attack
Player 2: Skip
Player 1: Skip
Attack phase ends because player two and player one have successively skipped attacking.

**Reinforcement Phase**:
- After both players decide to stop attacking they receive reinforcement armies

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

**Distribution**:
- New armies are distributed **randomly** across all territories the player controls
- Algorithm:
  1. Calculate reinforcement count (largest connected component size)
  2. For each new army, randomly select one of player's territories
  3. Add 1 army to selected territory
  4. Repeat for all reinforcement armies

**Rationale**:
- Rewards holding connected territory (strategic depth)
- Incentivizes expansion and consolidation
- Random distribution prevents purely defensive play
- Matches classic Dice Wars mechanics

### Turn Flow

```
1. Turn starts (active player can attack)
2. First player attacks an enemy territory, or decides not to attack
3. If attack occurred, check for victory (does attacker control all territories?)
   - If yes: Game ends, attacker wins
   - If no: Continue to step 4
4. Second player attacks an enemy territory, or decides not to attack
5. If attack occurred, check for victory (does attacker control all territories?)
   - If yes: Game ends, attacker wins
   - If no: Continue to step 6
6. If both players consecutively decided not to attack:
   - Calculate reinforcements for both players (largest connected component)
   - Distribute reinforcements randomly to both players
   - Next round begins (go to step 2)
7. Otherwise, go back to step 2 (continue alternating attacks)
```
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

### Territory Generation Algorithm

**Step 1: Initialize**
1. Create shuffled array of cell numbers (for randomization)
2. Initialize all cells to 0 (not assigned to any territory)
3. Mark one random cell as adjacent (rcel array) to start

**Step 2: Percolation Loop**
For each territory (until 18-32 territories created):
1. Find unassigned cell with lowest shuffle number that's adjacent to existing territories
2. Start "percolating" from that cell:
   - Assign cell to current territory number
   - Mark all 6 neighbors as adjacent candidates
   - Expand to lowest-numbered adjacent unassigned cell
   - Continue until territory reaches target size (~8 cells)
3. Mark neighbors of final territory as candidates for next territory
4. Increment territory number

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
    val seed: Long? = null              // Random seed for reproducible maps
)

// Player state
data class PlayerState(
    var territoryCount: Int,            // Number of territories owned
    var largestConnectedSize: Int,      // Size of largest connected territory group
    var totalArmies: Int                // Total armies across all territories
)
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
```

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

## Implementation Phases

### Phase 1: Map Generation & Rendering
**Goal**: Generate and display a playable map

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

### Phase 2: Army Reinforcement Algorithm
**Goal**: Implement and visualize reinforcement mechanics

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

---

### Phase 3: AI Bot Implementation
**Goal**: Port and test AI decision-making

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

### Phase 4: Bot vs Bot Turn-Based Mode
**Goal**: Two AIs playing a complete game

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

**Document Version**: 2.0
**Last Updated**: 2026-01-02
**Status**: Ready for Implementation
**Scope**: 1v1 turn-based gameplay (Human vs Bot, or Bot vs Bot)
