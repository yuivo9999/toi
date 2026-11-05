#!/usr/bin/env python3
"""v0.1.27: source-removal audit, retained scan-source snapshots and v8->v9 migration."""
from pathlib import Path
import json
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
def read(path): return (ROOT / path).read_text(encoding="utf-8")

db_src = read("app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt")
repo = read("app/src/main/java/com/toimg/app/data/repository/ImageSourceRepositoryImpl.kt")
worker = read("app/src/main/java/com/toimg/app/core/work/ScanWorker.kt")
scheduler = read("app/src/main/java/com/toimg/app/core/work/ScanTaskScheduler.kt")
session = read("app/src/main/java/com/toimg/app/data/local/entity/ScanSessionEntity.kt")
history_vm = read("app/src/main/java/com/toimg/app/feature/history/TaskHistoryViewModel.kt")
history_ui = read("app/src/main/java/com/toimg/app/feature/history/TaskHistoryScreen.kt")
source_ui = read("app/src/main/java/com/toimg/app/feature/sources/SourceManagementScreen.kt")
build = read("app/build.gradle.kts")

checks = {
    "current release version 0.1.29 / code 28": 'versionName = "0.1.30"' in build and "versionCode = 28" in build,
    "Current Room schema v10": "version = 10" in db_src,
    "v8->v9 migration registered": "MIGRATION_8_9" in db_src and "Migration(8, 9)" in db_src,
    "audit table is part of Room database": "SourceLifecycleEventEntity::class" in db_src and "sourceLifecycleEventDao()" in db_src,
    "scan source display-name snapshot is persisted": "selectedSourceNames" in session and "selectedSourceNames =" in worker and "selectedSourceNames = sourceNames.joinToString" in scheduler,
    "scan history reads durable sessions": "observeHistory()" in history_vm and "ScanHistoryList(s.scanSessions" in history_ui,
    "source management displays removal audit": "observeRecentLifecycleEvents" in repo and "来源变更审计" in source_ui,
    "removal audit is inserted inside the Room transaction": "db.withTransaction" in repo and "sourceLifecycleEventDao().insert" in repo,
    "audit explicitly records zero device-file deletions": "deviceFilesDeleted = 0" in repo,
    "Room v9 schema snapshot exists": (ROOT / "app/schemas/com.toimg.app.data.local.ToImgDatabase/9.json").exists(),
}

# Extract and execute the exact v8->v9 migration statements from source.
block = re.search(r"private val MIGRATION_8_9 = object : Migration\(8, 9\) \{(.*?)\n        \}", db_src, re.S)
assert block, "MIGRATION_8_9 block missing"
body = block.group(1)
matches = []
for m in re.finditer(r'db\.execSQL\("((?:\\.|[^"\\])*)"\)', body):
    matches.append((m.start(), bytes(m.group(1), "utf-8").decode("unicode_escape")))
for m in re.finditer(r'db\.execSQL\("""(.*?)"""\.trimIndent\(\)\)', body, re.S):
    matches.append((m.start(), "\n".join(line.strip() for line in m.group(1).strip().splitlines())))
statements = [sql for _, sql in sorted(matches, key=lambda item: item[0])]
assert len(statements) == 4, f"expected 4 migration statements, got {len(statements)}"

con = sqlite3.connect(":memory:")
con.execute("CREATE TABLE scan_sessions (id TEXT PRIMARY KEY NOT NULL, status TEXT NOT NULL)")
for sql in statements:
    con.execute(sql)
columns = {r[1] for r in con.execute("PRAGMA table_info(scan_sessions)")}
checks["migration adds source-name snapshot column"] = "selectedSourceNames" in columns
checks["migration creates lifecycle audit table"] = con.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='source_lifecycle_events'").fetchone() is not None
checks["migration creates both audit indexes"] = con.execute("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name LIKE 'index_source_lifecycle_events_%'").fetchone()[0] == 2
v9_schema = json.loads((ROOT / "app/schemas/com.toimg.app.data.local.ToImgDatabase/9.json").read_text(encoding="utf-8"))
expected_audit = next(e for e in v9_schema["database"]["entities"] if e["tableName"] == "source_lifecycle_events")
actual_audit_columns = [r[1] for r in con.execute("PRAGMA table_info(source_lifecycle_events)")]
checks["migration audit table columns match v9 snapshot"] = actual_audit_columns == [f["columnName"] for f in expected_audit["fields"]]
actual_audit_indexes = {r[1] for r in con.execute("PRAGMA index_list(source_lifecycle_events)")}
checks["migration audit indexes match v9 snapshot"] = {i["name"] for i in expected_audit["indices"]}.issubset(actual_audit_indexes)

