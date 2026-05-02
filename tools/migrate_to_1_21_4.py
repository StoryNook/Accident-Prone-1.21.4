#!/usr/bin/env python3
"""Single-use migration script: rewrites the resource pack from the legacy
1.19-style models/item/<base>.json + overrides system to the 1.21.4-style
assets/minecraft/items/<base>.json + range_dispatch system, and generates
equipment definitions to replace OptiFine CIT.

Run from the project root:
    python3 tools/migrate_to_1_21_4.py

Outputs to:
    src/main/resources/StoryNook1.2.4.staged/    (full new pack tree)
    tools/migration/manifest.txt
    tools/migration/equipment-definitions.json
    tools/migration/migration-java-patches.diff
    tools/migration/pom-patch.diff
    tools/migration/REPORT.md (appended)
"""

from __future__ import annotations

import sys
from pathlib import Path

# Implemented in subsequent tasks
def main() -> int:
    raise NotImplementedError("Wave 2 implementation pending")

if __name__ == "__main__":
    sys.exit(main())
