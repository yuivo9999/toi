#!/usr/bin/env python3
"""Regression checks for the batch recognition engine carried into 0.1.11."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
checks = {
    "current version is 0.1.29": 'versionName = "0.1.30"' in (ROOT / "app/build.gradle.kts").read_text(),
    "NVIDIA default interval is 25 seconds": "DEFAULT_NVIDIA_INTERVAL_SECONDS = 25" in (ROOT / "app/src/main/java/com/toimg/app/data/repository/AiConfigRepository.kt").read_text(),
    "custom interval range is bounded": "seconds in 5..600" in (ROOT / "app/src/main/java/com/toimg/app/data/repository/AiConfigRepository.kt").read_text(),
    "NVIDIA wait happens after persistence": (lambda s: s.index("persistChunk(appContext, db, chunk, results, counters, retryQueue, isRetry = false)") < s.index("waitNvidiaBatchInterval(configRepo, label)"))(ROOT.joinpath("app/src/main/java/com/toimg/app/core/util/BackgroundBatchManager.kt").read_text()),
    "request prompt prohibits cross-image inference": "严禁跨图片推断" in (ROOT / "app/src/main/java/com/toimg/app/data/remote/AiVisionClient.kt").read_text(),
    "request is single-message": 'put("role", "user")' in (ROOT / "app/src/main/java/com/toimg/app/data/remote/AiVisionClient.kt").read_text(),
    "dynamic output token budget": "effectiveNvidiaMaxTokens(items.size, config.maxTokens)" in (ROOT / "app/src/main/java/com/toimg/app/data/remote/AiVisionClient.kt").read_text(),
    "Retry-After is parsed": "parseRetryAfter(response.header(\"Retry-After\"))" in (ROOT / "app/src/main/java/com/toimg/app/data/remote/AiVisionClient.kt").read_text(),
    "Nemotron defaults to no reasoning": 'reasoningEffort = "none", enableThinking = false' in (ROOT / "app/src/main/java/com/toimg/app/domain/model/NvidiaModelProfile.kt").read_text(),
    "cloud response read timeout is 180 seconds": ".readTimeout(180, TimeUnit.SECONDS)" in (ROOT / "app/src/main/java/com/toimg/app/data/remote/AiVisionClient.kt").read_text(),
    "complete cloud call timeout is 210 seconds": ".callTimeout(210, TimeUnit.SECONDS)" in (ROOT / "app/src/main/java/com/toimg/app/data/remote/AiVisionClient.kt").read_text(),
    "hybrid fallback preserves retryable cloud failures": "allFailed && !hasPendingCloudRetry" in (ROOT / "app/src/main/java/com/toimg/app/core/util/BackgroundBatchManager.kt").read_text(),
    "release documentation exists": (ROOT / "docs/AI_BATCH_RECOGNITION_ENGINE.md").is_file(),
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit(1)
print(f"PASS: {len(checks)} batch-engine release checks")
