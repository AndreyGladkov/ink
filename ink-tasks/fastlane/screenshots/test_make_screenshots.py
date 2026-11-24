import datetime as dt
import re
import unittest
import xml.etree.ElementTree as ElementTree

import make_screenshots as shots

TODAY = dt.date(2026, 11, 24)
TASK = re.compile(r"^(\t?)- \[([ x])] (.+)$")
FORBIDDEN = set('*"\\/<>:|?#^[]')


class ListsTest(unittest.TestCase):
    def test_tasks_are_written_in_obsidian_tasks_syntax_with_dates_from_today(self):
        tasks = [("Gift", 3, False, [("Book", True)], ["Ask Kate"]), ("Flowers", None, True, [], [])]
        self.assertEqual(
            "- [ ] Gift 📅 2026-11-27\n\t- [x] Book ✅ 2026-11-24\n\tAsk Kate\n- [x] Flowers ✅ 2026-11-24\n",
            shots.list_markdown(tasks, TODAY),
        )

    def test_every_line_is_a_task_or_a_note_under_one(self):
        for texts in shots.LOCALES.values():
            for tasks in texts["lists"].values():
                lines = shots.list_markdown(tasks, TODAY).splitlines()
                self.assertRegex(lines[0], TASK)
                for line in lines:
                    self.assertTrue(TASK.match(line) or line.startswith("\t"), line)

    def test_the_shown_list_and_the_opened_task_are_there(self):
        for texts in shots.LOCALES.values():
            self.assertIn(texts["current"], texts["lists"])
            self.assertIn(texts["opened"], [title for title, *_ in texts["lists"][texts["current"]]])

    def test_list_names_are_ones_the_app_accepts(self):
        for texts in shots.LOCALES.values():
            for name in texts["lists"]:
                self.assertFalse(FORBIDDEN & set(name), name)


class PrefsTest(unittest.TestCase):
    def test_the_current_list_is_kept_and_the_style_is_inverted_only_when_asked(self):
        root = ElementTree.fromstring(shots.prefs_xml("Дом", inverted=True))
        self.assertEqual("Дом", root.find("string[@name='current_list']").text)
        self.assertIsNotNone(root.find("boolean[@name='style_inverted']"))
        self.assertIsNone(ElementTree.fromstring(shots.prefs_xml("Дом", inverted=False)).find("boolean[@name='style_inverted']"))


if __name__ == "__main__":
    unittest.main()
