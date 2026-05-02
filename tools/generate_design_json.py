#!/usr/bin/env python3
"""
Auto-detect everything about a new design from a folder of stage PNGs and:
  1. Splice 3*N font entries into images.json (in-place, before the closing ]).
  2. If the folder has an `icons/` subfolder with clean.png (+ optional
     wet.png / messy.png / wetmessy.png), generate models/item/<design>_<state>.json
     for each, and append `range_dispatch` entries to
     assets/minecraft/items/slime_ball.json for all 4 CMDs (sorted ascending).
  3. If the folder has an `armor/clean.png` (+ optional wet/messy/wetmessy.png
     for future per-state extension), write
     assets/minecraft/equipment/<equippableKey>.json (humanoid_leggings layer)
     and copy the source PNG to
     assets/minecraft/textures/entity/equipment/humanoid_leggings/<equippableKey>.png.
     Replaces the legacy OptiFine CIT properties pipeline (gone in 1.21.4).
  4. Write tools/pending_design.json (now including `equippableKey`) so
     /add-design can finish the wiring.
  5. Update tools/cmd_registry.json to reserve the CMD block.

Usage (the "drop in and magic" path):
    python3 tools/generate_design_json.py <folder>

The folder must live under
    src/main/resources/StoryNook1.2.4/assets/minecraft/textures/custom/special/

NAMING CONVENTION (recommended):
    Name your folder with a category prefix so detection is unambiguous:
        undies-<name>/    or  underwear-<name>/   -> Underwear  (4 stages)
        pullup-<name>/                            -> Pull-up    (8 stages)
        diaper-<name>/                            -> Diaper     (15 stages)
        thick-<name>/     or  thick_diaper-<name>/-> Thick      (25 stages)
    Example: pullup-goodnite-stars/  -> giveKey "goodnite_stars" (prefix stripped)

The script infers:
  - category         <- folder prefix (primary); PNG count (fallback);
                       4/8/15/25 = undies/pullup/diaper/thick. Mismatch warns.
  - design name      <- folder name with prefix stripped; hyphens -> underscores
  - designId         <- next free for this category, scanning DesignRegistry.java
                       and tools/applied/*.json
  - cleanCmd         <- next free 4-CMD block from tools/cmd_registry.json
                       (default base: 626060, step: 4)
  - displayName      <- title-cased design name; override with --display-name
  - equippableKey    <- defaults to the design name (giveKey)

Override any inferred value with --category / --design-id / --cmd / --name /
--display-name. Use --dry-run to print the JSON block to stdout without
modifying images.json.
"""

import argparse
import json
import os
import re
import shutil
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir))
IMAGES_JSON_REL = "src/main/resources/StoryNook1.2.4/assets/minecraft/font/images.json"
# 1.21.4: range_dispatch entries live under assets/minecraft/items/slime_ball.json,
# not under assets/minecraft/models/item/slime_ball.json (which is now a plain
# fallback model file).
SLIME_BALL_ITEMS_JSON_REL = "src/main/resources/StoryNook1.2.4/assets/minecraft/items/slime_ball.json"
MODELS_ITEM_DIR_REL = "src/main/resources/StoryNook1.2.4/assets/minecraft/models/item"
TEXTURES_ITEM_DIR_REL = "src/main/resources/StoryNook1.2.4/assets/minecraft/textures/item"
EQUIPMENT_DIR_REL = "src/main/resources/StoryNook1.2.4/assets/minecraft/equipment"
EQUIPMENT_TEXTURES_DIR_REL = "src/main/resources/StoryNook1.2.4/assets/minecraft/textures/entity/equipment/humanoid_leggings"
DESIGN_REGISTRY_REL = "src/main/java/com/storynook/DesignRegistry.java"
CMD_REGISTRY_REL = "tools/cmd_registry.json"
APPLIED_DIR_REL = "tools/applied"
PENDING_MANIFEST_REL = "tools/pending_design.json"

