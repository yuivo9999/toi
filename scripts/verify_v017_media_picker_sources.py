#!/usr/bin/env python3
"""Static release checks for MediaStore + Photo Picker source integration."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text(encoding="utf-8")
build = read("app/build.gradle.kts")
worker = read("app/src/main/java/com/toimg/app/core/work/ScanWorker.kt")
manager = read("app/src/main/java/com/toimg/app/core/util/ScanManager.kt")
repo = read("app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt")
contract = read("app/src/main/java/com/toimg/app/domain/repository/ImageRepository.kt")
picker = read("app/src/main/java/com/toimg/app/feature/picker/ImagePickerViewModel.kt")
sources = read("app/src/main/java/com/toimg/app/feature/sources/SourceManagementScreen.kt")
entity = read("app/src/main/java/com/toimg/app/data/local/entity/ImageSourceEntity.kt")
checks = {
    "release current version is 0.1.29 / code 28": 'versionName = "0.1.30"' in build and "versionCode = 28" in build,
    "MediaStore is registered as a persistent project source": "MEDIASTORE_SOURCE_ID" in worker and "MEDIASTORE_URI" in worker,
    "MediaStore source ID is passed into scan execution": "mediaStoreSourceId = mediaStoreSourceId" in worker and "mediaStoreSourceId: String?" in manager,
    "MediaStore rows are persisted with source links": "persistMediaBatch(entityList.toList())" in repo and "linkImagesToSource(unique.map { it.id }, sourceId, now)" in repo,
    "MediaStore scan counters are written per session": "recordScanBatch(scanSessionId, sourceId, unique.size, added, updated, existing)" in repo,
    "availability/hash work is scoped to MediaStore source IDs": "listOfNotNull(mediaStoreSourceId)" in manager and "imageIdsForSources(allSourceIds.toList())" in manager,
    "Photo Picker selection gets a distinct managed source": "PICKER_SOURCE_ID" in picker and "图片选择器选取" in picker,
    "Photo Picker selections are persisted to image index": "ImageIdentityResolver.resolveAndPersist(context, db, picked)" in picker and "db.imageDao().insertImages(uniqueEntities)" in picker,
    "Photo Picker selections are linked to source": "ImageSourceLinkEntity(it.id, currentSource.id, now, now)" in picker,
    "source details expose scan history and live linked counts": "observeScanStats(source.id)" in sources and "linkedImageCount" in sources,
    "source display name can be edited": "repository.updateSource(source.copy(displayName" in sources,
    "synthetic source IDs are stable": "system:mediastore:external-images" in entity and "system:photo-picker-selections" in entity,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit(1)
print(f"PASS: {len(checks)} v0.1.22 source integration checks")
