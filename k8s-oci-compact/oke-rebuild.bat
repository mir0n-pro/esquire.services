@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem ===========================================================================
rem oke-rebuild.bat -- rebuild + push + redeploy a single SUPER-COMPACT component
rem                    on OKE, auto-stamping the image tag per Esquire's rule:
rem                    "fresh per deploy, never overwrite"
rem                    (vMaj.Min.Micro-YYMM.DDHH).
rem
rem Usage:
rem   oke-rebuild.bat <component>
rem     components: all | mesnie | gateward | pacman | backend
rem
rem Four components, not seven: Mesnie carries enyMan, keySmith and the identity
rem work, and gateWard carries the gate and the tree cache.
rem
rem What it does:
rem   1. read Micro from top of ..\doc\release_notes.txt   (e.g. v1.2.13)
rem   2. STAMP = <Micro>-yyMM.ddHH                          (e.g. v1.2.13-2608.1921)
rem   3. mvn -pl <DIR> -am package                          (Spring services only)
rem   4. docker buildx build --platform amd64+arm64
rem        -t ghcr.io/mir0n-pro/esquire.<svc>:STAMP --push
rem   5. patch values\<svc>.yaml :  tag: "STAMP"
rem   6. helm upgrade esquire-<svc> ..\k8s-compact\charts\esquire-<svc> -f values\<svc>.yaml
rem        --reset-then-reuse-values --set image.tag=STAMP
rem   7. kubectl rollout status statefulset/esquire-<svc>-<svc> --timeout=5m
rem   8. echo STAMP for paste into release_notes.txt
rem
rem Requires:
rem   - GHCR_TOKEN env var (GitHub PAT with write:packages)
rem   - kubectl context already on the OKE cluster (oke-login.bat first)
rem   - docker buildx available
rem
rem Infra (postgres, keycloak, activemq) is deliberately out of scope --
rem infra restamping has separate constraints (secrets, KC_FEATURES pin, etc.)
rem and is done by hand when those rare changes happen.
rem ===========================================================================

set TARGET=%~1
if "%TARGET%"=="" goto usage

if "%GHCR_TOKEN%"=="" (
  echo ERROR: GHCR_TOKEN env var not set. Set to a GitHub PAT with write:packages.
  exit /b 1
)

rem === Read Micro from top of release_notes.txt ===
rem    Top entry looks like:   v1.2.13-2608.1719  <headline>
rem    We want just the v1.2.13 part (everything before the first '-').
set "VLINE="
for /f "tokens=1" %%v in ('powershell -nop -c "(Select-String -Path '..\doc\release_notes.txt' -Pattern '^v\d')[0].Line.Split(' ')[0]"') do set "VLINE=%%v"
if "%VLINE%"=="" (
  echo ERROR: could not parse version from ..\doc\release_notes.txt top line.
  exit /b 1
)
for /f "tokens=1 delims=-" %%m in ("%VLINE%") do set "MICRO=%%m"

for /f %%t in ('powershell -nop -c "Get-Date -Format yyMM.ddHH"') do set "TS=%%t"
set "STAMP=%MICRO%-%TS%"

echo === target=%TARGET%  stamp=%STAMP% ===

echo --- GHCR login...
echo %GHCR_TOKEN%| docker login ghcr.io -u mir0n-pro --password-stdin || goto fail
docker buildx use desktop-linux 2>nul || docker buildx use default

rem === Dispatch ===
if /i "%TARGET%"=="all"      goto target_all
if /i "%TARGET%"=="mesnie"   ( set "SVC=mesnie"   & set "DIR=mesnie"   & goto target_spring )
if /i "%TARGET%"=="gateward" ( set "SVC=gateward" & set "DIR=gateWard" & goto target_spring )
if /i "%TARGET%"=="pacman"   ( set "SVC=pacman"   & set "DIR=pacMan"   & goto target_spring )
if /i "%TARGET%"=="backend"  goto target_backend

:usage
echo Usage: oke-rebuild.bat ^<component^>
echo   components: all ^| mesnie ^| gateward ^| pacman ^| backend
exit /b 1

rem ---------------------------------------------------------------------------
:target_all
rem One mvn pass for every Spring service from the parent pom, then walk services in
rem dependency order: the independents first, then the gate, then backend (which depends
rem on the gate + KC). Same shape ..\k8s-compact\k8s-rebuild.bat uses.
echo --- [mvn] building all Spring services...
pushd ..
call mvn -q -DskipTests clean package
if errorlevel 1 ( popd & echo mvn failed & exit /b 1 )
popd

set "SVC=mesnie"   & set "DIR=mesnie"   & call :do_spring & if errorlevel 1 goto fail
set "SVC=pacman"   & set "DIR=pacMan"   & call :do_spring & if errorlevel 1 goto fail
set "SVC=gateward" & set "DIR=gateWard" & call :do_spring & if errorlevel 1 goto fail
call :do_backend
if errorlevel 1 goto fail

echo.
echo ============================================================
echo  ALL components rebuilt + pushed + helm-upgraded.
echo  stamp = %STAMP%   (paste into release_notes.txt deploy entry)
echo ============================================================
exit /b 0