# Icon states inside <design>/icons/. Order = fallback chain.
# If a PNG is missing, the slime_ball entry for that state points at the
# previous existing model (e.g. wetmessy missing -> uses messy's model).
ICON_STATES = ["clean", "wet", "messy", "wetmessy"]
ICON_STATE_TO_CMD_KEY = {
    "clean":    "cleanCmd",
    "wet":      "wetCmd",
    "messy":    "dirtyCmd",
    "wetmessy": "wetDirtyCmd",
}

# Mirrors DesignRegistry.java
SIZE_BASE   = [0xED00, 0xE200, 0xE780]   # small, normal, big
CAT_OFFSET  = [0x000,  0x080,  0x180,  0x380]
STAGE_BLOCK = [4,      8,      16,     32]
STAGE_COUNT = [4,      8,      15,     25]
CAT_NAMES   = ["underwear", "pull-up", "diaper", "thick_diaper"]

SIZE_NAMES   = ["small", "normal", "big"]
SIZE_METRICS = {
    "small":  (-40, 25),
    "normal": (70,  70),
    "big":    (25,  30),
}

STAGE_SUFFIXES = {
    0: ["clean", "wet1", "mess1", "wet1mess1"],
    1: ["clean", "wet1", "wet2", "wet3",
        "mess1", "wet1mess1", "wet2mess1", "wet3mess1"],
    2: ["clean",
        "wet1", "wet2", "wet3", "wet4",
        "mess1", "wet1mess1", "wet2mess1", "wet3mess1", "wet4mess1",
        "mess2", "wet1mess2", "wet2mess2", "wet3mess2", "wet4mess2"],
    3: ["clean",
        "wet1", "wet2", "wet3", "wet4", "wet5",
        "mess1", "wet1mess1", "wet2mess1", "wet3mess1", "wet4mess1", "wet5mess1",
        "mess2", "wet1mess2", "wet2mess2", "wet3mess2", "wet4mess2", "wet5mess2",
        "mess3", "wet1mess3", "wet2mess3", "wet3mess3", "wet4mess3", "wet5mess3"],
}

DEFAULT_CMD_BASE = 626060
DEFAULT_CMD_STEP = 4


# ---------------------------------------------------------------------------
# Discovery helpers
# ---------------------------------------------------------------------------

def repo_path(rel):
    return os.path.join(REPO_ROOT, rel)


def discover_stage_files(dir_path, category):
    """Map each stage suffix to a PNG in dir_path. Falls back to the previous
    file when a stage is missing."""
    if not os.path.isdir(dir_path):
        sys.exit("ERROR: --dir does not exist: " + dir_path)
    available = sorted(f for f in os.listdir(dir_path) if f.lower().endswith(".png"))
    if not available:
        sys.exit("ERROR: no PNG files in " + dir_path)

    suffixes = STAGE_SUFFIXES[category]
    chosen, last_good, missing = [], None, []
    for suffix in suffixes:
        match = next((f for f in available if f[:-4].endswith("_" + suffix)), None)
        if match is None:
            missing.append(suffix)
            chosen.append(last_good if last_good else available[0])
        else:
            chosen.append(match)
            last_good = match
    if missing:
        print("note: missing stage images: %s -- using fallback PNG" %
              ", ".join(missing), file=sys.stderr)
    return chosen


# Folder-name prefix → category. The prefix is stripped from the design name.
PREFIX_CATEGORY = {
    "undies":     0,
    "underwear":  0,
    "pullup":     1,
    "pull-up":    1,
    "pull_up":    1,
    "diaper":     2,
    "thick":      3,
    "thick-diaper": 3,
    "thick_diaper": 3,
}


def infer_category_from_prefix(folder_basename):
    """Return (category, stripped_name) if the folder starts with a known
    category prefix, else (None, folder_basename)."""
    lower = folder_basename.lower()
    # Try longest prefixes first so "thick-diaper-" wins over "thick-".
    for prefix in sorted(PREFIX_CATEGORY.keys(), key=len, reverse=True):
        if lower.startswith(prefix + "-") or lower.startswith(prefix + "_"):
            return PREFIX_CATEGORY[prefix], folder_basename[len(prefix) + 1:]
    return None, folder_basename


