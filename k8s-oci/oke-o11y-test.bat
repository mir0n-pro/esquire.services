@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem oke-o11y-test.bat -- THE ONE CLICK for OKE: DRIVE the fleet, then VERIFY, then
rem report the whole INVENTORY. The OKE twin of compose\o11y-test.bat / k8s\o11y-test.bat.
rem
rem   1. DRIVE   (..\test\o11y\o11y-drive.py): log in + hit every REST path that
rem              carries a meter or a mark, so nothing sits "never called".
rem   2. VERIFY  (..\test\o11y\o11y-verify.py): assert what appeared.
rem   3. INVENTORY (..\test\o11y\o11y-inventory.py --report): every asset, PROVEN or not.
rem
rem WHY BOTH: verify only OBSERVES; it cannot MAKE an asset appear, so a third of
rem the inventory reads "not proven" on a stack nobody drove. Drive THEN verify
rem answers "does ALL of it work?".
rem
rem OKE deltas (same as oke-o11y-verify.bat): SERVICES excludes auKeep; LOG_SERVICES
rem is the FULL workload name Loki labels streams with; EXCLUDE_METERS/TRACE_NODES
rem drop the auKeep keep-write meters + node so a fleet that legitimately has no
rem auKeep (OKE audits via DB triggers) reads "whole", not "short".
rem
rem PRE: FULL o11y is up (oke-o11y-on FULL) AND the backends are port-forwarded to
rem the 1xxxx band (oke-o11y-verify.bat header lists the four forwards). The DRIVE
rem hits the PUBLIC BFF (https://esquire.mir0n.pro), so no forward is needed for it.
rem ===========================================================================
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this is the OKE o11y test. Refusing.
  exit /b 1
)
set ENVNAME=oke
set BASE_URL=https://esquire.mir0n.pro
set PROM_URL=http://localhost:19090
set LOKI_URL=http://localhost:13100
set TEMPO_URL=http://localhost:13200
set GRAFANA_URL=http://localhost:13009
set LOKI_JOB=esq-k8s
set SERVICES=gateway,biztree,enyman,pacman,keysmith,kcmaster
set LOG_SERVICES=esquire-gateway-gateway,esquire-biztree-biztree,esquire-enyman-enyman,esquire-pacman-pacman,esquire-keysmith-keysmith,esquire-kcmaster-kcmaster,esquire-backend-backend
set EXCLUDE_METERS=esq_biz_keep_write_total,esq_biz_keep_write_duration_seconds,esq_keep_apply_seconds
set EXCLUDE_TRACE_NODES=aukeep

rem Credentials from the e2e's .env -- ONE place for the sandbox login, never a literal in a script.
if exist "..\..\explorer\e2e-test\.env" (
    for /f "usebackq tokens=1,2 delims==" %%A in ("..\..\explorer\e2e-test\.env") do (
        if /i "%%A"=="E2E_USERNAME" set ESQ_USER=%%B
        if /i "%%A"=="E2E_PASSWORD" set ESQ_PASS=%%B
    )
)
if "%ESQ_USER%"=="" (
    echo [oke-o11y-test] ESQ_USER not set and ..\..\explorer\e2e-test\.env not found.
    exit /b 2
)

echo.
echo === STEP 1/3 -- DRIVE (make the fleet emit, via the public BFF) ===
python ..\test\o11y\o11y-drive.py
if errorlevel 1 (
    echo [oke-o11y-test] the driver failed -- verifying now would only report an idle stack.
    exit /b 1
)

echo.
echo === STEP 2/3 -- VERIFY (assert what appeared) ===
echo --- waiting for the scrape...
python -c "import time; time.sleep(20)"
python ..\test\o11y\o11y-verify.py
set RC=%ERRORLEVEL%

echo.
echo === STEP 3/3 -- THE WHOLE INVENTORY, item by item ===
python ..\test\o11y\o11y-inventory.py --report

echo.
if "%RC%"=="0" (
    echo [oke-o11y-test] PASS -- driven and verified on %ENVNAME%.
) else (
    echo [oke-o11y-test] FAIL -- see the failures above.
)
exit /b %RC%
