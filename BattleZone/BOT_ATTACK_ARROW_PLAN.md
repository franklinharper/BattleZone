# Bot Attack Arrow Implementation Plan

## Overview

Replace the current animation system with a simple static arrow showing the last bot attack.

## Requirements

1. **Remove all animations**: Delete pulsing outlines, flash overlays, AnimationCoordinator
2. **Show single arrow**: Display only the most recent bot attack
3. **Arrow only for bots**: Human attacks don't create arrows, but erase previous bot arrow
4. **Arrow colors**:
   - Red: Attack succeeded
   - Blue: Attack failed
   - White outline: Always visible regardless of background
5. **Arrow lifecycle**:
   - Appears AFTER bot attack resolves
   - Erased when human attacks
   - Erased when bot skips turn (no arrow shown)
6. **Arrow positioning**:
   - Starts near border on attacking territory side (~20px before border)
   - Crosses the border between territories
   - Ends shortly after crossing into defending territory (~25px after border)

## Visual Design

```
┌─────────────────────┐         ┌─────────────────────┐
│                     │         │                     │
│  Attacking          │  ═══>   │   Defending         │
│  Territory          │  Arrow  │   Territory         │
│                     │         │                     │
└─────────────────────┘         └─────────────────────┘
                      ↑         ↑
                      |         |
                   Start      End
              (20px before) (25px after)
                   border      border

Arrow emphasis on border crossing, not territory centers
```

## Architecture

### State Management

```kotlin
// In GameController.kt or GameUiState
data class BotAttackArrow(
    val fromTerritoryId: Int,
    val toTerritoryId: Int,
    val attackSucceeded: Boolean
)

// Add to GameUiState
data class GameUiState(
    // ... existing fields
    val botAttackArrow: BotAttackArrow? = null  // null = no arrow shown
)
```

### Arrow Rendering

