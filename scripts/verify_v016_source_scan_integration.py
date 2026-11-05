#!/usr/bin/env python3
"""Static regression checks for v0.1.20 SAF source-scan integration."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text(encoding="utf-8")
build = read("app/build.gradle.kts")
worker = read("app/src/main/java/com/toimg/app/core/work/ScanWorker.kt")
manager = read("app/src/main/java/com/toimg/app/core/util/ScanManager.kt")
repo = read("app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt")
dao = read("app/src/main/java/com/toimg/app/data/local/dao/ImageSourceDao.kt")
image_dao = read("app/src/main/java/com/toimg/app/data/local/dao/ImageDao.kt")
index = read("app/src/main/java/com/toimg/app/core/util/ImageSourceIndexCoordinator.kt")
checks = {
    "current version is 0.1.29 / code 28": 'versionName = "0.1.30"' in build and "versionCode = 28" in build,
    "worker resolves or registers a persistent source for each SAF URI": "sourceDao.getByUri(rawUri)" in worker and "ImageSourceEntity(" in worker,
    "scan session persists the selected source ID snapshot": "selectedSourceIds = (sourceIdsByUri.values + listOfNotNull(mediaStoreSourceId)).joinToString" in worker,
    "worker initializes per-source scan statistics": 'status = "RUNNING"' in worker and "SourceScanStatEntity(" in worker,
    "worker passes source mapping and session ID into ScanManager": "sourceIdsByUri = sourceIdsByUri" in worker and "scanSessionId = sessionId" in worker,
    "ScanManager passes source ID into SAF repository scan": "repository.scanFolderUriWithMode(app, treeUri, includeSubfolders, sourceId, scanSessionId, scanMode)" in manager,
    "folder scan persists image-source links in batches": "ImageSourceIndexCoordinator(db.imageSourceDao())" in repo and "linkImagesToSource" in repo,
    "source batch stats count discovered / added / existing": "recordScanBatch(scanSessionId, sourceId, uniqueCount, added, updated, existing)" in repo,
    "image-source coordinator deduplicates links": "imageIds.distinct()" in index and "insertedRows.count { it != -1L }" in index,
    "old SAF indexes can be backfilled by tree URI prefix": "getImagesByUriPrefix(rawUri.trimEnd('/') + \"/document/\")" in worker,
    "new source scan scopes hash calculation to its linked images": "computeMissingHashesForSources" in manager and "imageIdsForSources" in repo,
    "new source scan scopes availability checks to its linked images": "checkAvailability(app, sourceImageIds)" in manager,
    "source-level failure is retained": "sourceErrors = it.sourceErrors + (sourceId to detail)" in manager,
    "root folder access failures are surfaced": "所选文件夹不可读取" in repo and "来源根目录无法读取" in repo,
    "source DAO stores per-source counters and availability": "recordScanBatch" in dao and "countUnavailableLinksForSource" in dao,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit(1)
print(f"PASS: {len(checks)} v0.1.22 source-scan integration checks")
