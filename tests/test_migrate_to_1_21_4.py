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
