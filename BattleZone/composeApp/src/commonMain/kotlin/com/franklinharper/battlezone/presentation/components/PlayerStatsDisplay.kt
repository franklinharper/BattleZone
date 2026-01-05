package com.franklinharper.battlezone.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.franklinharper.battlezone.PlayerState

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
    isCurrentPlayer: Boolean = false
) {
    val backgroundColor = when {
        isCurrentPlayer -> color.copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    val textColor = when {
        isEliminated -> Color.Gray
        else -> MaterialTheme.colorScheme.onBackground
    }

    val labelColor = when {
        isEliminated -> Color.Gray
        else -> color
    }

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .background(backgroundColor)
            .then(
                if (isCurrentPlayer) {
                    Modifier.border(2.dp, color)
                } else {
                    Modifier
                }
            )
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
                color = if (isEliminated) Color.Gray else MaterialTheme.colorScheme.error
            )
        }
    }
}
