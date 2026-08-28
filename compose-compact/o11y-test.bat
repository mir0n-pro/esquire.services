@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-test.bat -- THE ONE CLICK. Test the observability stack, docker compact.
rem
rem   1. DRIVE     (..\test\o11y\o11y-drive.py): log in + hit every REST path that
rem                carries a meter or a mark.
rem   2. VERIFY    (..\test\o11y\o11y-verify.py): assert what appeared -- health,
rem                meters, gauges, dependencies, bands, tracing, chain, datasources,
rem                logging.
rem   3. INVENTORY (..\test\o11y\o11y-inventory.py --report): every asset, PROVEN or not.
rem
rem WHY BOTH. o11y-verify only OBSERVES; it can never MAKE an asset appear, so on an
rem idle stack it reports "0 FAIL" with a third of the inventory never called. Verify
rem alone answers "is what fired healthy?"; only DRIVE then VERIFY answers "does ALL
rem of it work?".
rem
rem Run AFTER o11y-on.bat. Mirror of compose\o11y-test.bat and k8s-compact\o11y-test.bat
rem -- the SAME steps; what differs is the fleet. This profile runs FOUR processes, and
rem enyMan, keySmith, kcMaster, the gateway and bizTree are all still reported -- under
rem the `service` label, inside mesnie and gateward.
rem
rem NOT covered here, on purpose -- these need something to actually BREAK, and
rem this script must leave the stack as it found it:
rem   messaging.error / retry.backoff / retry.dropped / esq.biz.move.failed
rem     -> the broker-down smoke drives those.
rem   esq.svc.cache -> UNREACHABLE by config (BizTreeDirectorLegacy, and
rem      BIZTREE_DIRECTOR=taijitu). No driver can light it; do not go looking.
rem ===========================================================================
set ENVNAME=docker-compact
set BASE_URL=http://localhost:3000
set PROM_URL=http://localhost:9090
set LOKI_URL=http://localhost:3100
set TEMPO_URL=http://localhost:3200
set GRAFANA_URL=http://localhost:3009
set LOKI_JOB=esq-docker
rem The compact fleet is declared ONCE for every environment -- see the shared file.
call ..\test\o11y\fleet-compact.bat

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
rem BOARDS = the dashboards THIS environment provisions. The dependency and band checks read the
rem PANELS as the declaration of what must exist, so they have to read the boards that are actually
rem deployed here -- not another topology's. Path is relative to services/.
set BOARDS=compose-compact\o11y\grafana\provisioning\dashboards
python ..\test\o11y\o11y-verify.py
set RC=%ERRORLEVEL%

echo.
echo === STEP 3/3 -- THE RESULT, item by item ===
rem The whole point, in one list: every asset we collect and whether it is PROVEN. Same shape as the e2e
rem (ok N / not ok N / # SKIP, then the tally), so it reads the way every other suite here reads.
python ..\test\o11y\o11y-inventory.py --report --profile compact

echo.
if "%RC%"=="0" (
    echo [o11y-test] PASS -- driven and verified on %ENVNAME%.
) else (
    echo [o11y-test] FAIL -- see the failures above.
)
exit /b %RC%
