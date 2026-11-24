# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow>=10"]
# ///
import argparse
import datetime as dt
import importlib.util
import re
import time
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).resolve().parent
PHONE_SCREENSHOTS = HERE.parents[2] / "ink-phone" / "fastlane" / "screenshots"
METADATA = HERE.parent / "metadata" / "android"
PACKAGE = "io.github.andreygladkov.inktasks.debug"
ACTIVITY = f"{PACKAGE}/io.github.andreygladkov.inktasks.MainActivity"

# Each task is (title, due in days from today or None, done, subtasks as (title, done), notes).
LOCALES = {
    "en": dict(
        folder="en-US", app_locale="en-US", settings="Settings", other_date="Other date…", opened="Buy a gift for Mum",
        current="Home",
        lists={
            "Home": [
                ("Book a dentist appointment", -1, False, [], []),
                ("Pay the utility bills", 0, False, [], []),
                ("Buy a gift for Mum", 3, False, [("Choose a book", True), ("Wrap it", False)], ["She likes detective stories, ask Kate"]),
                ("Change the bulb in the hall", None, False, [], []),
                ("Take the winter tyres to the garage", 7, False, [], []),
                ("Clear out the balcony", None, False, [], []),
                ("Water the plants", None, True, [], []),
                ("Get rid of the old wardrobe", None, True, [], []),
            ],
            "Work": [
                ("Send the report", 1, False, [], []),
                ("Prepare slides for Monday", 6, False, [], []),
            ],
            "Groceries": [
                ("Milk", None, False, [], []),
                ("Bread", None, False, [], []),
                ("Apples", None, False, [], []),
            ],
            "Reading": [
                ("The Name of the Rose", None, False, [], []),
            ],
        },
    ),
    "ru": dict(
        folder="ru", app_locale="ru-RU", settings="Настройки", other_date="Другая дата…", opened="Купить подарок маме",
        current="Дом",
        lists={
            "Дом": [
                ("Записаться к стоматологу", -1, False, [], []),
                ("Оплатить коммуналку", 0, False, [], []),
                ("Купить подарок маме", 3, False, [("Выбрать книгу", True), ("Упаковать", False)], ["Любит детективы, спросить у Лены"]),
                ("Поменять лампочку в коридоре", None, False, [], []),
                ("Поменять резину на зимнюю", 7, False, [], []),
                ("Разобрать балкон", None, False, [], []),
                ("Полить цветы", None, True, [], []),
                ("Вывезти старый шкаф", None, True, [], []),
            ],
            "Работа": [
                ("Отправить отчёт", 1, False, [], []),
                ("Подготовить слайды к понедельнику", 6, False, [], []),
            ],
            "Покупки": [
                ("Молоко", None, False, [], []),
                ("Хлеб", None, False, [], []),
                ("Яблоки", None, False, [], []),
            ],
            "Книги": [
                ("Имя розы", None, False, [], []),
            ],
        },
    ),
}


def task_line(indent, title, due, done, today):
    line = f"{indent}- [{'x' if done else ' '}] {title}"
    if due is not None:
        line += f" 📅 {(today + dt.timedelta(days=due)).isoformat()}"
    if done:
        line += f" ✅ {today.isoformat()}"
    return line


def list_markdown(tasks, today):
    """Obsidian Tasks syntax, the way the app writes it: subtasks and notes indented by a tab under their task."""
    lines = []
    for title, due, done, subtasks, notes in tasks:
        lines.append(task_line("", title, due, done, today))
        lines += [task_line("\t", sub, None, sub_done, today) for sub, sub_done in subtasks]
        lines += [f"\t{note}" for note in notes]
    return "\n".join(lines) + "\n"


def prefs_xml(current, inverted):
    entries = [f'<string name="current_list">{escape(current)}</string>']
    if inverted:
        entries.append('<boolean name="style_inverted" value="true" />')
    return "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + "".join(f"    {e}\n" for e in entries) + "</map>\n"


