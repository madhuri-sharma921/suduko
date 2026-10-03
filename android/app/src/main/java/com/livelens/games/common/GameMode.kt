package com.livelens.games.common

/**
 * Selectable game modes for the Live Lens demo.
 * The active mode decides which backend routes get called on each captured frame
 * and which Compose overlay is shown on top of the camera preview.
 */
enum class GameMode(val displayName: String, val instructionHint: String) {
    SUDOKU(
        displayName = "4×4 Sudoku",
        instructionHint = "Show the 4x4 Sudoku grid to the camera. Ask \"where should I play next?\"",
    ),
    DOTS_AND_BOXES(
        displayName = "Dots & Boxes",
        instructionHint = "Draw a line, then say \"I drew a line\" and show the board.",
    ),
}
