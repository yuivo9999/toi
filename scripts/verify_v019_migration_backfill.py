#!/usr/bin/env python3
"""Execute the actual v7->v8 SQL statements against a legacy SQLite fixture."""
from __future__ import annotations

import json
import re
import sqlite3
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB_SOURCE = ROOT / "app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt"
TEST_SOURCE = ROOT / "app/src/androidTest/java/com/toimg/app/data/ToImgDatabaseMigrationTest.kt"


def migration_statements(source: str) -> list[str]:
    marker = "private val MIGRATION_7_8"
    start = source.index(marker)
    end = source.index("/** v8 -> v9", start)
    block = source[start:end]
    pattern = re.compile(
        r'db\.execSQL\(\s*(?:"""(.*?)"""|("(?:\\.|[^"\\])*"))\s*(?:\.trimIndent\(\))?\s*\)',
        re.DOTALL,
    )
    statements: list[str] = []
    for match in pattern.finditer(block):
        if match.group(1) is not None:
            sql = textwrap.dedent(match.group(1)).strip()
        else:
            sql = json.loads(match.group(2))
        statements.append(sql)
    if len(statements) != 10:
        raise AssertionError(f"Expected 10 executable migration statements, found {len(statements)}")
    return statements


def main() -> None:
    source = DB_SOURCE.read_text(encoding="utf-8")
    test_source = TEST_SOURCE.read_text(encoding="utf-8")
    build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    behavior_test = (ROOT / "app/src/androidTest/java/com/toimg/app/data/ImageSourceRepositoryBehaviorTest.kt").read_text(encoding="utf-8")
    assert "version = 10" in source
    assert "private val currentVersion = 10" in test_source
    assert 'versionName = "0.1.30"' in build and "versionCode = 28" in build
    assert "removeSource_deletesExclusiveIndexButRetainsSharedImageAndRecognition" in behavior_test
    assert "removeSource_isBlockedWhileSourceHasActiveScan" in behavior_test

    db = sqlite3.connect(":memory:")
    db.executescript("""
        CREATE TABLE folders (
            id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, uriString TEXT NOT NULL,
            path TEXT NOT NULL, imageCount INTEGER NOT NULL, isAuthorized INTEGER NOT NULL,
            lastScannedAt INTEGER NOT NULL
        );
        CREATE TABLE images (
            id TEXT NOT NULL PRIMARY KEY, uriString TEXT NOT NULL, name TEXT NOT NULL,
            path TEXT NOT NULL, size INTEGER NOT NULL, dateModified INTEGER NOT NULL,
            mimeType TEXT NOT NULL, folderName TEXT NOT NULL, folderPath TEXT NOT NULL,
            sha256 TEXT, status TEXT NOT NULL, createdAt INTEGER NOT NULL
        );
        CREATE TABLE scan_sessions (
            id TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, includeMediaStore INTEGER NOT NULL,
            folderUris TEXT NOT NULL, includeSubfolders INTEGER NOT NULL, startedAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL, finishedAt INTEGER, phase TEXT NOT NULL,
            currentSource TEXT NOT NULL, currentDirectory TEXT NOT NULL, discoveredCount INTEGER NOT NULL,
            skippedCount INTEGER NOT NULL, unavailableCount INTEGER NOT NULL, sourcesDone INTEGER NOT NULL,
            sourcesTotal INTEGER NOT NULL, statusText TEXT NOT NULL, errorMessage TEXT
        );
    """)
    db.execute(
        "INSERT INTO folders VALUES (?, ?, ?, ?, ?, ?, ?)",
        ("dcim", "DCIM", "content://provider/tree/primary%3ADCIM", "", 99, 1, 1234),
    )
    root = "content://provider/tree/primary%3ADCIM/document/"
    rows = [
        ("a", root + "primary%3ADCIM%2FCamera%2Fa.jpg", "a.jpg", 10),
        ("b", root + "primary%3ADCIM%2FCamera%2Fb.jpg", "b.jpg", 20),
        ("outside", "content://provider/tree/primary%3ADownload/document/primary%3ADownload%2Fx.jpg", "x.jpg", 30),
    ]
    for image_id, uri, name, created in rows:
        db.execute(
            "INSERT INTO images VALUES (?, ?, ?, '', 1, ?, 'image/jpeg', 'DCIM', 'DCIM', NULL, 'SCANNED', ?)",
            (image_id, uri, name, created, created),
        )

    for sql in migration_statements(source):
        db.execute(sql)

    sources = db.execute("SELECT id, displayName, imageCount FROM image_sources").fetchall()
    links = db.execute("SELECT imageId, sourceId FROM image_source_links ORDER BY imageId").fetchall()
    assert sources == [("dcim", "DCIM", 2)], sources
    assert links == [("a", "dcim"), ("b", "dcim")], links
    assert db.execute("SELECT COUNT(*) FROM images").fetchone()[0] == 3
    assert db.execute("SELECT selectedSourceIds FROM scan_sessions").fetchone() is None

    # Idempotence guard: repeated backfill SQL cannot duplicate the composite-key links.
    backfill = next(sql for sql in migration_statements(source) if "INSERT OR IGNORE INTO image_source_links" in sql)
    db.execute(backfill)
    assert db.execute("SELECT COUNT(*) FROM image_source_links").fetchone()[0] == 2

    print("PASS: v7->v8 migration SQL creates source records and backfills legacy image links")
    print("PASS: source imageCount is recomputed from links, not trusted from stale folders cache")
    print("PASS: unrelated images remain untouched; composite-key backfill is idempotent")


if __name__ == "__main__":
    main()
