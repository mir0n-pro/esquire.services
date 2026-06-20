@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem === k8s-rebuild.bat -- rebuild Esquire images and redeploy on local k8s ===
rem
rem Usage:
rem   k8s-rebuild.bat                  rebuild all images (java services + backend)
rem   k8s-rebuild.bat backend          rebuild only explorer/backend (SPA + BFF)
rem   k8s-rebuild.bat <service>        rebuild a single Spring service
rem                                    (gateway, biztree, enyman, pacman, keysmith, kcmaster)
rem
rem Flags (must come AFTER the target):
rem   --no-cache                       pass --no-cache to docker compose build
rem
rem Why this script exists:
rem   Chart pins image.tag=latest with imagePullPolicy=IfNotPresent. After
rem   docker build, kubelet keeps using the OLD digest cached under :latest.
rem   This script tags every fresh image with the canonical Esquire stamp
rem   vMaj.Min.Micro-YYMM.DDHH (same shape oke-rebuild.bat uses against GHCR,
rem   same shape release_notes.txt records) and helm-upgrades the release
rem   with --set image.tag=<stamp>, forcing the kubelet to pick up the
rem   new image.
rem
rem   Parity with OKE: build + stamp + helm-upgrade dance is identical on
rem   local Docker Desktop and on OKE -- local rehearsal is a real testing
rem   milestone for the next OKE push.
rem
rem   Tag granularity: hour-level (vMaj.Min.Micro-YYMM.DDHH). If the same
rem   target was already built this hour, esquire.<svc>:<stamp> already
rem   exists and the kubelet has cached its digest -- the second build
rem   would hit the same :latest trap. In that case we append minutes
rem   (-YYMM.DDHHmm) per-service so the kubelet sees a tag it has never
rem   resolved.
rem
rem   Micro version is read from the top of ..\doc\release_notes.txt
rem   (e.g. v1.2.4 from "v1.2.4-2605.1700 <headline>").

rem === Context safety guard ===
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop".
  echo Refusing to run k8s-rebuild.bat -- this script targets local Docker Desktop k8s only.
  exit /b 1
)

rem === Shared topology ConfigMap must exist BEFORE any service helm-upgrade (the new deployments mount it;
rem     a missing ConfigMap would fail the pod + time out the rollout). Idempotent; k8s-up installs it too. ===
echo --- ensuring topology ConfigMap...
call helm upgrade --install esquire-topology charts\esquire-topology || exit /b 1

set TARGET=%1
if "%TARGET%"=="" set TARGET=all

set NOCACHE=
if /i "%2"=="--no-cache" set NOCACHE=--no-cache
if /i "%1"=="--no-cache" ( set "NOCACHE=--no-cache"&set "TARGET=all" )

rem === Read Micro from top of release_notes.txt ===
rem    Top entry looks like:   v1.2.4-2605.1700 <headline>
rem    We want just the v1.2.4 part (everything before the first '-').
set "VLINE="
for /f "tokens=1" %%v in ('powershell -nop -c "(Select-String -Path '..\doc\release_notes.txt' -Pattern '^v\d')[0].Line.Split(' ')[0]"') do set "VLINE=%%v"
if "%VLINE%"=="" ( echo ERROR: could not parse version from ..\doc\release_notes.txt top line. & exit /b 1 )
for /f "tokens=1 delims=-" %%m in ("%VLINE%") do set "MICRO=%%m"

rem === Compute canonical stamp (vMaj.Min.Micro-YYMM.DDHH, +mm per-svc if collision) ===
for /f %%t in ('powershell -nop -c "Get-Date -Format yyMM.ddHH"') do set "DT=%%t"
for /f %%m in ('powershell -nop -c "Get-Date -Format mm"') do set "MM=%%m"
set "BASE_TS=%MICRO%-%DT%"
echo === target=%TARGET% base_tag=%BASE_TS% nocache=%NOCACHE% ===

rem    Two supported flows:
rem      a) cluster is up:           rebuild + helm-upgrade in place
rem      b) cluster down or partial: rebuild images only; next k8s-up picks them up
rem    Per-service checks below decide (a) vs (b) for each release individually.

if /i "%TARGET%"=="all"      goto target_all
if /i "%TARGET%"=="backend"  goto target_backend
if /i "%TARGET%"=="gateway"  ( set "SVC=gateway"&set "DIR=gateway"&goto target_one )
if /i "%TARGET%"=="biztree"  ( set "SVC=biztree"&set "DIR=bizTree"&goto target_one )
if /i "%TARGET%"=="enyman"   ( set "SVC=enyman"&set "DIR=enyMan"&goto target_one )
if /i "%TARGET%"=="pacman"   ( set "SVC=pacman"&set "DIR=pacMan"&goto target_one )
if /i "%TARGET%"=="keysmith" ( set "SVC=keysmith"&set "DIR=keySmith"&goto target_one )
if /i "%TARGET%"=="kcmaster" ( set "SVC=kcmaster"&set "DIR=kcMaster"&goto target_one )
if /i "%TARGET%"=="aukeep"   ( set "SVC=aukeep"&set "DIR=auKeep"&goto target_one )

echo ERROR: unknown target "%TARGET%"
echo Valid: all ^| backend ^| gateway ^| biztree ^| enyman ^| pacman ^| keysmith ^| kcmaster ^| aukeep
exit /b 1

:target_all
rem One mvn pass for all Spring services from the parent pom.
echo [mvn] building all Spring services...
pushd ..
call mvn -q -DskipTests clean package
if errorlevel 1 ( popd & echo mvn failed & exit /b 1 )
popd

