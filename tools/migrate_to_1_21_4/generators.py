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


# trim_type float predicate values map to vanilla trim materials.
# Source: vanilla leather_leggings.json overrides, which use these
# floats to gate model overrides for each trim material.
_TRIM_FLOAT_TO_MATERIAL = {
    0.1: "minecraft:quartz",
    0.2: "minecraft:iron",
    0.3: "minecraft:netherite",
    0.4: "minecraft:redstone",
    0.5: "minecraft:copper",
    0.6: "minecraft:gold",
    0.7: "minecraft:emerald",
    0.8: "minecraft:diamond",
    0.9: "minecraft:lapis",
    1.0: "minecraft:amethyst",
}


def build_leather_leggings_items_json(
    cmd_map: Dict[int, str],
    trim_map: Dict[float, str],
) -> dict:
    """Build assets/minecraft/items/leather_leggings.json.

    Outer: range_dispatch on custom_model_data.floats[0] for our CMDs.
    Fallback: select on minecraft:trim_material reproducing vanilla
    trim variants. select's fallback: plain leather_leggings model.

    Raises ValueError if trim_map contains a float not in the known
    vanilla material table.
    """
    sorted_cmds = sorted(cmd_map.keys())
    entries = [
        {
            "threshold": float(cmd),
            "model": {"type": "minecraft:model", "model": _ns(cmd_map[cmd])},
        }
        for cmd in sorted_cmds
    ]

    cases = []
    for trim_val, model_path in sorted(trim_map.items()):
        if trim_val not in _TRIM_FLOAT_TO_MATERIAL:
            raise ValueError(
                f"Unknown trim_type predicate value {trim_val} — "
                f"not in vanilla trim_material table"
            )
        cases.append(
            {
                "when": _TRIM_FLOAT_TO_MATERIAL[trim_val],
                "model": {"type": "minecraft:model", "model": _ns(model_path)},
            }
        )

    trim_select = {
        "type": "minecraft:select",
        "property": "minecraft:trim_material",
        "cases": cases,
        "fallback": {
            "type": "minecraft:model",
            "model": "minecraft:item/leather_leggings",
        },
    }

    return {
        "model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "scale": 1.0,
            "fallback": trim_select,
            "entries": entries,
        }
    }


def build_equipment_json(equipment_id: str) -> dict:
    """Build assets/minecraft/equipment/<equipment_id>.json.

    The texture key resolves to
    assets/minecraft/textures/entity/equipment/humanoid_leggings/<equipment_id>.png
    via the layer type prefix in the path.
    """
    return {
        "layers": {
            "humanoid_leggings": [{"texture": f"minecraft:{equipment_id}"}]
        }
    }
