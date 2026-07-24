@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-test.bat -- THE ONE CLICK. Test the observability stack, docker.
rem
rem   1. DRIVE   -- make the fleet emit (..\test\o11y\o11y-drive.py: login + every
rem                 REST path that carries a meter or a mark).
rem   2. VERIFY  -- assert what appeared (..\test\o11y\o11y-verify.py: scrape health,
rem                 meters, gauges, dependencies, bands, tracing, chain,
rem                 datasources, logging).
rem
rem WHY BOTH. o11y-verify only OBSERVES; it can never MAKE an asset appear. So
rem it reported "0 FAIL" on an IDLE stack and again on a busy one, and a third
rem of the inventory sat unproven -- not broken, just never called: esq.svc.node
rem and esq.svc.save had not fired ONCE in the life of the stack until a driver
rem called them. Verify alone answers "is what fired healthy?"; only DRIVE then
rem VERIFY answers "does all of it work?".
rem
rem Run AFTER o11y-on.bat. Mirror of k8s\o11y-test.bat -- the SAME steps; only
rem the addresses differ, because the stack is identical on every environment.
rem
rem NOT covered here, on purpose -- these need something to actually BREAK, and
rem this script must leave the stack as it found it:
rem   messaging.error / retry.backoff / retry.dropped / esq.biz.move.failed
rem     -> the broker-down smoke drives those.
rem   esq.svc.cache -> UNREACHABLE by config (BizTreeDirectorLegacy, and
rem      BIZTREE_DIRECTOR=taijitu). No driver can light it; do not go looking.
rem ===========================================================================
set ENVNAME=docker
set BASE_URL=http://localhost:3000
set PROM_URL=http://localhost:9090
set LOKI_URL=http://localhost:3100
set TEMPO_URL=http://localhost:3200
set GRAFANA_URL=http://localhost:3009
set LOKI_JOB=esq-docker
set SERVICES=gateway,biztree,enyman,pacman,keysmith,kcmaster,aukeep

rem Credentials come from the e2e's .env -- ONE place for the sandbox login, never a literal in a script.
if exist "..\..\explorer\e2e-test\.env" (
    for /f "usebackq tokens=1,2 delims==" %%A in ("..\..\explorer\e2e-test\.env") do (
        if /i "%%A"=="E2E_USERNAME" set ESQ_USER=%%B
        if /i "%%A"=="E2E_PASSWORD" set ESQ_PASS=%%B
    )
)
if "%ESQ_USER%"=="" (
    echo [o11y-test] ESQ_USER not set and ..\..\explorer\e2e-test\.env not found.
    echo             Set ESQ_USER / ESQ_PASS, or run from a tree with the e2e .env.
    exit /b 2
)

echo.
echo === STEP 1/3 -- DRIVE (make the fleet emit) ===
python ..\test\o11y\o11y-drive.py
if errorlevel 1 (
    echo [o11y-test] the driver failed -- verifying now would only report an idle stack.
    exit /b 1
)

echo.
echo === STEP 2/3 -- VERIFY (assert what appeared) ===
rem The scrape has to land before the assert, or everything reads as unproven -- the idle-stack trap.
echo --- waiting for the scrape...
python -c "import time; time.sleep(20)"
python ..\test\o11y\o11y-verify.py
set RC=%ERRORLEVEL%

echo.
rem NB: no %%126%% here -- cmd reads %%1 as the first ARG and prints "26". Let the report state its own count.
echo === STEP 3/3 -- THE RESULT, item by item ===
rem The whole point, in one list: every asset we collect and whether it is PROVEN. Same shape as the e2e
rem (ok N / not ok N / # SKIP, then the tally), so it reads the way every other suite here reads.
python ..\test\o11y\o11y-inventory.py --report

echo.
if "%RC%"=="0" (
    echo [o11y-test] PASS -- driven and verified on %ENVNAME%.
) else (
    echo [o11y-test] FAIL -- see the failures above.
)
exit /b %RC%