```kotlin
// New file: ArrowRenderer.kt
@Composable
fun BotAttackArrow(
    arrow: BotAttackArrow,
    gameMap: GameMap,
    cellWidth: Float,
    cellHeight: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val fromTerritory = gameMap.territories[arrow.fromTerritoryId]
        val toTerritory = gameMap.territories[arrow.toTerritoryId]

        // Find the border crossing point between territories
        val borderPoint = findBorderCrossingPoint(
            fromTerritory,
            toTerritory,
            gameMap,
            cellWidth,
            cellHeight
        ) ?: return@Canvas

        // Arrow starts slightly before border (on attacking side)
        // Arrow ends slightly after border (on defending side)
        val startOffset = 20f  // Distance before border
        val endOffset = 25f    // Distance after border

        val start = Offset(
            borderPoint.x - borderPoint.normalX * startOffset,
            borderPoint.y - borderPoint.normalY * startOffset
        )

        val end = Offset(
            borderPoint.x + borderPoint.normalX * endOffset,
            borderPoint.y + borderPoint.normalY * endOffset
        )

        // Draw arrow crossing the border
        drawArrowWithOutline(
            start = start,
            end = end,
            color = if (arrow.attackSucceeded) Color.Red else Color.Blue,
            outlineColor = Color.White,
            strokeWidth = 4f,
            outlineWidth = 1f
        )
    }
}

data class BorderPoint(
    val x: Float,        // Border crossing point X
    val y: Float,        // Border crossing point Y
    val normalX: Float,  // Normal vector X (points from attacker to defender)
    val normalY: Float   // Normal vector Y
)

/**
 * Find the midpoint of the shared border between two adjacent territories
 * and calculate the normal vector pointing from attacker to defender
 */
private fun findBorderCrossingPoint(
    fromTerritory: Territory,
    toTerritory: Territory,
    gameMap: GameMap,
    cellWidth: Float,
    cellHeight: Float
): BorderPoint? {
    // Find all border edges between the two territories
    val borderEdges = mutableListOf<Pair<Offset, Offset>>()

    for (cellIdx in gameMap.cells.indices) {
        if (gameMap.cells[cellIdx] == fromTerritory.id + 1) {
            val (cellX, cellY) = HexGrid.getCellPosition(cellIdx, cellWidth, cellHeight)
            val neighbors = gameMap.cellNeighbors[cellIdx].directions

            for (dir in neighbors.indices) {
                val neighborCell = neighbors[dir]
                if (neighborCell != -1 && gameMap.cells[neighborCell] == toTerritory.id + 1) {
                    // Found a shared edge
                    val edgePoints = HexGeometry.getHexEdgePoints(cellX, cellY, cellWidth, cellHeight, dir)
                    if (edgePoints != null) {
                        val (start, end) = edgePoints
                        borderEdges.add(
                            Offset(start.first, start.second) to Offset(end.first, end.second)
                        )
                    }
                }
            }
        }
    }

    if (borderEdges.isEmpty()) return null

    // Calculate average midpoint of all border edges
    var sumX = 0f
    var sumY = 0f
    borderEdges.forEach { (start, end) ->
        sumX += (start.x + end.x) / 2
        sumY += (start.y + end.y) / 2
    }
    val borderX = sumX / borderEdges.size
    val borderY = sumY / borderEdges.size

    // Calculate normal vector (from attacker center to defender center)
    val fromCenter = getTerritoryCenter(fromTerritory, gameMap, cellWidth, cellHeight)
    val toCenter = getTerritoryCenter(toTerritory, gameMap, cellWidth, cellHeight)

    val dx = toCenter.first - fromCenter.first
    val dy = toCenter.second - fromCenter.second
    val length = sqrt(dx * dx + dy * dy)

    val normalX = dx / length
    val normalY = dy / length

    return BorderPoint(borderX, borderY, normalX, normalY)
}

private fun DrawScope.drawArrowWithOutline(
    start: Offset,
    end: Offset,
    color: Color,
    outlineColor: Color,
    strokeWidth: Float,
    outlineWidth: Float
) {
    // Calculate arrow direction and arrowhead
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    val unitX = dx / length
    val unitY = dy / length

    // Arrowhead size
    val headLength = 15f
    val headWidth = 10f

    // Arrowhead points
    val tipX = end.x
    val tipY = end.y
    val baseX = end.x - unitX * headLength
    val baseY = end.y - unitY * headLength

    // Perpendicular for arrowhead width
    val perpX = -unitY * headWidth
    val perpY = unitX * headWidth

    val arrowPoint1 = Offset(baseX + perpX, baseY + perpY)
    val arrowPoint2 = Offset(baseX - perpX, baseY - perpY)

    // Draw outline (white border)
    drawLine(
        color = outlineColor,
        start = start,
        end = end,
        strokeWidth = strokeWidth + outlineWidth * 2
    )

    // Draw arrowhead outline
    val outlinePath = Path().apply {
        moveTo(tipX, tipY)
        lineTo(arrowPoint1.x, arrowPoint1.y)
        lineTo(arrowPoint2.x, arrowPoint2.y)
        close()
    }
    drawPath(outlinePath, color = outlineColor)

    // Draw main arrow line
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth
    )

    // Draw arrowhead
    val arrowPath = Path().apply {
        moveTo(tipX, tipY)
        lineTo(arrowPoint1.x, arrowPoint1.y)
        lineTo(arrowPoint2.x, arrowPoint2.y)
        close()
    }
    drawPath(arrowPath, color = color)
}

private fun getTerritoryCenter(
    territory: Territory,
    gameMap: GameMap,
    cellWidth: Float,
    cellHeight: Float
): Pair<Float, Float> {
    val centerCellIdx = territory.centerPos
    val (cellX, cellY) = HexGrid.getCellPosition(centerCellIdx, cellWidth, cellHeight)
    return Pair(cellX + cellWidth / 2, cellY + cellHeight / 2)
}
```

## Implementation Steps

### Phase 1: Add Arrow State (30 min)

1. Add `BotAttackArrow` data class to GameController.kt
2. Add `botAttackArrow` field to `GameUiState`
3. Update `executeAttack()` to set arrow only for bot attacks
4. Update `skipTurn()` to clear arrow when bot skips
5. Clear arrow when human attacks

