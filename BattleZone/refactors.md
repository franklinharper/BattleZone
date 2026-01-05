  5. Implement proper state management:
    - Replace manual recompositionTrigger++ with StateFlow/SharedFlow
    - Use ViewModel pattern with proper lifecycle
    - Add Command pattern for undoable moves
    - Implement state machine for phase transitions:
    sealed class GameEvent {
      data class AttackExecuted(val result: CombatResult) : GameEvent()
      object TurnSkipped : GameEvent()
      object ReinforcementPhaseStarted : GameEvent()
  }
  6. Separate rendering from business logic:
    - Extract all map rendering code to dedicated MapRenderer class
    - Move hex calculations to HexGeometry utility
    - Create TerritoryDrawer for territory-specific rendering
    - Keep App.kt focused on composition only
  7. Add architecture layers:
  domain/
    usecase/
      ExecuteAttackUseCase.kt
      GenerateMapUseCase.kt
      CalculateBotMoveUseCase.kt
    repository/
      GameRepository.kt
  presentation/
    viewmodel/
      GameViewModel.kt
  8. Fix fragile coroutine orchestration - App.kt lines 115-137 uses hard-coded delays for bot moves:
    - Implement proper state machine
    - Use channels or flows for turn coordination
    - Remove timing dependencies
