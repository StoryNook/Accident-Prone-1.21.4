#!/usr/bin/env python3
"""Single-use migration script."""
from __future__ import annotations
import sys
from pathlib import Path

# Ensure the repo root is on sys.path so 'from tools.X import Y' resolves
# when this script is invoked directly (vs. via -m or pytest).
_REPO_ROOT = Path(__file__).resolve().parent.parent
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))

from tools.migrate_to_1_21_4.orchestrator import MigrationConfig, run_migration


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    cfg = MigrationConfig(
        pack_root=repo_root / "src/main/resources/StoryNook1.2.4",
        staged_root=repo_root / "src/main/resources/StoryNook1.2.4.staged",
        migration_dir=repo_root / "tools/migration",
    )

    # Skip the orchestrator if the legacy pack has already been promoted
    # (legacy slime_ball.json is gone). This lets the script be re-run
    # post-promotion to regenerate just the Java patch report.
    legacy_marker = cfg.pack_root / "assets/minecraft/models/item/slime_ball.json"
    if legacy_marker.exists():
        run_migration(cfg)
    else:
        print(
            f"Skipping orchestrator: legacy file {legacy_marker.name} not found "
            f"(pack already promoted). Regenerating Java patch report only."
        )

    # Java patch report (always regenerated; reads only equipment-definitions.json
    # which persists across promotion).
    artifact_path = cfg.migration_dir / "equipment-definitions.json"
    if not artifact_path.exists():
        print(
            f"ERROR: {artifact_path} does not exist. Run the orchestrator "
            f"first (before pack promotion)."
        )
        return 1

    import json as _json
    artifact = _json.loads(artifact_path.read_text())
    from tools.migrate_to_1_21_4.java_patcher import (
        find_item_creation_sites,
        write_patch_report,
    )
    sites = find_item_creation_sites(repo_root / "src/main/java")
    write_patch_report(
        sites, artifact, cfg.migration_dir / "java-patches-report.md"
    )

    if legacy_marker.exists():
        print(f"Migration written to: {cfg.staged_root}")
        print(f"Manifest:             {cfg.migration_dir / 'manifest.txt'}")
    print(f"Java patch report:    {cfg.migration_dir / 'java-patches-report.md'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
