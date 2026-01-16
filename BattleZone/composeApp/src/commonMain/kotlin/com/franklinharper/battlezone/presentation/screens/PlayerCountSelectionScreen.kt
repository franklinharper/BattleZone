package com.franklinharper.battlezone.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.franklinharper.battlezone.AttackArrowRenderingOption
import com.franklinharper.battlezone.GameMode
import com.franklinharper.battlezone.MAX_PLAYERS
import com.franklinharper.battlezone.MIN_PLAYERS
import com.franklinharper.battlezone.REALTIME_ROUND_TIMER_MAX_SECONDS
import com.franklinharper.battlezone.REALTIME_ROUND_TIMER_MIN_SECONDS
import com.franklinharper.battlezone.UiConstants
import com.franklinharper.battlezone.TurnMode

@Composable
fun PlayerCountSelectionScreen(
    gameMode: GameMode,
    turnMode: TurnMode,
    roundTimerSeconds: Int = DEFAULT_REALTIME_ROUND_TIMER_SECONDS,
    onTurnModeChanged: (TurnMode) -> Unit,
    onRoundTimerSecondsChanged: (Int) -> Unit,
    attackArrowRenderingOption: AttackArrowRenderingOption,
    onAttackArrowRenderingOptionChanged: (AttackArrowRenderingOption) -> Unit,
    botDelayBaseText: String,
    onBotDelayBaseTextChanged: (String) -> Unit,
    botDelayDeltaText: String,
    onBotDelayDeltaTextChanged: (String) -> Unit,
    selectedPlayerCount: Int?,
    onPlayerCountChanged: (Int) -> Unit,
    onStartGame: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Back arrow in upper left corner
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        // Centered content
        Column(
            modifier = Modifier
                .fillMaxSize()
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Turn Mode")
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Round Timer (seconds)")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
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

                Button(
                    onClick = {
                        val updated = (roundTimerSeconds + 1).coerceAtMost(REALTIME_ROUND_TIMER_MAX_SECONDS)
                        onRoundTimerSecondsChanged(updated)
                    },
                    enabled = roundTimerSeconds < REALTIME_ROUND_TIMER_MAX_SECONDS
                ) {
                    Text("+")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bot Delay")
            TextField(
                value = botDelayBaseText,
                onValueChange = onBotDelayBaseTextChanged,
                singleLine = true,
                modifier = Modifier.width(UiConstants.BOT_DELAY_DELTA_FIELD_WIDTH)
            )
            Text("Delta")
            TextField(
                value = botDelayDeltaText,
                onValueChange = onBotDelayDeltaTextChanged,
                singleLine = true,
                modifier = Modifier.width(UiConstants.BOT_DELAY_DELTA_FIELD_WIDTH)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Attack Arrows")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeOptionRow(
                    selected = attackArrowRenderingOption ==
                        AttackArrowRenderingOption.ATTACKER_COLOR_SHAFT_RESULT_HEAD,
                    label = "Attacker color shaft + red/green head",
                    onSelect = {
                        onAttackArrowRenderingOptionChanged(
                            AttackArrowRenderingOption.ATTACKER_COLOR_SHAFT_RESULT_HEAD
                        )
                    }
                )
                ModeOptionRow(
                    selected = attackArrowRenderingOption ==
                        AttackArrowRenderingOption.MIDPOINT_BADGE_CURRENT_COLORS,
                    label = "Midpoint badge + current arrow colors",
                    onSelect = {
                        onAttackArrowRenderingOptionChanged(
                            AttackArrowRenderingOption.MIDPOINT_BADGE_CURRENT_COLORS
                        )
                    }
                )
            }
        }

        // Player count selection row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Players")
            for (playerCount in MIN_PLAYERS..MAX_PLAYERS) {
                val isSelected = selectedPlayerCount == playerCount
                if (isSelected) {
                    Button(
                        onClick = { onPlayerCountChanged(playerCount) }
                    ) {
                        Text(
                            text = playerCount.toString(),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { onPlayerCountChanged(playerCount) }
                    ) {
                        Text(
                            text = playerCount.toString(),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        // Start button
        Button(
            onClick = onStartGame,
            enabled = selectedPlayerCount != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Start Game", style = MaterialTheme.typography.titleLarge)
        }
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, style = MaterialTheme.typography.titleLarge)
    }
}
