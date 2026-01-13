@echo off
REM Kotlin Language Server (fwcd.kotlin) classpath helper.
REM This is IDE-only; FAST still controls the real build.

powershell -NoProfile -ExecutionPolicy Bypass -Command "^$
  jars = Get-ChildItem -Path '%~dp0libs','%~dp0deps' -Recurse -Filter *.jar -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }; ^$
  cp = ($jars -join ';'); ^$
  Write-Output $cp
" 
