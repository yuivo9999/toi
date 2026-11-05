#!/usr/bin/env python3
"""Static regression checks for the cloud AI response protocol in v0.1.11."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "app/src/main/java/com/toimg/app/data/remote/AiVisionClient.kt").read_text(encoding="utf-8")
tests = (ROOT / "app/src/test/java/com/toimg/app/data/remote/AiVisionClientTest.kt").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
repository = (ROOT / "app/src/main/java/com/toimg/app/data/repository/LocalImageRepositoryImpl.kt").read_text(encoding="utf-8")
manager = (ROOT / "app/src/main/java/com/toimg/app/core/util/BackgroundBatchManager.kt").read_text(encoding="utf-8")
checks = {
    "current version is 0.1.29 / code 28": 'versionName = "0.1.30"' in build and "versionCode = 28" in build,
    "strict protocol included in OpenAI-compatible prompt": "必须严格遵循 ToImg JSON 协议" in source,
    "Gemini prompt uses same strict protocol": source.count("必须严格遵循 ToImg JSON 协议") >= 2,
    "JSON extraction handles fences and surrounding prose": "extractJsonDocument" in source and "var inString = false" in source,
    "keywords array and 1-10 count validated": "keywordsValue !is JSONArray" in source and "keywords.size > 10" in source,
    "description required": "description 缺失或为空" in source,
    "OCR type validated": "ocr_text 必须是字符串" in source,
    "sensitive flag type validated": "is_sensitive 必须是布尔值" in source,
    "extra fields rejected": "存在未定义字段" in source,
    "invalid protocol response is retryable": "云端返回内容不符合 ToImg JSON 协议" in source,
    "ID mismatch is retryable": "云端返回图片 ID 校验失败" in source,
    "failed/local results cannot overwrite cloud success": "existingIsCloudSuccess" in repository and "protectedCloudSuccess" in manager,
    "unit tests cover protocol validation": "parseVisionResponse_rejectsMissingOrWrongTypedFields" in tests and "openAiBatchRequest_includesStrictToImgResponseContract" in tests,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit(1)
print(f"PASS: {len(checks)} AI response protocol release checks")
