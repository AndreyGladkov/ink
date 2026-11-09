# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow>=10"]
# ///
import argparse
import importlib.util
import json
import re
import time
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).resolve().parent
PHONE_SCREENSHOTS = HERE.parents[2] / "ink-phone" / "fastlane" / "screenshots"
METADATA = HERE.parent / "metadata" / "android"
PACKAGE = "io.github.andreygladkov.inkweather.debug"
ACTIVITY = f"{PACKAGE}/io.github.andreygladkov.inkweather.MainActivity"
LAUNCHER = "io.github.andreygladkov.inklauncher.debug"
HOME_ROLE = "android.app.role.HOME"

LOCALES = {
    "en": dict(
        folder="en-US", app_locale="en-US", places_title="Cities", settings="Settings", add_widget="Widget…", next_page="Next page",
        places=[
            dict(id=5128581, name="New York", region="New York", country="United States", latitude=40.7143, longitude=-74.006, timezone="America/New_York"),
            dict(id=2643743, name="London", region="England", country="United Kingdom", latitude=51.5085, longitude=-0.1257, timezone="Europe/London"),
            dict(id=1850147, name="Tokyo", region="Tokyo", country="Japan", latitude=35.6895, longitude=139.6917, timezone="Asia/Tokyo"),
        ],
    ),
    "ru": dict(
        folder="ru", app_locale="ru-RU", places_title="Города", settings="Настройки", add_widget="Виджет…", next_page="Следующая страница",
        places=[
            dict(id=524901, name="Москва", region="Москва", country="Россия", latitude=55.7522, longitude=37.6156, timezone="Europe/Moscow"),
            dict(id=498817, name="Санкт-Петербург", region="Санкт-Петербург", country="Россия", latitude=59.9386, longitude=30.3141, timezone="Europe/Moscow"),
            dict(id=491422, name="Сочи", region="Краснодарский край", country="Россия", latitude=43.6028, longitude=39.7342, timezone="Europe/Moscow"),
        ],
    ),
}


def load_phone_script():
    """Ink Phone's script already drives the emulator and cleans the status bar."""
    spec = importlib.util.spec_from_file_location("phone_screenshots", PHONE_SCREENSHOTS / "make_screenshots.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


phone = load_phone_script()


def prefs_xml(entries):
    return "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + "".join(f"    {entry}\n" for entry in entries) + "</map>\n"


def places_xml(places):
    saved = escape(json.dumps(places, ensure_ascii=False), {'"': "&quot;"})
    return prefs_xml([f'<string name="places">{saved}</string>', f'<long name="current" value="{places[0]["id"]}" />'])


def style_xml(inverted):
    return prefs_xml(['<boolean name="style_inverted" value="true" />'] if inverted else [])


class Weather(phone.Device):
    def prepare(self):
        self.shell(f"pm clear {PACKAGE}")
        self.shell("settings put global sysui_demo_allowed 1")
        self.shell("settings put secure icon_blacklist satellite,location,rotate,mobile,wifi,airplane,ethernet,connected_display,vpn")

    def write_prefs(self, name, xml):
        self.shell(f"am force-stop {PACKAGE}")
        path = self.push_text(xml, f"{name}.xml")
        self.shell(f"run-as {PACKAGE} sh -c 'mkdir -p shared_prefs && cat {path} > shared_prefs/{name}.xml'")

    def tap_containing(self, fragment):
        for node in re.findall(r"<node [^>]*>", self.ui()):
            if re.search(r'text="[^"]*' + re.escape(fragment), node):
                left, top, right, bottom = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
                self.shell(f"input tap {(left + right) // 2} {(top + bottom) // 2}")
                time.sleep(2)
                return True
        return False

    def add_widget_on_ink_launcher(self, texts):
        """The widget is shown on Ink Launcher's black-and-white home, which takes the home role only for the shot."""
        self.shell(f"pm clear {LAUNCHER}")
        self.shell(f"cmd locale set-app-locales {LAUNCHER} --locales {texts['app_locale']}")
        self.shell(f"cmd role add-role-holder {HOME_ROLE} {LAUNCHER}")
        self.shell(f"appwidget grantbind --package {LAUNCHER} --user 0")
        for _ in range(10):
            self.shell("input keyevent KEYCODE_HOME")
            time.sleep(3)
            self.shell("input swipe 206 300 206 300 1200")
            time.sleep(2)
            if f'text="{texts["add_widget"]}"' in self.ui():
                break
        self.tap(text=texts["add_widget"])
        while not self.tap_containing("Ink Weather · "):
            self.tap(desc=texts["next_page"])
        if not self.tap_containing(texts["places"][0]["name"]):
            raise RuntimeError("the widget's city list did not open")
        time.sleep(6)

    def open_forecast(self):
        """The forecast comes from the network, so the shot waits for it after the screen opens."""
        self.shell(f"am force-stop {PACKAGE}")
        self.shell(f"am start -W -n {ACTIVITY}")
        time.sleep(8)


def shoot(device, locale):
    texts = LOCALES[locale]
    out = METADATA / texts["folder"] / "images" / "phoneScreenshots"
    out.mkdir(parents=True, exist_ok=True)
    light, dark = PHONE_SCREENSHOTS / "data" / "status-light.png", PHONE_SCREENSHOTS / "data" / "status-dark.png"

    device.prepare()
    device.shell(f"cmd locale set-app-locales {PACKAGE} --locales {texts['app_locale']}")
    device.write_prefs("places", places_xml(texts["places"]))
    device.write_prefs("ink_weather", style_xml(inverted=False))

    device.open_forecast()
    device.screenshot(out / "1.png", light)
    device.tap(desc=texts["places_title"])
    device.screenshot(out / "2.png", light)
    device.open_forecast()
    device.tap(desc=texts["settings"])
    device.screenshot(out / "3.png", light)
    device.write_prefs("ink_weather", style_xml(inverted=True))
    device.open_forecast()
    device.screenshot(out / "4.png", dark)
    device.write_prefs("ink_weather", style_xml(inverted=False))
    home = device.shell(f"cmd role get-role-holders {HOME_ROLE}").strip()
    try:
        device.add_widget_on_ink_launcher(texts)
        device.screenshot(out / "5.png", light)
    finally:
        device.shell(f"cmd role add-role-holder {HOME_ROLE} {home}")
        device.shell("input keyevent KEYCODE_HOME")
    device.shell(f"am force-stop {PACKAGE}")


def main():
    parser = argparse.ArgumentParser(
        description="Retake the store screenshots on an emulator: the 824x1648, 360 dpi AVD (ink_a9) with internet access "
                    "and the Ink Weather and Ink Launcher debug builds installed. Both apps' data is wiped first.",
    )
    parser.add_argument("locales", nargs="*", metavar="locale", help="en, ru; both by default")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    locales = args.locales or list(LOCALES)
    unknown = [locale for locale in locales if locale not in LOCALES]
    if unknown:
        parser.error(f"unknown locale: {', '.join(unknown)}")
    device = Weather(args.serial)
    for locale in locales:
        shoot(device, locale)


if __name__ == "__main__":
    main()
