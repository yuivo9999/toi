#!/usr/bin/env python3
"""Static regression checks for v0.1.22 transactional image ingest/move updates."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

resolver = read("app/src/main/java/com/toimg/app/core/util/ImageIdentityResolver.kt")
repo = read("app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt")
picker = read("app/src/main/java/com/toimg/app/feature/picker/ImagePickerViewModel.kt")
move = read("app/src/main/java/com/toimg/app/core/util/FileMoveUtil.kt")

checks = {
    "identity ingest serialized by Mutex": "private val ingestMutex = Mutex()" in resolver and "ingestMutex.withLock" in resolver,
    "identity resolver persists canonical result": "suspend fun resolveAndPersist" in resolver and "db.imageDao().insertImages(listOf(resolved))" in resolver,
    "Room transaction wraps canonical upsert": "db.withTransaction" in resolver,
    "SAF scanner uses serialized resolver": "ImageIdentityResolver.resolveAndPersist(context, db, rawEntity)" in repo,
    "MediaStore scanner uses serialized resolver": repo.count("ImageIdentityResolver.resolveAndPersist(context, db, rawEntity)") >= 2,
    "MediaStore image/link/stat write is atomic": "suspend fun persistMediaBatch" in repo and "db.withTransaction" in repo[repo.index("suspend fun persistMediaBatch"):repo.index("val collection =")],
    "SAF image/link/stat write is atomic": "db.withTransaction" in repo[repo.index("suspend fun persistBatch"):repo.index("traverseDocumentTree")],
    "Photo Picker uses serialized resolver": "ImageIdentityResolver.resolveAndPersist(context, db, picked)" in picker,
    "Photo Picker image/link/source update is atomic": "db.withTransaction" in picker and "addImageSourceLinks(links)" in picker,
    "successful move updates image and FTS atomically": "db.withTransaction" in repo[repo.index("override suspend fun recordMoveSuccess"):repo.index("override suspend fun recordMoveHistory")] and "syncFtsForImage(imageId)" in repo,
    "rollback updates image, FTS and history atomically": "db.withTransaction" in move and "syncFtsForImage(history.imageId)" in move,
    "current database schema is v10": "version = 10" in read("app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt"),
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " - " + name)
if failed:
    raise SystemExit(f"{len(failed)} checks failed")
print(f"All {len(checks)} v0.1.22 checks passed")
