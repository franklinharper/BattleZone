# State Management Implementation

## Summary

This document describes the state management improvements partially implemented for the BattleZone project.

## ✅ Completed

### 1. GameEvent Sealed Class (`GameEvent.kt`)
Created a comprehensive event system for the state machine with the following events:
- `AttackExecuted` - Combat results
- `TurnSkipped` - Player skipped turn
- `ReinforcementPhaseStarted` / `ReinforcementPhaseCompleted`
- `GameStarted` / `GameEnded`
- `BotDecisionMade` - Bot AI decisions
- `TerritorySelected` / `SelectionCancelled` - Human player interactions

**Location**: `shared/src/commonMain/kotlin/com/franklinharper/battlezone/GameEvent.kt`

### 2. Command Pattern (`GameCommand.kt`)
Implemented full undo/redo system with:
- `GameCommand` interface for executable/undoable actions
- `AttackCommand` - Undoable attacks with state restoration
- `SkipTurnCommand` - Turn skipping
- `DistributeReinforcementsCommand` - Reversible reinforcement distribution
- `CommandHistory` manager with full undo/redo stack

**Features**:
- Full undo/redo support
- Command history tracking
- State restoration
- Descriptive command names for debugging

**Location**: `shared/src/commonMain/kotlin/com/franklinharper/battlezone/GameCommand.kt`

## 🚧 Needs Completion

### 3. StateFlow Migration

The GameController needs to be migrated from manual state management to StateFlow. Here's the implementation approach:

#### Current State (Manual)
```kotlin
class GameController(
    initialMap: GameMap,
    private val onStateChange: () -> Unit = {}
) {
    private var _gameState: GameState = createInitialGameState(initialMap)
    private var _uiState: GameUiState = GameUiState()

    val gameState: GameState get() = _gameState
    val uiState: GameUiState get() = _uiState

    private fun notifyStateChanged() {
        onStateChange()
    }
}
```

#### Target State (StateFlow)
```kotlin
class GameController(
    initialMap: GameMap
) {
    private val _gameState = MutableStateFlow(createInitialGameState(initialMap))
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>()
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private val commandHistory = CommandHistory()
}
```

#### Migration Steps

1. **Update GameController constructor**:
   - Remove `onStateChange` callback parameter
   - Add StateFlow declarations

2. **Update state access**:
   - Replace `_gameState.property` with `_gameState.value.property`
   - Replace `_uiState.property` with `_uiState.value.property`

3. **Update state mutations**:
   - Replace `_gameState = newState` with `_gameState.value = newState`
   - Replace `_uiState = newState` with `_uiState.value = newState`

4. **Remove manual notifications**:
   - Delete all `notifyStateChanged()` calls
   - StateFlow automatically notifies observers

5. **Add event emission**:
   ```kotlin
   private suspend fun emitEvent(event: GameEvent) {
       _events.emit(event)
   }
   ```

6. **Update App.kt**:
   ```kotlin
   @Composable
   fun App() {
       val controller = remember { GameController(MapGenerator.generate()) }

       // Collect state as Compose State
       val gameState by controller.gameState.collectAsState()
       val uiState by controller.uiState.collectAsState()

       // Collect events
       LaunchedEffect(Unit) {
           controller.events.collect { event ->
               // Handle one-time events
               when (event) {
                   is GameEvent.GameEnded -> { /* show victory screen */ }
                   // ...
               }
           }
       }

       GameScreen(gameState, uiState, controller)
   }
   ```

### 4. ViewModel Pattern

Create a proper ViewModel for lifecycle management:

```kotlin
class GameViewModel(initialMap: GameMap) : ViewModel() {
    private val controller = GameController(initialMap)

    val gameState = controller.gameState
    val uiState = controller.uiState
    val events = controller.events

    // Expose commands
    fun requestBotDecision() = controller.requestBotDecision()
    fun executeBotDecision() = controller.executeBotDecision()
    fun selectTerritory(id: Int) = controller.selectTerritory(id)
    fun skipTurn() = controller.skipTurn()

    // Undo/Redo
    fun undo() = controller.undo()
    fun redo() = controller.redo()
    fun canUndo() = controller.canUndo()
    fun canRedo() = controller.canRedo()
}
```

## Benefits of This Architecture

1. **Reactive UI** - No manual recomposition triggers needed
2. **Undo/Redo** - Full command history for reverting actions
3. **Event System** - Clean separation between state and events
4. **Type Safety** - Sealed classes prevent invalid states
5. **Testability** - StateFlow and Commands are easily testable
6. **Lifecycle Aware** - ViewModel handles lifecycle correctly

## Testing the Changes

After completing the migration:

```kotlin
@Test
fun testCommandUndo() {
    val map = MapGenerator.generate()
    val controller = GameController(map)

    // Execute attack
    val attackCommand = AttackCommand(...)
    controller.executeCommand(attackCommand)

    // Verify state changed
    assertTrue(/* check state */)

    // Undo
    controller.undo()

    // Verify state restored
    assertTrue(/* check original state */)
}
```

## Next Steps

1. Complete StateFlow migration in GameController
2. Update App.kt to use `collectAsState()`
3. Create GameViewModel
4. Add undo/redo UI buttons
5. Test all state transitions
6. Add integration tests for command pattern

## Files to Modify

- ✅ `shared/src/commonMain/kotlin/com/franklinharper/battlezone/GameEvent.kt` - Complete
- ✅ `shared/src/commonMain/kotlin/com/franklinharper/battlezone/GameCommand.kt` - Complete
- ⏳ `shared/src/commonMain/kotlin/com/franklinharper/battlezone/GameController.kt` - Needs StateFlow migration
- ⏳ `composeApp/src/commonMain/kotlin/com/franklinharper/battlezone/App.kt` - Needs `collectAsState()`
- 📝 `composeApp/src/commonMain/kotlin/com/franklinharper/battlezone/GameViewModel.kt` - To be created

## Resources

- [Kotlin Flow Documentation](https://kotlinlang.org/docs/flow.html)
- [StateFlow and SharedFlow](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html#shared-flow)
- [Command Pattern](https://refactoring.guru/design-patterns/command)
