@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not installed or not on PATH. Install a compatible Gradle 8.13+ distribution first.
  exit /b 1
)
gradle wrapper --gradle-version 8.13
