#!/usr/bin/env python3
"""Static sanity checks for the ToImg Android Studio project skeleton."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle/libs.versions.toml",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/toimg/app/MainActivity.kt",
    "app/src/main/java/com/toimg/app/navigation/AppRoute.kt",
    "app/src/main/java/com/toimg/app/navigation/ToImgNavHost.kt",
    "app/src/main/java/com/toimg/app/feature/home/HomeScreen.kt",
    "app/src/main/java/com/toimg/app/feature/picker/ImagePickerScreen.kt",
    "app/src/main/java/com/toimg/app/feature/workspace/ImageWorkspaceScreen.kt",
    "app/src/main/java/com/toimg/app/feature/result/MoveResultScreen.kt",
    "app/src/main/java/com/toimg/app/feature/settings/SettingsScreen.kt",
    "plan.md",
    "README.md",
]
ROUTES = ["HOME", "PICKER", "WORKSPACE", "RESULT", "SETTINGS"]

errors = []
for relative in REQUIRED:
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"Missing required file: {relative}")
    elif path.stat().st_size == 0:
        errors.append(f"Empty required file: {relative}")

try:
    ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
    ET.parse(ROOT / "app/src/main/res/values/styles.xml")
except (ET.ParseError, FileNotFoundError) as exc:
    errors.append(f"Invalid XML: {exc}")

route_file = ROOT / "app/src/main/java/com/toimg/app/navigation/AppRoute.kt"
nav_file = ROOT / "app/src/main/java/com/toimg/app/navigation/ToImgNavHost.kt"
route_text = route_file.read_text(encoding="utf-8") if route_file.exists() else ""
nav_text = nav_file.read_text(encoding="utf-8") if nav_file.exists() else ""
for route in ROUTES:
    if not re.search(rf"const val {route}\s*=", route_text):
        errors.append(f"Route constant missing: {route}")
    if f"AppRoute.{route}" not in nav_text:
        errors.append(f"Route is not referenced by navigation: {route}")

manifest = ROOT / "app/src/main/AndroidManifest.xml"
if manifest.exists():
    manifest_text = manifest.read_text(encoding="utf-8")
    if "READ_MEDIA_IMAGES" not in manifest_text or "READ_EXTERNAL_STORAGE" not in manifest_text:
        errors.append("MediaStore permission declarations are incomplete for supported Android versions")

if errors:
    print("FAIL")
    print("\n".join(f"- {error}" for error in errors))
    sys.exit(1)

print("PASS: required project files exist")
print("PASS: Android XML files parse")
print("PASS: all five navigation routes are declared and referenced")
print("PASS: media permission declarations cover modern and legacy Android")