def infer_category_from_pngs(dir_path):
    pngs = [f for f in os.listdir(dir_path) if f.lower().endswith(".png")]
    n = len(pngs)
    by_count = {4: 0, 8: 1, 15: 2, 25: 3}
    if n in by_count:
        return by_count[n]
    # Tolerance: match nearest legal count; warn if off-by-one (missing wet3mess1 etc)
    closest = min(by_count.keys(), key=lambda k: abs(k - n))
    if abs(closest - n) <= 2:
        print("note: %d PNGs found; inferring category=%d (expects %d stages)" %
              (n, by_count[closest], closest), file=sys.stderr)
        return by_count[closest]
    return None  # caller decides how to fail


def scan_existing_design_ids():
    """Return {category: set(designIds)} from DesignRegistry.java + applied manifests."""
    used = {0: set(), 1: set(), 2: set(), 3: set()}

    # Scan DesignRegistry.java for register(cat, designId, ...)
    reg_path = repo_path(DESIGN_REGISTRY_REL)
    if os.path.isfile(reg_path):
        with open(reg_path) as f:
            src = f.read()
        for m in re.finditer(r"\bregister\s*\(\s*(\d+)\s*,\s*(\d+)", src):
            cat, did = int(m.group(1)), int(m.group(2))
            if cat in used:
                used[cat].add(did)
        # Also legacy designs claim designId=0
        for m in re.finditer(r"\bregisterLegacy\s*\(\s*(\d+)", src):
            cat = int(m.group(1))
            if cat in used:
                used[cat].add(0)

    # Scan applied/ manifests
    applied_dir = repo_path(APPLIED_DIR_REL)
    if os.path.isdir(applied_dir):
        for fn in os.listdir(applied_dir):
            if fn.endswith(".json"):
                try:
                    with open(os.path.join(applied_dir, fn)) as f:
                        m = json.load(f)
                    used.setdefault(m["category"], set()).add(m["designId"])
                except Exception:
                    pass
    return used


def next_design_id(category):
    used = scan_existing_design_ids()
    n = 1
    while n in used.get(category, set()):
        n += 1
    return n


def load_cmd_registry():
    path = repo_path(CMD_REGISTRY_REL)
    if os.path.isfile(path):
        with open(path) as f:
            return json.load(f)
    return {"base": DEFAULT_CMD_BASE, "step": DEFAULT_CMD_STEP, "next": DEFAULT_CMD_BASE,
            "allocations": []}


def save_cmd_registry(reg):
    with open(repo_path(CMD_REGISTRY_REL), "w") as f:
        json.dump(reg, f, indent=2)


def reserve_cmd_block(reg, design_name):
    cmd = reg.get("next", reg["base"])
    reg["allocations"].append({"design": design_name, "cleanCmd": cmd})
    reg["next"] = cmd + reg.get("step", DEFAULT_CMD_STEP)
    return cmd


def folder_to_design_name(folder_path):
    base = os.path.basename(os.path.abspath(folder_path)).lower()
    return re.sub(r"[^a-z0-9]+", "_", base).strip("_")


def design_to_display_name(name):
    return " ".join(w.capitalize() for w in name.split("_"))


def snake_to_pascal(snake):
    return "".join(p.capitalize() for p in snake.split("_"))


# ---------------------------------------------------------------------------
# JSON entry generation + splicing
# ---------------------------------------------------------------------------

def build_entry_text(file_path, codepoint, ascent, height):
    return ('        {\n'
            '            "type": "bitmap",\n'
            '            "file": "%s",\n'
            '            "ascent": %d,\n'
            '            "height": %d,\n'
            '            "chars": ["\\u%04X"]\n'
            '        }') % (file_path, ascent, height, codepoint)


def build_entries_block(category, design_id, file_prefix, stage_files):
    parts = []
    for size_idx, size_name in enumerate(SIZE_NAMES):
        ascent, height = SIZE_METRICS[size_name]
        base_cp = SIZE_BASE[size_idx] + CAT_OFFSET[category] + design_id * STAGE_BLOCK[category]
        for stage_idx, fname in enumerate(stage_files):
            cp = base_cp + stage_idx
            parts.append(build_entry_text(file_prefix + "/" + fname, cp, ascent, height))
    return ",\n".join(parts)


