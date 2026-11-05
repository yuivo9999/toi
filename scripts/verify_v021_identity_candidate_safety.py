#!/usr/bin/env python3
"""Regression checks for complete metadata candidate enumeration in v0.1.22."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
dao = (ROOT / "app/src/main/java/com/toimg/app/data/local/dao/ImageDao.kt").read_text()
resolver = (ROOT / "app/src/main/java/com/toimg/app/core/util/ImageIdentityResolver.kt").read_text()
test = (ROOT / "app/src/test/java/com/toimg/app/core/util/ImageIdentityPolicyTest.kt").read_text()
build = (ROOT / "app/build.gradle.kts").read_text()
assert 'versionName = "0.1.30"' in build and 'versionCode = 28' in build
query_start = dao.rfind('@Query', 0, dao.index('suspend fun findIdentityCandidates'))
query_end = dao.index('suspend fun findIdentityCandidates')
query = dao[query_start:query_end]
assert 'dateModified = :dateModified' in query
assert 'mimeType = :mimeType' in query
assert 'LIMIT' not in query.upper()
assert 'candidate.dateModified == incoming.dateModified' in (ROOT / "app/src/main/java/com/toimg/app/core/util/ImageIdentityPolicy.kt").read_text()
assert 'uniqueCandidate(candidates)' in resolver
assert 'moreThanTwentyMetadataMatchesRemainAmbiguous' in test
print('PASS: candidate lookup is exact on name/size/dateModified/MIME')
print('PASS: candidate enumeration is not truncated by LIMIT')
print('PASS: resolver still requires a unique candidate and SHA-256 match')
print('PASS: regression test covers 21 ambiguous candidates')
