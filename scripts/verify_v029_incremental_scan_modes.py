#!/usr/bin/env python3
"""v0.1.29 incremental scan mode and old-image reconciliation regression checks."""
from pathlib import Path
import json
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
def read(path): return (ROOT / path).read_text(encoding="utf-8")

build = read("app/build.gradle.kts")
db_src = read("app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt")
session = read("app/src/main/java/com/toimg/app/data/local/entity/ScanSessionEntity.kt")
worker = read("app/src/main/java/com/toimg/app/core/work/ScanWorker.kt")
scheduler = read("app/src/main/java/com/toimg/app/core/work/ScanTaskScheduler.kt")
manager = read("app/src/main/java/com/toimg/app/core/util/ScanManager.kt")
repo = read("app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt")
screen = read("app/src/main/java/com/toimg/app/feature/scan/ScanScopeScreen.kt")
vm = read("app/src/main/java/com/toimg/app/feature/scan/ScanScopeViewModel.kt")

def check(condition, label):
    if not condition:
        raise AssertionError(label)
    print(f"PASS {label}")

check('versionName = "0.1.30"' in build and "versionCode = 28" in build, "release 0.1.29/code 28")
check("version = 10" in db_src and "MIGRATION_9_10" in db_src and "ALL_MIGRATIONS" in db_src, "Room v10 and formal 9->10 migration")
check("scanMode" in session and "FAST_INCREMENTAL" in session, "scan session persists scan mode")
check("KEY_SCAN_MODE" in worker and "ScanMode.fromPersisted" in worker, "worker restores persisted scan mode")
check("scanMode = scanMode.persistedValue" in scheduler and "putString(ScanWorker.KEY_SCAN_MODE" in scheduler, "scheduler persists selected mode")
check("scan_mode_fast_radio" in screen and "scan_mode_verify_radio" in screen, "UI exposes both scan modes")
check("scanMode: ScanMode = ScanMode.FAST_INCREMENTAL" in vm and "scanMode = s.scanMode" in vm, "fast mode is default and selection reaches scheduler")
check("getImageByUri(rawUriString)" in repo and "getImageByUri(rawFileUri)" in repo, "fast mode checks exact known URI before ingest")
check("scanMode == ScanMode.FAST_INCREMENTAL" in repo, "fast mode skips known SAF and MediaStore rows")
check("scanMode == ScanMode.VERIFY_EXISTING" in manager, "verify mode runs legacy image reconciliation")
check("checkAvailability(app, sourceImageIds)" in manager, "verify mode checks old image availability")
check("invalidateChangedRecognition" in repo and "deleteByImageIds(changedIds)" in repo, "changed old image invalidates stale recognition")
check("syncFtsForImage(it)" in repo, "changed old image refreshes FTS")
check("reportExisting(fastExistingCount)" in repo and "fun reportExisting(count: Int)" in manager, "fast mode accounts for already indexed images")
check("ensureSourceLinksForKnownImages" in repo and "linkedImageIds(sourceId, batch)" in repo, "fast mode repairs missing source links in batches without re-ingesting image")

# Execute the actual migration SQL extracted from MIGRATION_9_10 against a v9-shaped table.
migration = re.search(r"private val MIGRATION_9_10 = object : Migration\(9, 10\).*?override fun migrate\(db: SupportSQLiteDatabase\) \{(.*?)\n            \}", db_src, re.S)
check(migration is not None, "migration source block is discoverable")
sql = re.findall(r'db\.execSQL\("([^"]+)"\)', migration.group(1))
check(len(sql) == 1 and "scanMode" in sql[0], "migration adds scanMode column")
con = sqlite3.connect(":memory:")
con.execute("CREATE TABLE scan_sessions (id TEXT PRIMARY KEY NOT NULL, status TEXT NOT NULL)")
con.execute("INSERT INTO scan_sessions(id,status) VALUES('old','SUCCESS')")
con.execute(sql[0])
row = con.execute("SELECT scanMode FROM scan_sessions WHERE id='old'").fetchone()
check(row == ("VERIFY_EXISTING",), "existing sessions migrate safely into verify mode")
con.execute("INSERT INTO scan_sessions(id,status,scanMode) VALUES('new','QUEUED','FAST_INCREMENTAL')")
check(con.execute("SELECT scanMode FROM scan_sessions WHERE id='new'").fetchone() == ("FAST_INCREMENTAL",), "new sessions can persist fast mode")
con.close()

schema = json.loads(read("app/schemas/com.toimg.app.data.local.ToImgDatabase/10.json"))
check(schema["database"]["version"] == 10, "candidate Room v10 schema JSON parses")
scan_sessions = next(e for e in schema["database"]["entities"] if e["tableName"] == "scan_sessions")
check(any(f["columnName"] == "scanMode" for f in scan_sessions["fields"]), "candidate schema includes scanMode")

print("All v0.1.29 incremental scan checks passed")
