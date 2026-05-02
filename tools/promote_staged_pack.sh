#!/usr/bin/env bash
# Promotes the staged 1.21.4 resource pack tree into the production location.
# Single-use script — part of the 1.21.4 migration. Safe to delete after the
# migration ships.
#
# Pre-conditions:
#   - tools/migrate_to_1_21_4.py has been run, producing
#     src/main/resources/StoryNook1.2.4.staged/.
#   - You have eyeballed tools/migration/manifest.txt and
#     tools/migration/equipment-definitions.json.
#
# Effect:
#   - Renames the existing src/main/resources/StoryNook1.2.4/ to
#     src/main/resources/StoryNook1.2.4.replaced-by-migration/ (a local
#     safety copy, not committed).
#   - Renames the staged tree into src/main/resources/StoryNook1.2.4/.
#
# Reversal:
#   - Run the moves in reverse, OR
#   - git reset --hard pre-1-21-4-migration  (loses migration commits but
#     restores the production pack tree from the rollback tag).

set -euo pipefail

cd "$(dirname "$0")/.."

PACK_DIR="src/main/resources/StoryNook1.2.4"
STAGED_DIR="${PACK_DIR}.staged"
REPLACED_DIR="${PACK_DIR}.replaced-by-migration"

if [[ ! -d "$STAGED_DIR" ]]; then
    echo "ERROR: $STAGED_DIR does not exist." >&2
    echo "Run 'python3 tools/migrate_to_1_21_4.py' first." >&2
    exit 1
fi
if [[ -d "$REPLACED_DIR" ]]; then
    echo "ERROR: $REPLACED_DIR already exists." >&2
    echo "Remove or rename it before promoting." >&2
    exit 1
fi
if [[ ! -d "$PACK_DIR" ]]; then
    echo "ERROR: $PACK_DIR does not exist." >&2
    echo "Cannot promote — there is nothing to replace." >&2
    exit 1
fi

mv "$PACK_DIR" "$REPLACED_DIR"
mv "$STAGED_DIR" "$PACK_DIR"

echo "Promoted: $STAGED_DIR -> $PACK_DIR"
echo "Old pack saved at: $REPLACED_DIR  (delete after verification)"
