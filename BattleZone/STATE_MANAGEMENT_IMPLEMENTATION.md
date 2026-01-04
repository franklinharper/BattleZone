# State Management Implementation

## Summary

This document describes the state management improvements implemented for the BattleZone project.

## Completed

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

### 3. StateFlow Migration (`GameController.kt`)
Migrated GameController from manual state management to StateFlow:
- Removed `onStateChange` callback parameter
- Replaced `var _gameState: GameState` with `MutableStateFlow<GameState>`
- Replaced `var _uiState: GameUiState` with `MutableStateFlow<GameUiState>`
- Added `SharedFlow<GameEvent>` for one-time events
- Integrated `CommandHistory` for undo/redo support
- Removed all `notifyStateChanged()` calls - StateFlow automatically notifies observers

**Key changes**:
```kotlin
// Before
class GameController(
    initialMap: GameMap,
    private val onStateChange: () -> Unit = {}
) {
    private var _gameState: GameState = createInitialGameState(initialMap)
    private var _uiState: GameUiState = GameUiState()

    val gameState: GameState get() = _gameState
    val uiState: GameUiState get() = _uiState
}

// After
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

**Location**: `shared/src/commonMain/kotlin/com/franklinharper/battlezone/GameController.kt`

### 4. ViewModel Pattern (`GameViewModel.kt`)
Created GameViewModel for lifecycle-aware state management:
- Wraps GameController and exposes its StateFlows
- Provides clean API for UI components
- Delegates game logic to GameController

**Location**: `composeApp/src/commonMain/kotlin/com/franklinharper/battlezone/GameViewModel.kt`

### 5. UI Integration (`App.kt`, `GameScreen.kt`)
Updated UI components to use StateFlow and ViewModel:
- Removed `recompositionTrigger` manual recomposition hack
- Use `collectAsState()` to observe StateFlows
- App.kt creates GameViewModel and passes to GameScreen
- GameScreen uses ViewModel methods for all game actions

**Key changes**:
```kotlin
// Before
var recompositionTrigger by remember { mutableStateOf(0) }
gameController = GameController(
    initialMap = initialMap,
    onStateChange = { recompositionTrigger++ }
)
key(recompositionTrigger) {
    // UI content
}

// After
val viewModel = GameViewModel(initialMap = initialMap, gameMode = selectedMode)
val gameState by viewModel.gameState.collectAsState()
val uiState by viewModel.uiState.collectAsState()
// UI content - automatically recomposes when state changes
```

## Benefits of This Architecture

1. **Reactive UI** - No manual recomposition triggers needed
2. **Undo/Redo Ready** - Full command history for reverting actions
3. **Event System** - Clean separation between state and one-time events
4. **Type Safety** - Sealed classes prevent invalid states
5. **Testability** - StateFlow and Commands are easily testable
6. **Lifecycle Aware** - ViewModel handles lifecycle correctly

## Dependencies Added

Added `kotlinx-coroutines-core` to shared module for StateFlow support:
- `gradle/libs.versions.toml`: Added `kotlinx-coroutines-core` library
- `shared/build.gradle.kts`: Added `implementation(libs.kotlinx.coroutines.core)`

## Files Modified

- `shared/src/commonMain/kotlin/com/franklinharper/battlezone/GameEvent.kt` - New file
- `shared/src/commonMain/kotlin/com/franklinharper/battlezone/GameCommand.kt` - New file
- `shared/src/commonMain/kotlin/com/franklinharper/battlezone/GameController.kt` - StateFlow migration
- `shared/build.gradle.kts` - Added coroutines dependency
- `gradle/libs.versions.toml` - Added coroutines-core library
- `composeApp/src/commonMain/kotlin/com/franklinharper/battlezone/GameViewModel.kt` - New file
- `composeApp/src/commonMain/kotlin/com/franklinharper/battlezone/App.kt` - Use ViewModel and collectAsState
- `composeApp/src/commonMain/kotlin/com/franklinharper/battlezone/presentation/screens/GameScreen.kt` - Use ViewModel

## Future Enhancements

1. **Add undo/redo UI controls** - Buttons to undo/redo game actions
2. **Event handling** - Use `events` SharedFlow for one-time UI events (victory screens, etc.)
3. **Integration tests** - Add tests for command pattern and state transitions
