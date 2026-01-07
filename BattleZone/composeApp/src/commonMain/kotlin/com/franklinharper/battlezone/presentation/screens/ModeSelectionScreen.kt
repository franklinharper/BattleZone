package com.franklinharper.battlezone.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franklinharper.battlezone.GameMode
import com.franklinharper.battlezone.GameColors

/**
 * Screen for selecting game mode
 */
@Composable
fun ModeSelectionScreen(
    onModeSelected: (GameMode) -> Unit,
    onLoadRecording: () -> Unit,
    statusMessage: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "BattleZone",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        Button(
            onClick = { onModeSelected(GameMode.HUMAN_VS_BOT) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Human vs Bot", style = MaterialTheme.typography.headlineSmall)
        }

        Button(
            onClick = { onModeSelected(GameMode.BOT_VS_BOT) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Bot vs Bot", style = MaterialTheme.typography.headlineSmall)
        }

        Button(
            onClick = onLoadRecording,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Load Recording", style = MaterialTheme.typography.headlineSmall)
        }

        statusMessage?.let { message ->
            Text(
                text = message,
                color = GameColors.UiTextError,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
