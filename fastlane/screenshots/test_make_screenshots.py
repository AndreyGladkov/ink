import datetime as dt
import json
import unittest
from pathlib import Path

import make_screenshots as shots

DATA = Path(__file__).resolve().parent / "data"


class SeedTest(unittest.TestCase):
    def test_contact_gets_raw_contact_name_and_phone_rows(self):
        commands = shots.contact_commands([{"given": "Ben", "family": "Carter", "phone": "+1 555-0104", "starred": True}])
        self.assertEqual(3, len(commands))
        self.assertIn("starred:i:1", commands[0])
        self.assertIn("raw_contact_id:i:1", commands[1])
        self.assertIn("data1:s:'Ben Carter'", commands[1])
        self.assertIn("data1:s:'+1 555-0104'", commands[2])

    def test_single_name_contact_has_no_trailing_space(self):
        command = shots.contact_commands([{"given": "Mom", "family": "", "phone": "+1", "starred": False}])[1]
        self.assertIn("data1:s:Mom ", command)

    def test_call_date_is_counted_back_from_today(self):
        call = {"number": "+1 555-0118", "type": 3, "days_ago": 1, "time": "21:18", "duration": 0, "new": True, "name": "Mom"}
        command = shots.call_commands([call], dt.date(2026, 11, 3))[0]
        expected = int(dt.datetime(2026, 11, 2, 21, 18).timestamp() * 1000)
        self.assertIn(f"date:l:{expected}", command)
        self.assertIn("new:i:1 --bind is_read:i:0", command)
        self.assertIn("name:s:Mom", command)

    def test_unknown_caller_has_no_name(self):
        call = {"number": "+1 555-0199", "type": 5, "days_ago": 0, "time": "09:12", "duration": 0, "new": False}
        self.assertNotIn("name:s:", shots.call_commands([call], dt.date(2026, 11, 3))[0])

    def test_quotes_survive_shell(self):
        command = shots.contact_commands([{"given": "O'Neil", "family": "", "phone": "+1", "starred": False}])[1]
        self.assertIn("""'O'"'"'Neil'""", command)


class PrefsTest(unittest.TestCase):
    def test_speed_dial_and_theme(self):
        xml = shots.prefs_xml({"3": "+1 555-0104", "2": "+1 555-0118"}, inverted=True)
        self.assertLess(xml.index("speed_dial_2"), xml.index("speed_dial_3"))
        self.assertIn('<boolean name="style_inverted" value="true" />', xml)
        self.assertNotIn("style_inverted", shots.prefs_xml({}))


class DataTest(unittest.TestCase):
    def test_every_locale_has_matching_data_and_speed_dials_point_to_contacts(self):
        for locale in shots.LOCALES:
            data = json.loads((DATA / f"{locale}.json").read_text(encoding="utf-8"))
            phones = {contact["phone"] for contact in data["contacts"]}
            self.assertTrue(set(data["speed_dial"].values()) <= phones, locale)
            names = {f"{c['given']} {c['family']}".strip() for c in data["contacts"]}
            self.assertIn(shots.LOCALES[locale]["card"], names, locale)


if __name__ == "__main__":
    unittest.main()
