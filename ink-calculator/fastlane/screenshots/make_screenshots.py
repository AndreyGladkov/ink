# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow>=10"]
# ///
import argparse
import importlib.util
import re
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
PHONE_SCREENSHOTS = HERE.parents[2] / "ink-phone" / "fastlane" / "screenshots"
METADATA = HERE.parent / "metadata" / "android"
PACKAGE = "io.github.andreygladkov.inkcalculator.debug"
ACTIVITY = f"{PACKAGE}/io.github.andreygladkov.inkcalculator.MainActivity"
KEYS = ["1", "2", "5", "0", "×", "1", "2", "=", "÷", "4", "=", "−", "9", "9", "9", "="]

LOCALES = {
    "en": dict(folder="en-US", app_locale="en-US", settings="Settings"),
    "ru": dict(folder="ru", app_locale="ru-RU", settings="Настройки"),
}


def load_phone_script():
    """Ink Phone's script already drives the emulator and cleans the status bar."""
    spec = importlib.util.spec_from_file_location("phone_screenshots", PHONE_SCREENSHOTS / "make_screenshots.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


phone = load_phone_script()


def style_xml(inverted):
    entries = ['    <boolean name="style_inverted" value="true" />\n'] if inverted else []
    return "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + "".join(entries) + "</map>\n"


class Calculator(phone.Device):
    def prepare(self):
        self.shell(f"pm clear {PACKAGE}")
        self.shell("settings put global sysui_demo_allowed 1")
        self.shell("settings put secure icon_blacklist satellite,location,rotate,mobile,wifi,airplane,ethernet,connected_display,vpn")

    def write_style(self, inverted):
        self.shell(f"am force-stop {PACKAGE}")
        path = self.push_text(style_xml(inverted), "prefs.xml")
        self.shell(f"run-as {PACKAGE} sh -c 'mkdir -p shared_prefs && cat {path} > shared_prefs/ink_calculator.xml'")

    def open_and_calculate(self):
        self.shell(f"am force-stop {PACKAGE}")
        self.shell(f"am start -W -n {ACTIVITY}")
        time.sleep(3)
        keys = {}
        for key in KEYS:
            if key not in keys:
                keys[key] = self.center_of(key)
            self.shell("input tap {} {}".format(*keys[key]))
            time.sleep(0.5)

    def center_of(self, text):
        for node in re.findall(r"<node [^>]*>", self.ui()):
            if f'text="{text}"' in node and 'clickable="true"' in node:
                left, top, right, bottom = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
                return (left + right) // 2, (top + bottom) // 2
        raise RuntimeError(f"no key {text!r} on screen")


def shoot(device, locale):
    texts = LOCALES[locale]
    out = METADATA / texts["folder"] / "images" / "phoneScreenshots"
    out.mkdir(parents=True, exist_ok=True)
    light, dark = PHONE_SCREENSHOTS / "data" / "status-light.png", PHONE_SCREENSHOTS / "data" / "status-dark.png"

    device.prepare()
    device.shell(f"cmd locale set-app-locales {PACKAGE} --locales {texts['app_locale']}")
    device.write_style(inverted=False)
    device.open_and_calculate()
    device.screenshot(out / "1.png", light)
    device.tap(desc=texts["settings"])
    device.screenshot(out / "2.png", light)
    device.write_style(inverted=True)
    device.open_and_calculate()
    device.screenshot(out / "3.png", dark)
    device.write_style(inverted=False)
    device.shell(f"am force-stop {PACKAGE}")


def main():
    parser = argparse.ArgumentParser(
        description="Retake the store screenshots on an emulator: the 824x1648, 360 dpi AVD (ink_a9) with the Ink "
                    "Calculator debug build installed. The app's data is wiped first.",
    )
    parser.add_argument("locales", nargs="*", metavar="locale", help="en, ru; both by default")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    locales = args.locales or list(LOCALES)
    unknown = [locale for locale in locales if locale not in LOCALES]
    if unknown:
        parser.error(f"unknown locale: {', '.join(unknown)}")
    device = Calculator(args.serial)
    for locale in locales:
        shoot(device, locale)


if __name__ == "__main__":
    main()
