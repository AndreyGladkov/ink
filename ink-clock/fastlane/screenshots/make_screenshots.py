# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow>=10"]
# ///
import argparse
import importlib.util
import json
import time
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).resolve().parent
PHONE_SCREENSHOTS = HERE.parents[2] / "ink-phone" / "fastlane" / "screenshots"
METADATA = HERE.parent / "metadata" / "android"
PACKAGE = "io.github.andreygladkov.inkclock.debug"
ACTIVITY = f"{PACKAGE}/io.github.andreygladkov.inkclock.MainActivity"
SET_TIMER = f"{PACKAGE}/io.github.andreygladkov.inkclock.ClockIntentsActivity"
DEVICE_PREFS = f"/data/user_de/0/{PACKAGE}/shared_prefs"
MINUTE_MS = 60_000

LOCALES = {
    "en": dict(folder="en-US", app_locale="en-US", weekdays="Weekdays", work="Work", weekend="Long run", tea="Tea", desk="Desk clock", settings="Settings"),
    "ru": dict(folder="ru", app_locale="ru-RU", weekdays="По будням", work="Работа", weekend="Пробежка", tea="Чай", desk="Настольные часы", settings="Настройки"),
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


def alarms_xml(texts):
    alarms = [
        dict(id=1, hour=6, minute=30, days=[1, 2, 3, 4, 5], label=texts["work"], enabled=True, vibrates=True),
        dict(id=2, hour=8, minute=0, days=[6, 7], label=texts["weekend"], enabled=True, vibrates=True),
        dict(id=3, hour=22, minute=15, days=[], label="", enabled=False, vibrates=True),
    ]
    return prefs_xml([f'<string name="alarms">{escape(json.dumps(alarms, ensure_ascii=False), {chr(34): "&quot;"})}</string>'])


def stopwatch_xml():
    laps = [62_400, 125_100, 191_800, 254_300]
    return prefs_xml([f'<long name="elapsed" value="{301_700}" />', f'<string name="laps">{",".join(map(str, laps))}</string>'])


def pomodoro_xml(now_ms, today):
    return prefs_xml([
        '<string name="phase">WORK</string>',
        f'<long name="started_at" value="{now_ms - 11 * MINUTE_MS - 20_000}" />',
        '<int name="cycle_done" value="2" />',
        '<int name="done_today" value="5" />',
        f'<long name="day" value="{today}" />',
    ])


class Clock(phone.Device):
    def prepare(self):
        self.shell(f"pm clear {PACKAGE}")
        self.shell(f"pm grant {PACKAGE} android.permission.POST_NOTIFICATIONS || true")
        self.shell("settings put secure immersive_mode_confirmations confirmed")
        self.shell("settings put secure icon_blacklist satellite,location,rotate,mobile,wifi,airplane,ethernet,connected_display,vpn")

    def now_ms(self):
        return int(self.shell("date +%s").strip()) * 1000

    def write_device_prefs(self, name, xml):
        """Alarms, timer, stopwatch and pomodoro live in device-protected storage so that alarms ring before the first unlock."""
        self.shell(f"am force-stop {PACKAGE}")
        path = self.push_text(xml, f"{name}.xml")
        self.shell(f"run-as {PACKAGE} sh -c 'mkdir -p {DEVICE_PREFS} && cat {path} > {DEVICE_PREFS}/{name}.xml'")

    def write_style(self, inverted):
        self.shell(f"am force-stop {PACKAGE}")
        entry = ['<boolean name="style_inverted" value="true" />'] if inverted else []
        path = self.push_text(prefs_xml(entry), "prefs.xml")
        self.shell(f"run-as {PACKAGE} sh -c 'mkdir -p shared_prefs && cat {path} > shared_prefs/ink_clock.xml'")

    def open_tab(self, tab):
        self.shell(f"am force-stop {PACKAGE}")
        self.shell(f"am start -W -n {ACTIVITY} --es tab {tab}")
        time.sleep(3)

    def start_timer(self, seconds, label):
        self.shell(f"am start -W -n {SET_TIMER} -a android.intent.action.SET_TIMER "
                   f"--ei android.intent.extra.alarm.LENGTH {seconds} --es android.intent.extra.alarm.MESSAGE {phone.quote(label)}")
        time.sleep(3)

    def fullscreen_shot(self, path):
        """The desk clock hides the status bar, so there is nothing to clean up and compare."""
        time.sleep(2)
        path.write_bytes(self.adb("exec-out", "screencap", "-p"))
        print("saved", path)


def shoot(device, locale):
    texts = LOCALES[locale]
    out = METADATA / texts["folder"] / "images" / "phoneScreenshots"
    out.mkdir(parents=True, exist_ok=True)
    light, dark = PHONE_SCREENSHOTS / "data" / "status-light.png", PHONE_SCREENSHOTS / "data" / "status-dark.png"

    device.prepare()
    device.shell(f"cmd locale set-app-locales {PACKAGE} --locales {texts['app_locale']}")
    device.write_style(inverted=False)
    device.write_device_prefs("alarms", alarms_xml(texts))
    device.write_device_prefs("stopwatch", stopwatch_xml())

    device.open_tab("ALARMS")
    device.screenshot(out / "1.png", light)
    device.tap(desc=texts["desk"])
    device.fullscreen_shot(out / "6.png")
    device.open_tab("ALARMS")
    device.tap(text=f"{texts['weekdays']} · {texts['work']}")
    device.screenshot(out / "2.png", light)
    now = device.now_ms()
    device.write_device_prefs("pomodoro", pomodoro_xml(now, now // (24 * 60 * MINUTE_MS)))
    device.open_tab("POMODORO")
    device.screenshot(out / "3.png", light)
    device.open_tab("TIMER")
    device.start_timer(25 * 60, texts["tea"])
    device.screenshot(out / "4.png", light)
    device.open_tab("STOPWATCH")
    device.screenshot(out / "5.png", light)
    device.open_tab("ALARMS")
    device.tap(desc=texts["settings"])
    device.screenshot(out / "7.png", light)
    device.write_style(inverted=True)
    device.open_tab("POMODORO")
    device.screenshot(out / "8.png", dark)
    device.clear_notifications()
    device.shell(f"am force-stop {PACKAGE}")


def main():
    parser = argparse.ArgumentParser(
        description="Retake the store screenshots on an emulator: the 824x1648, 360 dpi AVD (ink_a9) with the Ink Clock "
                    "debug build installed. The app's data is wiped first.",
    )
    parser.add_argument("locales", nargs="*", metavar="locale", help="en, ru; both by default")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    locales = args.locales or list(LOCALES)
    unknown = [locale for locale in locales if locale not in LOCALES]
    if unknown:
        parser.error(f"unknown locale: {', '.join(unknown)}")
    device = Clock(args.serial)
    for locale in locales:
        shoot(device, locale)


if __name__ == "__main__":
    main()
