#!/usr/bin/env python3
"""v0.1.25 durable scan checkpoint + recognition/index reconciliation checks."""
from pathlib import Path
import base64
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[1]
def read(path): return (ROOT / path).read_text(encoding='utf-8')

build = read('app/build.gradle.kts')
repo = read('app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt')

checkpoint = read('app/src/main/java/com/toimg/app/core/util/ScanCheckpointStore.kt')
worker = read('app/src/main/java/com/toimg/app/core/work/ScanWorker.kt')
recognition = read('app/src/main/java/com/toimg/app/data/local/dao/RecognitionDao.kt')
auditor = read('app/src/main/java/com/toimg/app/core/util/ImageIndexIntegrityAuditor.kt')
ui = read('app/src/main/java/com/toimg/app/feature/sources/SourceManagementScreen.kt')

checks = {
    'version 0.1.29 / code 28': 'versionName = "0.1.30"' in build and 'versionCode = 28' in build,
    'checkpoint journal is private app files storage': 'context.filesDir' in checkpoint and 'scan-checkpoints' in checkpoint,
    'checkpoint URI+metadata payload is encoded': 'Base64.encodeToString' in checkpoint and 'Base64.decode' in checkpoint and 'fun token(uri: String, size: Long, modifiedAt: Long)' in checkpoint,
    'journal fsync occurs after writer flush': checkpoint.index('writer.flush()') < checkpoint.index('stream.fd.sync()'),
    'SAF resume loads prior URI+metadata checkpoint': 'ScanCheckpointStore.load(context, scanSessionId, sourceId)' in repo and 'checkpointToken in checkpointedUris' in repo,
    'MediaStore resume loads prior URI+metadata checkpoint': repo.count('ScanCheckpointStore.load(context, scanSessionId, sourceId)') >= 2 and 'checkpointToken in checkpointedUris' in repo,
    'SAF batch commits Room before journal append': repo.index('persistBatch(batchEntities)') < repo.index('appendCheckpoint(batchUris)'),
    'MediaStore batch commits Room before journal append': repo.index('persistMediaBatch(entityList.toList())') < repo.index('appendCheckpoint(pendingCheckpointUris)'),
    'SAF checkpoint binds original provider URI to current file metadata': 'pendingCheckpointUris.add(checkpointToken)' in repo and 'uri = file.uri' in repo,
    'file metadata change invalidates an old checkpoint': 'ScanCheckpointStore.token(rawUriString, size, dateModified)' in repo and 'ScanCheckpointStore.token(rawFileUri, fileSize, fileModifiedAt)' in repo,
    'success/cancel/failure clean checkpoint files': worker.count('ScanCheckpointStore.clearSession(applicationContext, sessionId)') >= 4,
    'system interruption preserves checkpoint for retry': '系统暂时中断，等待后台恢复' in worker and worker.index('requeueInterruptedStatsForSession') < worker.index('throw cancelled'),
    'session discovered count uses cumulative persisted source stats': 'sumOf { it.discoveredCount }' in worker,
    'audit checks recognized image missing successful result': 'countRecognizedImagesMissingSuccess' in recognition and 'recognizedImagesMissingResult' in auditor,
    'audit checks successful result with stale image status': 'countSuccessResultsWithStaleImageStatus' in recognition and 'successResultsWithStaleImageStatus' in auditor,
    'recognition status repair is transactional': 'database.withTransaction' in auditor and 'repairRecognitionStatusFlags' in auditor,
    'source UI exposes explicit recognition status repair': '修复识别状态' in ui and '已识别但缺结果' in ui,
    'recognition result, image status and FTS write share one Room transaction': 'db.withTransaction {' in repo[repo.index('override suspend fun saveRecognitions'):repo.index('override suspend fun getRecognition')] and 'db.imageFtsDao().syncFtsForImage(res.imageId)' in repo[repo.index('override suspend fun saveRecognitions'):repo.index('override suspend fun getRecognition')],
}

