#!/usr/bin/env python3
"""Static release checks for default scan exclusions and explicit SAF root opt-in."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text(encoding="utf-8")
helper = read("app/src/main/java/com/toimg/app/core/util/ScanDirectoryFilter.kt")
repo = read("app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt")
ui = read("app/src/main/java/com/toimg/app/feature/scan/ScanScopeScreen.kt")
build = read("app/build.gradle.kts")
test = read("app/src/test/java/com/toimg/app/core/util/ScanDirectoryFilterTest.kt")
checks = {
    "current version is 0.1.29 / code 28": 'versionName = "0.1.30"' in build and "versionCode = 28" in build,
    "central exclusion list includes thumbnail/cache/temp folders": all(x in helper for x in [".thumbnails", "thumbnails", ".cache", "cache", ".trash", "trash", "temp", "tmp"]),
    "MediaStore path filtering happens before adding records": "ScanDirectoryFilter.isExcludedPath(path)" in repo and repo.index("ScanDirectoryFilter.isExcludedPath(path)") < repo.index("imageList.add(item)"),
    "SAF traversal skips excluded folders before listing": "ScanDirectoryFilter.shouldSkipDirectory(dir.name, isSelectedRoot)" in repo and repo.index("ScanDirectoryFilter.shouldSkipDirectory(dir.name, isSelectedRoot)") < repo.index("dir.listFiles()"),
    "explicit selected SAF root gets the one-root exception": "explicitlySelectedExcludedRoot" in repo and "includeSubfolders, false, scanMode, sourceIdForCurrentTraversal, fastExistingCounter, onProgress" in repo,
    "nested folders do not inherit root exception": "depth + 1, visitedDirs, includeSubfolders, false, scanMode, sourceIdForCurrentTraversal, fastExistingCounter, onProgress" in repo,
    "UI explains default exclusion and user opt-in": "系统默认跳过" in ui and "主动把其中某个目录本身选为扫描根目录" in ui,
    "unit tests cover path, names and explicit root behavior": "isSelectedRoot = true" in test and "isExcludedPath" in test,
}
failed = [k for k, v in checks.items() if not v]
for name, ok in checks.items(): print(("PASS" if ok else "FAIL") + ": " + name)
if failed: raise SystemExit(1)
print(f"PASS: {len(checks)} scan-directory-filter checks")
