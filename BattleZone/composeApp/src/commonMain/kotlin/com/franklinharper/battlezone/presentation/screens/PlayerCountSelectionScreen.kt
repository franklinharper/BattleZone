package com.franklinharper.battlezone.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franklinharper.battlezone.GameMode
import com.franklinharper.battlezone.MAX_PLAYERS
import com.franklinharper.battlezone.MIN_PLAYERS

@Composable
fun PlayerCountSelectionScreen(
    gameMode: GameMode,
    onPlayerCountSelected: (Int) -> Unit,
    onBack: () -> Unit
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
            text = when (gameMode) {
                GameMode.HUMAN_VS_BOT -> "Human vs Bots"
                GameMode.BOT_VS_BOT -> "Bot vs Bot"
            },
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Select Number of Players",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Create buttons for 2-8 players
        for (playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            Button(
                onClick = { onPlayerCountSelected(playerCount) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "$playerCount Players",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Back button
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text("Back", style = MaterialTheme.typography.titleLarge)
        }
    }
}