def splice_into_images_json(entries_block):
    """Insert entries_block at the very end of the providers array. Adds a
    leading comma to the previous last entry's closing brace."""
    path = repo_path(IMAGES_JSON_REL)
    with open(path) as f:
        content = f.read()

    # The file ends with:  ...        }\n    ]\n}\n
    # We need to find the last '}' before the closing ']' of providers.
    closing_match = re.search(r"\n(\s*)\]\s*}\s*$", content)
    if not closing_match:
        sys.exit("ERROR: could not find closing ] of providers array in images.json")
    insert_pos = closing_match.start()
    new_content = (content[:insert_pos]
                   + ",\n" + entries_block + "\n"
                   + content[insert_pos:])

    # Verify resulting file is still valid JSON
    try:
        data = json.loads(new_content)
        provider_count = len(data["providers"])
    except json.JSONDecodeError as e:
        sys.exit("ERROR: splice produced invalid JSON: %s" % e)

    with open(path, "w") as f:
        f.write(new_content)
    return provider_count


def compute_file_prefix(dir_path, namespace):
    norm = dir_path.replace(os.sep, "/")
    if "/textures/" not in norm:
        sys.exit("ERROR: --dir must live under .../textures/... so the namespace path can be computed.\n"
                 "Got: " + dir_path)
    rel = norm.split("/textures/", 1)[1]
    return "%s:%s" % (namespace, rel)


# ---------------------------------------------------------------------------
# Inventory icon plumbing -- icons/<state>.png inside the design folder
# ---------------------------------------------------------------------------

def discover_icons(dir_path):
    """Return {state: filename} dict for icons/<state>.png that exist.
    Returns None if the icons/ subfolder does not exist."""
    icons_dir = os.path.join(dir_path, "icons")
    if not os.path.isdir(icons_dir):
        return None
    found = {}
    for state in ICON_STATES:
        candidate = os.path.join(icons_dir, state + ".png")
        if os.path.isfile(candidate):
            found[state] = state + ".png"
    return found


def icon_model_name(design_name, state):
    """Java/JSON model identifier (no parent prefix). e.g.
    ('goodnite_stars', 'clean') -> 'goodnite_stars_clean'.
    """
    return "%s_%s" % (design_name, state)


def write_item_models(design_name, dir_path, namespace, found_icons):
    """For each present icon, COPY the PNG into the standard textures/item/
    location as <design>_<state>.png and generate the matching item model.

    Why: this resource pack's setup only resolves item-model textures under
    `textures/item/...` — deep paths like `textures/custom/special/<folder>/icons/`
    render as missing-texture (purple/black) at runtime, even though the font
    loader handles them fine. Every working item in this pack uses the
    `textures/item/<name>.png` convention; we follow it.

    The user keeps source icons under <design-folder>/icons/ for discoverability;
    the script handles the copy + model generation transparently.
    """
    icons_dir = os.path.join(dir_path, "icons")
    textures_item_dir = repo_path(TEXTURES_ITEM_DIR_REL)
    models_dir = repo_path(MODELS_ITEM_DIR_REL)
    written_models = []
    for state, fname in found_icons.items():
        model_name = icon_model_name(design_name, state)  # e.g. goodnite_stars_clean
        # Copy texture into textures/item/<model_name>.png
        src = os.path.join(icons_dir, fname)
        dst_png = os.path.join(textures_item_dir, model_name + ".png")
        shutil.copy2(src, dst_png)
        try:
            os.chmod(dst_png, 0o644)
        except OSError:
            pass
        # Write model JSON pointing at item/<model_name>
        model_path = os.path.join(models_dir, model_name + ".json")
        model = {
            "parent":   "item/generated",
            "textures": {"layer0": "item/" + model_name},
        }
        with open(model_path, "w") as f:
            json.dump(model, f, indent=2)
            f.write("\n")
        written_models.append(model_path)
    return written_models


