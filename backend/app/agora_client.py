"""
Agora integration: RTC token generation and Conversational AI Engine
REST calls (join / update / stop).

RTC tokens are built locally (app/rtc_token.py) using only Python's
standard library - no pip install needed for that part.

Uses:
  pip install requests python-dotenv
"""
import base64

import requests

from app.config import settings
from app.rtc_token import build_rtc_token

CONVOAI_BASE_URL = "https://api.agora.io/api/conversational-ai-agent/v2/projects"


def generate_rtc_token(channel_name: str, uid: int, expire_seconds: int = 3600) -> str:
    """RTC token for a client (human) or the agent to join a channel."""
    return build_rtc_token(
        settings.agora_app_id,
        settings.agora_app_certificate,
        channel_name,
        uid,
        expire_seconds,
    )


def _basic_auth_header() -> dict:
    raw = f"{settings.agora_customer_key}:{settings.agora_customer_secret}".encode("utf-8")
    encoded = base64.b64encode(raw).decode("utf-8")
    return {"Authorization": f"Basic {encoded}", "Content-Type": "application/json"}


def start_agent(
    channel_name: str,
    agent_uid: int,
    agent_token: str,
    system_prompt: str,
) -> dict:
    """
    Starts a Conversational AI agent in the given channel using:
      - LLM: your Groq endpoint (OpenAI-compatible), BYOK
      - ASR: Deepgram, BYOK
      - TTS: OpenAI TTS in Agora-managed mode (no API key needed - Agora
        bills this directly rather than requiring your own OpenAI key)

    Returns the parsed JSON response, which includes "agent_id" - store
    this, you need it to update or stop the agent later.

    Raises RuntimeError with Agora's actual response body on failure, so
    the real reason (not just a generic 500) surfaces to /bootstrap's caller.
    """
    url = f"{CONVOAI_BASE_URL}/{settings.agora_app_id}/join"

    body = {
        "name": f"agent-{channel_name}",
        "properties": {
            "channel": channel_name,
            "token": agent_token,
            "agent_rtc_uid": str(agent_uid),
            "remote_rtc_uids": ["*"],
            "idle_timeout": 120,
            "llm": {
                "url": f"{settings.llm_base_url.rstrip('/')}/chat/completions",
                "api_key": settings.llm_api_key,
                "system_messages": [
                    {"role": "system", "content": system_prompt},
                ],
                "params": {
                    "model": settings.llm_model,
                    "max_tokens": 1024,
                },
            },
            "asr": {
                "vendor": "deepgram",
                "params": {
                    "api_key": settings.deepgram_api_key,
                },
            },
            "tts": {
                "credential_mode": "managed",
                "vendor": "openai",
                "params": {
                    "url": "https://api.openai.com/v1/audio/speech",
                    "model": "tts-1",
                    "voice": "alloy",
                },
            },
        },
    }

    resp = requests.post(url, json=body, headers=_basic_auth_header(), timeout=15)
    if not resp.ok:
        raise RuntimeError(f"Agora join failed ({resp.status_code}): {resp.text}")
    return resp.json()


def update_agent_context(agent_id: str, context_text: str, system_prompt: str) -> dict:
    """
    Injects fresh context (the current game-board summary) into a running
    agent by re-sending the system_messages with the context appended.
    This is what push_context_to_agent() in main.py calls on every frame.
    """
    url = f"{CONVOAI_BASE_URL}/{settings.agora_app_id}/agents/{agent_id}/update"

    body = {
        "properties": {
            "llm": {
                "system_messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "system", "content": f"Current game state:\n{context_text}"},
                ],
            },
        },
    }

    resp = requests.post(url, json=body, headers=_basic_auth_header(), timeout=15)
    if not resp.ok:
        raise RuntimeError(f"Agora update failed ({resp.status_code}): {resp.text}")
    return resp.json()


def stop_agent(agent_id: str) -> dict:
    url = f"{CONVOAI_BASE_URL}/{settings.agora_app_id}/agents/{agent_id}/leave"
    resp = requests.post(url, json={}, headers=_basic_auth_header(), timeout=15)
    if not resp.ok:
        raise RuntimeError(f"Agora stop failed ({resp.status_code}): {resp.text}")
    return resp.json()