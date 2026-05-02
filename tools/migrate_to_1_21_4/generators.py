"""Generators for the new 1.21.4 resource pack format."""

from __future__ import annotations

from typing import Dict


def _ns(model_path: str) -> str:
    """Ensure a model reference is namespaced. 'item/foo' -> 'minecraft:item/foo'."""
    if ":" in model_path:
        return model_path
    return f"minecraft:{model_path}"


def build_items_json(base_item: str, cmd_map: Dict[int, str]) -> dict:
    """Build the new-format assets/minecraft/items/<base>.json content
    for an item discriminated by integer custom_model_data via range_dispatch
    on custom_model_data.floats[0].

    Entries are emitted in ascending CMD order so range_dispatch's
    'highest threshold <= value' lookup yields exact matches for our
    discrete integer CMDs.
    """
    sorted_cmds = sorted(cmd_map.keys())
    entries = [
        {
            "threshold": float(cmd),
            "model": {"type": "minecraft:model", "model": _ns(cmd_map[cmd])},
        }
        for cmd in sorted_cmds
    ]
    return {
        "model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "scale": 1.0,
            "fallback": {
                "type": "minecraft:model",
                "model": _ns(f"item/{base_item}"),
            },
            "entries": entries,
        }
    }