# ---------------------------------------------------------------------------
# 1.21.4 items/slime_ball.json range_dispatch splicing
# ---------------------------------------------------------------------------

def append_range_dispatch_entry(items_doc, cmd, model_path):
    """Insert {threshold: cmd, model: ...} into the range_dispatch entries
    list, maintaining ascending sort by threshold. Idempotent: if a matching
    threshold already exists, the model is replaced (so re-runs always reflect
    the latest icon state mappings)."""
    entries = items_doc["model"]["entries"]
    new_entry = {
        "threshold": float(cmd),
        "model": {"type": "minecraft:model", "model": "minecraft:%s" % model_path},
    }
    # If the threshold is already there, update in place.
    for i, e in enumerate(entries):
        if e.get("threshold") == new_entry["threshold"]:
            entries[i] = new_entry
            entries.sort(key=lambda e: e["threshold"])
            return
    entries.append(new_entry)
    entries.sort(key=lambda e: e["threshold"])


def splice_slime_ball_range_dispatch(design_name, found_icons, manifest):
    """Insert (or update) range_dispatch entries in
    assets/minecraft/items/slime_ball.json for the design's 4 CMDs.

    The resolver matches by threshold; entries are kept sorted ascending so
    that vanilla 1.21.4 lookup semantics work as expected. Returns
    {cmd: model_path} for logging."""
    sb_path = repo_path(SLIME_BALL_ITEMS_JSON_REL)
    with open(sb_path) as f:
        doc = json.load(f)

    if "model" not in doc or doc["model"].get("type") != "minecraft:range_dispatch":
        sys.exit("ERROR: %s does not have a top-level minecraft:range_dispatch model. "
                 "1.21.4 pack format expected." % SLIME_BALL_ITEMS_JSON_REL)

    # Resolve each CMD to its model (with fallback chain).
    cmds_to_models = {}
    last_existing = None
    for state in ICON_STATES:
        if state in found_icons:
            last_existing = state
        target_state = state if state in found_icons else last_existing
        if target_state is None:
            continue
        cmd = manifest[ICON_STATE_TO_CMD_KEY[state]]
        cmds_to_models[cmd] = "item/" + icon_model_name(design_name, target_state)

    for cmd, model_ref in cmds_to_models.items():
        append_range_dispatch_entry(doc, cmd, model_ref)

    new_content = json.dumps(doc, indent=2) + "\n"

    # Sanity check
    try:
        json.loads(new_content)
    except json.JSONDecodeError as e:
        sys.exit("ERROR: items/slime_ball.json splice produced invalid JSON: %s" % e)

    with open(sb_path, "w") as f:
        f.write(new_content)
    return cmds_to_models


# ---------------------------------------------------------------------------
# 1.21.4 equipment definitions -- replaces the OptiFine CIT pipeline
# ---------------------------------------------------------------------------

def discover_armor_textures(dir_path):
    """Look in <design-folder>/armor/ for clean/wet/messy/wetmessy PNGs.
    Returns {state: filename} dict, or None if the folder doesn't exist.

    Replaces the legacy optifine/cit/special/<folder>/clean.png convention
    (OptiFine CIT was removed in 1.21.4)."""
    armor_dir = os.path.join(dir_path, "armor")
    if not os.path.isdir(armor_dir):
        return None
    found = {}
    for state in ICON_STATES:
        candidate = os.path.join(armor_dir, state + ".png")
        if os.path.isfile(candidate):
            found[state] = state + ".png"
    return found


def write_equipment_definition(equippable_key):
    """Write assets/minecraft/equipment/<equippable_key>.json with a single
    humanoid_leggings layer pointing at the same key as the texture path
    (textures/entity/equipment/humanoid_leggings/<equippable_key>.png).

    Returns the absolute path written."""
    eq_dir = repo_path(EQUIPMENT_DIR_REL)
    os.makedirs(eq_dir, exist_ok=True)
    content = {
        "layers": {
            "humanoid_leggings": [{"texture": "minecraft:%s" % equippable_key}]
        }
    }
    out_path = os.path.join(eq_dir, "%s.json" % equippable_key)
    with open(out_path, "w") as f:
        json.dump(content, f, indent=2)
        f.write("\n")
    return out_path


