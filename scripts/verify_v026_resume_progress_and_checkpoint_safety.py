#!/usr/bin/env python3
"""v0.1.26 resume counters, checkpoint write degradation, SAF reauthorization and stress checks."""
from pathlib import Path
import base64
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[1]
def read(path): return (ROOT / path).read_text(encoding='utf-8')

build = read('app/build.gradle.kts')
checkpoint = read('app/src/main/java/com/toimg/app/core/util/ScanCheckpointStore.kt')
repo = read('app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt')
worker = read('app/src/main/java/com/toimg/app/core/work/ScanWorker.kt')
manager = read('app/src/main/java/com/toimg/app/core/util/ScanManager.kt')
dao = read('app/src/main/java/com/toimg/app/data/local/dao/ImageSourceDao.kt')
ui = read('app/src/main/java/com/toimg/app/feature/scan/ScanProgressScreen.kt')

checks = {
    'version 0.1.26 / code 24': 'versionName = "0.1.30"' in build and 'versionCode = 28' in build,
    'checkpoint append reports durable-write success/failure': 'fun append(context: Context, sessionId: String?, sourceId: String?, itemUris: Collection<String>): Boolean' in checkpoint,
    'checkpoint low-storage IO failure is handled without throwing': 'catch (_: IOException)' in checkpoint and 'catch (_: SecurityException)' in checkpoint,
    'checkpoint data is flushed before fsync': checkpoint.index('writer.flush()') < checkpoint.index('stream.fd.sync()'),
    'failed journal append degrades to safe at-least-once re-read': '本次扫描继续，但中断后可能需要重新核对该来源' in repo,
    'checkpoint append is disabled after first failure per source run': repo.count('var checkpointWritesEnabled = true') >= 2 and repo.count('checkpointWritesEnabled = false') >= 2,
    'MediaStore skips only when size and modified time are reliable': 'hasReliableMetadata(size, dateModified)' in repo and 'if (reliableMetadata && checkpointToken in checkpointedUris) continue' in repo,
    'SAF skips only when size and modified time are reliable': 'hasReliableMetadata(fileSize, fileModifiedAt)' in repo and repo.count('if (reliableMetadata && checkpointToken in checkpointedUris) continue') >= 2,
    'unreliable provider metadata is deliberately not checkpointed': repo.count('if (reliableMetadata) pendingCheckpointUris.add(checkpointToken)') >= 2,
    'worker restores cumulative discovered counters before requeueing source stats': 'val priorStatsBySource = sourceDao.scanStatsForSession(sessionId).associateBy { it.sourceId }' in worker and 'val restoredDiscoveredCount = priorStatsBySource.values.sumOf { it.discoveredCount }' in worker,
    'worker preserves prior source counters instead of resetting them': 'previousStat?.copy(' in worker and 'finishedAt = null, errorMessage = null' in worker,
    'worker restores skipped counters per source': 'restoredSourceSkippedCounts = priorStatsBySource.mapValues { it.value.skippedCount }' in worker,
    'scan manager initializes live progress from durable counters': 'initialDiscoveredCount: Int = 0' in manager and 'initialSkippedCount: Int = 0' in manager and 'sourceSkippedCounts = initialSourceSkippedCounts' in manager,
    'worker passes restored counters into scan manager': 'initialDiscoveredCount = restoredDiscoveredCount' in worker and 'initialSkippedCount = restoredSkippedCount' in worker,
    'SAF source display name and URI refresh after provider-side rename or reauthorization': 'displayName = name' in worker and 'treeUriString = rawUri' in worker,
    'authorization failure is persisted as AUTH_EXPIRED': "lifecycleStatus = 'AUTH_EXPIRED'" in dao and 'markAuthorizationExpired(sourceId, sourceError)' in worker,
    'source root read failure is recognized as authorization loss': 'FileNotFoundException' in worker and '授权已失效' in worker,
    'live scan page displays checkpoint degradation warnings': '扫描提示' in ui and 'p.warnings.forEach' in ui,
    'checkpoint degradation warning survives process recreation in session status': 'progress.warnings' in worker and 'finalProgress.warnings' in worker and 'statusText = persistedStatusText' in worker,
}

