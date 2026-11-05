import json
import unittest
import xml.etree.ElementTree as ElementTree

import make_screenshots as shots


class SeedTest(unittest.TestCase):
    def test_alarms_are_stored_as_json_the_app_reads(self):
        root = ElementTree.fromstring(shots.alarms_xml(dict(work='Работа "A&B"', weekend="Пробежка")))
        alarms = json.loads(root.find("string[@name='alarms']").text)
        self.assertEqual([1, 2, 3], [alarm["id"] for alarm in alarms])
        self.assertEqual('Работа "A&B"', alarms[0]["label"])
        self.assertEqual([6, 7], alarms[1]["days"])
        self.assertFalse(alarms[2]["enabled"])

    def test_stopwatch_is_paused_with_laps_before_its_total(self):
        root = ElementTree.fromstring(shots.stopwatch_xml())
        elapsed = int(root.find("long[@name='elapsed']").get("value"))
        laps = [int(lap) for lap in root.find("string[@name='laps']").text.split(",")]
        self.assertIsNone(root.find("long[@name='started_at']"))
        self.assertEqual(laps, sorted(laps))
        self.assertLess(laps[-1], elapsed)

    def test_pomodoro_is_in_the_middle_of_work_today(self):
        root = ElementTree.fromstring(shots.pomodoro_xml(now_ms=10_000_000, today=42))
        self.assertEqual("WORK", root.find("string[@name='phase']").text)
        started = int(root.find("long[@name='started_at']").get("value"))
        self.assertEqual(11, (10_000_000 - started) // shots.MINUTE_MS)
        self.assertEqual("42", root.find("long[@name='day']").get("value"))


if __name__ == "__main__":
    unittest.main()
