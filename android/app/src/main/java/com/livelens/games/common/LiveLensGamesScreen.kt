package com.livelens.games.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.livelens.games.dotsboxes.DotsAndBoxesOverlay
import com.livelens.games.sudoku.SudokuOverlay

/**
 * Top-level live-session screen. It:
 *   1. Shows GamePickerScreen first if no mode chosen yet (Sudoku or Dots & Boxes)
 *   2. Once chosen, renders your camera preview / Agora UI as
 *      `cameraPreviewContent` (passed in — this file doesn't touch Agora)
 *   3. Layers the correct game overlay on top based on the active mode
 */
@Composable
fun LiveLensGamesScreen(
    viewModel: GameSessionViewModel,
    cameraPreviewContent: @Composable (mode: GameMode) -> Unit,
    onCaptureFrameForConfirm: (onCaptured: (ByteArray) -> Unit) -> Unit,
) {
    val mode by viewModel.mode.collectAsState()
    var modeChosen by remember { mutableStateOf(false) }

    if (!modeChosen) {
        GamePickerScreen(onModeSelected = {
            viewModel.selectMode(it)
            modeChosen = true
        })
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        cameraPreviewContent(mode)

        when (mode) {
            GameMode.SUDOKU -> {
                val board by viewModel.sudokuBoard.collectAsState()
                val solved by viewModel.sudokuSolved.collectAsState()
                Box(modifier = Modifier.align(Alignment.TopStart)) {
                    SudokuOverlay(board = board, solved = solved)
                }
            }
            GameMode.DOTS_AND_BOXES -> {
                val context by viewModel.dotsBoxesContext.collectAsState()
                val gameOver by viewModel.dotsBoxesGameOver.collectAsState()
                val status by viewModel.statusMessage.collectAsState()
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    DotsAndBoxesOverlay(
                        stateContext = context,
                        gameOver = gameOver,
                        statusMessage = status,
                        onConfirmMove = {
                            onCaptureFrameForConfirm { jpeg -> viewModel.onPlayerClaimsMove(jpeg) }
                        },
                        onResetGame = { viewModel.resetDotsAndBoxes() },
                    )
                }
            }
        }
    }
}
