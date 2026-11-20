#!/usr/bin/env python3
"""Export the Amuratsu SVG to platform assets. Requires ImageMagick 7, no app dependency."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SVG = ROOT / "branding/amuratsu.svg"
NS = {"svg": "http://www.w3.org/2000/svg"}
path = ET.parse(SVG).find("svg:path", NS)
assert path is not None
MARK = path.attrib["d"]
RED = path.attrib["fill"]


def write(relative, text):
    target = ROOT / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")


def vector(color, background=""):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{background}    <path android:fillColor="{color}" android:fillType="evenOdd"
        android:pathData="{MARK}" />
</vector>
'''


def raster(svg, target, size):
    destination = ROOT / target
    destination.parent.mkdir(parents=True, exist_ok=True)
    opaque = ["-alpha", "off"] if destination.suffix == ".png" else []
    subprocess.run([
        "magick", "-background", "none", "-density", "384", "svg:-",
        "-resize", f"{size}x{size}", *opaque, "-strip", str(destination),
    ], input=svg.encode(), check=True)


source = SVG.read_text()
square = source.replace('viewBox="0 0 108 108"', 'viewBox="18 18 72 72"')
circle = square.replace(
    '<rect width="108" height="108" fill="#FFFFFF"/>',
    '<circle cx="54" cy="54" r="36" fill="#FFFFFF"/>',
)
write("metadata/en-US/icon.svg", square)
for target, size in [
    ("metadata/en-US/icon.png", 512),
    ("app/src/main/ic_launcher-playstore.png", 512),
    ("docs/public/icon.png", 512),
    ("iosApp/Kototoro/Assets.xcassets/AppIcon.appiconset/AppIcon.png", 1024),
]:
    raster(square, target, size)

for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    raster(square, f"app/src/main/res/mipmap-{density}/ic_launcher.webp", size)
    raster(circle, f"app/src/main/res/mipmap-{density}/ic_launcher_round.webp", size)
    (ROOT / f"app/src/main/res/mipmap-{density}/ic_launcher_foreground.webp").unlink(missing_ok=True)

write("app/src/main/res/drawable/ic_launcher_foreground.xml", vector(RED))
write("app/src/main/res/drawable/ic_launcher_monochrome.xml", vector("#000000"))
splash_background = '''    <path android:fillColor="#FFFFFF"
        android:pathData="M18 54 A36 36 0 1 1 90 54 A36 36 0 1 1 18 54" />
'''
for qualifier in ["drawable", "drawable-night"]:
    write(f"app/src/main/res/{qualifier}/avd_splash.xml", vector(RED, splash_background))
for qualifier in ["values", "values-night"]:
    write(f"app/src/main/res/{qualifier}/ic_launcher_background.xml", '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#FFFFFF</color>
</resources>
''')
for name in ["ic_launcher", "ic_launcher_round"]:
    write(f"app/src/main/res/mipmap-anydpi-v26/{name}.xml", '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
''')
# Nightly inherits the same mark instead of covering it with the upstream artwork.
for target in (ROOT / "app/src/nightly/res").glob("mipmap-*/ic_launcher*"):
    target.unlink()
