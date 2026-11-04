# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow>=10"]
# ///
import argparse
import importlib.util
import json
import re
import time
import urllib.parse
from pathlib import Path

HERE = Path(__file__).resolve().parent
PHONE_SCREENSHOTS = HERE.parents[2] / "ink-phone" / "fastlane" / "screenshots"
METADATA = HERE.parent / "metadata" / "android"
PACKAGE = "io.github.andreygladkov.inkmessages.debug"
ACTIVITY = f"{PACKAGE}/io.github.andreygladkov.inkmessages.MainActivity"
PERMISSIONS = ["READ_SMS", "SEND_SMS", "RECEIVE_SMS", "READ_CONTACTS", "READ_PHONE_STATE", "POST_NOTIFICATIONS"]

LOCALES = {
    "en": dict(folder="en-US", app_locale="en-US", other="Other", send="Send", search="Search", compose="New message",
               settings="Settings", copy="Copy"),
    "ru": dict(folder="ru", app_locale="ru-RU", other="Прочие", send="Отправить", search="Поиск", compose="Написать",
               settings="Настройки", copy="Копировать"),
}


def load_phone_script():
    """Ink Phone's script already drives the emulator, seeds contacts and cleans the status bar."""
    spec = importlib.util.spec_from_file_location("phone_screenshots", PHONE_SCREENSHOTS / "make_screenshots.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


phone = load_phone_script()


def dialable(number):
    """Kept international: the emulator's country is the US, so a bare 7900… would not match a +7 900… contact."""
    return "".join(c for c in number if c.isdigit() or c == "+")


class Messages(phone.Device):
    def prepare(self):
        # The telephony provider takes writes only from the default SMS app, so messages arrive the way real
        # ones do: incoming through the emulator's radio and outgoing through Ink Messages itself.
        self.shell("pm clear com.android.providers.telephony")
        self.reboot()
        for permission in PERMISSIONS:
            self.shell(f"pm grant {PACKAGE} android.permission.{permission} || true")
        self.shell(f"cmd role add-role-holder android.app.role.SMS {PACKAGE}")
        self.shell("settings put secure icon_blacklist satellite,location,rotate,mobile,wifi,airplane,ethernet,connected_display,vpn")

    def reboot(self):
        """The telephony provider lives in the persistent phone process, which keeps the cleared database open."""
        self.adb("reboot")
        self.adb("wait-for-device")
        while self.shell("getprop sys.boot_completed").strip() != "1":
            time.sleep(2)
        time.sleep(10)

    def seed_contacts(self, contacts):
        self.shell("pm clear com.android.providers.contacts")
        self.shell("sh " + self.push_text("\n".join(phone.contact_commands(contacts)) + "\n", "contacts.sh"))

    def receive(self, number, text):
        self.adb("emu", "sms", "send", dialable(number), text)
        time.sleep(2)

    def send(self, number, text, send_label):
        uri = f"smsto:{urllib.parse.quote(dialable(number))}?body={urllib.parse.quote(text)}"
        self.shell(f"am start -W -a android.intent.action.SENDTO -d {phone.quote(uri)}")
        time.sleep(2)
        self.tap(desc=send_label)
        time.sleep(2)
        self.shell("input keyevent BACK")

    def center(self, text):
        """Matches the start of a text, since an unread conversation's title ends with its count."""
        for node in re.findall(r"<node [^>]*>", self.ui()):
            if f'text="{text}' in node:
                left, top, right, bottom = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
                return (left + right) // 2, (top + bottom) // 2
        raise RuntimeError(f"nothing with text={text!r} on screen")

    def open_conversation(self, title):
        x, y = self.center(title)
        self.shell(f"input tap {x} {y}")
        time.sleep(3)

    def long_press(self, text):
        x, y = self.center(text)
        self.shell(f"input swipe {x} {y} {x} {y} 900")
        time.sleep(2)

    def write_prefs(self, inverted):
        self.shell(f"am force-stop {PACKAGE}")
        entry = '    <boolean name="style_inverted" value="true" />\n' if inverted else ""
        xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + entry + "</map>\n"
        path = self.push_text(xml, "prefs.xml")
        self.shell(f"run-as {PACKAGE} sh -c 'mkdir -p shared_prefs && cat {path} > shared_prefs/ink_messages.xml'")

    def open_main(self):
        self.shell(f"am force-stop {PACKAGE}")
        self.shell(f"am start -W -n {ACTIVITY}")
        time.sleep(3)


# The emulator console drops the high bits of characters outside the Basic Multilingual Plane (👍 arrives as
# U+F44D), so the sample messages only use emoji from that plane; real carriers deliver all of them.
def shoot(device, locale):
    texts = LOCALES[locale]
    data = json.loads((HERE / "data" / f"{locale}.json").read_text(encoding="utf-8"))
    contacts = json.loads((PHONE_SCREENSHOTS / "data" / f"{locale}.json").read_text(encoding="utf-8"))["contacts"]
    out = METADATA / texts["folder"] / "images" / "phoneScreenshots"
    out.mkdir(parents=True, exist_ok=True)
    light, dark = PHONE_SCREENSHOTS / "data" / "status-light.png", PHONE_SCREENSHOTS / "data" / "status-dark.png"

    device.prepare()
    device.shell(f"cmd locale set-app-locales {PACKAGE} --locales {texts['app_locale']}")
    device.write_prefs(inverted=False)
    device.seed_contacts(contacts)
    for conversation in data["conversations"]:
        for direction, text in conversation["messages"]:
            if direction == "in":
                device.receive(conversation["number"], text)
            else:
                device.send(conversation["number"], text, texts["send"])
    device.clear_notifications()

    device.open_main()
    device.screenshot(out / "1.png", light)
    device.open_conversation(data["thread"])
    device.screenshot(out / "2.png", light)
    device.long_press(data["select"])
    device.screenshot(out / "3.png", light)
    device.open_main()
    device.tap(text=texts["other"])
    device.screenshot(out / "4.png", light)
    device.open_main()
    device.tap(desc=texts["search"])
    device.shell(f"input text {phone.quote(data['search'])}")
    time.sleep(2)
    device.shell("input keyevent BACK")
    device.screenshot(out / "5.png", light)
    device.open_main()
    device.tap(text=texts["compose"])
    device.shell(f"input text {data['compose']}")
    time.sleep(2)
    device.shell("input keyevent BACK")
    device.screenshot(out / "6.png", light)
    device.open_main()
    device.tap(desc=texts["settings"])
    device.screenshot(out / "7.png", light)
    device.write_prefs(inverted=True)
    device.open_main()
    device.screenshot(out / "8.png", dark)
    device.write_prefs(inverted=False)


def main():
    parser = argparse.ArgumentParser(
        description="Retake the store screenshots on an emulator: the 824x1648, 360 dpi AVD (ink_a9) with Google APIs, "
                    "the Ink Messages debug build installed, and nothing else that matters on it: its SMS and contacts are wiped.",
    )
    parser.add_argument("locales", nargs="*", metavar="locale", help="en, ru; both by default")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    locales = args.locales or list(LOCALES)
    unknown = [locale for locale in locales if locale not in LOCALES]
    if unknown:
        parser.error(f"unknown locale: {', '.join(unknown)}")
    device = Messages(args.serial)
    for locale in locales:
        shoot(device, locale)


if __name__ == "__main__":
    main()
