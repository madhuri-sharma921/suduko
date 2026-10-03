package com.livelens.games.common

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

object FrameCapture {

    private const val MAX_DIMENSION = 720
    private const val JPEG_QUALITY = 80

    fun toJpegBytes(bitmap: Bitmap, rotationDegrees: Int = 0): ByteArray {
        val rotated = if (rotationDegrees != 0) rotate(bitmap, rotationDegrees) else bitmap
        val scaled = downscale(rotated, MAX_DIMENSION)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return stream.toByteArray()
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longEdge
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}