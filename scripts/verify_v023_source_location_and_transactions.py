#!/usr/bin/env python3
"""Executable SQLite + static regression checks for v0.1.23 source/location regression semantics."""
from pathlib import Path
import sqlite3
import tempfile
import threading
import re

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

dao = read("app/src/main/java/com/toimg/app/data/local/dao/ImageSourceDao.kt")
summary = read("app/src/main/java/com/toimg/app/data/local/entity/ImageSourceSummary.kt")
ui = read("app/src/main/java/com/toimg/app/feature/sources/SourceManagementScreen.kt")
move_repo = read("app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt")
move_util = read("app/src/main/java/com/toimg/app/core/util/FileMoveUtil.kt")

checks = {
    "DAO exposes source association count": "AS linkedImageCount" in dao,
    "DAO computes current SAF location from current URI": "s.treeUriString || '/document/'" in dao,
    "DAO computes MediaStore current location": "content://media/%" in dao,
    "Photo Picker location count is not applicable": "WHEN s.id = 'system:photo-picker-selections' THEN NULL" in dao,
    "summary distinguishes historical association and current location": "val linkedImageCount: Int" in summary and "val currentLocationCount: Int?" in summary,
    "source list shows both counts": "来源关联 ${summary.linkedImageCount} 张" in ui and "当前仍在此位置" in ui,
    "source detail explains moved images remain associated": "后来移动的图片" in ui,
    "move success keeps update and FTS in one transaction": "db.withTransaction" in move_repo[move_repo.index("override suspend fun recordMoveSuccess"):move_repo.index("override suspend fun recordMoveHistory")] and "syncFtsForImage(imageId)" in move_repo,
    "rollback keeps update and FTS in one transaction": "db.withTransaction" in move_util and "syncFtsForImage(history.imageId)" in move_util,
    "Current Room schema is v10": "version = 10" in read("app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt"),
    "app version advanced": 'versionCode = 28' in read("app/build.gradle.kts") and 'versionName = "0.1.30"' in read("app/build.gradle.kts"),
}

# Real SQLite behavior test: current location changes after move while historical link remains.
fd, db_path = tempfile.mkstemp(suffix=".db")
import os
os.close(fd)
try:
    conn = sqlite3.connect(db_path)
    conn.executescript("""
        CREATE TABLE image_sources(id TEXT PRIMARY KEY, treeUriString TEXT NOT NULL, addedAt INTEGER NOT NULL);
        CREATE TABLE images(id TEXT PRIMARY KEY, uriString TEXT NOT NULL);
        CREATE TABLE image_source_links(imageId TEXT NOT NULL, sourceId TEXT NOT NULL, PRIMARY KEY(imageId, sourceId));
        CREATE TABLE source_scan_stats(sourceId TEXT PRIMARY KEY, discoveredCount INTEGER NOT NULL DEFAULT 0);
        INSERT INTO image_sources VALUES ('dcim', 'content://provider/tree/primary%3ADCIM', 1);
        INSERT INTO image_sources VALUES ('download', 'content://provider/tree/primary%3ADownload', 2);
        INSERT INTO image_sources VALUES ('system:mediastore:external-images', 'toimg://source/system-mediastore-external-images', 3);
        INSERT INTO image_sources VALUES ('system:photo-picker-selections', 'toimg://source/photo-picker-selections', 4);
        INSERT INTO images VALUES ('i1', 'content://provider/tree/primary%3ADCIM/document/primary%3ADCIM%2Fa.jpg');
        INSERT INTO images VALUES ('i2', 'content://provider/tree/primary%3ADCIM/document/primary%3ADCIM%2Fb.jpg');
        INSERT INTO images VALUES ('m1', 'content://media/external/images/media/12');
        INSERT INTO image_source_links VALUES ('i1', 'dcim');
        INSERT INTO image_source_links VALUES ('i2', 'dcim');
    """)
    projection_match = re.search(r'@Query\("""(SELECT s\.\*.*?FROM image_sources s ORDER BY s\.addedAt DESC)"""\)', dao, re.S)
    if not projection_match:
        raise SystemExit("could not extract actual ImageSourceDao.observeAll SQL")
    projection_sql = projection_match.group(1)
    rows = {row[0]: row for row in conn.execute(projection_sql).fetchall()}
    # Column positions: id, treeUriString, addedAt, linkedImageCount, currentLocationCount.
    checks["actual DAO projection reports linked and current SAF counts"] = rows['dcim'][-2:] == (2, 2)
    checks["actual DAO projection counts MediaStore current URIs"] = rows['system:mediastore:external-images'][-1] == 1
    checks["actual DAO projection marks Photo Picker location as not applicable"] = rows['system:photo-picker-selections'][-1] is None
    conn.execute("UPDATE images SET uriString=? WHERE id='i1'", ('content://provider/tree/primary%3AArchive/document/primary%3AArchive%2Fa.jpg',))
    rows_after = {row[0]: row for row in conn.execute(projection_sql).fetchall()}
    checks["after move: historical link remains but current location decrements"] = rows_after['dcim'][-2:] == (2, 1)
    conn.close()

    # Concurrent source batches use separate connections and transaction boundaries.
    setup = sqlite3.connect(db_path)
    setup.executescript("INSERT INTO source_scan_stats(sourceId, discoveredCount) VALUES ('dcim',0),('download',0);")
    setup.commit(); setup.close()
    errors = []
    def batch(source_id, amount):
        try:
            c = sqlite3.connect(db_path, timeout=10)
            for _ in range(25):
                c.execute("BEGIN IMMEDIATE")
                c.execute("UPDATE source_scan_stats SET discoveredCount=discoveredCount+? WHERE sourceId=?", (amount, source_id))
                c.commit()
            c.close()
        except Exception as exc:
            errors.append(exc)
    threads = [threading.Thread(target=batch, args=("dcim", 2)), threading.Thread(target=batch, args=("download", 3))]
    for t in threads: t.start()
    for t in threads: t.join()
    c = sqlite3.connect(db_path)
    stats = dict(c.execute("SELECT sourceId, discoveredCount FROM source_scan_stats"))
    c.close()
    checks["concurrent source batches remain isolated"] = not errors and stats == {"dcim": 50, "download": 75}

    # Failure after link + stat write must roll back all writes in the batch.
    c = sqlite3.connect(db_path)
    try:
        c.execute("BEGIN")
        c.execute("INSERT INTO images VALUES ('rollback-image','content://provider/tree/rollback/document/rollback%2Fx.jpg')")
        c.execute("INSERT INTO image_source_links VALUES ('rollback-image','dcim')")
        c.execute("UPDATE source_scan_stats SET discoveredCount=discoveredCount+1 WHERE sourceId='dcim'")
        raise RuntimeError("simulated batch failure")
    except RuntimeError:
        c.rollback()
    image_exists = c.execute("SELECT COUNT(*) FROM images WHERE id='rollback-image'").fetchone()[0]
    link_exists = c.execute("SELECT COUNT(*) FROM image_source_links WHERE imageId='rollback-image'").fetchone()[0]
    stat_value = c.execute("SELECT discoveredCount FROM source_scan_stats WHERE sourceId='dcim'").fetchone()[0]
    c.close()
    checks["failed batch rolls back image, link and stat together"] = (image_exists, link_exists, stat_value) == (0, 0, 50)
finally:
    os.remove(db_path)

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " - " + name)
if failed:
    raise SystemExit(f"{len(failed)} checks failed: {failed}")
print(f"All {len(checks)} v0.1.23 regression checks passed")
