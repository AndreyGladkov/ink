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
ROOT = HERE.parents[2]
PHONE_SCREENSHOTS = ROOT / "ink-phone" / "fastlane" / "screenshots"
METADATA = HERE.parent / "metadata" / "android"
PACKAGE = "io.github.andreygladkov.inklauncher.debug"
LISTENER = f"{PACKAGE}/io.github.andreygladkov.inklauncher.notify.InkNotificationListener"
MESSAGES = "io.github.andreygladkov.inkmessages.debug"
WEATHER = "io.github.andreygladkov.inkweather.debug"
HOME_ROLE = "android.app.role.HOME"
SAME_NAMED_GOOGLE_APPS = ["com.google.android.deskclock", "com.google.android.apps.messaging", "com.google.android.dialer"]
SMS_ROLE = "android.app.role.SMS"


def ink(app):
    return f"io.github.andreygladkov.ink{app}.debug/io.github.andreygladkov.ink{app}.MainActivity"


TOP_ROW = [ink("calculator"), ink("weather"), "com.android.chrome/com.google.android.apps.chrome.Main", "com.android.settings/com.android.settings.Settings"]
WEATHER_APP_TILE = (309, 357)
DOCK = [ink("phone"), ink("messages"), ink("clock"), "com.android.camera2/com.android.camera.CameraLauncher"]

LOCALES = {
    "en": dict(
        folder="en-US", app_locale="en-US", add_widget="Widget…", next_page="Next page", prev_page="Previous page",
        all_apps="All apps", settings="Launcher settings", by_app="By app",
        city=dict(id=5128581, name="New York", region="New York", country="United States", latitude=40.7143, longitude=-74.006, timezone="America/New_York"),
        contacts=[
            dict(given="Emma", family="Wilson", phone="+15550123", starred=True),
            dict(given="Mom", family="", phone="+15550142", starred=True),
        ],
        texts=[
            ("+15550142", "Call me tonight, I have news"),
            ("7722", "Card payment $12.50 at Corner Coffee. Available: $1,840.55"),
            ("+15550123", "Running 10 minutes late, order me a flat white?"),
        ],
    ),
    "ru": dict(
        folder="ru", app_locale="ru-RU", add_widget="Виджет…", next_page="Следующая страница", prev_page="Предыдущая страница",
        all_apps="Все приложения", settings="Настройки лаунчера", by_app="По приложениям",
        city=dict(id=524901, name="Москва", region="Москва", country="Россия", latitude=55.7522, longitude=37.6156, timezone="Europe/Moscow"),
        contacts=[
            dict(given="Анна", family="Кузнецова", phone="+79005550123", starred=True),
            dict(given="Мама", family="", phone="+79005550142", starred=True),
        ],
        texts=[
            ("+79005550142", "Позвони вечером, расскажу новости"),
            ("7722", "Покупка 1 250,00 ₽, КОФЕЙНЯ. Доступно 18 340,55 ₽"),
            ("+79005550123", "Опаздываю на 10 минут, возьми мне флэт уайт?"),
        ],
    ),
}


