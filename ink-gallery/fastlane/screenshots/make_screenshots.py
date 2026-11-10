# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow>=10"]
# ///
import argparse
import importlib.util
import math
import random
import re
import shutil
import subprocess
import tempfile
import time
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
PHONE_SCREENSHOTS = HERE.parents[2] / "ink-phone" / "fastlane" / "screenshots"
METADATA = HERE.parent / "metadata" / "android"
PACKAGE = "io.github.andreygladkov.inkgallery.debug"
ACTIVITY = f"{PACKAGE}/io.github.andreygladkov.inkgallery.MainActivity"
PERMISSIONS = ["READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO"]
VIDEO = "VID_20261103_181502.mp4"
OPENED_PHOTO = "IMG_20261104_074211.jpg"

LOCALES = {
    "en": dict(folder="en-US", app_locale="en-US", albums="Albums", settings="Settings", pause="Pause"),
    "ru": dict(folder="ru", app_locale="ru-RU", albums="Альбомы", settings="Настройки", pause="Пауза"),
}

SKIES = [
    ((255, 170, 90), (120, 70, 140)),
    ((140, 200, 255), (40, 90, 170)),
    ((255, 220, 160), (230, 120, 90)),
    ((30, 30, 70), (90, 60, 120)),
    ((200, 230, 255), (120, 170, 220)),
]