```kotlin
// In GameController.kt

private fun executeAttack(fromTerritoryId: Int, toTerritoryId: Int) {
    // ... existing attack logic ...

    // Determine if this is a bot attack
    val isBotAttack = isCurrentPlayerBot()

    // ... execute attack, update game state ...

    if (isBotAttack) {
        // Show arrow for bot attack
        _uiState.value = _uiState.value.copy(
            botAttackArrow = BotAttackArrow(
                fromTerritoryId = fromTerritoryId,
                toTerritoryId = toTerritoryId,
                attackSucceeded = attackerWins
            )
        )
    } else {
        // Human attack - clear any previous bot arrow
        _uiState.value = _uiState.value.copy(botAttackArrow = null)
    }
}

fun skipTurn() {
    // ... existing skip logic ...

    if (isCurrentPlayerBot()) {
        // Bot skipped - clear arrow
        _uiState.value = _uiState.value.copy(botAttackArrow = null)
    }
}
```

### Phase 2: Create Arrow Renderer (1 hour)

1. Create `ArrowRenderer.kt` in presentation/components
2. Implement `BotAttackArrow` composable
3. Implement arrow drawing with outline
4. Calculate territory centers from centerPos

### Phase 3: Integrate into GameScreen (15 min)

1. Update GameScreen to show arrow overlay
2. Remove AnimationOverlay references
3. Pass arrow state from uiState

```kotlin
// In GameScreen.kt

Box {
    MapRenderer(/* ... */)

    // Show bot attack arrow if present
    uiState.botAttackArrow?.let { arrow ->
        BotAttackArrow(
            arrow = arrow,
            gameMap = gameState.map,
            cellWidth = renderParams.cellWidth,
            cellHeight = renderParams.cellHeight,
            modifier = Modifier.matchParentSize()
        )
    }
}
```

### Phase 4: Remove Animation System (30 min)

1. Delete `Animation.kt`
2. Delete `AnimationCoordinator.kt`
3. Delete `AnimationOverlay.kt`
4. Remove from GameViewModel:
   - Remove `animationCoordinator` field
   - Remove `viewModelScope`
   - Remove `dispose()` method (or simplify)
5. Remove from GameController:
   - Remove `onAttackAnimation` callback parameter
   - Remove animation trigger code
6. Update App.kt:
   - Remove TurnCoordinator usage (no longer needed)
   - Simplify bot turn handling

### Phase 5: Testing (30 min)

1. Test bot vs bot mode - verify arrow appears after each bot attack
2. Test human vs bot mode:
   - Bot attacks → arrow appears
   - Human attacks → arrow disappears
   - Bot skips → arrow disappears
3. Test arrow colors (red vs blue)
4. Verify arrow visibility on all backgrounds

## Benefits

✅ **Simpler**: No complex animation timing or coordination
✅ **Clearer**: Static arrow shows exactly what the bot did
✅ **Performant**: No 60 FPS updates or concurrent animation management
✅ **Smaller**: Removes ~1500 lines of animation code
✅ **Maintainable**: Fewer moving parts, easier to debug

## Trade-offs

⚠️ **Less flashy**: No pulsing outlines or flash effects
⚠️ **Less feedback**: No visual indication of attack in progress
✅ **But**: Attack results still clear from arrow color and state changes

## Total Estimated Time

**2.5 hours** total:
- Phase 1: 30 min (state management)
- Phase 2: 1 hour (arrow rendering)
- Phase 3: 15 min (integration)
- Phase 4: 30 min (cleanup)
- Phase 5: 30 min (testing)

## Files to Modify

**Create:**
- `composeApp/src/.../presentation/components/ArrowRenderer.kt`

**Modify:**
- `shared/src/.../GameController.kt` (add arrow state)
- `composeApp/src/.../presentation/screens/GameScreen.kt` (show arrow)
- `composeApp/src/.../GameViewModel.kt` (remove animation coordinator)
- `composeApp/src/.../App.kt` (simplify turn handling)

**Delete:**
- `shared/src/.../Animation.kt`
- `composeApp/src/.../AnimationCoordinator.kt`
- `composeApp/src/.../presentation/components/AnimationOverlay.kt`
- `BattleZone/ATTACK_ANIMATIONS_PLAN.md` (obsolete)
