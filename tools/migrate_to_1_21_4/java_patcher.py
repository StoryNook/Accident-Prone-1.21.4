"""Generates an item-creation-site report for manual patching.

Rather than emit a literal git-apply patch (fragile against whitespace
and surrounding code drift), this module emits a human-readable file
listing every item-creation site to patch, with the suggested two-line
addition. The engineer applies them by hand in Wave 3 / Task 18.
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Dict, List, Optional


def equipment_id_for_cmd(artifact: List[dict], cmd: int) -> Optional[str]:
    """Return the equipment-definition id for a CMD, or None if unknown."""
    for entry in artifact:
        if entry["cmd"] == cmd:
            return entry["equipment_id"]
    return None


_CMD_CALL_PATTERN = re.compile(
    r"\.setCustomModelData\(\s*(\d+)\s*\)\s*;"
)


def find_item_creation_sites(java_root: Path) -> List[dict]:
    """Scan src/main/java for setCustomModelData calls. Returns
    [{file, line, cmd, surrounding_5_lines}, ...].
    """
    results = []
    for java_file in java_root.rglob("*.java"):
        text = java_file.read_text()
        for m in _CMD_CALL_PATTERN.finditer(text):
            cmd = int(m.group(1))
            line_no = text[: m.start()].count("\n") + 1
            lines = text.splitlines()
            ctx_start = max(0, line_no - 3)
            ctx_end = min(len(lines), line_no + 2)
            results.append(
                {
                    "file": str(java_file),
                    "line": line_no,
                    "cmd": cmd,
                    "context": "\n".join(lines[ctx_start:ctx_end]),
                }
            )
    return results


def write_patch_report(
    sites: List[dict],
    artifact: List[dict],
    out_path: Path,
) -> None:
    lines = ["# Java item-creation patch report\n"]
    lines.append(
        "For each site below, add an equippable component setter next to "
        "the existing setCustomModelData call. Skip sites whose CMD has "
        "no equipment_id (those are non-armor items — inventory icon only).\n"
    )
    for site in sites:
        eq_id = equipment_id_for_cmd(artifact, site["cmd"])
        lines.append(f"## {site['file']}:{site['line']} — CMD {site['cmd']}\n")
        if eq_id:
            lines.append(f"**Add equippable:** `{eq_id}`\n")
            lines.append(
                "```java\n"
                f"// after meta.setCustomModelData({site['cmd']});\n"
                "EquippableComponent equip = meta.getEquippable();\n"
                "equip.setSlot(EquipmentSlot.LEGS);\n"
                f"equip.setModel(NamespacedKey.minecraft(\"{eq_id}\"));\n"
                "meta.setEquippable(equip);\n"
                "```\n"
            )
        else:
            lines.append("**Skip** — no equipment_id for this CMD (non-armor).\n")
        lines.append(f"Context:\n```java\n{site['context']}\n```\n")
    out_path.write_text("\n".join(lines))
