"""Parsers for legacy resource pack files."""

from __future__ import annotations

from typing import Dict, Tuple


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


def parse_leather_leggings_overrides(data: dict) -> Tuple[Dict[int, str], Dict[float, str]]:
    """Extract ({custom_model_data: model_path}, {trim_type: model_path})
    from a legacy models/item/leather_leggings.json document.

    Trim entries use predicate.trim_type (a float in 0.1 .. 1.0).
    CMD entries use predicate.custom_model_data (an int).
    """
    cmds: Dict[int, str] = {}
    trims: Dict[float, str] = {}
    for entry in data.get("overrides", []):
        predicate = entry.get("predicate", {})
        if "custom_model_data" in predicate:
            cmd = int(predicate["custom_model_data"])
            cmds[cmd] = entry["model"]
        elif "trim_type" in predicate:
            trim = float(predicate["trim_type"])
            trims[trim] = entry["model"]
    return cmds, trims
