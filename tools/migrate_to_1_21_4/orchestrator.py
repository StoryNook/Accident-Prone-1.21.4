"""End-to-end orchestration for the 1.21.4 migration."""

from __future__ import annotations

import json
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Set

from .generators import (
    build_equipment_json,
    build_items_json,
    build_leather_leggings_items_json,
)
from .parsers import (
    CitEntry,
    parse_cit_properties,
    parse_leather_leggings_overrides,
    parse_slime_ball_overrides,
)


def enforce_cit_cmds_have_models(
    cit_cmds: Set[int],
    slime_cmds: Set[int],
    leggings_cmds: Set[int],
) -> None:
    """Every CMD that has a CIT entry must also have an inventory model
    (in either slime_ball or leather_leggings overrides). Otherwise the
    worn-armor texture maps to a CMD with no inventory icon — broken state.
    """
    all_model_cmds = slime_cmds | leggings_cmds
    missing = cit_cmds - all_model_cmds
    if missing:
        raise ValueError(
            f"CIT entries reference CMDs with no inventory model: "
            f"{sorted(missing)}"
        )


@dataclass
class MigrationConfig:
    pack_root: Path
    staged_root: Path
    migration_dir: Path  # where manifest.txt etc. land


def run_migration(cfg: MigrationConfig) -> None:
    """End-to-end: read legacy pack, write staged pack + artifacts."""
    pack = cfg.pack_root
    staged = cfg.staged_root
    out = cfg.migration_dir

    if staged.exists():
        shutil.rmtree(staged)
    staged.mkdir(parents=True)
    out.mkdir(parents=True, exist_ok=True)

    # 1. Copy through anything not transformed
    _copy_through(pack, staged)

    # 2. Parse legacy
    slime_data = json.loads(
        (pack / "assets/minecraft/models/item/slime_ball.json").read_text()
    )
    slime_cmds = parse_slime_ball_overrides(slime_data)

    leggings_data = json.loads(
        (pack / "assets/minecraft/models/item/leather_leggings.json").read_text()
    )
    leggings_cmds, trim_map = parse_leather_leggings_overrides(leggings_data)

    cit_entries = _parse_all_cit(pack / "assets/minecraft/optifine/cit")

    # 3. Invariants
    enforce_cit_cmds_have_models(
        {e.cmd for e in cit_entries},
        set(slime_cmds.keys()),
        set(leggings_cmds.keys()),
    )
    enforce_unique_equipment_ids(cit_entries)
    enforce_submodels_exist(slime_cmds, pack)
    enforce_submodels_exist(leggings_cmds, pack)

    # 4. Write new pack files
    _write_pack_mcmeta(staged)
    _write_items_json(staged, "slime_ball", slime_cmds)
    _write_leather_leggings_items_json(staged, leggings_cmds, trim_map)
    _write_equipment_definitions_and_textures(staged, pack, cit_entries)

    # 5. Drop legacy files from staged
    _delete_legacy(staged)

    # 6. Write artifacts
    _write_manifest(pack, staged, out)
    _write_equipment_definitions_artifact(out, cit_entries)


def _copy_through(src: Path, dst: Path) -> None:
    shutil.copytree(src, dst, dirs_exist_ok=True)


def _delete_legacy(staged: Path) -> None:
    for p in [
        staged / "assets/minecraft/models/item/slime_ball.json",
        staged / "assets/minecraft/models/item/leather_leggings.json",
    ]:
        if p.exists():
            p.unlink()
    optifine = staged / "assets/minecraft/optifine"
    if optifine.exists():
        shutil.rmtree(optifine)


def _write_pack_mcmeta(staged: Path) -> None:
    content = {
        "pack": {
            "pack_format": 46,
            "supported_formats": [46, 99],
            "description": "For the Story Nook Diaper Plugin",
        }
    }
    (staged / "pack.mcmeta").write_text(json.dumps(content, indent=2))


def _write_items_json(staged: Path, base_item: str, cmd_map: Dict[int, str]) -> None:
    items_dir = staged / "assets/minecraft/items"
    items_dir.mkdir(parents=True, exist_ok=True)
    out = items_dir / f"{base_item}.json"
    out.write_text(json.dumps(build_items_json(base_item, cmd_map), indent=2))


def _write_leather_leggings_items_json(
    staged: Path,
    cmd_map: Dict[int, str],
    trim_map: Dict[float, str],
) -> None:
    items_dir = staged / "assets/minecraft/items"
    items_dir.mkdir(parents=True, exist_ok=True)
    out = items_dir / "leather_leggings.json"
    out.write_text(
        json.dumps(
            build_leather_leggings_items_json(cmd_map, trim_map), indent=2
        )
    )


