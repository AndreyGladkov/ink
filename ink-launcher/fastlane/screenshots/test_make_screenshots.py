import json
import unittest
import xml.etree.ElementTree as ElementTree

import make_screenshots as shots


class SeedTest(unittest.TestCase):
    def test_the_home_leaves_the_widget_spot_free(self):
        layout = json.loads(shots.home_json())
        top = [tile for tile in layout["tiles"] if tile["sheet"] == "top"]
        self.assertFalse(any(tile["row"] == 0 and tile["column"] < 2 for tile in top))
        self.assertEqual(["screen_time"], [tile["type"] for tile in top if tile["row"] == 0])

    def test_tiles_have_unique_ids_and_apps_the_launcher_can_unflatten(self):
        tiles = json.loads(shots.home_json())["tiles"]
        self.assertEqual(len(tiles), len({tile["id"] for tile in tiles}))
        for tile in tiles:
            if tile["type"] == "app":
                package, activity = tile["app"].split("/")
                self.assertTrue(package and activity)

    def test_the_weather_tile_is_held_where_the_layout_puts_it(self):
        tile = next(tile for tile in json.loads(shots.home_json())["tiles"] if tile.get("app", "").startswith("io.github.andreygladkov.inkweather"))
        self.assertEqual((1, 1), (tile["column"], tile["row"]))

    def test_the_widget_city_is_the_current_place(self):
        city = shots.LOCALES["ru"]["city"]
        root = ElementTree.fromstring(shots.city_xml(city))
        self.assertEqual([city], json.loads(root.find("string[@name='places']").text))
        self.assertEqual(str(city["id"]), root.find("long[@name='current']").get("value"))


if __name__ == "__main__":
    unittest.main()
