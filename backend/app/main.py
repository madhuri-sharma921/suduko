"""
Live Lens - Games backend.

One FastAPI service backing two board games over the same
bootstrap -> Agora RTC/RTM -> camera frame -> vision -> context-injection
pipeline:

  /bootstrap                    -> Agora App ID + RTC token + starts the live agent
  /game/sudoku/analyze          -> read full board from a frame
  /game/sudoku/inject           -> analyze + push context into the live agent
  /game/dots-boxes/analyze      -> read line segments from a frame
  /game/dots-boxes/move         -> analyze + diff + apply + inject into agent
  /game/dots-boxes/reset        -> start a fresh game for a session
  /session/end                  -> stops the live agent for a session

Loads secrets from a local .env file (never committed) via python-dotenv,
read by app/config.py.
"""
from dotenv import load_dotenv
load_dotenv()

import random

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app.games import sudoku, dots_and_boxes
from app.vision_client import analyze_frame
from app.config import settings
from app import agora_client

app = FastAPI(title="Live Lens Games Backend")

# Maps session_id -> agora agent_id for a running Conversational AI agent.
# In-memory is fine for a single-instance hackathon deployment; swap for
# Redis or a DB if you ever run multiple backend instances.
_session_agents: dict[str, str] = {}

AGENT_SYSTEM_PROMPT = (
    "You are the voice assistant for Live Lens Games, a camera-aware board "
    "game app. You will receive periodic 'Current game state' system "
    "messages describing either a 4x4 Sudoku board or a Dots and Boxes game. "
    "Always answer using the MOST RECENT game state message you've seen, "
    "and never invent board values that weren't given to you. Keep spoken "
    "answers short and natural, as if talking to someone glancing at a "
    "physical board in front of them."
)


# ---------------------------------------------------------------------------
# Bootstrap: issues Agora credentials to the Android app and starts the
# live Conversational AI agent in the same channel.
# ---------------------------------------------------------------------------
class BootstrapRequest(BaseModel):
    session_id: str


class BootstrapResponse(BaseModel):
    app_id: str
    channel_name: str
    rtc_token: str
    uid: int
    agent_id: str


@app.post("/bootstrap", response_model=BootstrapResponse)
async def bootstrap(req: BootstrapRequest):
    channel_name = f"live-lens-{req.session_id}"[:64]
    human_uid = random.randint(100000, 999999)
    agent_uid = random.randint(100000, 999999)
    while agent_uid == human_uid:
        agent_uid = random.randint(100000, 999999)

    human_token = agora_client.generate_rtc_token(channel_name, human_uid)
    agent_token = agora_client.generate_rtc_token(channel_name, agent_uid)

    try:
        join_result = agora_client.start_agent(
            channel_name=channel_name,
            agent_uid=agent_uid,
            agent_token=agent_token,
            system_prompt=AGENT_SYSTEM_PROMPT,
        )
    except Exception as e:
        raise HTTPException(502, f"Failed to start Agora agent: {e}")

    agent_id = join_result.get("agent_id", "")
    if agent_id:
        _session_agents[req.session_id] = agent_id

    return BootstrapResponse(
        app_id=settings.agora_app_id,
        channel_name=channel_name,
        rtc_token=human_token,
        uid=human_uid,
        agent_id=agent_id,
    )


@app.post("/session/end")
async def end_session(session_id: str):
    agent_id = _session_agents.pop(session_id, None)
    if not agent_id:
        return {"stopped": False, "reason": "no_active_agent_for_session"}
    try:
        agora_client.stop_agent(agent_id)
    except Exception as e:
        raise HTTPException(502, f"Failed to stop Agora agent: {e}")
    return {"stopped": True}


# ---------------------------------------------------------------------------
# Context injection - now a real Agora Conversational AI "update" call
# instead of a stub. Both games call this exact same function.
# ---------------------------------------------------------------------------
async def push_context_to_agent(session_id: str, context_text: str) -> None:
    agent_id = _session_agents.get(session_id)
    if not agent_id:
        # No agent started for this session (e.g. bootstrap wasn't called,
        # or it failed) - log and continue rather than crash the game flow.
        print(f"[push_context_to_agent] no agent_id for session={session_id}, skipping")
        return
    try:
        agora_client.update_agent_context(agent_id, context_text, AGENT_SYSTEM_PROMPT)
    except Exception as e:
        print(f"[push_context_to_agent] failed for session={session_id}: {e}")


# ---------------------------------------------------------------------------
# Sudoku
# ---------------------------------------------------------------------------
class SudokuAnalyzeRequest(BaseModel):
    session_id: str
    image_b64: str


class SudokuAnalyzeResponse(BaseModel):
    board: list[list[int]]
    confidence: str
    conflicts: list[dict]
    solved: bool
    solution: list[list[int]] | None
    hint: dict | None


@app.post("/game/sudoku/analyze", response_model=SudokuAnalyzeResponse)
async def sudoku_analyze(req: SudokuAnalyzeRequest):
    try:
        result = analyze_frame(req.image_b64, sudoku.VISION_INSTRUCTION)
    except ValueError as e:
        raise HTTPException(422, str(e))

    print("RAW GEMINI RESULT:", result)

    board = result.get("board")
    if not board or len(board) != 4 or any(len(row) != 4 for row in board):
        raise HTTPException(422, "Vision did not return a valid 4x4 board")

    return SudokuAnalyzeResponse(
        board=board,
        confidence=result.get("confidence", "medium"),
        conflicts=sudoku.find_conflicts(board),
        solved=sudoku.is_solved(board),
        solution=sudoku.solve(board),
        hint=sudoku.find_best_hint(board),
    )


@app.post("/game/sudoku/inject")
async def sudoku_inject(req: SudokuAnalyzeRequest):
    """Analyze a frame AND push the resulting context straight to the live agent - call this
    on your periodic frame-capture timer while the Sudoku game mode is active."""
    analysis = await sudoku_analyze(req)
    context_text = sudoku.board_to_speech_context(analysis.board)
    await push_context_to_agent(req.session_id, context_text)
    return {
        "injected": True,
        "board": analysis.board,
        "solved": analysis.solved,
        "solution": analysis.solution,
    }


# ---------------------------------------------------------------------------
# Dots & Boxes
# ---------------------------------------------------------------------------
class DotsBoxesAnalyzeRequest(BaseModel):