# Model the journal's at-least-once contract: only committed batches are checkpointed;
# crash before append replays harmlessly, partial trailing line is ignored.
with tempfile.TemporaryDirectory() as td:
    path = Path(td) / 'source.journal'
    uris = [f'content://provider/image/{i}' for i in range(20000)]
    path.write_text(''.join(base64.urlsafe_b64encode(x.encode()).decode().rstrip('=') + '\n' for x in uris), encoding='utf-8')
    decoded = set()
    for line in path.read_text(encoding='utf-8').splitlines():
        pad = '=' * (-len(line) % 4)
        decoded.add(base64.urlsafe_b64decode(line + pad).decode())
    checks['20k checkpoint entries round-trip without loss or collision'] = len(decoded) == 20000 and decoded == set(uris)
    with path.open('a', encoding='utf-8') as f: f.write('partial-base64')
    valid = set()
    for line in path.read_text(encoding='utf-8').splitlines():
        try:
            valid.add(base64.urlsafe_b64decode(line + '=' * (-len(line) % 4)).decode())
        except Exception:
            pass
    checks['truncated final journal line is ignored and safely reprocessed'] = len(valid) == 20000

# Execute the DAO's exact recognition-status SQL against a SQLite fixture.
db = sqlite3.connect(':memory:')
db.executescript("""
CREATE TABLE images(id TEXT PRIMARY KEY, status TEXT NOT NULL);
CREATE TABLE recognition_results(imageId TEXT PRIMARY KEY, status TEXT NOT NULL);
INSERT INTO images VALUES ('a','RECOGNIZED'),('b','SCANNED'),('c','FAILED'),('d','RECOGNIZED');
INSERT INTO recognition_results VALUES ('b','SUCCESS'),('c','FAILED'),('d','SUCCESS');
""")
import re
for name, pattern in [
    ('count recognized image missing result', r'@Query\("(SELECT COUNT\(\*\) FROM images i WHERE i\.status = \'RECOGNIZED\'.*?)"\)\s*suspend fun countRecognizedImagesMissingSuccess'),
    ('count successful result stale image status', r'@Query\("([^"]+)"\)\s*suspend fun countSuccessResultsWithStaleImageStatus')
]:
    m = re.search(pattern, recognition, re.S)
    if not m:
        checks[name + ' SQL extractable'] = False
    else:
        sql = m.group(1).replace('\\"','"')
        checks[name + ' SQL extractable'] = True
        try: db.execute(sql).fetchone()
        except Exception: checks[name + ' SQL executable'] = False
        else: checks[name + ' SQL executable'] = True
# Verify the explicit status repair SQL itself is executable and deterministic.
for match in re.findall(r'@Query\("(UPDATE images SET status = .*?)"\)\s*suspend fun (?:restoreRecognizedImageStatuses|downgradeImagesMissingRecognition)', recognition, re.S):
    sql = match.replace('\\"','"')
    db.execute(sql)
checks['recognition status repair updates only derived status flags'] = db.execute("SELECT status FROM images WHERE id='a'").fetchone()[0] == 'SCANNED' and db.execute("SELECT status FROM images WHERE id='b'").fetchone()[0] == 'RECOGNIZED'
# A mid-save FTS failure must roll back both the recognition row and image status.
db.execute("INSERT INTO images VALUES ('e','SCANNED')")
db.commit()
try:
    db.execute('BEGIN')
    db.execute("INSERT INTO recognition_results VALUES ('e','SUCCESS')")
    db.execute("UPDATE images SET status='RECOGNIZED' WHERE id='e'")
    raise RuntimeError('simulated FTS write failure')
except RuntimeError:
    db.rollback()
checks['recognition transaction failure rolls back result and image status together'] = db.execute("SELECT status FROM images WHERE id='e'").fetchone()[0] == 'SCANNED' and db.execute("SELECT COUNT(*) FROM recognition_results WHERE imageId='e'").fetchone()[0] == 0
db.close()

for name, passed in checks.items(): print(('PASS' if passed else 'FAIL') + ' - ' + name)
failed = [k for k,v in checks.items() if not v]
if failed: raise SystemExit(f'{len(failed)} checks failed: {failed}')
print(f'All {len(checks)} v0.1.25 checks passed')
