package com.livelens.games.common

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns which game is active and routes captured camera frames to the right
 * backend endpoint.
 */
class GameSessionViewModel(
    private val api: LiveLensApi,
    private val sessionId: String,
) : ViewModel() {

    private val _mode = MutableStateFlow(GameMode.SUDOKU)
    val mode: StateFlow<GameMode> = _mode

    private val _sudokuBoard = MutableStateFlow<List<List<Int>>?>(null)
    val sudokuBoard: StateFlow<List<List<Int>>?> = _sudokuBoard

    private val _sudokuSolved = MutableStateFlow(false)
    val sudokuSolved: StateFlow<Boolean> = _sudokuSolved

    private val _dotsBoxesContext = MutableStateFlow("Draw the first line to begin.")
    val dotsBoxesContext: StateFlow<String> = _dotsBoxesContext

    private val _dotsBoxesGameOver = MutableStateFlow(false)
    val dotsBoxesGameOver: StateFlow<Boolean> = _dotsBoxesGameOver

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    fun selectMode(newMode: GameMode) {
        _mode.value = newMode
        _statusMessage.value = newMode.instructionHint
        if (newMode == GameMode.DOTS_AND_BOXES) {
            resetDotsAndBoxes()
        }
    }

    fun onFrameCaptured(jpegBytes: ByteArray) {
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        when (_mode.value) {
            GameMode.SUDOKU -> handleSudokuFrame(base64)
            GameMode.DOTS_AND_BOXES -> {
                // Dots and Boxes only advances state on an explicit move signal
                // (see onPlayerClaimsMove), not on every frame.
            }
        }
    }

    private fun handleSudokuFrame(base64: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Calling sudokuInject...")
                val resp = api.sudokuInject(FrameRequest(sessionId, base64))
                Log.d(TAG, "sudokuInject response code=${resp.code()} successful=${resp.isSuccessful}")
                if (resp.isSuccessful) {
                    val body = resp.body()
                    Log.d(TAG, "sudokuInject body=$body")
                    body?.let {
                        _sudokuBoard.value = it.board
                        _sudokuSolved.value = it.solved
                        _statusMessage.value = if (it.solved) "Solved!" else null
                        Log.d(TAG, "Updated _sudokuBoard.value to ${it.board}")
                    }
                } else {
                    Log.w(TAG, "sudokuInject failed: ${resp.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "sudokuInject error", e)
            }
        }
    }

    fun onPlayerClaimsMove(jpegBytes: ByteArray) {
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        viewModelScope.launch {
            try {
                val resp = api.dotsBoxesMove(FrameRequest(sessionId, base64))
                if (resp.isSuccessful) {
                    resp.body()?.let {
                        _dotsBoxesContext.value = it.state_context
                        _dotsBoxesGameOver.value = it.game_over
                        _statusMessage.value = when {
                            !it.applied && it.reason == "no_new_line" ->
                                "Didn't spot a new line yet - try again."
                            !it.applied && it.reason == "ambiguous_frame" ->
                                "Saw more than one change - hold the camera steady."
                            it.extra_turn -> "Box completed! Go again."
                            it.game_over -> "Game over - ${it.winner} wins!"
                            else -> null
                        }
                    }
                } else {
                    Log.w(TAG, "dotsBoxesMove failed: ${resp.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "dotsBoxesMove error", e)
            }
        }
    }

    fun resetDotsAndBoxes() {
        viewModelScope.launch {
            try {
                val resp = api.dotsBoxesReset(sessionId)
                if (resp.isSuccessful) {
                    _dotsBoxesContext.value = resp.body()?.state_context.orEmpty()
                    _dotsBoxesGameOver.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "dotsBoxesReset error", e)
            }
        }
    }

    companion object {
        private const val TAG = "GameSessionViewModel"
    }
}