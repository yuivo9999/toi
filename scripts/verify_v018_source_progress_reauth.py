#!/usr/bin/env python3
"""Static regression checks for v0.1.20 source progress and SAF reauthorization."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text(encoding="utf-8")
build = read("app/build.gradle.kts")
source_dao = read("app/src/main/java/com/toimg/app/data/local/dao/ImageSourceDao.kt")
source_repo = read("app/src/main/java/com/toimg/app/data/repository/ImageSourceRepositoryImpl.kt")
local_repo = read("app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt")
source_contract = read("app/src/main/java/com/toimg/app/domain/repository/ImageSourceRepository.kt")
scan_scope_vm = read("app/src/main/java/com/toimg/app/feature/scan/ScanScopeViewModel.kt")
scan_progress = read("app/src/main/java/com/toimg/app/feature/scan/ScanProgressScreen.kt")
source_screen = read("app/src/main/java/com/toimg/app/feature/sources/SourceManagementScreen.kt")
manager = read("app/src/main/java/com/toimg/app/core/util/ScanManager.kt")
worker = read("app/src/main/java/com/toimg/app/core/work/ScanWorker.kt")
scan_scope_screen = read("app/src/main/java/com/toimg/app/feature/scan/ScanScopeScreen.kt")
checks = {
    "current version is 0.1.29 / code 28": 'versionName = "0.1.30"' in build and "versionCode = 28" in build,
    "per-source stats update count is persisted": "updatedCount = updatedCount + :updated" in source_dao and "updated: Int" in source_dao,
    "per-session stats are observable as Flow": "observeScanStatsForSession" in source_dao and "observeScanStatsForSession(sessionId" in source_contract,
    "progress UI subscribes to per-source stats": "observeScanStatsForSession(it)" in scan_progress and "本次各来源进度" in scan_progress,
    "progress UI displays all source counters": all(x in scan_progress for x in ["discoveredCount", "addedCount", "updatedCount", "existingCount", "skippedCount", "unavailableCount", "failedCount"]),
    "scanner attributes skipped items to current source": "currentSourceId" in manager and "sourceSkippedCounts" in manager,
    "source completion is persisted immediately": "completedSourceIds" in manager and "progress.completedSourceIds" in worker,
    "SAF reauthorization preserves stable source ID": "sourceDao.getById(sourceId)" in source_repo and "source.copy(" in source_repo,
    "source repository persists new SAF URI permission": "takePersistableUriPermission(newUri" in source_repo and "releaseTreePermission(Uri.parse(source.uriString))" in source_repo,
    "newly selected folders register UNSCANNED source": "lifecycleStatus = ImageSourceEntity.UNSCANNED" in local_repo,
    "folder listing checks persisted grant and actual readability": "hasGrant && runCatching" in local_repo and "root.canRead()" in local_repo,
    "source display name is synced to scan-scope list": "source.displayName}" in source_repo and "folders_list" in source_repo,
    "moving source out removes saved folder entry": "entries.removeAll" in source_repo and "releaseTreePermission(Uri.parse(source.treeUriString))" in source_repo,
    "reauthorization requires explicit replacement confirmation": "确认替换文件夹授权" in source_screen and "请确认新选择的确实是原来的文件夹" in source_screen,
    "scan-scope no longer has a destructive remove button": 'Text("移除", fontSize = 12.sp' not in scan_scope_screen,
    "existing source display name refreshes after provider rename": "existing.copy(\n                        displayName = name" in worker,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit(1)
print(f"PASS: {len(checks)} v0.1.22 source progress / reauthorization checks")