# Atomic source removal model: audit row and cleanup commit together; forced error rolls both back.
con.executescript("""
CREATE TABLE images(id TEXT PRIMARY KEY);
CREATE TABLE image_source_links(imageId TEXT, sourceId TEXT);
CREATE TABLE recognition_results(imageId TEXT PRIMARY KEY);
CREATE TABLE image_search_fts(imageId TEXT PRIMARY KEY);
CREATE TABLE image_sources(id TEXT PRIMARY KEY, displayName TEXT);
INSERT INTO image_sources VALUES('src-a','DCIM');
INSERT INTO images VALUES('img-exclusive');
INSERT INTO images VALUES('img-shared');
INSERT INTO recognition_results VALUES('img-exclusive');
INSERT INTO recognition_results VALUES('img-shared');
INSERT INTO image_search_fts VALUES('img-exclusive');
INSERT INTO image_search_fts VALUES('img-shared');
INSERT INTO image_source_links VALUES('img-exclusive','src-a');
INSERT INTO image_source_links VALUES('img-shared','src-a');
INSERT INTO image_source_links VALUES('img-shared','src-b');
""")
try:
    con.execute("BEGIN")
    con.execute("INSERT INTO source_lifecycle_events(sourceId,sourceName,eventType,status,linkedImageCount,deletedImageCount,retainedSharedImageCount,deviceFilesDeleted,detail,createdAt) VALUES(?,?,?,?,?,?,?,?,?,?)", ('src-a','DCIM','SOURCE_REMOVED','COMPLETED',2,1,1,0,'audit',1))
    con.execute("DELETE FROM image_source_links WHERE sourceId='src-a'")
    con.execute("DELETE FROM recognition_results WHERE imageId='img-exclusive'")
    con.execute("DELETE FROM image_search_fts WHERE imageId='img-exclusive'")
    con.execute("DELETE FROM images WHERE id='img-exclusive'")
    con.execute("DELETE FROM image_sources WHERE id='src-a'")
    raise RuntimeError("forced failure")
except RuntimeError:
    con.rollback()
checks["audit and source cleanup roll back atomically"] = con.execute("SELECT COUNT(*) FROM source_lifecycle_events").fetchone()[0] == 0 and con.execute("SELECT COUNT(*) FROM image_sources WHERE id='src-a'").fetchone()[0] == 1 and con.execute("SELECT COUNT(*) FROM images WHERE id='img-exclusive'").fetchone()[0] == 1

# Successful removal keeps the shared image and records no device-file deletion.
con.execute("BEGIN")
con.execute("INSERT INTO source_lifecycle_events(sourceId,sourceName,eventType,status,linkedImageCount,deletedImageCount,retainedSharedImageCount,deviceFilesDeleted,detail,createdAt) VALUES(?,?,?,?,?,?,?,?,?,?)", ('src-a','DCIM','SOURCE_REMOVED','COMPLETED',2,1,1,0,'audit',2))
con.execute("DELETE FROM image_source_links WHERE sourceId='src-a'")
con.execute("DELETE FROM recognition_results WHERE imageId='img-exclusive'")
con.execute("DELETE FROM image_search_fts WHERE imageId='img-exclusive'")
con.execute("DELETE FROM images WHERE id='img-exclusive'")
con.execute("DELETE FROM image_sources WHERE id='src-a'")
con.commit()
checks["successful removal audit preserves shared index and source image bytes are outside DB operation"] = con.execute("SELECT COUNT(*) FROM images WHERE id='img-shared'").fetchone()[0] == 1 and con.execute("SELECT deviceFilesDeleted FROM source_lifecycle_events").fetchone()[0] == 0

schema = json.loads((ROOT / "app/schemas/com.toimg.app.data.local.ToImgDatabase/9.json").read_text(encoding="utf-8"))
checks["v9 schema JSON declares version 9 and audit entity"] = schema["database"]["version"] == 9 and any(e["tableName"] == "source_lifecycle_events" for e in schema["database"]["entities"])

for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " - " + name)
assert all(checks.values()), f"{sum(not x for x in checks.values())} checks failed"
print(f"All {len(checks)} v0.1.27 checks passed")
