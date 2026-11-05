#!/usr/bin/env python3
"""v0.1.24 scan cancellation/recovery, source-removal race and index-integrity regression checks."""
from pathlib import Path
import sqlite3
import tempfile
import threading
import re
import os

ROOT = Path(__file__).resolve().parents[1]
def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

scheduler = read("app/src/main/java/com/toimg/app/core/work/ScanTaskScheduler.kt")
worker = read("app/src/main/java/com/toimg/app/core/work/ScanWorker.kt")
scan_manager = read("app/src/main/java/com/toimg/app/core/util/ScanManager.kt")
repo = read("app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt")
source_dao = read("app/src/main/java/com/toimg/app/data/local/dao/ImageSourceDao.kt")
recognition_dao = read("app/src/main/java/com/toimg/app/data/local/dao/RecognitionDao.kt")
fts_dao = read("app/src/main/java/com/toimg/app/data/local/dao/ImageFtsDao.kt")
auditor = read("app/src/main/java/com/toimg/app/core/util/ImageIndexIntegrityAuditor.kt")
source_ui = read("app/src/main/java/com/toimg/app/feature/sources/SourceManagementScreen.kt")
scan_session_dao = read("app/src/main/java/com/toimg/app/data/local/dao/ScanSessionDao.kt")
build = read("app/build.gradle.kts")

checks = {
    "release version 0.1.29/code 28": 'versionName = "0.1.30"' in build and "versionCode = 28" in build,
    "Room remains v8": "version = 10" in read("app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt"),
    "queued scan session is persisted before WorkManager enqueue": scheduler.index("db.withTransaction") < scheduler.index("enqueueUniqueWork(ScanWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)"),
    "each source receives QUEUED reservation before enqueue": 'status = "QUEUED"' in scheduler and "upsertScanStat" in scheduler,
    "queued and post-traversal sessions remain active for removal guard": "INNER JOIN scan_sessions ss" in source_dao and "ss.status IN ('QUEUED', 'RUNNING', 'PAUSED')" in source_dao,
    "cancel confirms WorkManager stop before releasing active flag": "cancellationConfirmed" in scheduler and "if (!cancellationConfirmed) return@withLock" in scheduler and scheduler.index("cancelUniqueWork(ScanWorker.UNIQUE_WORK_NAME).result.get()") < scheduler.index("markFinished(app, sessionId, cancellationCompleted = true)", scheduler.index("fun cancel")),
    "enqueue/cancel race uses generation token and shared lifecycle mutex": "KEY_GENERATION" in scheduler and "prefs.getInt(KEY_GENERATION, 0) != generation" in scheduler and "lifecycleMutex.withLock" in scheduler,
    "old Worker cannot clear a newer or cancelling task marker": "currentSessionId == expectedSessionId" in scheduler and "cancellationCompleted" in scheduler and "KEY_CANCEL_REQUESTED" in scheduler,
    "system stop is distinguished from explicit user cancellation": "current?.status == ScanSessionEntity.CANCELLED" in worker and "系统暂时中断" in worker,
    "system stop requeues scan stats": "requeueInterruptedStatsForSession" in worker,
    "Worker joins old scanner before retry": "ScanManager.cancelAndJoin()" in worker,
    "same-process scanner exposes cancelAndJoin": "suspend fun cancelAndJoin()" in scan_manager and "job?.cancelAndJoin()" in scan_manager,
    "missing WorkManager row can be reconstructed from Room snapshot": "infos.isEmpty() && storedActive" in scheduler and "session.folderUriList()" in scheduler,
    "scan batch checks source still exists inside Room transaction": repo.count("writableSourceCount(sourceId) == 0") >= 2 and repo.count("db.withTransaction") >= 2,
    "source removal still blocks active source scans": "activeScanCountForSource(sourceId) > 0" in read("app/src/main/java/com/toimg/app/data/repository/ImageSourceRepositoryImpl.kt"),
    "recognition orphan diagnostic exists": "countOrphanRecognitions" in recognition_dao,
    "FTS orphan and missing-row diagnostics exist": "countOrphanFtsRows" in fts_dao and "countImagesMissingFtsRows" in fts_dao,
    "source-link orphan diagnostic exists": "countOrphanSourceLinks" in source_dao,
    "integrity auditor is read-only except derived FTS rebuild": "suspend fun inspect()" in auditor and "rebuildAllFts()" in auditor,
    "source management UI exposes integrity check and safe FTS rebuild": "检查索引完整性" in source_ui and "重建搜索索引" in source_ui and "ImageIndexIntegrityAuditor" in source_ui,
    "Photo Picker selection reserves an active source session": "正在登记图片选择器选取" in read("app/src/main/java/com/toimg/app/feature/picker/ImagePickerViewModel.kt") and "status = \"RUNNING\"" in read("app/src/main/java/com/toimg/app/feature/picker/ImagePickerViewModel.kt"),
    "stale Photo Picker sessions are expired after process death": "failStalePickerSessions" in scan_session_dao and "failStalePickerStats" in source_dao and "6L * 60L * 60L * 1000L" in source_ui,
}

