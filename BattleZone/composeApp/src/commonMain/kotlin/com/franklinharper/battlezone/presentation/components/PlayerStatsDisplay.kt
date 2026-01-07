package com.franklinharper.battlezone.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.franklinharper.battlezone.CombatResult
import com.franklinharper.battlezone.GameColors
import com.franklinharper.battlezone.GameMode
import com.franklinharper.battlezone.PlayerState
import com.franklinharper.battlezone.debugLog
import com.franklinharper.battlezone.presentation.playerLabel

/**
 * Display statistics for a single player
 */
@Composable
fun PlayerStatsDisplay(
    playerIndex: Int,
    playerState: PlayerState,
    label: String,
    color: Color,
    isEliminated: Boolean = false,
    isCurrentPlayer: Boolean = false,
    combatResult: CombatResult? = null,
    hasSkipped: Boolean = false,
    gameMode: GameMode
) {
    val backgroundColor = when {
        isEliminated -> GameColors.PanelEliminatedBackground
        else -> color.copy(alpha = 0.2f)
    }

    val textColor = when {
        isEliminated -> GameColors.UiTextMuted
        else -> GameColors.UiTextPrimary
    }

    val labelColor = when {
        isEliminated -> GameColors.UiTextMuted
        else -> GameColors.UiTextPrimary
    }

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .border(2.dp, color)
            .padding(4.dp)
    ) {
        Text(
            text = if (isEliminated) "$label [ELIMINATED]" else label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor
        )
        Text(
            "Territories: ${playerState.territoryCount}",
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
        Text(
            "Armies: ${playerState.totalArmies}",
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
        Text(
            "Connected: ${playerState.largestConnectedSize}",
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
        if (playerState.reserveArmies > 0) {
            Text(
                "Reserve: ${playerState.reserveArmies}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isEliminated) GameColors.UiTextMuted else MaterialTheme.colorScheme.error
            )
        }

        // Combat result or skip status display
        debugLog { "DEBUG PlayerStatsDisplay: Player $playerIndex combat result: ${if (combatResult != null) "present" else "null"}" }
        if (hasSkipped) {
            Text(
                "skipped",
                style = MaterialTheme.typography.bodySmall,
                color = GameColors.UiTextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            combatResult?.let { combat ->
                val defenderLabel = playerLabel(combat.defenderPlayerId, gameMode)

                val resultEmoji = if (combat.attackerWins) "✅" else "❌"
                val resultText = if (combat.attackerWins) "win" else "fail"

                Text(
                    "$resultEmoji attack $defenderLabel $resultText",
                    style = MaterialTheme.typography.bodySmall,
                    color = GameColors.UiTextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "attacker: [${combat.attackerRoll.joinToString(", ")}] = ${combat.attackerTotal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
                Text(
                    "defender: [${combat.defenderRoll.joinToString(", ")}] = ${combat.defenderTotal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
            }
        }
    }
}
