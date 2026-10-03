import base64
import json
import os
from typing import Any

from google import genai
from google.genai import types

GEMINI_API_KEY = os.environ["GEMINI_API_KEY"]
_client = genai.Client(api_key=GEMINI_API_KEY)


def _b64_to_part(image_b64: str) -> types.Part:
    if "," in image_b64[:40]:
        image_b64 = image_b64.split(",", 1)[1]
    return types.Part.from_bytes(
        data=base64.b64decode(image_b64),
        mime_type="image/jpeg",
    )


def analyze_frame(image_b64: str, instruction: str) -> dict[str, Any]:
    image_part = _b64_to_part(image_b64)
    response = _client.models.generate_content(
   model="gemini-3.5-flash-lite",
        contents=[instruction, image_part],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
        ),
    )
    text = response.text.strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError as e:
        raise ValueError(f"Gemini did not return valid JSON: {text[:200]}") from e