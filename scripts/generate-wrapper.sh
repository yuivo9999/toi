#!/usr/bin/env sh
set -eu
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not installed or not on PATH. Install a compatible Gradle 8.13+ distribution first." >&2
  exit 1
fi
gradle wrapper --gradle-version 8.13
