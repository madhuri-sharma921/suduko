"""
4x4 Sudoku (2x2 boxes, digits 1-4).

Design: each camera frame is independently re-read by Gemini Vision into a
4x4 grid (0 = empty cell). The board *is* the visual state, so we don't
track history here — we just validate the latest read and answer questions
against it. This keeps the game self-correcting: if one frame is misread,
the next good frame fixes it, instead of compounding a bad diff.
"""
from typing import Optional

Board = list[list[int]]  # 4x4, 0 = empty

VISION_INSTRUCTION = """You are looking at a 4x4 Sudoku grid (2x2 boxes, digits 1-4, blank cells empty).
Read the grid exactly as printed/written. Return ONLY this JSON shape, nothing else:
{
  "board": [[int, int, int, int], [int, int, int, int], [int, int, int, int], [int, int, int, int]],
  "confidence": "high" | "medium" | "low"
}
Use 0 for empty cells. Row 0 is the top row. If you cannot see the board clearly, set confidence to "low"
but still return your best-effort reading."""


def find_conflicts(board: Board) -> list[dict]:
    """Returns a list of {type, index, values} for any row/col/box rule violations."""
    conflicts = []

    for r in range(4):
        seen: dict[int, int] = {}
        for c in range(4):
            v = board[r][c]
            if v == 0:
                continue
            if v in seen:
                conflicts.append({"type": "row", "index": r, "cells": [[r, seen[v]], [r, c]], "value": v})
            seen[v] = c

    for c in range(4):
        seen = {}
        for r in range(4):
            v = board[r][c]
            if v == 0:
                continue
            if v in seen:
                conflicts.append({"type": "column", "index": c, "cells": [[seen[v], c], [r, c]], "value": v})
            seen[v] = r

    for box_r in range(2):
        for box_c in range(2):
            seen = {}
            for dr in range(2):
                for dc in range(2):
                    r, c = box_r * 2 + dr, box_c * 2 + dc
                    v = board[r][c]
                    if v == 0:
                        continue
                    if v in seen:
                        conflicts.append({
                            "type": "box", "index": box_r * 2 + box_c,
                            "cells": [seen[v], [r, c]], "value": v,
                        })
                    seen[v] = [r, c]

    return conflicts


def is_solved(board: Board) -> bool:
    return all(cell != 0 for row in board for cell in row) and not find_conflicts(board)


def candidates_for_cell(board: Board, row: int, col: int) -> list[int]:
    """What digits could legally go in an empty cell right now."""
    if board[row][col] != 0:
        return []
    used = set(board[row]) | {board[r][col] for r in range(4)}
    box_r, box_c = (row // 2) * 2, (col // 2) * 2
    used |= {board[box_r + dr][box_c + dc] for dr in range(2) for dc in range(2)}
    return [d for d in (1, 2, 3, 4) if d not in used]


def find_best_hint(board: Board) -> Optional[dict]:
    """
    Finds the most 'obvious' next move: a cell with exactly one candidate
    (naked single), preferring those first since they make the best spoken hints.
    Falls back to the cell with fewest candidates.
    """
    best: Optional[dict] = None
    for r in range(4):
        for c in range(4):
            if board[r][c] != 0:
                continue
            cands = candidates_for_cell(board, r, c)
            if not cands:
                continue
            if len(cands) == 1:
                return {"row": r, "col": c, "value": cands[0], "reason": "only_candidate"}
            if best is None or len(cands) < len(best["candidates"]):
                best = {"row": r, "col": c, "candidates": cands, "reason": "fewest_candidates"}
    if best and len(best["candidates"]) == 1:
        return {"row": best["row"], "col": best["col"], "value": best["candidates"][0], "reason": best["reason"]}
    return best


def solve(board: Board) -> Optional[Board]:
    """
    Brute-force backtracking solver for a 4x4 Sudoku (2x2 boxes, digits 1-4).
    Returns a fully solved board, or None if the given board is unsolvable
    (e.g. it already has conflicting values, or has no valid completion).

    4x4 is tiny (at most 4^16 states, pruned hard by constraints), so plain
    backtracking solves instantly - no need for anything fancier.
    """
    if find_conflicts(board):
        return None

    work = [row[:] for row in board]  # don't mutate the caller's board

    def find_empty() -> Optional[tuple[int, int]]:
        for r in range(4):
            for c in range(4):
                if work[r][c] == 0:
                    return r, c
        return None

    def backtrack() -> bool:
        pos = find_empty()
        if pos is None:
            return True  # no empty cells left - solved
        r, c = pos
        for candidate in candidates_for_cell(work, r, c):
            work[r][c] = candidate
            if backtrack():
                return True
            work[r][c] = 0  # undo and try next candidate
        return False

    if backtrack():
        return work
    return None


def board_to_speech_context(board: Board) -> str:
    """
    Turns the board into a compact text block injected into the live agent's
    context via RTM — this is what the voice agent 'sees' when reasoning
    about spoken questions like 'where should I play next'.
    """
    conflicts = find_conflicts(board)
    rows_desc = []
    for r in range(4):
        cells = ", ".join(str(v) if v != 0 else "_" for v in board[r])
        rows_desc.append(f"Row {r+1}: {cells}")

    lines = ["Current 4x4 Sudoku board (rows top to bottom, _ = empty):", *rows_desc]

    if conflicts:
        lines.append(f"NOTE: {len(conflicts)} rule violation(s) detected — mention this gently if asked for a hint.")
    if is_solved(board):
        lines.append("The board is fully and correctly solved.")

    hint = find_best_hint(board)
    if hint and "value" in hint:
        lines.append(
            f"Best next move if asked: row {hint['row']+1}, column {hint['col']+1} -> {hint['value']}."
        )

    return "\n".join(lines)