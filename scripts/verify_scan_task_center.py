#!/usr/bin/env python3
"""Static regression checks for the persistent scan task center."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "scan worker exists": ROOT / "app/src/main/java/com/toimg/app/core/work/ScanWorker.kt",
    "unique scheduler exists": ROOT / "app/src/main/java/com/toimg/app/core/work/ScanTaskScheduler.kt",
    "persistent session entity exists": ROOT / "app/src/main/java/com/toimg/app/data/local/entity/ScanSessionEntity.kt",
    "session DAO exists": ROOT / "app/src/main/java/com/toimg/app/data/local/dao/ScanSessionDao.kt",
    "home active scan entry exists": ROOT / "app/src/main/java/com/toimg/app/feature/home/HomeScreen.kt",
    "scan progress persistence view exists": ROOT / "app/src/main/java/com/toimg/app/feature/scan/ScanProgressScreen.kt",
}
errors = []
for label, path in checks.items():
    if not path.is_file() or path.stat().st_size == 0:
        errors.append(f"FAIL: {label}")
    else:
        print(f"PASS: {label}")

scheduler = checks["unique scheduler exists"].read_text(encoding="utf-8")
worker = checks["scan worker exists"].read_text(encoding="utf-8")
home = checks["home active scan entry exists"].read_text(encoding="utf-8")
scope = (ROOT / "app/src/main/java/com/toimg/app/feature/scan/ScanScopeScreen.kt").read_text(encoding="utf-8")
db = (ROOT / "app/src/main/java/com/toimg/app/data/local/ToImgDatabase.kt").read_text(encoding="utf-8")
if "ExistingWorkPolicy.KEEP" not in scheduler or "UNIQUE_WORK_NAME = \"toimg_scan_session\"" not in worker:
    errors.append("FAIL: unique work KEEP policy missing")
else:
    print("PASS: duplicate work guarded by unique KEEP policy")
if "scan_sessions" not in db or "MIGRATION_6_7" not in db:
    errors.append("FAIL: Room 6->7 scan session migration missing")
else:
    print("PASS: Room scan-session migration declared")
if "home_active_scan_card" not in home or "onOpenScanProgress" not in home:
    errors.append("FAIL: home scan progress entry missing")
else:
    print("PASS: home scan progress entry wired")
if "hasActiveTask(context)" not in scope:
    errors.append("FAIL: scan scope active-task guard missing")
else:
    print("PASS: start scan checks existing active task")
if "setForeground" not in worker or "ScanManager.start" not in worker:
    errors.append("FAIL: foreground worker execution missing")
else:
    print("PASS: scan executes through foreground WorkManager worker")
if "dao.upsert(updated)" not in worker or "ScanSessionEntity.PAUSED" not in worker:
    errors.append("FAIL: scan progress/status persistence missing")
else:
    print("PASS: progress and pause state persist to Room")
if "ScanSessionEntity.SUCCESS" not in worker or "while (currentSession.status == ScanSessionEntity.PAUSED)" not in worker:
    errors.append("FAIL: worker terminal idempotency or pause recovery missing")
else:
    print("PASS: terminal task will not be rerun; paused task waits for resume")

manifest = ROOT / "app/src/main/AndroidManifest.xml"
try:
    ET.parse(manifest)
    text = manifest.read_text(encoding="utf-8")
    if "SystemForegroundService" not in text or "FOREGROUND_SERVICE_DATA_SYNC" not in text:
        errors.append("FAIL: foreground worker manifest declarations missing")
    else:
        print("PASS: foreground service manifest declarations parse")
except ET.ParseError as exc:
    errors.append(f"FAIL: invalid AndroidManifest.xml: {exc}")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print("PASS: scan task center static checks complete")
