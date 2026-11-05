#!/usr/bin/env python3
"""Static checks for the F1-F5 skeleton-first source management framework."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text(encoding="utf-8")
build = read("app/build.gradle.kts")
db = read("app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt")
repo = read("app/src/main/java/com/toimg/app/data/repository/ImageSourceRepositoryImpl.kt")
nav = read("app/src/main/java/com/toimg/app/navigation/ToImgNavHost.kt")
home = read("app/src/main/java/com/toimg/app/feature/home/HomeScreen.kt")
checks = {
    "current release version 0.1.29 / code 28": 'versionName = "0.1.30"' in build and "versionCode = 28" in build,
    "Current Room v10 retains source, link and per-source scan entities": all(x in db for x in ["ImageSourceEntity::class", "ImageSourceLinkEntity::class", "SourceScanStatEntity::class", "version = 10"]),
    "v7-to-v8 migration adds source ID snapshot": "MIGRATION_7_8" in db and "selectedSourceIds" in db,
    "v5+ migrations cannot silently fall back to destructive rebuild": ".fallbackToDestructiveMigration()" not in db,
    "source management repository performs transactional removal": "withTransaction" in repo and "deleteLinksForSource" in repo,
    "removal protects active scan": "activeScanCountForSource" in repo and "blockedByActiveScan = true" in repo,
    "shared image links are checked before deleting index": "sourceReferenceCount(it) <= 1" in repo,
    "source management screen is navigable": "composable(AppRoute.SOURCES)" in nav,
    "home has source management entry": "onOpenSources" in home and "open_image_sources_button" in home,
    "plan documents skeleton-first development": "先建立 F1-F5 的数据结构" in read("plan.md"),
}
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit(1)
print(f"PASS: {len(checks)} v0.1.22 framework checks")
