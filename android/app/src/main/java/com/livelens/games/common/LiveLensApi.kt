package com.livelens.games.common

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

data class FrameRequest(
    val session_id: String,
    val image_b64: String,
)

data class SudokuAnalyzeResponse(
    val board: List<List<Int>>,
    val confidence: String,
    val conflicts: List<Map<String, Any>>,
    val solved: Boolean,
    val hint: Map<String, Any>?,
)

data class SudokuInjectResponse(
    val injected: Boolean,
    val board: List<List<Int>>,
    val solved: Boolean,
)

data class DotsBoxesMoveResponse(
    val applied: Boolean,
    val reason: String?,
    val completed_boxes: List<List<Int>>,
    val extra_turn: Boolean,
    val game_over: Boolean,
    val winner: String?,
    val state_context: String,
)

data class DotsBoxesResetResponse(
    val reset: Boolean,
    val state_context: String,
)

interface LiveLensApi {

    @POST("/game/sudoku/analyze")
    suspend fun sudokuAnalyze(@Body req: FrameRequest): Response<SudokuAnalyzeResponse>

    @POST("/game/sudoku/inject")
    suspend fun sudokuInject(@Body req: FrameRequest): Response<SudokuInjectResponse>

    @POST("/game/dots-boxes/move")
    suspend fun dotsBoxesMove(@Body req: FrameRequest): Response<DotsBoxesMoveResponse>

    @POST("/game/dots-boxes/reset")
    suspend fun dotsBoxesReset(@Query("session_id") sessionId: String): Response<DotsBoxesResetResponse>
}