"""
Dots & Boxes on a 3x3 dot grid (2x2 boxes) — small enough to read reliably
from a phone camera and finish in a reasonable demo window.

Design (different from Sudoku on purpose):
The backend is the SOURCE OF TRUTH for game state — whose turn it is, which
lines exist, which boxes are claimed. Vision is only used to CONFIRM the
human's move each turn (did a new line appear, and where), not to re-derive
the whole board from scratch every frame. This avoids the failure mode where
a partially-occluded frame with many lines already drawn gets misread and
silently corrupts the game state.

Flow per human turn:
  1. Backend already knows the current line-set (state).
  2. Camera frame comes in -> vision_client asked "list all line segments
     you see" -> backend diffs against known lines -> exactly one new line
     expected.
  3. If exactly one new line found: apply it, check for completed boxes,
     update turn, return new state.
  4. If zero or >1 new lines found: ask for a clearer frame (don't guess).
"""
from typing import Optional

# 3x3 dots -> 2x2 grid of boxes. Coordinates are dot indices (0..2, 0..2).
GRID_DOTS = 3
GRID_BOXES = GRID_DOTS - 1

Line = tuple[tuple[int, int], tuple[int, int]]  # two dot coords, always sorted


def _norm_line(a: tuple[int, int], b: tuple[int, int]) -> Line:
    return (a, b) if a <= b else (b, a)


def all_possible_lines() -> list[Line]:
    lines = []
    for r in range(GRID_DOTS):
        for c in range(GRID_DOTS - 1):
            lines.append(_norm_line((r, c), (r, c + 1)))  # horizontal
    for r in range(GRID_DOTS - 1):
        for c in range(GRID_DOTS):
            lines.append(_norm_line((r, c), (r + 1, c)))  # vertical
    return lines


def _box_lines(box_r: int, box_c: int) -> list[Line]:
    top = _norm_line((box_r, box_c), (box_r, box_c + 1))
    bottom = _norm_line((box_r + 1, box_c), (box_r + 1, box_c + 1))
    left = _norm_line((box_r, box_c), (box_r + 1, box_c))
    right = _norm_line((box_r, box_c + 1), (box_r + 1, box_c + 1))
    return [top, bottom, left, right]


class DotsAndBoxesGame:
    """In-memory per-session game state. Swap _sessions dict for Redis for multi-instance deploys."""

    def __init__(self, session_id: str, human_name: str = "You", agent_name: str = "Agent"):
        self.session_id = session_id
        self.lines: set[Line] = set()
        self.box_owner: dict[tuple[int, int], str] = {}  # (r,c) -> player name
        self.turn = human_name
        self.human_name = human_name
        self.agent_name = agent_name
        self.scores = {human_name: 0, agent_name: 0}

    def known_lines_as_pairs(self) -> list[list[list[int]]]:
        return [[list(a), list(b)] for a, b in self.lines]

    def apply_new_line(self, line: Line, player: str) -> dict:
        """Applies one confirmed new line, checks for completed boxes, advances turn."""
        if line in self.lines:
            return {"applied": False, "reason": "line_already_exists"}
        if line not in all_possible_lines():
            return {"applied": False, "reason": "invalid_line"}

        self.lines.add(line)
        completed = []
        for r in range(GRID_BOXES):
            for c in range(GRID_BOXES):
                if (r, c) in self.box_owner:
                    continue
                if all(bl in self.lines for bl in _box_lines(r, c)):
                    self.box_owner[(r, c)] = player
                    self.scores[player] += 1
                    completed.append([r, c])

        # Extra turn on completing a box, standard Dots & Boxes rule
        if not completed:
            self.turn = self.agent_name if player == self.human_name else self.human_name

        return {"applied": True, "completed_boxes": completed, "extra_turn": bool(completed)}

    def diff_new_lines(self, observed_lines: list[Line]) -> list[Line]:
        return [l for l in observed_lines if l not in self.lines]

    def is_game_over(self) -> bool:
        return len(self.box_owner) == GRID_BOXES * GRID_BOXES

    def winner(self) -> Optional[str]:
        if not self.is_game_over():
            return None
        h, a = self.scores[self.human_name], self.scores[self.agent_name]
        if h == a:
            return "tie"
        return self.human_name if h > a else self.agent_name

    def to_speech_context(self) -> str:
        lines_desc = ", ".join(f"({a[0]},{a[1]})-({b[0]},{b[1]})" for a, b in sorted(self.lines)) or "none yet"
        boxes_desc = ", ".join(f"box({r},{c})->{owner}" for (r, c), owner in self.box_owner.items()) or "none claimed yet"
        out = [
            f"Dots & Boxes on a {GRID_DOTS}x{GRID_DOTS} dot grid ({GRID_BOXES}x{GRID_BOXES} boxes).",
            f"Lines drawn so far: {lines_desc}",
            f"Boxes claimed: {boxes_desc}",
            f"Score - {self.human_name}: {self.scores[self.human_name]}, {self.agent_name}: {self.scores[self.agent_name]}",
            f"It is currently {self.turn}'s turn.",
        ]
        if self.is_game_over():
            out.append(f"Game over. Result: {self.winner()}.")
        return "\n".join(out)


VISION_INSTRUCTION_TEMPLATE = """You are looking at a Dots & Boxes game: a {n}x{n} grid of dots.
Dot coordinates are (row, col), 0-indexed, row 0 at the top, col 0 at the left.
List every straight line segment currently drawn connecting two ADJACENT dots (horizontal or vertical only, no diagonals).
Return ONLY this JSON shape, nothing else:
{{
  "lines": [[[r1, c1], [r2, c2]], ...],
  "confidence": "high" | "medium" | "low"
}}
If unsure about a faint or ambiguous line, omit it rather than guess."""


def vision_instruction() -> str:
    return VISION_INSTRUCTION_TEMPLATE.format(n=GRID_DOTS)


# Simple in-memory session store — fine for a hackathon demo, single backend instance.
_sessions: dict[str, DotsAndBoxesGame] = {}


def get_or_create_session(session_id: str) -> DotsAndBoxesGame:
    if session_id not in _sessions:
        _sessions[session_id] = DotsAndBoxesGame(session_id)
    return _sessions[session_id]


def reset_session(session_id: str) -> DotsAndBoxesGame:
    _sessions[session_id] = DotsAndBoxesGame(session_id)
    return _sessions[session_id]
