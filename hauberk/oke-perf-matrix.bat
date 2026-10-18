@echo off
rem ===========================================================================
rem oke-perf-matrix.bat -- run the Esquire performance matrix on the LIVE OKE cluster.
rem
rem   6 configs (oke x1 / x2, each o11y OFF / LOG / FULL)
rem   x 2 runs per config, reached by TOGGLING the cluster in place (OKE cannot
rem     wipe PG/KC/broker -- live data, ALTER-migrated), clean-house between runs
rem   x 4+ loads per run (extends while still warming; load 1 is warm-up)
rem
rem   -> output\oke-perf-matrix\matrix.csv     (appended after EVERY load)
rem      output\oke-perf-matrix\matrix.log
rem
rem A full matrix takes ~3-4 HOURS. It is RESUMABLE: rerun and it skips the runs
rem already in the CSV. Use --fresh to start a new one. It starts and ends with
rem the OKE default (pro.mir0n/root ERROR, no viewing stack, x2).
rem
rem   oke-perf-matrix.bat              run / resume the matrix (detached)
rem   oke-perf-matrix.bat --fresh      discard previous results and start over
rem   oke-perf-matrix.bat --fg         run in THIS console (watch it live)
rem
rem PRE: kubectl context is the OKE cluster (NOT docker-desktop -- the script refuses),
rem the pool is scaled to 6 nodes (the 2 paid tier=o11y nodes must exist for FULL), and
rem esquire.mir0n.pro is reachable. Read the header of oke-perf-matrix.ps1 first -- the
rem toggle-in-place / clean-house / separate-paid-node departures from the local rig are
rem explained there.
rem
rem Analyse the CSV afterwards:  python perf-matrix-report.py output\oke-perf-matrix\matrix.csv
rem ===========================================================================
setlocal
cd /d "%~dp0"

set ARGS=
set FG=0
:parse
if "%~1"=="" goto run
if /i "%~1"=="--fresh" set ARGS=%ARGS% -Fresh& shift & goto parse
if /i "%~1"=="--fg"    set FG=1& shift & goto parse
set ARGS=%ARGS% %1
shift
goto parse

:run
if "%FG%"=="1" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0oke-perf-matrix.ps1" %ARGS%
  goto :eof
)

rem Detached: a multi-hour matrix must survive the console that started it.
start "esq-oke-perf-matrix" /min powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0oke-perf-matrix.ps1" %ARGS%
echo.
echo OKE perf matrix started in the background.
echo   progress : type output\oke-perf-matrix\matrix.log
echo   results  : output\oke-perf-matrix\matrix.csv
echo   report   : python perf-matrix-report.py output\oke-perf-matrix\matrix.csv
echo.
endlocal
