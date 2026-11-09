import json
import unittest
import xml.etree.ElementTree as ElementTree

import make_screenshots as shots


class SeedTest(unittest.TestCase):
    def test_places_are_stored_as_json_the_app_reads_with_the_first_shown(self):
        places = shots.LOCALES["ru"]["places"]
        root = ElementTree.fromstring(shots.places_xml(places))
        self.assertEqual(places, json.loads(root.find("string[@name='places']").text))
        self.assertEqual(str(places[0]["id"]), root.find("long[@name='current']").get("value"))

    def test_every_place_has_what_the_app_needs(self):
        for texts in shots.LOCALES.values():
            for place in texts["places"]:
                self.assertEqual({"id", "name", "region", "country", "latitude", "longitude", "timezone"}, set(place))

    def test_the_style_is_inverted_only_when_asked(self):
        self.assertIsNotNone(ElementTree.fromstring(shots.style_xml(inverted=True)).find("boolean[@name='style_inverted']"))
        self.assertIsNone(ElementTree.fromstring(shots.style_xml(inverted=False)).find("boolean[@name='style_inverted']"))


if __name__ == "__main__":
    unittest.main()
