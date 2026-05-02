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


from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional


@dataclass
class CitEntry:
    cmd: int
    texture: str  # value from texture.leather_layer_2 — basename of source PNG, scoped to its folder
    match_items: str
    # Set by the caller (typically _parse_all_cit) when context is known.
    # equipment_id must be globally unique across all CIT entries — derived
    # from the .properties filename stem because texture-name collisions exist
    # in the legacy pack (e.g., pants/leather_layer_2.png + diapers/leather_layer_2.png).
    equipment_id: str = ""
    properties_path: Optional[Path] = None


def parse_cit_properties(raw: str) -> CitEntry:
    """Parse an OptiFine CIT properties file into a CitEntry.

    Expects keys: matchItems, texture.leather_layer_2,
    components.custom_model_data.

    Raises ValueError if any required key is missing.
    """
    props: Dict[str, str] = {}
    for line in raw.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, _, value = line.partition("=")
        props[key.strip()] = value.strip()

    try:
        cmd = int(props["components.custom_model_data"])
        texture = props["texture.leather_layer_2"]
        match_items = props["matchItems"]
    except KeyError as e:
        raise ValueError(f"CIT properties missing required key: {e}") from e

    return CitEntry(cmd=cmd, texture=texture, match_items=match_items)
