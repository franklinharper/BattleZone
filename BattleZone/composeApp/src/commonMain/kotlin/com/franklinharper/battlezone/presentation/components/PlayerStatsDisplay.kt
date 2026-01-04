package com.franklinharper.battlezone.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.franklinharper.battlezone.PlayerState

/**
 * Display statistics for a single player
 */
@Composable
fun PlayerStatsDisplay(
    playerIndex: Int,
    playerState: PlayerState,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
        Text(
            "Territories: ${playerState.territoryCount}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Armies: ${playerState.totalArmies}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Connected: ${playerState.largestConnectedSize}",
            style = MaterialTheme.typography.bodySmall
        )
        if (playerState.reserveArmies > 0) {
            Text(
                "Reserve: ${playerState.reserveArmies}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
