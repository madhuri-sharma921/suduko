package com.livelens.games.sudoku

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Small read-only overlay drawn on top of (or beside) the camera preview,
 * showing the last board Gemini Vision read back — lets the player visually
 * confirm what the agent is "seeing" before asking it a question out loud.
 */
@Composable
fun SudokuOverlay(
    board: List<List<Int>>?,
    solved: Boolean,
    modifier: Modifier = Modifier,
) {
    if (board == null) {
        Box(modifier = modifier.padding(12.dp)) {
            Text("Point the camera at your 4×4 grid…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(Color(0xCC1B1B1B))
            .padding(8.dp),
    ) {
        Box(modifier = Modifier.size(160.dp)) {
            LazyVerticalGrid(columns = GridCells.Fixed(4)) {
                items(16) { index ->
                    val r = index / 4
                    val c = index % 4
                    val value = board[r][c]
                    val thickBottom = r == 1
                    val thickRight = c == 1
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .border(
                                width = if (thickRight || thickBottom) 1.5.dp else 0.5.dp,
                                color = Color.White.copy(alpha = 0.6f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (value == 0) "" else value.toString(),
                            color = if (solved) Color(0xFF4CAF50) else Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