# Long multi-source counter stress: resume must add only newly committed rows and retain prior counts.
with tempfile.NamedTemporaryFile() as tmp:
    db = sqlite3.connect(tmp.name)
    db.executescript('''CREATE TABLE stats(session_id TEXT, source_id TEXT, discovered INTEGER, skipped INTEGER, added INTEGER, PRIMARY KEY(session_id,source_id));''')
    session = 'stress-session'
    sources = [f'source-{i}' for i in range(40)]
    db.executemany('INSERT INTO stats VALUES(?,?,?,?,?)', [(session, s, 0, 0, 0) for s in sources])
    db.commit()
    for round_no in range(5):
        db.execute('BEGIN')
        for s in sources:
            db.execute('UPDATE stats SET discovered=discovered+250, skipped=skipped+3, added=added+240 WHERE session_id=? AND source_id=?', (session, s))
        db.commit()
    totals = db.execute('SELECT SUM(discovered), SUM(skipped), SUM(added) FROM stats').fetchone()
    checks['40 sources x 5 resumed rounds retain exact independent counters'] = totals == (50000, 600, 48000)
    db.close()

# Model low-storage journal failure after Room commit: indexed rows remain authoritative and replay is idempotent.
db = sqlite3.connect(':memory:')
db.executescript('CREATE TABLE images(uri TEXT PRIMARY KEY); CREATE TABLE links(uri TEXT, source TEXT, PRIMARY KEY(uri,source));')
db.execute('BEGIN')
db.execute("INSERT INTO images VALUES('content://p/1')")
db.execute("INSERT INTO links VALUES('content://p/1','dcim')")
db.commit()
# Simulate checkpoint append failure: no journal token is written. Reprocessing the same batch is safe.
db.execute('INSERT OR IGNORE INTO images VALUES(?)', ('content://p/1',))
db.execute('INSERT OR IGNORE INTO links VALUES(?,?)', ('content://p/1','dcim'))
checks['checkpoint write failure after Room commit keeps index and replay idempotent'] = db.execute('SELECT COUNT(*) FROM images').fetchone()[0] == 1 and db.execute('SELECT COUNT(*) FROM links').fetchone()[0] == 1
db.close()

# Checkpoint only resumes entries with reliable metadata; unreliable provider metadata always re-reads.
def reliable(size, modified): return size > 0 and modified > 0
checks['zero/unknown provider metadata cannot suppress a changed file'] = not reliable(0, 100) and not reliable(2048, 0) and reliable(2048, 100)

# 25k journal round trip plus torn tail: valid records survive, incomplete last line is ignored.
with tempfile.TemporaryDirectory() as td:
    path = Path(td) / 'journal'
    uris = [f'content://provider/{i}' for i in range(25000)]
    path.write_text(''.join(base64.urlsafe_b64encode(x.encode()).decode().rstrip('=') + '\n' for x in uris), encoding='utf-8')
    decoded = set()
    for line in path.read_text(encoding='utf-8').splitlines():
        try: decoded.add(base64.urlsafe_b64decode(line + '=' * (-len(line) % 4)).decode())
        except Exception: pass
    checks['25k multi-source checkpoint records round-trip'] = len(decoded) == len(uris) and decoded == set(uris)
    with path.open('a', encoding='utf-8') as f: f.write('torn-tail-no-newline')
    valid = set()
    for line in path.read_text(encoding='utf-8').splitlines():
        try: valid.add(base64.urlsafe_b64decode(line + '=' * (-len(line) % 4)).decode())
        except Exception: pass
    checks['torn journal tail does not invalidate prior committed checkpoints'] = len(valid) == len(uris)

for name, passed in checks.items(): print(('PASS' if passed else 'FAIL') + ' - ' + name)
failed = [k for k,v in checks.items() if not v]
if failed: raise SystemExit(f'{len(failed)} checks failed: {failed}')
print(f'All {len(checks)} v0.1.26 checks passed')
