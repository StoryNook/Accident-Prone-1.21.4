"""Parsers for legacy resource pack files."""

from __future__ import annotations

from typing import Dict


def parse_slime_ball_overrides(data: dict) -> Dict[int, str]:
    """Extract {custom_model_data: model_path} from a legacy
    models/item/slime_ball.json document.

    Ignores any non-custom_model_data predicates (there shouldn't be any
    in slime_ball.json, but we tolerate them).
    """
    result: Dict[int, str] = {}
    for entry in data.get("overrides", []):
        predicate = entry.get("predicate", {})
        if "custom_model_data" not in predicate:
            continue
        cmd = int(predicate["custom_model_data"])
        result[cmd] = entry["model"]
    return result