def load_phone_script():
    """Ink Phone's script already drives the emulator and cleans the status bar."""
    spec = importlib.util.spec_from_file_location("phone_screenshots", PHONE_SCREENSHOTS / "make_screenshots.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def gradient(size, top, bottom):
    width, height = size
    image = Image.new("RGB", size)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        t = y / max(1, height - 1)
        draw.line([(0, y), (width, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return image


def ridge(rng, width, base, height):
    waves = [(rng.uniform(0.5, 3.0), rng.uniform(0, math.tau), rng.uniform(0.2, 1.0)) for _ in range(4)]
    total = sum(amplitude for _, _, amplitude in waves)
    return [(x, base - height * sum(a * math.sin(f * x / width * math.tau + p) for f, p, a in waves) / total) for x in range(0, width + 8, 8)]


def landscape(seed, size):
    """Sky, a sun or moon, and layers of hills fading into the distance."""
    rng = random.Random(seed)
    width, height = size
    top, bottom = SKIES[seed % len(SKIES)]
    image = gradient(size, top, bottom)
    draw = ImageDraw.Draw(image)
    radius = width * rng.uniform(0.06, 0.1)
    sun = (width * rng.uniform(0.2, 0.8), height * rng.uniform(0.15, 0.4))
    draw.ellipse([sun[0] - radius, sun[1] - radius, sun[0] + radius, sun[1] + radius], fill=(255, 240, 210))
    layers = rng.randint(3, 5)
    for layer in range(layers):
        shade = 0.75 - 0.6 * layer / layers
        colour = tuple(round(c * shade) for c in bottom)
        base = height * (0.5 + 0.12 * layer)
        draw.polygon(ridge(rng, width, base, height * 0.12) + [(width, height), (0, height)], fill=colour)
    return image.filter(ImageFilter.GaussianBlur(1))


def city(seed, size):
    """A skyline at dusk with lit windows."""
    rng = random.Random(seed)
    width, height = size
    image = gradient(size, (40, 40, 90), (230, 140, 100))
    draw = ImageDraw.Draw(image)
    x = 0
    while x < width:
        building = rng.randint(width // 14, width // 6)
        top = height * rng.uniform(0.35, 0.7)
        draw.rectangle([x, top, x + building, height], fill=(25, 25, 40))
        for wy in range(int(top) + 20, height - 20, 36):
            for wx in range(x + 12, x + building - 16, 28):
                if rng.random() < 0.45:
                    draw.rectangle([wx, wy, wx + 12, wy + 18], fill=(255, 210, 120))
        x += building + rng.randint(4, 16)
    return image


def sea(seed, size):
    """A horizon over the sea with the sun's track on the water."""
    rng = random.Random(seed)
    width, height = size
    horizon = int(height * rng.uniform(0.45, 0.6))
    image = gradient(size, (250, 200, 150), (120, 160, 210))
    draw = ImageDraw.Draw(image)
    cx = width * rng.uniform(0.3, 0.7)
    radius = width * 0.08
    draw.ellipse([cx - radius, horizon - radius * 1.6, cx + radius, horizon + radius * 0.4], fill=(255, 235, 200))
    image.paste(gradient((width, height - horizon), (60, 110, 160), (20, 40, 80)), (0, horizon))
    for y in range(horizon + 6, height, 14):
        spread = radius * (0.6 + (y - horizon) / height * 2)
        draw.line([(cx - spread * rng.uniform(0.5, 1), y), (cx + spread * rng.uniform(0.5, 1), y)], fill=(255, 220, 170), width=4)
    return image


def flowers(seed, size):
    """A meadow seen from above: round blossoms on green."""
    rng = random.Random(seed)
    width, height = size
    image = gradient(size, (90, 150, 70), (40, 90, 40))
    draw = ImageDraw.Draw(image)
    for _ in range(60):
        x, y, r = rng.uniform(0, width), rng.uniform(0, height), rng.uniform(width * 0.02, width * 0.06)
        petal = rng.choice([(250, 220, 90), (240, 120, 150), (250, 250, 250), (180, 120, 230)])
        for k in range(6):
            angle = k * math.tau / 6
            px, py = x + r * math.cos(angle), y + r * math.sin(angle)
            draw.ellipse([px - r * 0.6, py - r * 0.6, px + r * 0.6, py + r * 0.6], fill=petal)
        draw.ellipse([x - r * 0.4, y - r * 0.4, x + r * 0.4, y + r * 0.4], fill=(240, 180, 40))
    return image


def screenshot_picture(seed, size):
    """Something that looks like a phone screenshot: a light page of text lines and a chart."""
    rng = random.Random(seed)
    width, height = size
    image = Image.new("RGB", size, (250, 250, 250))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, width, height * 0.08], fill=(60, 60, 60))
    y = height * 0.12
    while y < height * 0.55:
        draw.rectangle([width * 0.08, y, width * rng.uniform(0.4, 0.92), y + 18], fill=(120, 120, 120))
        y += 40
    bars = 7
    for i in range(bars):
        bar = (width * 0.8) / bars
        top = height * rng.uniform(0.62, 0.85)
        draw.rectangle([width * 0.1 + i * bar, top, width * 0.1 + (i + 0.7) * bar, height * 0.9], fill=(70, 70, 70))
    return image


PORTRAIT, WIDE = (1200, 1600), (1600, 1200)

# Newest first, as the grid shows them: (folder, file name, scene, seed, size).
LIBRARY = [
    ("DCIM/Camera", "IMG_20261108_183015.jpg", landscape, 0, PORTRAIT),
    ("DCIM/Camera", "IMG_20261108_121840.jpg", flowers, 1, PORTRAIT),
    ("Pictures/Screenshots", "Screenshot_20261107_221004.png", screenshot_picture, 2, (824, 1648)),
    ("DCIM/Camera", "IMG_20261107_190522.jpg", city, 3, WIDE),
    ("DCIM/Camera", "IMG_20261106_085133.jpg", sea, 4, PORTRAIT),
    ("Pictures/Travel", "IMG_20261105_163348.jpg", landscape, 5, WIDE),
    ("Pictures/Travel", "IMG_20261105_101207.jpg", sea, 6, WIDE),
    ("DCIM/Camera", OPENED_PHOTO, landscape, 7, PORTRAIT),
    ("DCIM/Camera", VIDEO, None, 8, PORTRAIT),
    ("Pictures/Travel", "IMG_20261102_140958.jpg", city, 9, PORTRAIT),
    ("DCIM/Camera", "IMG_20261101_112045.jpg", flowers, 10, WIDE),
    ("Pictures/Travel", "IMG_20261031_195512.jpg", landscape, 11, PORTRAIT),
    ("DCIM/Camera", "IMG_20261030_172301.jpg", sea, 12, PORTRAIT),
    ("Pictures/Screenshots", "Screenshot_20261029_093317.png", screenshot_picture, 13, (824, 1648)),
    ("DCIM/Camera", "IMG_20261028_081944.jpg", landscape, 14, WIDE),
]


def video_from(still, path, seconds=20):
    """A slow zoom into a still, enough to show a playing video; needs ffmpeg."""
    if shutil.which("ffmpeg") is None:
        raise SystemExit("ffmpeg is needed to make the sample video")
    frames = seconds * 25
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-loop", "1", "-i", str(still),
        "-vf", f"scale=1080:-2,zoompan=z='min(zoom+0.0005,1.3)':d={frames}:s=720x960:fps=25",
        "-t", str(seconds), "-c:v", "libx264", "-pix_fmt", "yuv420p", str(path),
    ], check=True)


def prefs_xml(inverted):
    entries = ['<boolean name="style_inverted" value="true" />'] if inverted else []
    return "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + "".join(f"    {e}\n" for e in entries) + "</map>\n"


phone = load_phone_script()


class Gallery(phone.Device):
    def prepare(self):
        self.shell(f"pm clear {PACKAGE}")
        for permission in PERMISSIONS:
            self.shell(f"pm grant {PACKAGE} android.permission.{permission}")
        self.shell("settings put global sysui_demo_allowed 1")
        self.shell("settings put secure icon_blacklist satellite,location,rotate,mobile,wifi,airplane,ethernet,connected_display,vpn")

    def seed_library(self):
        """Replaces the sample items from an earlier run, pushing them oldest first so the newest gets the newest id."""
        names = " ".join(f"/sdcard/{folder}/{name}" for folder, name, *_ in LIBRARY)
        self.shell(f"rm -f {names}")
        with tempfile.TemporaryDirectory() as temp:
            for folder, name, scene, seed, size in reversed(LIBRARY):
                local = Path(temp) / name
                if scene is None:
                    still = Path(temp) / f"{name}.png"
                    landscape(seed, size).save(still)
                    video_from(still, local)
                else:
                    scene(seed, size).save(local, quality=90)
                self.shell(f"mkdir -p /sdcard/{folder}")
                self.adb("push", str(local), f"/sdcard/{folder}/{name}")
                self.shell(f"content call --uri content://media --method scan_file --arg /sdcard/{folder}/{name}")
                time.sleep(0.3)
        self.shell("content call --uri content://media --method scan_volume --arg external_primary")

    def write_prefs(self, xml):
        self.shell(f"am force-stop {PACKAGE}")
        path = self.push_text(xml, "ink_gallery.xml")
        self.shell(f"run-as {PACKAGE} sh -c 'mkdir -p shared_prefs && cat {path} > shared_prefs/ink_gallery.xml'")

    def open_grid(self):
        self.shell(f"am force-stop {PACKAGE}")
        self.shell(f"am start -W -n {ACTIVITY}")
        time.sleep(4)

    def tap_desc_starting(self, prefix):
        for node in re.findall(r"<node [^>]*>", self.ui()):
            if re.search(r'content-desc="' + re.escape(prefix), node):
                left, top, right, bottom = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
                self.shell(f"input tap {(left + right) // 2} {(top + bottom) // 2}")
                time.sleep(3)
                return
        raise RuntimeError(f"nothing described as {prefix!r}… on screen")

    def tap_middle(self):
        self.shell("input tap 412 800")
        time.sleep(2)


def shoot(device, locale):
    texts = LOCALES[locale]
    out = METADATA / texts["folder"] / "images" / "phoneScreenshots"
    out.mkdir(parents=True, exist_ok=True)
    light, dark = PHONE_SCREENSHOTS / "data" / "status-light.png", PHONE_SCREENSHOTS / "data" / "status-dark.png"

    device.prepare()
    device.shell(f"cmd locale set-app-locales {PACKAGE} --locales {texts['app_locale']}")
    device.write_prefs(prefs_xml(inverted=False))

    device.open_grid()
    device.screenshot(out / "1.png", light)
    device.tap(text=texts["albums"])
    device.screenshot(out / "2.png", light)

    device.open_grid()
    device.tap_desc_starting(OPENED_PHOTO)
    device.tap_middle()
    device.screenshot(out / "3.png", light)

    device.open_grid()
    device.tap_desc_starting(VIDEO)
    device.tap_middle()
    device.tap(text=texts["pause"])
    device.screenshot(out / "4.png", light)

    device.open_grid()
    device.tap(desc=texts["settings"])
    device.screenshot(out / "5.png", light)

    device.write_prefs(prefs_xml(inverted=True))
    device.open_grid()
    device.screenshot(out / "6.png", dark)
    device.write_prefs(prefs_xml(inverted=False))
    device.shell(f"am force-stop {PACKAGE}")


def main():
    parser = argparse.ArgumentParser(
        description="Retake the store screenshots on an emulator: the 824x1648, 360 dpi AVD (ink_a9) with the Ink Gallery "
                    "debug build installed and ffmpeg on this computer. The app's data is wiped, and sample photos and a "
                    "video are put in DCIM/Camera, Pictures/Travel and Pictures/Screenshots on the emulator.",
    )
    parser.add_argument("locales", nargs="*", metavar="locale", help="en, ru; both by default")
    parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    locales = args.locales or list(LOCALES)
    unknown = [locale for locale in locales if locale not in LOCALES]
    if unknown:
        parser.error(f"unknown locale: {', '.join(unknown)}")
    device = Gallery(args.serial)
    device.seed_library()
    for locale in locales:
        shoot(device, locale)


if __name__ == "__main__":
    main()
