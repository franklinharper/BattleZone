package com.franklinharper.battlezone.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franklinharper.battlezone.DEFAULT_REALTIME_ROUND_TIMER_SECONDS
import com.franklinharper.battlezone.GameMode
import com.franklinharper.battlezone.MAX_PLAYERS
import com.franklinharper.battlezone.MIN_PLAYERS
import com.franklinharper.battlezone.REALTIME_ROUND_TIMER_MAX_SECONDS
import com.franklinharper.battlezone.REALTIME_ROUND_TIMER_MIN_SECONDS
import com.franklinharper.battlezone.TurnMode

@Composable
fun PlayerCountSelectionScreen(
    gameMode: GameMode,
    turnMode: TurnMode,
    roundTimerSeconds: Int = DEFAULT_REALTIME_ROUND_TIMER_SECONDS,
    onTurnModeChanged: (TurnMode) -> Unit,
    onRoundTimerSecondsChanged: (Int) -> Unit,
    botDelayDeltaText: String,
    onBotDelayDeltaTextChanged: (String) -> Unit,
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

        Text(
            text = "Turn Mode",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeOptionRow(
                selected = turnMode == TurnMode.REAL_TIME,
                label = "Real-time",
                onSelect = { onTurnModeChanged(TurnMode.REAL_TIME) }
            )
            ModeOptionRow(
                selected = turnMode == TurnMode.TURN_BASED,
                label = "Turn-by-turn",
                onSelect = { onTurnModeChanged(TurnMode.TURN_BASED) }
            )
        }

        Text(
            text = "Round Timer (seconds)",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    val updated = (roundTimerSeconds - 1).coerceAtLeast(REALTIME_ROUND_TIMER_MIN_SECONDS)
                    onRoundTimerSecondsChanged(updated)
                },
                enabled = roundTimerSeconds > REALTIME_ROUND_TIMER_MIN_SECONDS
            ) {
                Text("-")
            }

            Text(
                text = roundTimerSeconds.toString(),
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedButton(
                onClick = {
                    val updated = (roundTimerSeconds + 1).coerceAtMost(REALTIME_ROUND_TIMER_MAX_SECONDS)
                    onRoundTimerSecondsChanged(updated)
                },
                enabled = roundTimerSeconds < REALTIME_ROUND_TIMER_MAX_SECONDS
            ) {
                Text("+")
            }
        }

        Text(
            text = "Bot Delay Delta (seconds)",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        TextField(
            value = botDelayDeltaText,
            onValueChange = onBotDelayDeltaTextChanged,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
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

@Composable
private fun ModeOptionRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, style = MaterialTheme.typography.titleLarge)
    }
}
