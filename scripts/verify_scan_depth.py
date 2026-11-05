#!/usr/bin/env python3
"""Static release checks for optional recursive SAF folder scanning (0.1.11)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text(encoding="utf-8")
ui = read("app/src/main/java/com/toimg/app/feature/scan/ScanScopeScreen.kt")
vm = read("app/src/main/java/com/toimg/app/feature/scan/ScanScopeViewModel.kt")
manager = read("app/src/main/java/com/toimg/app/core/util/ScanManager.kt")
repo = read("app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt")
contract = read("app/src/main/java/com/toimg/app/domain/repository/ImageRepository.kt")
build = read("app/build.gradle.kts")
checks = {
    "current version is 0.1.29 / code 28": 'versionName = "0.1.30"' in build and "versionCode = 28" in build,
    "start scan prompts when SAF folders are selected": "showSubfolderDialog = true" in ui and 'Text("是否扫描下级文件夹？")' in ui,
    "current-folder-only choice is available and is the safe default": 'Text("仅扫描当前文件夹"' in ui and "mutableStateOf(false)" in ui and "includeSubfoldersChoice = false" in ui,
    "recursive choice is available": 'Text("同时扫描所有下级文件夹"' in ui,
    "dialog cancel path does not start scan": 'Text("取消")' in ui and 'onClick = { showSubfolderDialog = false }' in ui,
    "choice is passed through ViewModel": "includeSubfolders = includeSubfolders" in vm,
    "choice is passed through ScanManager": "includeSubfolders: Boolean = true" in manager and "treeUri, includeSubfolders" in manager,
    "repository contract exposes the option with backward-compatible default": "includeSubfolders: Boolean = true" in contract,
    "recursion is gated by the selected option": "if (includeSubfolders)" in repo and "depth + 1, visitedDirs, includeSubfolders, false" in repo,
    "system MediaStore scan remains separate": "scanMediaStoreImagesWithMode(app, mediaStoreSourceId, scanSessionId, scanMode)" in manager and "scanFolderUriWithMode(app, treeUri, includeSubfolders, sourceId, scanSessionId, scanMode)" in manager,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit(1)
print(f"PASS: {len(checks)} scan-depth release checks")