rem ---------------------------------------------------------------------------
:do_spring
rem Subroutine called only from :target_all -- skips the per-component mvn
rem (parent pom build already done above), uses the buildAndDeploy core.
set "IMG=ghcr.io/mir0n-pro/esquire.%SVC%"
set "DOCKERFILE=..\%DIR%\Dockerfile"
set "CONTEXT=..\%DIR%"
call :buildAndDeploy
exit /b %errorlevel%

:do_backend
set "SVC=backend"
set "IMG=ghcr.io/mir0n-pro/esquire.backend"
set "DOCKERFILE=..\..\explorer\backend\Dockerfile"
rem Build context = explorer/ (parent of backend + frontend) per Dockerfile.
set "CONTEXT=..\..\explorer"
call :buildAndDeploy
exit /b %errorlevel%

rem ---------------------------------------------------------------------------
:target_spring
echo --- [mvn] building %DIR%...
pushd ..
call mvn -q -DskipTests -pl %DIR% -am package
if errorlevel 1 ( popd & echo mvn failed for %DIR% & exit /b 1 )
popd
set "IMG=ghcr.io/mir0n-pro/esquire.%SVC%"
set "DOCKERFILE=..\%DIR%\Dockerfile"
set "CONTEXT=..\%DIR%"
goto buildAndDeploy

rem ---------------------------------------------------------------------------
:target_backend
set "SVC=backend"
set "IMG=ghcr.io/mir0n-pro/esquire.backend"
set "DOCKERFILE=..\..\explorer\backend\Dockerfile"
rem Multi-stage Dockerfile reaches into ../frontend, so context is the
rem parent explorer/ directory (per Dockerfile header comment).
set "CONTEXT=..\..\explorer"
goto buildAndDeploy

rem ---------------------------------------------------------------------------
:buildAndDeploy
echo --- [build] %IMG%:%STAMP% (multi-arch amd64+arm64)...
docker buildx build --platform linux/amd64,linux/arm64 ^
  -t %IMG%:%STAMP% ^
  --push ^
  -f "%DOCKERFILE%" ^
  "%CONTEXT%"
if errorlevel 1 goto fail

echo --- [yaml] values\%SVC%.yaml :  tag -^> %STAMP%
powershell -nop -c "(Get-Content -Raw values\%SVC%.yaml) -replace '(?m)^(\s*tag:\s*).*$', ('${1}\"' + '%STAMP%' + '\"') | Set-Content -NoNewline values\%SVC%.yaml"
if errorlevel 1 goto fail

rem Mirror k8s-rebuild.bat: skip the helm step if the release does not exist yet.
rem Lets oke-down -> oke-rebuild -> oke-up flow work (rebuild stamps the yaml +
rem pushes the image to GHCR; the next oke-up picks up the stamped yaml).
helm status esquire-%SVC% >nul 2>&1
if errorlevel 1 (
  echo --- [skip] esquire-%SVC% not deployed -- yaml stamped %STAMP%, GHCR has the image; next oke-up will deploy it.
  echo.
  echo ============================================================
  echo  done.   esquire-%SVC%   stamp = %STAMP%
  echo  ^(paste into release_notes.txt as the deploy datetime^)
  echo ============================================================
  exit /b 0
)

echo --- [helm] upgrading esquire-%SVC% to %STAMP%...
rem --force-conflicts: helm 4 applies SERVER-SIDE, and anything that scales OUTSIDE helm (the perf
rem matrix does) owns .spec.replicas -- an upgrade that then sets replicas is REFUSED. Seen on OKE 08-19.
rem -f values\%SVC%.yaml, exactly as oke-up.bat does: step 5 above rewrote that file, so a rebuild that does
rem not pass it ships the new IMAGE with the OLD ConfigMap -- the rollout goes green and the pod keeps the
rem previous configuration. The local twin (k8s-compact\k8s-rebuild.bat) was corrected for this; the
rem correction had not reached the cloud path, which is the one its own header recommends for a
rem one-component change.
helm upgrade esquire-%SVC% ..\k8s-compact\charts\esquire-%SVC% -f values\%SVC%.yaml --reset-then-reuse-values --set image.tag=%STAMP% --force-conflicts
if errorlevel 1 goto fail

rem StatefulSet, not Deployment: every chart in both profiles builds a StatefulSet, because the
rem ordinal is what gives each pod its instanceNo and its rod-id.
rem The compact charts name the workload after the RELEASE alone (esquire-mesnie); the chart suffix
rem (esquire-mesnie-mesnie) is the CLASSIC naming, and waiting on it watches a workload that does not
rem exist -- the rebuild then reports NotFound over a roll that was fine.
echo --- [rollout] waiting for statefulset/esquire-%SVC% (timeout 5m)...
kubectl rollout status statefulset/esquire-%SVC% --timeout=5m
if errorlevel 1 goto fail

echo.
echo ============================================================
echo  done.   esquire-%SVC%   stamp = %STAMP%
echo  (paste into release_notes.txt as the deploy datetime)
echo ============================================================
exit /b 0

:fail
echo.
echo OKE-REBUILD FAILED.
exit /b 1
