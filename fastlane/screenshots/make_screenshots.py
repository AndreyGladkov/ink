# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow>=10"]
# ///
import argparse
import datetime as dt
import io
import json
import re
import shlex
import subprocess
import tempfile
import time
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
METADATA = HERE.parent / "metadata" / "android"
PACKAGE = "io.github.andreygladkov.inkphone.debug"
ACTIVITY = f"{PACKAGE}/io.github.andreygladkov.inkphone.MainActivity"
STATUS_RIGHT = (544, 0, 824, 60)
PERMISSIONS = [
    "READ_CONTACTS", "WRITE_CONTACTS", "READ_CALL_LOG", "WRITE_CALL_LOG",
    "READ_PHONE_STATE", "CALL_PHONE", "POST_NOTIFICATIONS",
]

LOCALES = {
    "en": dict(
        folder="en-US", app_locale="en-US", digits="26", card="Ben Carter", speed_dial="Speed dial",
        caller="+15550118", answer="Answer", speaker="Speaker", end="End", settings="Settings",
    ),
    "ru": dict(
        folder="ru", app_locale="ru-RU", digits="25", card="Дмитрий Новиков", speed_dial="Быстрый набор",
        caller="+79005550116", answer="Принять", speaker="Громкая", end="Завершить", settings="Настройки",
    ),
}


def quote(value):
    return shlex.quote(str(value))


def contact_commands(contacts):
    commands = []
    for raw_id, contact in enumerate(contacts, start=1):
        name = f"{contact['given']} {contact['family']}".strip()
        commands += [
            "content insert --uri content://com.android.contacts/raw_contacts"
            f" --bind account_type:n: --bind account_name:n: --bind starred:i:{int(contact['starred'])}",
            "content insert --uri content://com.android.contacts/data"
            f" --bind raw_contact_id:i:{raw_id} --bind mimetype:s:vnd.android.cursor.item/name"
            f" --bind data1:s:{quote(name)} --bind data2:s:{quote(contact['given'])} --bind data3:s:{quote(contact['family'])}",
            "content insert --uri content://com.android.contacts/data"
            f" --bind raw_contact_id:i:{raw_id} --bind mimetype:s:vnd.android.cursor.item/phone_v2"
            f" --bind data1:s:{quote(contact['phone'])} --bind data2:i:2",
        ]
    return commands


def call_commands(calls, today):
    commands = []
    for call in calls:
        hours, minutes = map(int, call["time"].split(":"))
        at = dt.datetime.combine(today - dt.timedelta(days=call["days_ago"]), dt.time(hours, minutes))
        command = (
            "content insert --uri content://call_log/calls"
            f" --bind number:s:{quote(call['number'])} --bind type:i:{call['type']}"
            f" --bind date:l:{int(at.timestamp() * 1000)} --bind duration:l:{call['duration']}"
            f" --bind new:i:{int(call['new'])} --bind is_read:i:{int(not call['new'])}"
        )
        if "name" in call:
            command += f" --bind name:s:{quote(call['name'])}"
        commands.append(command)
    return commands


def prefs_xml(speed_dial, inverted=False):
    entries = [f'    <string name="speed_dial_{key}">{number}</string>' for key, number in sorted(speed_dial.items())]
    if inverted:
        entries.append('    <boolean name="style_inverted" value="true" />')
    return "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + "\n".join(entries) + "\n</map>\n"


