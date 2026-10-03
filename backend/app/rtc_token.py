"""
Minimal Agora RTC token (v007 / AccessToken2) builder using only Python's
standard library - no pip install needed. Implements the same binary
signing format as Agora's official token builders.
"""
import hashlib
import hmac
import struct
import time
import zlib
import base64
import random

VERSION_LENGTH = 3
APP_ID_LENGTH = 32
VERSION = "007"

PRIVILEGE_JOIN_CHANNEL = 1
PRIVILEGE_PUBLISH_AUDIO = 2
PRIVILEGE_PUBLISH_VIDEO = 3
PRIVILEGE_PUBLISH_DATA = 4

SERVICE_TYPE_RTC = 1


def _pack_uint16(x):
    return struct.pack("<H", x)


def _pack_uint32(x):
    return struct.pack("<I", x)


def _pack_string(s):
    b = s.encode("utf-8") if isinstance(s, str) else s
    return _pack_uint16(len(b)) + b


def _pack_map_uint32(m):
    out = _pack_uint16(len(m))
    for k, v in m.items():
        out += _pack_uint16(k) + _pack_uint32(v)
    return out


def build_rtc_token(app_id: str, app_certificate: str, channel_name: str,
                     uid: int, expire_seconds: int = 3600) -> str:
    """
    Builds an Agora RTC token (v007) with publisher privileges, valid for
    expire_seconds from now. uid=0 lets any uid join with this token.
    """
    now = int(time.time())
    expire_ts = now + expire_seconds
    salt = random.randint(1, 99999999)

    uid_str = str(uid) if uid != 0 else ""

    # --- Build the "message" (service list) ---
    privileges = {
        PRIVILEGE_JOIN_CHANNEL: expire_ts,
        PRIVILEGE_PUBLISH_AUDIO: expire_ts,
        PRIVILEGE_PUBLISH_VIDEO: expire_ts,
        PRIVILEGE_PUBLISH_DATA: expire_ts,
    }

    service = (
        _pack_uint16(SERVICE_TYPE_RTC)
        + _pack_string(channel_name)
        + _pack_string(uid_str)
        + _pack_map_uint32(privileges)
    )

    message = (
        _pack_uint32(salt)
        + _pack_uint32(now)
        + _pack_uint16(1)  # number of services
        + service
    )

    # --- Sign it ---
    app_id_bytes = app_id.encode("utf-8")
    signing = app_id_bytes + _pack_uint32(expire_ts) + message
    signature = hmac.new(
        app_certificate.encode("utf-8"), signing, hashlib.sha256
    ).digest()

    crc_channel = zlib.crc32(channel_name.encode("utf-8")) & 0xFFFFFFFF
    crc_uid = zlib.crc32(uid_str.encode("utf-8")) & 0xFFFFFFFF

    content = (
        _pack_string(signature)
        + _pack_uint32(crc_channel)
        + _pack_uint32(crc_uid)
        + _pack_uint16(len(message))
        + message
    )

    compressed = zlib.compress(content)

    version = VERSION.encode("utf-8")
    token_bytes = version + app_id_bytes.ljust(APP_ID_LENGTH, b"\x00")[:APP_ID_LENGTH] + compressed

    return base64.b64encode(token_bytes).decode("utf-8")