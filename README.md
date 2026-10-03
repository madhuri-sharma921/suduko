# Suduko

A camera-aware Voice AI demo: point your phone at a paper board game, talk
to a live voice agent about it. Two games, one Android app, one FastAPI
backend.

```
Android app → /bootstrap → Agora RTC/RTM tokens → live voice session
            → camera frames → backend vision call → context injection → spoken response
```

## The two games, and why they're built differently

**4×4 Sudoku** — the board *is* the state. Every captured frame is
independently re-read into a full 4×4 grid and injected as context. Simple
and self-correcting: a bad frame just gets overwritten by the next good one.



## Repo layout

```
android/                       # Full Gradle project — open this folder in Android Studio
  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/livelens/games/
        MainActivity.kt         # entry point — hosts the game picker + session screen
        common/
          GameMode.kt            # enum: SUDOKU | DOTS_AND_BOXES
          LiveLensApi.kt         # Retrofit interface for backend game routes
          GameSessionViewModel.kt# mode state + per-frame routing
          FrameCapture.kt        # bitmap -> downscaled JPEG helper
          GamePickerScreen.kt    # mode-selection screen
          LiveLensGamesScreen.kt # wraps camera preview with the right overlay
          CameraPreview.kt       # CameraX preview + frame capture wiring
        sudoku/
          SudokuOverlay.kt       # 4x4 grid readout drawn over camera preview
      res/                       # strings, theme, launcher icon
  build.gradle.kts
  settings.gradle.kts
  gradlew / gradlew.bat / gradle/wrapper/   # Gradle wrapper — no local Gradle install needed

backend/
  app/
    main.py                 # FastAPI app — bootstrap + sudoku + dots-boxes routes
    vision_client.py        # shared Gemini Vision wrapper (JSON-only responses)
    games/
      sudoku.py             # board model, conflict detection, hint solver
    
  requirements.txt
```

## Running the backend

```bash
cd backend
pip install -r requirements.txt
export GEMINI_API_KEY=your_key_here      # Windows PowerShell: $env:GEMINI_API_KEY = "your_key_here"
uvicorn app.main:app --reload --port 8000
```

Confirm it's up: `curl http://localhost:8000/health` → `{"status":"ok"}`

Interactive API docs: `http://localhost:8000/docs`

| Endpoint | Purpose |
|---|---|
| `POST /game/sudoku/analyze` | Read a frame into a 4×4 board, no side effects |
| `POST /game/sudoku/inject` | Analyze + push context into the live agent (call on your frame timer) |


You still need to add your own `/bootstrap` endpoint (Agora App ID +
short-lived RTC/RTM token issuance) to `main.py` — that part is specific to
your Agora project credentials and isn't included here.

`push_context_to_agent()` in `main.py` is a stub — wire it to whatever you
use to get context into the live Agora Conversational AI agent (RTM
publish, or the Conversational AI context-update REST call).

## Running the Android app

1. Open the `android/` folder directly in Android Studio (File → Open).
   It's a complete Gradle project — sync will run automatically. Android
   Studio creates `local.properties` (pointing at your SDK install) for you
   on first sync — no manual setup needed there.
2. Add your Agora RTC/RTM SDK dependency in `app/build.gradle.kts` (see the
   commented block — either a Maven coordinate or a local AAR in `app/libs/`).
3. Set the backend URL in `MainActivity.kt` (`backendBaseUrl`). It defaults
   to `http://10.0.2.2:8000/`, which only works on the **Android emulator**
   (that address is the emulator's alias for your host machine). If you're
   testing on a **physical device**, change it to your machine's LAN IP
   instead (e.g. `http://192.168.1.23:8000/`) — find it with `ipconfig`
   (Windows) or `ifconfig | grep inet` (macOS/Linux). Phone and backend must
   be on the same Wi-Fi network.
4. Build & run. `MainActivity` launches straight into the game picker
   (Sudoku or Dots & Boxes); after picking, it requests camera + mic
   permissions and starts the session.

## Suggested demo script

- **Sudoku**: "Where should I play next?" → agent reads the grid, gives row/col + digit with reasoning.


## Suggested Gemini prompt additions for the live agent's system prompt

```
When game_mode = "sudoku": you are a Sudoku assistant. The board context
you receive is a full re-read of the grid, not a diff — trust the latest
one completely. Never invent cell values not present in the context.

```
