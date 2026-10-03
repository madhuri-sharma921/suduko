"""
Central place that reads every secret/config value from environment
variables by name. Nothing here has a real value hardcoded - actual
secrets live only in your local .env file (never committed, never pasted
anywhere) and are loaded via python-dotenv in main.py before this module
is imported.

Required environment variables (see backend/.env.example for the full list
with blank values):
  AGORA_APP_ID
  AGORA_APP_CERTIFICATE
  AGORA_CUSTOMER_KEY
  AGORA_CUSTOMER_SECRET
  GEMINI_API_KEY
  OPENAI_API_KEY        (your Groq key, used as an OpenAI-compatible LLM)
  OPENAI_MODEL
  OPENAI_BASE_URL
  DEEPGRAM_API_KEY

Optional:
  PUBLIC_BACKEND_URL
  AGORA_WEBHOOK_SECRET
  CORS_ALLOW_ORIGINS
"""
import os


def _require(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(
            f"Missing required environment variable: {name}. "
            f"Set it in your local .env file (see .env.example)."
        )
    return value


def _optional(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


class Settings:
    """Loaded once at import time. Raises immediately if a required
    variable is missing, so misconfiguration is caught at server startup
    rather than mid-request."""

    def __init__(self) -> None:
        self.agora_app_id = _require("AGORA_APP_ID")
        self.agora_app_certificate = _require("AGORA_APP_CERTIFICATE")
        self.agora_customer_key = _require("AGORA_CUSTOMER_KEY")
        self.agora_customer_secret = _require("AGORA_CUSTOMER_SECRET")

        self.gemini_api_key = _require("GEMINI_API_KEY")

        # Your LLM: Groq, exposed via an OpenAI-compatible chat completions API
        self.llm_api_key = _require("OPENAI_API_KEY")
        self.llm_model = _require("OPENAI_MODEL")
        self.llm_base_url = _require("OPENAI_BASE_URL")

        self.deepgram_api_key = _require("DEEPGRAM_API_KEY")

        self.agora_webhook_secret = _optional("AGORA_WEBHOOK_SECRET")
        self.public_backend_url = _optional("PUBLIC_BACKEND_URL")
        self.cors_allow_origins = _optional("CORS_ALLOW_ORIGINS", '["*"]')


settings = Settings()