def load_phone_script():
    """Ink Phone's script already drives the emulator and cleans the status bar."""
    spec = importlib.util.spec_from_file_location("phone_screenshots", PHONE_SCREENSHOTS / "make_screenshots.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


phone = load_phone_script()


class Tasks(phone.Device):
    def prepare(self):
        self.shell(f"pm clear {PACKAGE}")
        self.shell("settings put global sysui_demo_allowed 1")
        self.shell("settings put secure icon_blacklist satellite,location,rotate,mobile,wifi,airplane,ethernet,connected_display,vpn")

    def today(self):
        return dt.date.fromisoformat(self.shell("date +%F").strip())

    def seed_lists(self, lists, today):
        self.shell(f"am force-stop {PACKAGE}")
        self.shell(f"run-as {PACKAGE} sh -c 'rm -rf files/tasks && mkdir -p files/tasks'")
        for index, (name, tasks) in enumerate(lists.items()):
            path = self.push_text(list_markdown(tasks, today), f"list{index}.md")
            self.shell(f"run-as {PACKAGE} sh -c \"cat {path} > 'files/tasks/{name}.md'\"")

    def write_prefs(self, xml):
        self.shell(f"am force-stop {PACKAGE}")
        path = self.push_text(xml, "ink_tasks.xml")
        self.shell(f"run-as {PACKAGE} sh -c 'mkdir -p shared_prefs && cat {path} > shared_prefs/ink_tasks.xml'")

    def tap_starting(self, prefix):
        """A task's button also holds its note and date on the lines below the title."""
        for node in re.findall(r"<node [^>]*>", self.ui()):
            if re.search(r'text="' + re.escape(escape(prefix)), node):
                left, top, right, bottom = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
                self.shell(f"input tap {(left + right) // 2} {(top + bottom) // 2}")
                time.sleep(2)
                return
        raise RuntimeError(f"nothing starting with {prefix!r} on screen")

    def open_list(self):
        self.shell(f"am force-stop {PACKAGE}")
        self.shell(f"am start -W -n {ACTIVITY}")
        time.sleep(3)


def shoot(device, locale):
    texts = LOCALES[locale]
    out = METADATA / texts["folder"] / "images" / "phoneScreenshots"
    out.mkdir(parents=True, exist_ok=True)
    light, dark = PHONE_SCREENSHOTS / "data" / "status-light.png", PHONE_SCREENSHOTS / "data" / "status-dark.png"

    device.prepare()
    device.shell(f"cmd locale set-app-locales {PACKAGE} --locales {texts['app_locale']}")
    device.seed_lists(texts["lists"], device.today())
    device.write_prefs(prefs_xml(texts["current"], inverted=False))

    device.open_list()
    device.screenshot(out / "1.png", light)
    device.tap_starting(texts["opened"])
    device.screenshot(out / "2.png", light)
    device.tap(text=texts["other_date"])
    device.screenshot(out / "3.png", light)

    device.open_list()
    device.tap(text=texts["current"])
    device.screenshot(out / "4.png", light)

    device.open_list()
    device.tap(desc=texts["settings"])
    device.screenshot(out / "5.png", light)

    device.write_prefs(prefs_xml(texts["current"], inverted=True))
    device.open_list()
    device.screenshot(out / "6.png", dark)
    device.write_prefs(prefs_xml(texts["current"], inverted=False))
    device.shell(f"am force-stop {PACKAGE}")


def main():
    parser = argparse.ArgumentParser(
        description="Retake the store screenshots on an emulator: the 824x1648, 360 dpi AVD (ink_a9) with the Ink Tasks "
                    "debug build installed. The app's data is wiped and replaced by sample lists dated from the emulator's today.",
    )
    parser.add_argument("locales", nargs="*", metavar="locale", help="en, ru; both by default")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    locales = args.locales or list(LOCALES)
    unknown = [locale for locale in locales if locale not in LOCALES]
    if unknown:
        parser.error(f"unknown locale: {', '.join(unknown)}")
    device = Tasks(args.serial)
    for locale in locales:
        shoot(device, locale)


if __name__ == "__main__":
    main()