def copy_equipment_texture(source_png, equippable_key):
    """Copy source_png to
    assets/minecraft/textures/entity/equipment/humanoid_leggings/<equippable_key>.png.

    Returns the absolute path written."""
    tex_dir = repo_path(EQUIPMENT_TEXTURES_DIR_REL)
    os.makedirs(tex_dir, exist_ok=True)
    out_path = os.path.join(tex_dir, "%s.png" % equippable_key)
    shutil.copy2(source_png, out_path)
    try:
        os.chmod(out_path, 0o644)
    except OSError:
        pass
    return out_path


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def sync_icons_from_applied():
    """Walk tools/applied/*.json and re-copy each design's icon PNGs from
    <design-folder>/icons/ to textures/item/<design>_<state>.png. Idempotent.
    Used by the Maven antrun task before re-zipping the resource pack so
    that hand-edits to the source icons always make it into the .zip."""
    applied_dir = repo_path(APPLIED_DIR_REL)
    if not os.path.isdir(applied_dir):
        print("note: no tools/applied/ directory found; nothing to sync.", file=sys.stderr)
        return 0
    textures_item_dir = repo_path(TEXTURES_ITEM_DIR_REL)
    synced = 0
    for fn in sorted(os.listdir(applied_dir)):
        if not fn.endswith(".json"):
            continue
        with open(os.path.join(applied_dir, fn)) as f:
            m = json.load(f)
        folder = m.get("folderBasename")
        design = m.get("designName")
        if not folder or not design:
            print("note: skipping %s -- missing folderBasename or designName" % fn,
                  file=sys.stderr)
            continue
        icons_dir = os.path.join(repo_path(
            "src/main/resources/StoryNook1.2.4/assets/minecraft/textures/custom/special"),
            folder, "icons")
        if not os.path.isdir(icons_dir):
            continue
        for state in ICON_STATES:
            src = os.path.join(icons_dir, state + ".png")
            if not os.path.isfile(src):
                continue
            dst = os.path.join(textures_item_dir, "%s_%s.png" % (design, state))
            shutil.copy2(src, dst)
            try:
                os.chmod(dst, 0o644)
            except OSError:
                pass
            synced += 1
    print("synced %d icon PNG(s) from design folders to textures/item/" % synced,
          file=sys.stderr)
    return synced


