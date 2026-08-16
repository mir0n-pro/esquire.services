@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-test.bat -- THE ONE CLICK for local k8s: DRIVE the fleet, VERIFY, report
rem the whole INVENTORY. The k8s twin of compose\o11y-test.bat (this one was named
rem in o11y-drive.py's header but never existed -- filled in T12).
rem
rem   1. DRIVE   (..\test\o11y\o11y-drive.py): log in + hit every REST path that
rem              carries a meter or a mark.
rem   2. VERIFY  (..\test\o11y\o11y-verify.py): assert what appeared.
rem   3. INVENTORY (..\test\o11y\o11y-inventory.py --report): every asset, PROVEN or not.
rem
rem WHY BOTH: verify only OBSERVES; only DRIVE-then-VERIFY answers "does ALL of it work?".
rem
rem PRE: FULL o11y is up (o11y-full-on.bat) AND the backends are port-forwarded to
rem the 1xxxx band (o11y-forward.bat). The DRIVE hits the ingress BFF
rem (http://esquire.localhost -- needs the hosts-file mapping e2e-k8s.bat documents).
rem Local k8s runs the WHOLE fleet incl. auKeep, so nothing is excluded here.
rem ===========================================================================
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop". Refusing.
  exit /b 1
)
set ENVNAME=k8s
set BASE_URL=http://esquire.localhost
set PROM_URL=http://localhost:19090
set LOKI_URL=http://localhost:13100
set TEMPO_URL=http://localhost:13200
set GRAFANA_URL=http://localhost:13009
set LOKI_JOB=esq-k8s
set SERVICES=gateward,mesnie,pacman

rem Credentials from the e2e's .env -- ONE place for the sandbox login, never a literal in a script.
if exist "..\..\explorer\e2e-test\.env" (
    for /f "usebackq tokens=1,2 delims==" %%A in ("..\..\explorer\e2e-test\.env") do (
        if /i "%%A"=="E2E_USERNAME" set ESQ_USER=%%B
        if /i "%%A"=="E2E_PASSWORD" set ESQ_PASS=%%B
    )
)
if "%ESQ_USER%"=="" (
    echo [o11y-test] ESQ_USER not set and ..\..\explorer\e2e-test\.env not found.
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
echo --- waiting for the scrape...
python -c "import time; time.sleep(20)"
python ..\test\o11y\o11y-verify.py
set RC=%ERRORLEVEL%

echo.
echo === STEP 3/3 -- THE WHOLE INVENTORY, item by item ===
python ..\test\o11y\o11y-inventory.py --report

echo.
if "%RC%"=="0" (
    echo [o11y-test] PASS -- driven and verified on %ENVNAME%.
) else (
    echo [o11y-test] FAIL -- see the failures above.
)
exit /b %RC%