# SQLite model: source reservation closes the QUEUED->remove race; serialized batch transactions stay isolated.
fd, db_path = tempfile.mkstemp(suffix=".db")
os.close(fd)
try:
    c = sqlite3.connect(db_path)
    c.executescript("""
      CREATE TABLE image_sources(id TEXT PRIMARY KEY, lifecycleStatus TEXT NOT NULL);
      CREATE TABLE scan_sessions(id TEXT PRIMARY KEY, status TEXT NOT NULL, startedAt INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL DEFAULT 0, finishedAt INTEGER, statusText TEXT NOT NULL DEFAULT '', errorMessage TEXT);
      CREATE TABLE source_scan_stats(scanSessionId TEXT NOT NULL, sourceId TEXT NOT NULL, status TEXT NOT NULL, discovered INTEGER NOT NULL DEFAULT 0, finishedAt INTEGER, errorMessage TEXT, PRIMARY KEY(scanSessionId,sourceId));
      INSERT INTO scan_sessions(id,status) VALUES ('s1','RUNNING');
      CREATE TABLE images(id TEXT PRIMARY KEY, sourceId TEXT NOT NULL);
      INSERT INTO image_sources VALUES ('dcim','SCANNING'),('download','SCANNING');
      INSERT INTO source_scan_stats(scanSessionId,sourceId,status,discovered) VALUES ('s1','dcim','QUEUED',0),('s1','download','QUEUED',0);
    """)
    c.commit(); c.close()

    # A removal transaction sees the QUEUED reservation and must refuse removal.
    c = sqlite3.connect(db_path)
    active_match = re.search(r'@Query\(\"\"\"(SELECT COUNT\(\*\).*?)\"\"\"\)', source_dao, re.S)
    if not active_match:
        raise SystemExit("could not extract activeScanCountForSource SQL")
    active_sql = active_match.group(1).replace(":sourceId", "?")
    active = c.execute(active_sql, ('dcim',)).fetchone()[0]
    checks["queued source reservation blocks project removal"] = active == 1
    c.execute("UPDATE source_scan_stats SET status='COMPLETED' WHERE sourceId='dcim'")
    still_active = c.execute(active_sql, ('dcim',)).fetchone()[0]
    checks["post-traversal cleanup phase still blocks source removal"] = still_active == 1
    checks["actual DAO active-session SQL is executable and blocks removal"] = active == 1 and still_active == 1
    c.close()

    # If removal wins first, a later scanner batch must fail its source-exists fence.
    c = sqlite3.connect(db_path)
    c.execute("BEGIN IMMEDIATE")
    c.execute("DELETE FROM source_scan_stats WHERE sourceId='download'")
    c.execute("DELETE FROM image_sources WHERE id='download'")
    c.commit()
    c.execute("BEGIN IMMEDIATE")
    writable = c.execute("SELECT COUNT(*) FROM image_sources WHERE id='download' AND lifecycleStatus != 'REMOVING'").fetchone()[0]
    if writable:
        c.execute("INSERT INTO images VALUES ('should-not-exist','download')")
    c.rollback()
    exists = c.execute("SELECT COUNT(*) FROM images WHERE id='should-not-exist'").fetchone()[0]
    checks["removed source cannot be resurrected by late scan batch"] = writable == 0 and exists == 0
    c.close()

    # Execute the actual stale Photo Picker cleanup SQL against SQLite.
    picker = sqlite3.connect(":memory:")
    picker.executescript("""
      CREATE TABLE scan_sessions(id TEXT PRIMARY KEY, status TEXT NOT NULL, startedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, finishedAt INTEGER, statusText TEXT NOT NULL, errorMessage TEXT);
      CREATE TABLE source_scan_stats(scanSessionId TEXT NOT NULL, sourceId TEXT NOT NULL, status TEXT NOT NULL, finishedAt INTEGER, errorMessage TEXT);
      CREATE TABLE image_sources(id TEXT PRIMARY KEY, lifecycleStatus TEXT NOT NULL, errorMessage TEXT);
      INSERT INTO scan_sessions VALUES ('picker-old','RUNNING',1,1,NULL,'running',NULL);
      INSERT INTO source_scan_stats VALUES ('picker-old','system:photo-picker-selections','RUNNING',NULL,NULL);
      INSERT INTO image_sources VALUES ('system:photo-picker-selections','SCANNING',NULL);
    """)
    session_sql_match = re.search(r'@Query\(\"\"\"(UPDATE scan_sessions .*?)\"\"\"\)\s*suspend fun failStalePickerSessions', scan_session_dao, re.S)
    stats_sql_match = re.search(r'@Query\(\"\"\"(UPDATE source_scan_stats .*?)\"\"\"\)\s*suspend fun failStalePickerStats', source_dao, re.S)
    if not session_sql_match or not stats_sql_match:
        raise SystemExit("could not extract Photo Picker stale cleanup SQL")
    picker.execute(session_sql_match.group(1), {"cutoff": 100, "now": 200})
    picker.execute(stats_sql_match.group(1), {"cutoff": 100, "now": 200})
    picker.execute("UPDATE image_sources SET lifecycleStatus='PARTIAL', errorMessage='interrupted' WHERE id='system:photo-picker-selections' AND EXISTS (SELECT 1 FROM source_scan_stats WHERE sourceId='system:photo-picker-selections' AND status='FAILED' AND errorMessage='图片选择器导入中断')")
    session_state = picker.execute("SELECT status FROM scan_sessions WHERE id='picker-old'").fetchone()[0]
    stat_state = picker.execute("SELECT status FROM source_scan_stats WHERE scanSessionId='picker-old'").fetchone()[0]
    source_state = picker.execute("SELECT lifecycleStatus FROM image_sources WHERE id='system:photo-picker-selections'").fetchone()[0]
    checks["actual Photo Picker stale cleanup SQL expires orphan session and releases source"] = (session_state, stat_state, source_state) == ('FAILED', 'FAILED', 'PARTIAL')
    picker.close()

    # 8 workers x 100 batches: independent source counters must remain exact.
    c = sqlite3.connect(db_path)
    c.executemany("INSERT INTO source_scan_stats(scanSessionId,sourceId,status,discovered) VALUES (?,?,'RUNNING',0)", [(f"stress-{i}", f"source-{i}") for i in range(8)])
    c.commit(); c.close()
    errors = []
    def writer(index):
        try:
            con = sqlite3.connect(db_path, timeout=20)
            for _ in range(100):
                con.execute("BEGIN IMMEDIATE")
                con.execute("UPDATE source_scan_stats SET discovered=discovered+7 WHERE scanSessionId=?", (f"stress-{index}",))
                con.commit()
            con.close()
        except Exception as exc:
            errors.append(str(exc))
    threads = [threading.Thread(target=writer, args=(i,)) for i in range(8)]
    for t in threads: t.start()
    for t in threads: t.join()
    c = sqlite3.connect(db_path)
    values = dict(c.execute("SELECT scanSessionId, discovered FROM source_scan_stats WHERE scanSessionId LIKE 'stress-%'"))
    c.close()
    checks["8-source concurrent stress test preserves exact counters"] = not errors and len(values) == 8 and all(v == 700 for v in values.values())
finally:
    os.remove(db_path)

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + " - " + name)
if failed:
    raise SystemExit(f"{len(failed)} checks failed: {failed}")
print(f"All {len(checks)} v0.1.24 regression checks passed on v0.1.29")
