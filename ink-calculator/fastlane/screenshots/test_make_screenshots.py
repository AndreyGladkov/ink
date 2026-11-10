import unittest
import xml.etree.ElementTree as ElementTree

import make_screenshots as shots


class SeedTest(unittest.TestCase):
    def test_the_style_is_inverted_only_when_asked(self):
        self.assertIsNotNone(ElementTree.fromstring(shots.style_xml(inverted=True)).find("boolean[@name='style_inverted']"))
        self.assertIsNone(ElementTree.fromstring(shots.style_xml(inverted=False)).find("boolean[@name='style_inverted']"))

    def test_every_calculation_ends_with_equals(self):
        self.assertEqual("=", shots.KEYS[-1])
        self.assertEqual(3, shots.KEYS.count("="))


if __name__ == "__main__":
    unittest.main()
