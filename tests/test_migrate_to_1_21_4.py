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