def _parse_all_cit(cit_root: Path) -> List[CitEntry]:
    """Parse every .properties file under cit_root. The equipment_id is
    derived from the filename stem (globally unique across the whole
    cit/ tree), while entry.texture remains the OptiFine-style basename
    used to resolve the source PNG in the same folder.
    """
    entries: List[CitEntry] = []
    for prop_file in cit_root.rglob("*.properties"):
        entry = parse_cit_properties(prop_file.read_text())
        entry.equipment_id = prop_file.stem
        entry.properties_path = prop_file
        entries.append(entry)
    return entries


def enforce_unique_equipment_ids(cit_entries: Iterable[CitEntry]) -> None:
    """Equipment IDs must be globally unique because the equippable
    component resolves textures from a single flat directory
    (assets/minecraft/textures/entity/equipment/humanoid_leggings/<id>.png)
    with no folder-based namespacing.
    """
    seen: Dict[str, int] = {}
    for e in cit_entries:
        if e.equipment_id in seen and seen[e.equipment_id] != e.cmd:
            raise ValueError(
                f"Duplicate equipment id '{e.equipment_id}' for CMDs "
                f"{seen[e.equipment_id]} and {e.cmd}"
            )
        seen[e.equipment_id] = e.cmd


def enforce_submodels_exist(cmd_map: Dict[int, str], pack: Path) -> None:
    """Every model reference in the new items/<base>.json must exist as
    models/item/<id>.json. Dangling reference -> abort.
    """
    for cmd, model_path in cmd_map.items():
        # 'minecraft:item/foo' or 'item/foo' -> 'item/foo'
        if model_path.startswith("minecraft:"):
            model_path = model_path[len("minecraft:"):]
        candidate = pack / "assets/minecraft/models" / f"{model_path}.json"
        if not candidate.exists():
            raise ValueError(
                f"CMD {cmd} references {model_path}, but "
                f"{candidate} does not exist"
            )


def _write_equipment_definitions_and_textures(
    staged: Path,
    pack: Path,
    cit_entries: List[CitEntry],
) -> None:
    eq_dir = staged / "assets/minecraft/equipment"
    eq_dir.mkdir(parents=True, exist_ok=True)
    tex_dir = staged / "assets/minecraft/textures/entity/equipment/humanoid_leggings"
    tex_dir.mkdir(parents=True, exist_ok=True)

    # Each CIT properties file's source PNG lives in the SAME folder as
    # the .properties file, named <texture>.png (per OptiFine resolution).
    # The output equipment_id (filename-derived) is globally unique;
    # use it to name the destination PNG and equipment definition JSON.
    for entry in cit_entries:
        eq_id = entry.equipment_id
        if not entry.properties_path:
            raise ValueError(
                f"CIT entry for CMD {entry.cmd} has no properties_path; "
                f"_parse_all_cit must set this"
            )
        source_png = entry.properties_path.parent / f"{entry.texture}.png"
        if not source_png.exists():
            raise ValueError(
                f"CIT entry at {entry.properties_path} references "
                f"texture '{entry.texture}' but {source_png} does not exist"
            )
        # Equipment definition JSON (uses the unique eq_id)
        (eq_dir / f"{eq_id}.json").write_text(
            json.dumps(build_equipment_json(eq_id), indent=2)
        )
        # Equipment texture PNG (renamed to the unique eq_id)
        shutil.copy2(source_png, tex_dir / f"{eq_id}.png")


def _write_manifest(pack: Path, staged: Path, out: Path) -> None:
    """Emit a created/modified/deleted diff between pack and staged."""
    src_files = {p.relative_to(pack) for p in pack.rglob("*") if p.is_file()}
    dst_files = {p.relative_to(staged) for p in staged.rglob("*") if p.is_file()}
    created = dst_files - src_files
    deleted = src_files - dst_files
    common = src_files & dst_files
    modified = {
        f for f in common
        if (pack / f).read_bytes() != (staged / f).read_bytes()
    }

    lines = []
    for f in sorted(created):
        size = (staged / f).stat().st_size
        lines.append(f"CREATED  {f}  ({size} bytes)")
    for f in sorted(modified):
        old = (pack / f).stat().st_size
        new = (staged / f).stat().st_size
        lines.append(f"MODIFIED {f}  ({old} -> {new} bytes)")
    for f in sorted(deleted):
        lines.append(f"DELETED  {f}")
    (out / "manifest.txt").write_text("\n".join(lines) + "\n")


def _write_equipment_definitions_artifact(
    out: Path, cit_entries: List[CitEntry]
) -> None:
    artifact = [
        {
            "cmd": e.cmd,
            "equipment_id": e.equipment_id,
            "source_texture": e.texture,
            "match_items": e.match_items,
        }
        for e in sorted(cit_entries, key=lambda x: x.cmd)
    ]
    (out / "equipment-definitions.json").write_text(json.dumps(artifact, indent=2))
