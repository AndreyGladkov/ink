import unittest
import xml.etree.ElementTree as ElementTree

import make_screenshots as shots


class LibraryTest(unittest.TestCase):
    def test_items_are_listed_newest_first_by_the_time_in_their_names(self):
        stamps = [name.split("_", 1)[1].rsplit(".", 1)[0] for _, name, *_ in shots.LIBRARY]
        self.assertEqual(sorted(stamps, reverse=True), stamps)

    def test_names_are_unique_and_the_opened_items_are_there(self):
        names = [name for _, name, *_ in shots.LIBRARY]
        self.assertEqual(len(names), len(set(names)))
        self.assertIn(shots.VIDEO, names)
        self.assertIn(shots.OPENED_PHOTO, names)

    def test_the_first_page_of_the_grid_is_full(self):
        self.assertGreaterEqual(len(shots.LIBRARY), 13)

    def test_every_scene_draws_a_picture_of_its_size_that_is_not_flat(self):
        for scene in {scene for _, _, scene, _, _ in shots.LIBRARY if scene is not None}:
            image = scene(3, (120, 160))
            self.assertEqual((120, 160), image.size, scene.__name__)
            self.assertGreater(len(set(image.getdata())), 2, scene.__name__)

    def test_a_scene_is_the_same_for_the_same_seed(self):
        self.assertEqual(shots.landscape(5, (80, 60)).tobytes(), shots.landscape(5, (80, 60)).tobytes())


class PrefsTest(unittest.TestCase):
    def test_the_style_is_inverted_only_when_asked(self):
        self.assertIsNotNone(ElementTree.fromstring(shots.prefs_xml(inverted=True)).find("boolean[@name='style_inverted']"))
        self.assertIsNone(ElementTree.fromstring(shots.prefs_xml(inverted=False)).find("boolean[@name='style_inverted']"))


if __name__ == "__main__":
    unittest.main()
