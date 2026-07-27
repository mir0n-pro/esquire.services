@echo off
setlocal
cd /d "%~dp0"

rem === o11y-forward-stop.bat -- stop the local k8s o11y port-forwards started by o11y-forward.bat ===
rem
rem A stale forward is not harmless: it holds the local port, so the NEXT o11y-forward.bat run aborts (by design
rem -- see the trap note there). Close them here rather than hunting the windows.

echo.
echo === stopping Esquire k8s o11y port-forwards ===

for %%w in (esq-k8s-tempo esq-k8s-prometheus esq-k8s-loki) do (
  taskkill /fi "WINDOWTITLE eq %%w" /t /f >nul 2>&1
  if errorlevel 1 (echo   %%w  -- not running) else (echo   %%w  -- stopped)
)

echo.
endlocal
