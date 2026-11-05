#!/usr/bin/env python3
"""Static + SQLite checks for v0.1.20 cross-entry image identity and Room schemas."""
from __future__ import annotations
import json
import sqlite3
import re
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

resolver = (ROOT / "app/src/main/java/com/toimg/app/core/util/ImageIdentityResolver.kt").read_text()
policy = (ROOT / "app/src/main/java/com/toimg/app/core/util/ImageIdentityPolicy.kt").read_text()
picker = (ROOT / "app/src/main/java/com/toimg/app/feature/picker/ImagePickerViewModel.kt").read_text()
repo = (ROOT / "app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt").read_text()
dao = (ROOT / "app/src/main/java/com/toimg/app/data/local/dao/ImageDao.kt").read_text()
test = (ROOT / "app/src/androidTest/java/com/toimg/app/data/ToImgDatabaseMigrationTest.kt").read_text()
build = (ROOT / "app/build.gradle.kts").read_text()
db_source = (ROOT / "app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt").read_text()

assert 'versionName = "0.1.30"' in build and "versionCode = 28" in build
assert "MediaStore.getMediaUri(context, uri)" in resolver
assert "Build.VERSION.SDK_INT < Build.VERSION_CODES.Q" in resolver
assert "Sha256Util.calculateSha256" in resolver
assert "ImageIdentityPolicy.uniqueCandidate(candidates)" in resolver
assert "ImageIdentityResolver.resolveAndPersist(context, db, picked)" in picker
assert repo.count("ImageIdentityResolver.resolveAndPersist(context, db, rawEntity)") >= 2
assert "findIdentityCandidates" in dao and "LIMIT 20" not in dao
assert "dateModified = :dateModified" in dao and "mimeType = :mimeType" in dao
assert "dateModified == incoming.dateModified" in policy
assert "candidates.singleOrNull()" in policy
identity_test = (ROOT / "app/src/test/java/com/toimg/app/core/util/ImageIdentityPolicyTest.kt").read_text()
assert "moreThanTwentyMetadataMatchesRemainAmbiguous" in identity_test
assert "private val currentVersion = 10" in test

schema_dir = ROOT / "app/schemas/com.toimg.app.data.local.ToImgDatabase"
schemas = {v: json.loads((schema_dir / f"{v}.json").read_text()) for v in (6, 7, 8)}
assert schemas[6]["database"]["version"] == 6
assert schemas[7]["database"]["version"] == 7
assert schemas[8]["database"]["version"] == 8
v7_tables = {e["tableName"] for e in schemas[7]["database"]["entities"]}
v8_tables = {e["tableName"] for e in schemas[8]["database"]["entities"]}
assert "scan_sessions" in v7_tables
assert {"image_sources", "image_source_links", "source_scan_stats"}.issubset(v8_tables)
scan = next(e for e in schemas[8]["database"]["entities"] if e["tableName"] == "scan_sessions")
assert "selectedSourceIds" in {f["columnName"] for f in scan["fields"]}

# Execute the exported v8 table/index SQL to ensure the snapshot is internally executable.
db = sqlite3.connect(":memory:")
for entity in schemas[8]["database"]["entities"]:
    sql = entity["createSql"].replace("${TABLE_NAME}", entity["tableName"])
    db.execute(sql)
    for index in entity.get("indices", []):
        db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
actual = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type IN ('table','view')")}
assert {"images", "scan_sessions", "image_sources", "image_source_links", "source_scan_stats"}.issubset(actual)
assert db.execute("PRAGMA table_info(scan_sessions)").fetchall()[-1][1] == "selectedSourceIds"
assert db.execute("PRAGMA index_list(image_source_links)").fetchall()



def migration_sql(marker: str, end_marker: str) -> list[str]:
    start = db_source.index(marker)
    end = db_source.index(end_marker, start)
    block = db_source[start:end]
    pattern = re.compile(r'db\.execSQL\(\s*(?:"""(.*?)"""|("(?:\\.|[^"\\])*"))\s*(?:\.trimIndent\(\))?\s*\)', re.DOTALL)
    statements = []
    for match in pattern.finditer(block):
        if match.group(1) is not None:
            statements.append(textwrap.dedent(match.group(1)).strip())
        else:
            statements.append(json.loads(match.group(2)))
    return statements

# Recreate v6 from its Room snapshot, apply the actual Kotlin 6->7->8 SQL,
# then compare resulting columns/index names with the v8 snapshot.
migrated = sqlite3.connect(":memory:")
for entity in schemas[6]["database"]["entities"]:
    migrated.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for index in entity.get("indices", []):
        migrated.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
for sql in migration_sql("private val MIGRATION_6_7", "private val MIGRATION_7_8"):
    migrated.execute(sql)
for sql in migration_sql("private val MIGRATION_7_8", "/** v8 -> v9"):
    migrated.execute(sql)
for entity in schemas[8]["database"]["entities"]:
    table = entity["tableName"]
    expected_cols = [f["columnName"] for f in entity["fields"]]
    actual_cols = [r[1] for r in migrated.execute(f"PRAGMA table_info(`{table}`)")]
    assert actual_cols == expected_cols, (table, expected_cols, actual_cols)
    expected_indexes = {i["name"] for i in entity.get("indices", [])}
    actual_indexes = {r[1] for r in migrated.execute(f"PRAGMA index_list(`{table}`)")}
    assert expected_indexes.issubset(actual_indexes), (table, expected_indexes, actual_indexes)

print("PASS: SAF, MediaStore and Photo Picker call the shared image identity resolver")
print("PASS: metadata narrows candidates; unique match and SHA-256 are required before reuse")
print("PASS: Room v7/v8 schema snapshots include scan/source/link/stat structures")
print("PASS: exported v8 schema table/index SQL executes in SQLite")
print("PASS: actual v6->v7->v8 migration SQL matches v8 schema columns and indexes")
