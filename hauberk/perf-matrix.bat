@echo off
rem ===========================================================================
rem perf-matrix.bat -- run the Esquire performance matrix.
rem
rem   6 configs (docker / k8s-x1 / k8s-x2, each o11y OFF and ON)
rem   x 2 runs per config, EACH FROM SCRATCH (Postgres + KeyCloak wiped)
rem   x 4+ loads per run (extends while still warming; load 1 is warm-up)
rem
rem   -> output\perf-matrix\matrix.csv     (appended after EVERY load)
rem      output\perf-matrix\matrix.log
rem
rem A full matrix takes ~3-4 HOURS. It is RESUMABLE: rerun and it skips the runs
rem already in the CSV. Use --fresh to start a new one.
rem
rem   perf-matrix.bat              run / resume the matrix (detached)
rem   perf-matrix.bat --fresh      discard previous results and start over
rem   perf-matrix.bat --fg         run in THIS console (watch it live)
rem
rem Read the header of perf-matrix.ps1 before changing anything -- every rule in
rem there (wipe the data, discard load 1, two runs per config, one stack at a
rem time) was paid for with a measurement that turned out to be wrong.
rem
rem Analyse the CSV afterwards:  python perf-matrix-report.py
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
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0perf-matrix.ps1" %ARGS%
  goto :eof
)

rem Detached: a 4-hour matrix must survive the console that started it.
start "esq-perf-matrix" /min powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0perf-matrix.ps1" %ARGS%
echo.
echo Perf matrix started in the background.
echo   progress : type output\perf-matrix\matrix.log
echo   results  : output\perf-matrix\matrix.csv
echo   report   : python perf-matrix-report.py
echo.
endlocal
