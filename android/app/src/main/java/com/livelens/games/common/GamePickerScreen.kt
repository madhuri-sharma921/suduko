package com.livelens.games.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown before "Start Live Session" — lets the user pick which board game
 * this run will be. Selecting a mode just sets state in GameSessionViewModel;
 * your existing session-bootstrap + Agora join flow is unaffected and fires
 * the same way regardless of which mode is selected.
 */
@Composable
fun GamePickerScreen(
    onModeSelected: (GameMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Live Lens", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(
            text = "Choose a board to play against the live voice agent",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        GameMode.entries.forEach { mode ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = mode.displayName, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(
                        text = mode.instructionHint,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Button(
                        onClick = { onModeSelected(mode) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Play ${mode.displayName}")
                    }
                }
            }
        }
    }
}
