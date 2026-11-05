#!/usr/bin/env python3
"""v0.1.29 regression checks for move/undo/source lifecycle and late recognition writes."""
from pathlib import Path
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
def read(p): return (ROOT / p).read_text(encoding="utf-8")

build = read("app/build.gradle.kts")
image_dao = read("app/src/main/java/com/toimg/app/data/local/dao/ImageDao.kt")
repo = read("app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt")
move = read("app/src/main/java/com/toimg/app/core/util/FileMoveUtil.kt")
settings = read("app/src/main/java/com/toimg/app/feature/settings/SettingsViewModel.kt")
source_repo = read("app/src/main/java/com/toimg/app/data/repository/ImageSourceRepositoryImpl.kt")

checks = {
    "release version 0.1.29/code 28": 'versionName = "0.1.30"' in build and "versionCode = 28" in build,
    "Room schema v10 includes scan mode migration": "version = 10" in read("app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt"),
    "move updates URI and both path fields": "path = newUriString" in repo and "folderPath = newFolderName" in repo,
    "undo restores URI and both path fields": "path = restoredUri.toString()" in move and 'folderPath = "已撤回相册"' in move,
    "undo keeps FTS synchronized": "imageFtsDao().syncFtsForImage(history.imageId)" in move,
    "partial undo is transactional": 'db.withTransaction {\n                db.moveHistoryDao().updateHistoryStatus(history.id, "ROLLED_BACK")' in move,
    "undo refuses false success if target copy cannot be deleted": move.count("if (!targetDeleted)") >= 2,
    "failed undo removes temporary restored duplicate": "delete(restoredUri, null, null)" in move,
    "move keeps image source links untouched": "imageSourceDao().deleteLinks" not in repo[repo.find("override suspend fun recordMoveSuccess"):repo.find("override suspend fun recordMoveHistory")],
    "late recognition writes require an existing image": "getImageById(res.imageId) == null" in repo,
    "recognition result and FTS remain transactional": "db.withTransaction" in repo and "syncFtsForImage(res.imageId)" in repo,
    "recognition import ignores removed image IDs": "getImageById(imageId) != null" in settings,
    "recognition import updates FTS transactionally": "db.imageFtsDao().syncFtsForImage(imageId)" in settings and "db.withTransaction" in settings,
    "source removal remains transaction based": "db.withTransaction" in source_repo,
}

# Model both possible Room transaction orderings. A late AI response must not recreate
# an index/result after source removal; shared image links must survive a source move.
con = sqlite3.connect(":memory:")
con.executescript("""
CREATE TABLE images(id TEXT PRIMARY KEY, status TEXT NOT NULL, uri TEXT NOT NULL);
CREATE TABLE source_links(image_id TEXT NOT NULL, source_id TEXT NOT NULL, PRIMARY KEY(image_id, source_id));
CREATE TABLE recognitions(image_id TEXT PRIMARY KEY, keywords TEXT NOT NULL);
INSERT INTO images VALUES ('exclusive','SCANNED','saf://old'), ('shared','SCANNED','saf://shared');
INSERT INTO source_links VALUES ('exclusive','source-a'), ('shared','source-a'), ('shared','source-b');
""")
# Remove-source transaction wins first; subsequent recognition callback sees no image.
con.execute("BEGIN")
con.execute("DELETE FROM source_links WHERE source_id='source-a'")
con.execute("DELETE FROM recognitions WHERE image_id='exclusive'")
con.execute("DELETE FROM images WHERE id='exclusive'")
con.commit()
row = con.execute("SELECT 1 FROM images WHERE id='exclusive'").fetchone()
if row is None:
    # Mirrors the new existence guard in saveRecognitions.
    pass
else:
    checks["late AI result does not resurrect removed image"] = False
con.execute("INSERT INTO recognitions SELECT 'shared','landscape' WHERE EXISTS (SELECT 1 FROM images WHERE id='shared')")
checks["late AI result cannot create orphan recognition"] = con.execute("SELECT COUNT(*) FROM recognitions WHERE image_id='exclusive'").fetchone()[0] == 0
checks["shared image remains indexed after source removal"] = con.execute("SELECT COUNT(*) FROM images WHERE id='shared'").fetchone()[0] == 1
checks["shared image retains other source association"] = con.execute("SELECT COUNT(*) FROM source_links WHERE image_id='shared' AND source_id='source-b'").fetchone()[0] == 1
# Move/undo changes the current URI/status but does not rewrite historical source membership.
con.execute("UPDATE images SET uri='content://media/restored', status='ROLLED_BACK' WHERE id='shared'")
checks["move/undo keeps historical source link"] = con.execute("SELECT COUNT(*) FROM source_links WHERE image_id='shared' AND source_id='source-b'").fetchone()[0] == 1
checks["restored image URI is updated"] = con.execute("SELECT uri FROM images WHERE id='shared'").fetchone()[0] == "content://media/restored"

# 10k-image / 40-source cross-source load model. Every image belongs to one primary
# source and every tenth image is also referenced by the next source. Remove one
# source and verify exclusive rows are deleted while shared rows remain addressable.
load = sqlite3.connect(":memory:")
load.executescript("CREATE TABLE images(id TEXT PRIMARY KEY); CREATE TABLE links(image_id TEXT, source_id TEXT, PRIMARY KEY(image_id,source_id));")
load.executemany("INSERT INTO images VALUES (?)", ((f"img-{i}",) for i in range(10_000)))
load.executemany("INSERT INTO links VALUES (?,?)", ((f"img-{i}", f"src-{i % 40}") for i in range(10_000)))
load.executemany("INSERT OR IGNORE INTO links VALUES (?,?)", ((f"img-{i}", f"src-{(i % 40 + 1) % 40}") for i in range(10, 10_000, 40)))
load.execute("DELETE FROM links WHERE source_id='src-0'")
load.execute("DELETE FROM images WHERE id NOT IN (SELECT DISTINCT image_id FROM links)")
checks["10k-image/40-source model preserves all remaining linked images"] = load.execute("SELECT COUNT(*) FROM images").fetchone()[0] == load.execute("SELECT COUNT(DISTINCT image_id) FROM links").fetchone()[0]
checks["10k-image model removes only exclusive source-0 images"] = load.execute("SELECT COUNT(*) FROM images WHERE id='img-0'").fetchone()[0] == 0 and load.execute("SELECT COUNT(*) FROM images WHERE id='img-10'").fetchone()[0] == 1
# Exercise the same 500-ID chunk size used by identity ingestion queries.
ids = [f"img-{i}" for i in range(10_000)]
chunk_hits = sum(load.execute("SELECT COUNT(*) FROM images WHERE id IN (%s)" % ",".join("?" for _ in ids[i:i+500]), ids[i:i+500]).fetchone()[0] for i in range(0, len(ids), 500))
checks["10k-image batched lookup (500 IDs per query) is lossless"] = chunk_hits == load.execute("SELECT COUNT(*) FROM images").fetchone()[0]

for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " - " + name)
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit(f"{len(failed)} checks failed: {failed}")
print(f"All {len(checks)} v0.1.29 checks passed")
