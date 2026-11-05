#!/usr/bin/env python3
"""Safety audit: retired image watcher is archived, not compiled; core media flows remain."""
from pathlib import Path
import hashlib
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main"
ARCHIVE = ROOT / "archive/disabled-image-watcher-v0.1.29"

archived = {
    "app/src/main/java/com/toimg/app/core/util/NewImageWatcher.kt": "archive/disabled-image-watcher-v0.1.29/app/src/main/java/com/toimg/app/core/util/NewImageWatcher.kt",
    "app/src/main/java/com/toimg/app/core/work/NewImageWatchWorker.kt": "archive/disabled-image-watcher-v0.1.29/app/src/main/java/com/toimg/app/core/work/NewImageWatchWorker.kt",
    "app/src/main/java/com/toimg/app/core/util/NewImageIngestor.kt": "archive/disabled-image-watcher-v0.1.29/app/src/main/java/com/toimg/app/core/util/NewImageIngestor.kt",
}
checks = {}

# Runtime source set must no longer contain observer implementation or watcher classes.
production_files = [p for p in APP.rglob("*.kt")]
production_text = "\n".join(p.read_text(encoding="utf-8") for p in production_files)
checks["no ContentObserver in Android production Kotlin source"] = "ContentObserver" not in production_text
checks["no retired watcher class in Android production source"] = all(x not in production_text for x in ("NewImageWatcher", "NewImageWatchWorker", "NewImageIngestor"))

# Archive is outside app/src and each byte-for-byte copy matches the baseline hash.
checks["archive directory is outside Android source set"] = not str(ARCHIVE).startswith(str(APP))
checks["all three original watcher files preserved"] = all((ROOT / dst).is_file() for dst in archived.values())
checks["archived files match v0.1.29 baseline hashes"] = all(
    hashlib.sha256((ROOT / dst).read_bytes()).hexdigest() == expected
    for src, dst, expected in [
        (src, dst, digest)
        for src, dst, digest in [
            ("NewImageWatcher", archived["app/src/main/java/com/toimg/app/core/util/NewImageWatcher.kt"], "ab9d7546c2a0d56ba2e1863963637408b3edc77b3e7a4c18baf164eaed9b0630"),
            ("NewImageWatchWorker", archived["app/src/main/java/com/toimg/app/core/work/NewImageWatchWorker.kt"], "b01244bc273468539befd06fea95c734167100f52947cd6e5b51a1d1063756d5"),
            ("NewImageIngestor", archived["app/src/main/java/com/toimg/app/core/util/NewImageIngestor.kt"], "ff13546e34f0ba8262c46de0a92267d45865c920a037ce4ba6916eb28ba481ad"),
        ]
    ]
)

repo = (ROOT / "app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt").read_text(encoding="utf-8")
picker = (ROOT / "app/src/main/java/com/toimg/app/feature/picker/ImagePickerViewModel.kt").read_text(encoding="utf-8")
move = (ROOT / "app/src/main/java/com/toimg/app/core/util/FileMoveUtil.kt").read_text(encoding="utf-8")
resolver = (ROOT / "app/src/main/java/com/toimg/app/core/util/ImageIdentityResolver.kt").read_text(encoding="utf-8")
scan_scheduler = (ROOT / "app/src/main/java/com/toimg/app/core/util/ScanTaskScheduler.kt").read_text(encoding="utf-8") if (ROOT / "app/src/main/java/com/toimg/app/core/util/ScanTaskScheduler.kt").exists() else ""
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

checks["manual SAF and MediaStore scanner still uses ContentResolver"] = "contentResolver.query(" in repo and "traverseDocumentTree" in repo
checks["Photo Picker still uses ContentResolver and identity resolver"] = "contentResolver" in picker and "ImageIdentityResolver.resolveAndPersist" in picker
checks["file move and rollback still use ContentResolver"] = "contentResolver.openInputStream" in move and "contentResolver.delete" in move
checks["shared identity resolver remains available"] = "suspend fun resolveAndPersist" in resolver and "ingestMutex.withLock" in resolver
checks["WorkManager dependency remains for scan jobs"] = "androidx.work.runtime.ktx" in (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
checks["manifest retains recognition service and no watcher service"] = "RecognitionService" in manifest and "NewImageWatch" not in manifest
checks["Room remains v10 without watcher-specific migration"] = "version = 10" in (ROOT / "app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt").read_text(encoding="utf-8")
checks["watcher preferences are not actively cleared"] = "toimg_watch" not in production_text

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " - " + name)
if failed:
    raise SystemExit(f"{len(failed)} safety checks failed")
print(f"All {len(checks)} v0.1.30 watcher-isolation checks passed")
