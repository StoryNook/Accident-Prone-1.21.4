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
    run_migration(cfg)
    print(f"Migration written to: {cfg.staged_root}")
    print(f"Manifest:             {cfg.migration_dir / 'manifest.txt'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