class Device:
    def __init__(self, serial):
        self.serial = serial

    def adb(self, *args):
        return subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, check=True).stdout

    def shell(self, command):
        return self.adb("shell", command).decode()

    def push_text(self, text, name):
        with tempfile.NamedTemporaryFile("w", suffix=name, delete=False, encoding="utf-8") as file:
            file.write(text)
        self.adb("push", file.name, f"/data/local/tmp/{name}")
        Path(file.name).unlink()
        return f"/data/local/tmp/{name}"

    def prepare(self):
        for permission in PERMISSIONS:
            self.shell(f"pm grant {PACKAGE} android.permission.{permission} || true")
        self.shell(f"cmd role add-role-holder android.app.role.DIALER {PACKAGE}")
        self.shell("settings put secure icon_blacklist satellite,location,rotate,mobile,wifi,airplane,ethernet,connected_display,vpn")

    def seed(self, data, today):
        self.shell(f"am force-stop {PACKAGE}")
        self.shell("pm clear com.android.providers.contacts")
        commands = contact_commands(data["contacts"]) + call_commands(data["calls"], today)
        self.shell("sh " + self.push_text("\n".join(commands) + "\n", "seed.sh"))

    def write_prefs(self, xml):
        self.shell(f"am force-stop {PACKAGE}")
        path = self.push_text(xml, "prefs.xml")
        self.shell(f"run-as {PACKAGE} sh -c 'cat {path} > shared_prefs/ink_phone.xml'")

    def open_tab(self, tab):
        self.shell(f"am force-stop {PACKAGE}")
        self.shell(f"am start -W -n {ACTIVITY} --es tab {tab}")
        time.sleep(3)

    def ui(self):
        for _ in range(5):
            if "dumped" in self.shell("uiautomator dump /sdcard/ui.xml"):
                return self.shell("cat /sdcard/ui.xml")
            time.sleep(1)
        raise RuntimeError("uiautomator dump failed")

    def tap(self, text=None, desc=None):
        attribute, value = ("text", text) if text is not None else ("content-desc", desc)
        for node in re.findall(r"<node [^>]*>", self.ui()):
            if f'{attribute}="{value}"' in node:
                left, top, right, bottom = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
                self.shell(f"input tap {(left + right) // 2} {(top + bottom) // 2}")
                time.sleep(2)
                return
        raise RuntimeError(f"nothing with {attribute}={value!r} on screen")

    def clear_notifications(self):
        self.shell("cmd statusbar expand-notifications")
        time.sleep(2)
        try:
            self.tap(text="Clear all")
        except RuntimeError:
            pass
        self.shell("cmd statusbar collapse")
        time.sleep(1)

    def demo(self, *args):
        self.shell("am broadcast -a com.android.systemui.demo -e command " + " ".join(args))

    def screenshot(self, path, status_reference):
        reference = Image.open(status_reference).convert("L").tobytes()
        # The satellite icon keeps reappearing even when listed in icon_blacklist, so the shot is
        # retaken until the right side of the status bar holds nothing but the battery.
        for _ in range(12):
            self.demo("exit")
            time.sleep(0.5)
            self.demo("enter")
            self.demo("clock", "-e", "hhmm", "1000")
            self.demo("battery", "-e", "level", "100", "-e", "plugged", "false")
            self.demo("notifications", "-e", "visible", "false")
            time.sleep(1.5)
            png = self.adb("exec-out", "screencap", "-p")
            if Image.open(io.BytesIO(png)).convert("L").crop(STATUS_RIGHT).tobytes() == reference:
                break
        else:
            print(f"status bar is not clean in {path}")
        path.write_bytes(png)
        print("saved", path)


def shoot(device, locale, today):
    texts = LOCALES[locale]
    data = json.loads((HERE / "data" / f"{locale}.json").read_text(encoding="utf-8"))
    out = METADATA / texts["folder"] / "images" / "phoneScreenshots"
    light, dark = HERE / "data" / "status-light.png", HERE / "data" / "status-dark.png"

    device.seed(data, today)
    device.shell(f"cmd locale set-app-locales {PACKAGE} --locales {texts['app_locale']}")
    device.write_prefs(prefs_xml(data["speed_dial"]))
    device.clear_notifications()

    device.open_tab("DIALER")
    for digit in texts["digits"]:
        device.tap(desc=digit)
    device.screenshot(out / "1.png", light)
    device.open_tab("CALL_LOG")
    device.screenshot(out / "2.png", light)
    device.open_tab("CONTACTS")
    device.screenshot(out / "3.png", light)
    device.tap(text=texts["card"])
    device.screenshot(out / "4.png", light)
    device.open_tab("FAVORITES")
    device.tap(text=texts["speed_dial"])
    device.screenshot(out / "5.png", light)

    device.adb("emu", "gsm", "call", texts["caller"])
    time.sleep(5)
    device.shell("input swipe 412 200 412 20 200")
    time.sleep(1.5)
    device.screenshot(out / "6.png", light)
    device.tap(text=texts["answer"])
    device.tap(text=texts["speaker"])
    time.sleep(62)
    device.screenshot(out / "7.png", light)
    device.tap(text=texts["end"])
    time.sleep(3)

    device.open_tab("CONTACTS")
    device.tap(desc=texts["settings"])
    device.screenshot(out / "8.png", light)
    device.write_prefs(prefs_xml(data["speed_dial"], inverted=True))
    device.open_tab("CONTACTS")
    device.screenshot(out / "9.png", dark)
    device.write_prefs(prefs_xml(data["speed_dial"]))


def main():
    parser = argparse.ArgumentParser(
        description="Retake the store screenshots on an emulator: an 824x1648, 360 dpi AVD (ink_a9) "
                    "running Android with Google APIs, with the debug build installed.",
    )
    parser.add_argument("locales", nargs="*", metavar="locale", help="en, ru; both by default")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    locales = args.locales or list(LOCALES)
    unknown = [locale for locale in locales if locale not in LOCALES]
    if unknown:
        parser.error(f"unknown locale: {', '.join(unknown)}")
    device = Device(args.serial)
    device.prepare()
    for locale in locales:
        shoot(device, locale, dt.date.today())


if __name__ == "__main__":
    main()