def load_phone_script():
    """Ink Phone's script already drives the emulator, seeds contacts and cleans the status bar."""
    spec = importlib.util.spec_from_file_location("phone_screenshots", PHONE_SCREENSHOTS / "make_screenshots.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


phone = load_phone_script()


def prefs_xml(entries):
    return "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + "".join(f"    {entry}\n" for entry in entries) + "</map>\n"


def city_xml(city):
    saved = escape(json.dumps([city], ensure_ascii=False), {'"': "&quot;"})
    return prefs_xml([f'<string name="places">{saved}</string>', f'<long name="current" value="{city["id"]}" />'])


def tile(tile_id, app, sheet, column):
    return dict(type="app", id=tile_id, app=app, labelHidden=False, sheet=sheet, page=0, column=column, row=0 if sheet == "bottom" else 1, width=1, height=1)


def home_json():
    """A top row left free for the weather widget beside the screen time tile, apps below it and in the dock."""
    tiles = [dict(type="screen_time", id=1, sheet="top", page=0, column=2, row=0, width=2, height=1)]
    tiles += [tile(10 + column, app, "top", column) for column, app in enumerate(TOP_ROW)]
    tiles += [tile(20 + column, app, "bottom", column) for column, app in enumerate(DOCK)]
    return json.dumps(dict(version=1, spec=dict(columns=4, rows=7, pinnedTop=4, pinnedBottom=1), pages=1, tiles=tiles))


class Launcher(phone.Device):
    def prepare(self):
        self.shell("settings put global sysui_demo_allowed 1")
        self.shell("settings put system notification_cooldown_enabled 0")
        self.shell("settings put secure icon_blacklist satellite,location,rotate,mobile,wifi,airplane,ethernet,connected_display,vpn")
        self.shell(f"cmd role add-role-holder {SMS_ROLE} {MESSAGES}")
        self.shell(f"pm grant {MESSAGES} android.permission.POST_NOTIFICATIONS || true")
        self.shell(f"appwidget grantbind --package {PACKAGE} --user 0")

    def write_file(self, package, text, name, folder):
        self.shell(f"am force-stop {package}")
        path = self.push_text(text, name)
        self.shell(f"run-as {package} sh -c 'mkdir -p {folder} && cat {path} > {folder}/{name}'")

    def seed(self, texts):
        for package in (PACKAGE, WEATHER):
            self.shell(f"pm clear {package}")
        for package in (PACKAGE, MESSAGES, WEATHER):
            self.shell(f"cmd locale set-app-locales {package} --locales {texts['app_locale']}")
        self.shell(f"appops set {PACKAGE} GET_USAGE_STATS allow")
        self.shell(f"cmd notification allow_listener {LISTENER}")
        self.shell("pm clear com.android.providers.contacts")
        self.shell("sh " + self.push_text("\n".join(phone.contact_commands(texts["contacts"])) + "\n", "contacts.sh"))
        self.write_file(WEATHER, city_xml(texts["city"]), "places.xml", "shared_prefs")
        self.write_file(PACKAGE, home_json(), "home.json", "files")

    def use_apps(self):
        """Screen time counts only real use, so the tile has something to show once the apps have been open a while."""
        for app, seconds in ((ink("weather"), 70), (ink("calculator"), 40)):
            self.shell(f"am start -W -n {app}")
            time.sleep(seconds)

    def go_home(self, texts):
        for _ in range(10):
            self.shell("input keyevent KEYCODE_HOME")
            time.sleep(3)
            if f'content-desc="{texts["all_apps"]}"' in self.ui() or f'text="{texts["all_apps"]}"' in self.ui():
                return
        raise RuntimeError("Ink Launcher's home did not show")

    def open_screen_time(self):
        """The tile's description carries its numbers, so it is found by its place in the top row instead."""
        self.shell("input tap 618 130")
        time.sleep(3)

    def hold(self, x, y):
        self.shell(f"input swipe {x} {y} {x} {y} 1200")
        time.sleep(2)

    def tap_containing(self, fragment):
        for node in re.findall(r"<node [^>]*>", self.ui()):
            if re.search(r'text="[^"]*' + re.escape(fragment), node):
                left, top, right, bottom = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
                self.shell(f"input tap {(left + right) // 2} {(top + bottom) // 2}")
                time.sleep(2)
                return True
        return False

    def add_weather_widget(self, texts):
        for _ in range(10):
            self.go_home(texts)
            self.hold(103, 130)
            if f'text="{texts["add_widget"]}"' in self.ui():
                break
        self.tap(text=texts["add_widget"])
        while not self.tap_containing("Ink Weather · "):
            self.tap(desc=texts["next_page"])
        if not self.tap_containing(texts["city"]["name"]):
            raise RuntimeError("the widget's city list did not open")
        time.sleep(8)

    def send_texts(self, texts):
        self.clear_notifications()
        for sender, text in texts["texts"]:
            self.adb("emu", "sms", "send", sender, text)
            time.sleep(4)


def shoot(device, locale):
    texts = LOCALES[locale]
    out = METADATA / texts["folder"] / "images" / "phoneScreenshots"
    out.mkdir(parents=True, exist_ok=True)
    light = PHONE_SCREENSHOTS / "data" / "status-light.png"

    device.seed(texts)
    device.use_apps()
    device.shell(f"cmd role add-role-holder {HOME_ROLE} {PACKAGE}")
    device.add_weather_widget(texts)
    device.send_texts(texts)

    device.go_home(texts)
    device.screenshot(out / "1.png", light)
    device.tap(desc=texts["prev_page"])
    device.screenshot(out / "2.png", light)
    device.go_home(texts)
    device.tap(desc=texts["all_apps"])
    device.screenshot(out / "3.png", light)
    device.go_home(texts)
    device.open_screen_time()
    device.tap(text=texts["by_app"])
    device.screenshot(out / "4.png", light)
    device.go_home(texts)
    device.hold(*WEATHER_APP_TILE)
    device.screenshot(out / "5.png", light)
    device.go_home(texts)
    device.hold(412, 900)
    device.tap(text=texts["settings"])
    device.screenshot(out / "6.png", light)
    device.clear_notifications()


def main():
    parser = argparse.ArgumentParser(
        description="Retake the store screenshots on an emulator: the 824x1648, 360 dpi AVD (ink_a9) with internet access "
                    "and the debug builds of Ink Launcher, Ink Messages, Ink Weather, Ink Phone, Ink Clock and Ink Calculator "
                    "installed. Ink Launcher's and Ink Weather's data and the contacts are wiped first; the home role is "
                    "given back at the end.",
    )
    parser.add_argument("locales", nargs="*", metavar="locale", help="en, ru; both by default")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    locales = args.locales or list(LOCALES)
    unknown = [locale for locale in locales if locale not in LOCALES]
    if unknown:
        parser.error(f"unknown locale: {', '.join(unknown)}")
    device = Launcher(args.serial)
    device.prepare()
    home = device.shell(f"cmd role get-role-holders {HOME_ROLE}").strip()
    # Google's clock, messages and phone have the same names as Ink's, so the list of all apps would show each twice.
    for package in SAME_NAMED_GOOGLE_APPS:
        device.shell(f"pm disable-user {package}")
    try:
        for locale in locales:
            shoot(device, locale)
    finally:
        for package in SAME_NAMED_GOOGLE_APPS:
            device.shell(f"pm enable {package}")
        device.shell(f"cmd role add-role-holder {HOME_ROLE} {home}")
        device.shell("input keyevent KEYCODE_HOME")


if __name__ == "__main__":
    main()
