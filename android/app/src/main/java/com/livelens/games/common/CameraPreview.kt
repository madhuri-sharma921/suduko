package com.livelens.games.common

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "CameraPreview"
private const val WARMUP_MILLIS = 800L

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    throttleMillis: Long = 1500L,
    onFrame: (Bitmap, rotationDegrees: Int) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lastEmit = remember { AtomicLong(0L) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraBoundAt = remember { AtomicLong(0L) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            Log.d(TAG, "AndroidView factory running - setting up camera")
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                Log.d(TAG, "CameraProvider future resolved")
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy: ImageProxy ->
                            try {
                                val boundAt = cameraBoundAt.get()
                                val now = System.currentTimeMillis()

                                // Skip frames captured while the camera is still
                                // auto-focusing/adjusting exposure right after bind.
                                if (boundAt == 0L || now - boundAt < WARMUP_MILLIS) {
                                    Log.d(TAG, "Skipping frame during camera warm-up")
                                    return@setAnalyzer
                                }

                                Log.d(TAG, "Analyzer invoked, frame ${imageProxy.width}x${imageProxy.height}")
                                val last = lastEmit.get()
                                if (now - last >= throttleMillis && lastEmit.compareAndSet(last, now)) {
                                    val bitmap = imageProxy.toBitmapOrNull()
                                    if (bitmap != null) {
                                        Log.d(TAG, "Frame converted OK, calling onFrame")
                                        onFrame(bitmap, imageProxy.imageInfo.rotationDegrees)
                                    } else {
                                        Log.w(TAG, "Frame conversion returned null - skipping this frame")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in analyzer", e)
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    cameraBoundAt.set(System.currentTimeMillis())
                    Log.d(TAG, "Camera bound to lifecycle successfully, camera=$camera")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind camera to lifecycle", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}

private fun ImageProxy.toBitmapOrNull(): Bitmap? {
    return try {
        val nv21 = yuv420ToNv21(this)
        if (nv21 == null) {
            Log.w(TAG, "yuv420ToNv21 returned null")
            return null
        }
        val yuvImage = android.graphics.YuvImage(
            nv21, android.graphics.ImageFormat.NV21, width, height, null,
        )
        val out = ByteArrayOutputStream()
        val ok = yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, out)
        if (!ok) {
            Log.w(TAG, "YuvImage.compressToJpeg returned false")
            return null
        }
        val bytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) {
            Log.w(TAG, "BitmapFactory.decodeByteArray returned null (bytes=${bytes.size})")
        }
        bitmap
    } catch (e: Exception) {
        Log.e(TAG, "toBitmapOrNull failed", e)
        null
    }
}

/**
 * Converts a YUV_420_888 ImageProxy to a tightly-packed NV21 byte array,
 * correctly handling per-plane row stride and pixel stride.
 *
 * Many devices (notably some OPPO/MediaTek camera stacks) pad each row to a
 * stride wider than the image width. Copying plane buffers as if they were
 * contiguous produces a skewed/garbled image that still encodes fine as JPEG
 * but is unreadable as a photo.
 */
private fun yuv420ToNv21(image: ImageProxy): ByteArray? {
    return try {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val nv21 = ByteArray(width * height * 3 / 2)
        var pos = 0

        // --- Y plane: copy row by row, dropping any row padding ---
        val yBuffer = yPlane.buffer.duplicate()
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val yRow = ByteArray(yRowStride)
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            val bytesToRead = minOf(yRowStride, yBuffer.remaining())
            yBuffer.get(yRow, 0, bytesToRead)
            if (yPixelStride == 1) {
                System.arraycopy(yRow, 0, nv21, pos, width)
            } else {
                for (col in 0 until width) {
                    nv21[pos + col] = yRow[col * yPixelStride]
                }
            }
            pos += width
        }

        // --- Chroma: NV21 wants V,U interleaved at half resolution ---
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        val uRow = ByteArray(uRowStride)
        val vRow = ByteArray(vRowStride)

        for (row in 0 until chromaHeight) {
            uBuffer.position(row * uRowStride)
            uBuffer.get(uRow, 0, minOf(uRowStride, uBuffer.remaining()))

            vBuffer.position(row * vRowStride)
            vBuffer.get(vRow, 0, minOf(vRowStride, vBuffer.remaining()))

            for (col in 0 until chromaWidth) {
                val vuIndex = pos + col * 2
                nv21[vuIndex] = vRow[col * vPixelStride]
                nv21[vuIndex + 1] = uRow[col * uPixelStride]
            }
            pos += chromaWidth * 2
        }

        nv21
    } catch (e: Exception) {
        Log.e(TAG, "yuv420ToNv21 failed", e)
        null
    }
}