package com.blockpuzzle.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blockpuzzle.game.R
import com.blockpuzzle.game.ui.theme.GameColors

/** Shared card chrome for both dialogs. */
@Composable
private fun DialogCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(GameColors.panel)
            .border(1.dp, GameColors.panelEdge, RoundedCornerShape(26.dp))
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun GameOverDialog(
    score: Int,
    best: Int,
    isNewBest: Boolean,
    linesCleared: Int,
    piecesPlaced: Int,
    bestCombo: Int,
    onPlayAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Text(
                text = stringResource(R.string.game_over),
                color = GameColors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.game_over_no_moves),
                color = GameColors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = score.toString(),
                color = GameColors.textPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = if (isNewBest) {
                    stringResource(R.string.new_best)
                } else {
                    "${stringResource(R.string.best)}: $best"
                },
                color = if (isNewBest) GameColors.accent else GameColors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(18.dp))

            StatRow(stringResource(R.string.stats_lines), linesCleared.toString())
            StatRow(stringResource(R.string.stats_pieces), piecesPlaced.toString())
            StatRow(stringResource(R.string.stats_best_combo), "x$bestCombo")

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = onPlayAgain,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GameColors.BLOCKS[8],
                    contentColor = GameColors.backgroundBottom,
                ),
            ) {
                Text(
                    text = stringResource(R.string.play_again),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.close),
                    color = GameColors.textSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
    onSoundChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onRestart: () -> Unit,
    onResetBest: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard {
            Text(
                text = stringResource(R.string.settings),
                color = GameColors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            Spacer(Modifier.height(16.dp))

            ToggleRow(
                label = stringResource(R.string.sound),
                checked = soundEnabled,
                onCheckedChange = onSoundChange,
            )
            ToggleRow(
                label = stringResource(R.string.haptics),
                checked = hapticsEnabled,
                onCheckedChange = onHapticsChange,
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GameColors.BLOCKS[2],
                    contentColor = GameColors.textPrimary,
                ),
            ) {
                Text(stringResource(R.string.restart), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onResetBest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GameColors.boardPlate,
                    contentColor = GameColors.textSecondary,
                ),
            ) {
                Text(stringResource(R.string.reset_best), fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.no_ads_note),
                color = GameColors.textSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.close),
                    color = GameColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = GameColors.textSecondary, fontSize = 14.sp)
        Text(value, color = GameColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = GameColors.textPrimary, fontSize = 15.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GameColors.backgroundBottom,
                checkedTrackColor = GameColors.BLOCKS[8],
                uncheckedThumbColor = GameColors.textSecondary,
                uncheckedTrackColor = GameColors.boardPlate,
            ),
        )
    }
}
