def test_smoke_pytest_runs():
    assert True


import json
from pathlib import Path
from tools.migrate_to_1_21_4.parsers import parse_slime_ball_overrides

FIXTURES = Path(__file__).parent / "fixtures" / "migrate"

def test_parse_slime_ball_overrides_returns_cmd_to_model_map():
    data = json.loads((FIXTURES / "legacy_slime_ball.json").read_text())
    result = parse_slime_ball_overrides(data)
    assert result == {
        625000: "item/sound_enable",
        626001: "item/diaper_thick",
        626002: "item/underwear",
        627000: "item/crib_acacia",
    }


from tools.migrate_to_1_21_4.parsers import parse_leather_leggings_overrides

def test_parse_leather_leggings_overrides_separates_cmds_and_trim():
    data = json.loads((FIXTURES / "legacy_leather_leggings.json").read_text())
    cmds, trims = parse_leather_leggings_overrides(data)
    assert cmds == {
        626001: "minecraft:item/diaper_thick",
        626015: "minecraft:item/pants",
    }
    assert trims == {
        0.1: "minecraft:item/leather_leggings_quartz_trim",
        0.2: "minecraft:item/leather_leggings_iron_trim",
    }


from tools.migrate_to_1_21_4.parsers import parse_cit_properties

def test_parse_cit_properties_extracts_cmd_and_texture():
    raw = (FIXTURES / "cit_undies.properties").read_text()
    result = parse_cit_properties(raw)
    assert result.cmd == 626002
    assert result.texture == "underwear"
    assert result.match_items == "leather_leggings"


from tools.migrate_to_1_21_4.generators import build_items_json

def test_build_items_json_emits_range_dispatch_in_ascending_order():
    cmd_map = {
        626002: "item/underwear",
        626001: "item/diaper_thick",
        625000: "item/sound_enable",
    }
    result = build_items_json("slime_ball", cmd_map)
    model = result["model"]
    assert model["type"] == "minecraft:range_dispatch"
    assert model["property"] == "minecraft:custom_model_data"
    assert model["scale"] == 1.0
    assert model["fallback"]["model"] == "minecraft:item/slime_ball"
    thresholds = [e["threshold"] for e in model["entries"]]
    assert thresholds == sorted(thresholds)
    assert thresholds == [625000.0, 626001.0, 626002.0]
    assert model["entries"][0]["model"]["model"] == "minecraft:item/sound_enable"


from tools.migrate_to_1_21_4.generators import build_leather_leggings_items_json

def test_build_leather_leggings_items_json_nests_trim_select_in_fallback():
    cmd_map = {626015: "minecraft:item/pants", 626001: "minecraft:item/diaper_thick"}
    trim_map = {
        0.1: "minecraft:item/leather_leggings_quartz_trim",
        0.2: "minecraft:item/leather_leggings_iron_trim",
    }
    result = build_leather_leggings_items_json(cmd_map, trim_map)
    model = result["model"]
    assert model["type"] == "minecraft:range_dispatch"
    fallback = model["fallback"]
    assert fallback["type"] == "minecraft:select"
    assert fallback["property"] == "minecraft:trim_material"
    # cases is a list of {when: <material>, model: ...}
    materials = [c["when"] for c in fallback["cases"]]
    assert "minecraft:quartz" in materials
    assert "minecraft:iron" in materials
    # Default fallback is plain leather_leggings model
    assert fallback["fallback"]["model"] == "minecraft:item/leather_leggings"


from tools.migrate_to_1_21_4.generators import build_equipment_json

def test_build_equipment_json_emits_humanoid_leggings_layer():
    result = build_equipment_json("underwear")
    assert result == {
        "layers": {
            "humanoid_leggings": [{"texture": "minecraft:underwear"}]
        }
    }


from tools.migrate_to_1_21_4.orchestrator import enforce_cit_cmds_have_models

def test_enforce_cit_cmds_have_models_passes_when_complete():
    cit_cmds = {626001, 626002}
    slime_cmds = {626001, 626002, 626009}
    leggings_cmds = {626001, 626002}
    # No exception
    enforce_cit_cmds_have_models(cit_cmds, slime_cmds, leggings_cmds)


def test_enforce_cit_cmds_have_models_aborts_on_missing():
    cit_cmds = {626001, 626002, 626099}  # 626099 is in CIT but not in any models file
    slime_cmds = {626001, 626002}
    leggings_cmds = {626001, 626002}
    try:
        enforce_cit_cmds_have_models(cit_cmds, slime_cmds, leggings_cmds)
    except ValueError as e:
        assert "626099" in str(e)
    else:
        raise AssertionError("expected ValueError")