rem === Infra image: esquire-postgres:17 (k8s-only). docker compose no longer builds it -- the Postgres
rem     container was removed from compose (docker uses the external host pg18); the dockerized Postgres
rem     is exclusively the k8s esquire-infra-postgres StatefulSet. Build it here so the image stays
rem     available for k8s-up. Context = repo root (../..) so the Dockerfile can COPY db.seed + initdb. ===
echo [docker] building infra image esquire-postgres:17 (db.seed schema)...
docker build %NOCACHE% -f ..\postgres\Dockerfile -t esquire-postgres:17 ..\..
if errorlevel 1 ( echo postgres image build failed & exit /b 1 )

set "SVC=gateway"&set "DIR=gateway"
call :one
if errorlevel 1 exit /b 1
set "SVC=biztree"&set "DIR=bizTree"
call :one
if errorlevel 1 exit /b 1
set "SVC=enyman"&set "DIR=enyMan"
call :one
if errorlevel 1 exit /b 1
set "SVC=pacman"&set "DIR=pacMan"
call :one
if errorlevel 1 exit /b 1
set "SVC=keysmith"&set "DIR=keySmith"
call :one
if errorlevel 1 exit /b 1
set "SVC=kcmaster"&set "DIR=kcMaster"
call :one
if errorlevel 1 exit /b 1
set "SVC=aukeep"&set "DIR=auKeep"
call :one
if errorlevel 1 exit /b 1
goto target_backend

:target_one
call :one
if errorlevel 1 exit /b 1
goto end

:target_backend
echo [docker] building explorer/backend (BFF + SPA)...
pushd ..\..\explorer\backend
docker compose build %NOCACHE%
if errorlevel 1 ( popd & echo backend build failed & exit /b 1 )
popd
set "SVC=backend"&set "IMG=backend"
call :resolve_tag
docker tag esquire.backend:latest esquire.backend:%TS%
rem Patch yaml ALWAYS -- the yaml is the canonical record of "what should
rem be deployed". Whether the release is up now or not, the next k8s-up
rem must read the freshly-built stamp from here.
call :patch_yaml backend
helm status esquire-backend >nul 2>&1
if errorlevel 1 (
  echo [skip] esquire-backend not deployed -- yaml stamped %TS%; next k8s-up will deploy it.
  goto end
)
echo [helm] upgrading esquire-backend to tag %TS%...
call helm upgrade esquire-backend charts\esquire-backend --reset-then-reuse-values --set image.tag=%TS%
if errorlevel 1 ( echo helm upgrade failed & exit /b 1 )
kubectl rollout status deploy/esquire-backend-backend --timeout=180s
goto end

:one
rem Subroutine: rebuild + redeploy a single Spring service.
rem Inputs:
rem   SVC = lowercase service name (matches image suffix and helm release suffix)
rem   DIR = source directory name (camelCase: bizTree, keySmith, ...)
rem When called from target_all, mvn already built all jars.
rem When called directly (single-target), mvn the specific module first.
set "IMG=%SVC%"
if /i "%TARGET%" neq "all" (
  echo [mvn] building %DIR%...
  pushd ..
  call mvn -q -DskipTests -pl %DIR% -am package
  if errorlevel 1 ( popd & echo mvn failed for %DIR% & exit /b 1 )
  popd
)
echo [docker] building esquire.%IMG% from %DIR%/...
pushd ..\%DIR%
docker compose build %NOCACHE%
if errorlevel 1 ( popd & echo docker build failed for %DIR% & exit /b 1 )
popd
call :resolve_tag
docker tag esquire.%IMG%:latest esquire.%IMG%:%TS%
rem Patch yaml ALWAYS -- the yaml is the canonical record of "what should
rem be deployed". Whether the release is up now or not, the next k8s-up
rem must read the freshly-built stamp from here. Mirrors oke-rebuild.bat.
call :patch_yaml %SVC%
helm status esquire-%SVC% >nul 2>&1
if errorlevel 1 (
  echo [skip] esquire-%SVC% not deployed -- yaml stamped %TS%; next k8s-up will deploy it.
  exit /b 0
)
echo [helm] upgrading esquire-%SVC% to tag %TS%...
call helm upgrade esquire-%SVC% charts\esquire-%SVC% --reset-then-reuse-values --set image.tag=%TS%
if errorlevel 1 ( echo helm upgrade failed for esquire-%SVC% & exit /b 1 )
kubectl rollout status deploy/esquire-%SVC%-%SVC% --timeout=180s
exit /b 0

:resolve_tag
rem Subroutine: set TS based on whether esquire.%IMG%:%BASE_TS% already exists.
rem   Inputs:  IMG, BASE_TS, MM
rem   Output:  TS = %BASE_TS%       (first build of the hour for this service)
rem            TS = %BASE_TS%%MM%   (kubelet may have cached %BASE_TS% -- need fresh)
docker image inspect esquire.%IMG%:%BASE_TS% >nul 2>&1
if errorlevel 1 ( set "TS=%BASE_TS%" ) else ( set "TS=%BASE_TS%%MM%" )
exit /b 0

:patch_yaml
rem Subroutine: rewrite the 'tag:' line in values\%1.yaml to the stamped tag.
rem   Inputs:  %1 = svc name (matches values\<svc>.yaml); TS = the new stamp
rem Mirrors oke-rebuild.bat's same edit pattern. Pure source-of-truth update;
rem the rollout itself happens via --reset-then-reuse-values --set image.tag below.
echo [yaml] values\%1.yaml :  tag -^> %TS%
powershell -nop -c "(Get-Content -Raw values\%1.yaml) -replace '(?m)^(\s*tag:\s*).*$', ('${1}\"' + '%TS%' + '\"') | Set-Content -NoNewline values\%1.yaml"
exit /b 0

:end
echo.
echo === rebuild done. base tag=%BASE_TS% ===
kubectl get pods -n default