def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--sync-icons", action="store_true",
                   help="Maintenance mode: walk tools/applied/ and re-copy icon PNGs "
                        "from each design's icons/ subfolder into textures/item/. "
                        "Used by the Maven antrun task on every build. Other args ignored.")
    p.add_argument("dir", nargs="?",
                   help="Folder containing stage PNGs (under textures/custom/special/...)")
    p.add_argument("--category", type=int, choices=[0, 1, 2, 3], default=None,
                   help="Override inferred category.")
    p.add_argument("--design-id", type=int, default=None,
                   help="Override inferred designId.")
    p.add_argument("--cmd", type=int, default=None,
                   help="Override inferred clean CMD; wet/dirty/wetDirty default to +1/+2/+3.")
    p.add_argument("--name", default=None,
                   help="Override inferred snake_case design name (also the giveKey).")
    p.add_argument("--display-name", default=None,
                   help="Override inferred human-readable name.")
    p.add_argument("--equippable-key", default=None,
                   help="Override the equipment-definition id (default: design name / giveKey).")
    p.add_argument("--namespace", default="minecraft", help="Resource pack namespace.")
    p.add_argument("--dry-run", action="store_true",
                   help="Print JSON to stdout instead of editing images.json or any registries.")
    args = p.parse_args()

    if args.sync_icons:
        sync_icons_from_applied()
        return

    if not args.dir:
        p.error("dir is required (or use --sync-icons)")
    dir_path = os.path.abspath(args.dir)
    if not os.path.isdir(dir_path):
        sys.exit("ERROR: not a directory: " + dir_path)

    # Detection cascade for category and design name:
    #   1. --category / --name explicit overrides win.
    #   2. Folder prefix (pullup-, diaper-, thick-, undies-) — primary signal.
    #   3. PNG count — fallback when no prefix matches.
    # PNG count always validates against (1)/(2); a mismatch warns loudly.
    folder_basename = os.path.basename(dir_path)
    prefix_cat, stripped = infer_category_from_prefix(folder_basename)
    pngs_cat = infer_category_from_pngs(dir_path)

    if args.category is not None:
        category = args.category
        if prefix_cat is not None and prefix_cat != category:
            print("warning: folder prefix suggests category=%d but --category=%d" %
                  (prefix_cat, category), file=sys.stderr)
        if pngs_cat is not None and pngs_cat != category:
            print("warning: PNG count suggests category=%d but --category=%d" %
                  (pngs_cat, category), file=sys.stderr)
    elif prefix_cat is not None:
        category = prefix_cat
        if pngs_cat is not None and pngs_cat != category:
            print("warning: folder prefix says category=%d (%s) but PNG count says category=%d (%s). "
                  "Trusting prefix; check filenames or pass --category to override." %
                  (prefix_cat, CAT_NAMES[prefix_cat], pngs_cat, CAT_NAMES[pngs_cat]),
                  file=sys.stderr)
    elif pngs_cat is not None:
        category = pngs_cat
        print("note: no category prefix on folder; inferred category=%d (%s) from PNG count. "
              "Rename the folder to '%s-<name>/' to make this explicit." %
              (category, CAT_NAMES[category], CAT_NAMES[category].replace(" ", "_")),
              file=sys.stderr)
    else:
        sys.exit("ERROR: cannot determine category. Rename the folder to start with a category "
                 "prefix (pullup-, diaper-, thick-, undies-) or pass --category 0|1|2|3.")

    # Design name: strip the prefix when it was used.
    inferred_name = stripped if prefix_cat is not None else folder_basename
    design_name = args.name or re.sub(r"[^a-zA-Z0-9]+", "_", inferred_name).strip("_").lower()
    display_name = args.display_name or design_to_display_name(design_name)
    design_id = args.design_id if args.design_id is not None else next_design_id(category)
    if design_id < 1:
        sys.exit("ERROR: design-id must be >= 1 (0 is reserved for legacy)")

    # Equipment id defaults to the giveKey/design name. Override when a design
    # wants to share a single equipment texture across multiple designs.
    equippable_key = args.equippable_key or design_name

    # CMD reservation
    if args.dry_run:
        cmd_reg = load_cmd_registry()
        clean_cmd = args.cmd if args.cmd is not None else cmd_reg.get("next", DEFAULT_CMD_BASE)
    else:
        cmd_reg = load_cmd_registry()
        if args.cmd is not None:
            clean_cmd = args.cmd
            cmd_reg["allocations"].append({"design": design_name, "cleanCmd": clean_cmd, "manual": True})
            if clean_cmd + DEFAULT_CMD_STEP > cmd_reg.get("next", DEFAULT_CMD_BASE):
                cmd_reg["next"] = clean_cmd + DEFAULT_CMD_STEP
        else:
            clean_cmd = reserve_cmd_block(cmd_reg, design_name)
        save_cmd_registry(cmd_reg)

    wet_cmd = clean_cmd + 1
    dirty_cmd = clean_cmd + 2
    wetdirty_cmd = clean_cmd + 3

    print("Design summary", file=sys.stderr)
    print("  name:           %s" % design_name, file=sys.stderr)
    print("  display:        %s" % display_name, file=sys.stderr)
    print("  category:       %d (%s)" % (category, CAT_NAMES[category]), file=sys.stderr)
    print("  designId:       %d" % design_id, file=sys.stderr)
    print("  CMDs:           %d / %d / %d / %d" % (clean_cmd, wet_cmd, dirty_cmd, wetdirty_cmd),
          file=sys.stderr)
    print("  equippableKey:  %s" % equippable_key, file=sys.stderr)

    # Build font entries
    file_prefix = compute_file_prefix(dir_path, args.namespace)
    stage_files = discover_stage_files(dir_path, category)
    entries_block = build_entries_block(category, design_id, file_prefix, stage_files)

    if args.dry_run:
        print(entries_block)
        print("\n(dry run -- no files modified)", file=sys.stderr)
        return

    # Splice JSON
    provider_count = splice_into_images_json(entries_block)
    print("images.json now has %d providers" % provider_count, file=sys.stderr)

    # Write manifest (needed before icon plumbing so cmds_to_models can reference it)
    manifest = {
        "designName":     design_name,
        "displayName":    display_name,
        "category":       category,
        "designId":       design_id,
        "cleanCmd":       clean_cmd,
        "wetCmd":         wet_cmd,
        "dirtyCmd":       dirty_cmd,
        "wetDirtyCmd":    wetdirty_cmd,
        "methodName":     snake_to_pascal(design_name),
        "giveKey":        design_name,
        "equippableKey":  equippable_key,
        "folderBasename": folder_basename,  # used by --sync-icons to find source PNGs
    }

    # Inventory icon plumbing -- only if <design>/icons/clean.png exists.
    # Generates models/item/<design>_<state>.json and splices range_dispatch
    # entries into assets/minecraft/items/slime_ball.json.
    found_icons = discover_icons(dir_path)
    if found_icons is None:
        print("note: no icons/ subfolder found -- skipping inventory icon model generation. "
              "Drop icons/clean.png (and optional wet.png/messy.png/wetmessy.png) into "
              "the design folder and re-run to wire up the inventory item textures.",
              file=sys.stderr)
    elif "clean" not in found_icons:
        print("note: icons/ folder found but icons/clean.png is missing -- skipping. "
              "clean.png is required.", file=sys.stderr)
    else:
        models_written = write_item_models(design_name, dir_path, args.namespace, found_icons)
        cmds_to_models = splice_slime_ball_range_dispatch(design_name, found_icons, manifest)
        print("Wrote %d item model(s): %s" %
              (len(models_written), ", ".join(os.path.basename(p) for p in models_written)),
              file=sys.stderr)
        print("items/slime_ball.json: %s" %
              ", ".join("%d->%s" % (cmd, mref) for cmd, mref in sorted(cmds_to_models.items())),
              file=sys.stderr)

    # Equipment definition + worn-armor texture (replaces OptiFine CIT).
    # Source convention: <design-folder>/armor/clean.png. Writes a single
    # equipment definition + texture per design — per-state worn variants
    # are out of scope for the auto-generator (the in-world leggings model
    # is still controlled by leather_leggings.json's range_dispatch entries
    # which /add-design wires up).
    armor_textures = discover_armor_textures(dir_path)
    if armor_textures is None:
        print("note: no armor/ subfolder found -- skipping equipment definition. "
              "Drop armor/clean.png into the design folder and re-run to wire up "
              "the worn-leggings body texture.", file=sys.stderr)
    elif "clean" not in armor_textures:
        print("note: armor/ folder found but armor/clean.png is missing -- skipping. "
              "clean.png is required.", file=sys.stderr)
    else:
        eq_def_path = write_equipment_definition(equippable_key)
        eq_tex_path = copy_equipment_texture(
            os.path.join(dir_path, "armor", armor_textures["clean"]),
            equippable_key,
        )
        print("Wrote equipment definition: %s" % os.path.basename(eq_def_path), file=sys.stderr)
        print("Copied equipment texture:   %s" % os.path.basename(eq_tex_path), file=sys.stderr)
        extra = [s for s in ICON_STATES if s != "clean" and s in armor_textures]
        if extra:
            print("note: armor/ contains %s.png — only clean.png is wired up by the "
                  "generator. Per-state worn textures are not yet supported here." %
                  ", ".join(extra), file=sys.stderr)

    manifest_path = repo_path(PENDING_MANIFEST_REL)
    os.makedirs(os.path.dirname(manifest_path), exist_ok=True)
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)
    print("Wrote manifest: %s" % manifest_path, file=sys.stderr)
    print("Run /add-design in Claude Code to wire up the Java registration.",
          file=sys.stderr)


if __name__ == "__main__":
    main()
