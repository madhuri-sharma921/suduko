package com.livelens.games

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.livelens.games.common.CameraPreview
import com.livelens.games.common.FrameCapture
import com.livelens.games.common.GameMode
import com.livelens.games.common.GameSessionViewModel
import com.livelens.games.common.LiveLensApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * App entry point. Wires together:
 *   - runtime CAMERA + RECORD_AUDIO permission requests
 *   - a Retrofit client pointed at the FastAPI backend
 *   - GameSessionViewModel (mode selection + per-frame backend routing)
 *   - CameraPreview (CameraX) as the camera surface behind the game overlays
 *
 * This does NOT include your Agora RTC/RTM session join/publish code - that's
 * specific to your existing Agora project setup (App ID, token server, agent
 * config). Hook it in around the TODO markers below: join the RTC channel
 * and start publishing mic audio once permissions are granted, alongside
 * the camera preview already wired up here.
 */
class MainActivity : ComponentActivity() {

    // ---- Point this at your backend. Use your machine's LAN IP (not localhost)
    // when testing on a physical device - see README.md.
    private val backendBaseUrl = "http://192.168.1.8:8000/" // 10.0.2.2 = host machine, for the Android emulator

    private val sessionId = UUID.randomUUID().toString()

    private lateinit var api: LiveLensApi
    private lateinit var viewModel: GameSessionViewModel

    private var onPermissionsResult: ((Boolean) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.CAMERA] == true &&
                grants[Manifest.permission.RECORD_AUDIO] == true
        onPermissionsResult?.invoke(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(backendBaseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(LiveLensApi::class.java)
        viewModel = GameSessionViewModel(api, sessionId)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LiveLensGamesApp(
                        viewModel = viewModel,
                        hasPermissions = { hasCameraAndMicPermissions() },
                        requestPermissions = { onGranted -> requestCameraAndMicPermissions(onGranted) },
                    )
                }
            }
        }
    }

    private fun hasCameraAndMicPermissions(): Boolean {
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return camera == PackageManager.PERMISSION_GRANTED && mic == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraAndMicPermissions(onResult: (Boolean) -> Unit) {
        onPermissionsResult = onResult
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
}

/**
 * Top-level Composable: permission gate -> game picker + camera/overlay screen.
 * Kept separate from MainActivity so it's easy to preview / test independently.
 */
@androidx.compose.runtime.Composable
private fun LiveLensGamesApp(
    viewModel: GameSessionViewModel,
    hasPermissions: () -> Boolean,
    requestPermissions: (onResult: (Boolean) -> Unit) -> Unit,
) {
    var permissionsGranted by remember { mutableStateOf(hasPermissions()) }
    // Holds the most recent captured frame so the Dots and Boxes "confirm move"
    // button can reuse it instead of needing a second, separate capture path.
    val latestFrameJpeg = remember { mutableStateOf<ByteArray?>(null) }

    if (!permissionsGranted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Live Lens Games needs camera and microphone access " +
                        "to see your board and run the live voice session.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
            Button(onClick = {
                requestPermissions { granted -> permissionsGranted = granted }
            }) {
                Text("Grant camera & microphone access")
            }
        }
        return
    }

    com.livelens.games.common.LiveLensGamesScreen(
        viewModel = viewModel,
        cameraPreviewContent = { mode: GameMode ->
            CameraPreview(
                // Sudoku: read continuously so the board stays up to date.
                // Dots and Boxes: still sample frames (so a recent one is always
                // ready to hand to "confirm move"), just less frequently since
                // we don't act on every frame automatically.
                throttleMillis = if (mode == GameMode.SUDOKU) 15000L else 1000L,
                onFrame = { bitmap, rotation ->
                    val jpeg = FrameCapture.toJpegBytes(bitmap, rotation)
                    latestFrameJpeg.value = jpeg
                    if (mode == GameMode.SUDOKU) {
                        viewModel.onFrameCaptured(jpeg)
                    }
                },
            )
        },
        onCaptureFrameForConfirm = { onCaptured ->
            // Reuses the most recent sampled frame rather than opening a
            // second capture path - good enough since Dots and Boxes frames are
            // still being sampled every ~1s even outside Sudoku mode (above).
            latestFrameJpeg.value?.let(onCaptured)
        },
    )
}