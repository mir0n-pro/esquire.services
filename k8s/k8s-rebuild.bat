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
rem   This script tags every fresh image with a YYMM.DDHH tag (matches
rem   release_notes.txt version stamps) and helm-upgrades the release with
rem   --set image.tag=<tag>, forcing the kubelet to pick up the new image.
rem
rem   Tag granularity: hour-level (YYMM.DDHH). If the same target was already
rem   built this hour, esquire.<svc>:<YYMM.DDHH> already exists and the
rem   kubelet has cached its digest -- the second build would hit the same
rem   :latest trap. In that case we append minutes (YYMM.DDHHmm) per-service
rem   so the kubelet sees a tag it has never resolved.

rem === Context safety guard ===
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop".
  echo Refusing to run k8s-rebuild.bat -- this script targets local Docker Desktop k8s only.
  exit /b 1
)

set TARGET=%1
if "%TARGET%"=="" set TARGET=all

set NOCACHE=
if /i "%2"=="--no-cache" set NOCACHE=--no-cache
if /i "%1"=="--no-cache" ( set "NOCACHE=--no-cache"&set "TARGET=all" )

rem === Compute timestamp tag (YYMM.DDHH base, +mm per-service if collision) ===
for /f %%t in ('powershell -nop -c "Get-Date -Format yyMM.ddHH"') do set "BASE_TS=%%t"
for /f %%m in ('powershell -nop -c "Get-Date -Format mm"') do set "MM=%%m"
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

echo ERROR: unknown target "%TARGET%"
echo Valid: all ^| backend ^| gateway ^| biztree ^| enyman ^| pacman ^| keysmith ^| kcmaster
exit /b 1

:target_all
rem One mvn pass for all Spring services from the parent pom.
echo [mvn] building all Spring services...
pushd ..
call mvn -q -DskipTests clean package
if errorlevel 1 ( popd & echo mvn failed & exit /b 1 )
popd

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
set "SVC=backend"
call :resolve_tag
docker tag esquire.backend:latest esquire.backend:%TS%
helm status esquire-backend >nul 2>&1
if errorlevel 1 (
  echo [skip] esquire-backend not deployed -- image rebuilt as esquire.backend:%TS%; run k8s-up.bat to deploy.
  goto end
)
echo [helm] upgrading esquire-backend to tag %TS%...
call helm upgrade esquire-backend charts\esquire-backend --reuse-values --set image.tag=%TS%
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
if /i "%TARGET%" neq "all" (
  echo [mvn] building %DIR%...
  pushd ..
  call mvn -q -DskipTests -pl %DIR% -am package
  if errorlevel 1 ( popd & echo mvn failed for %DIR% & exit /b 1 )
  popd
)
echo [docker] building esquire.%SVC% from %DIR%/...
pushd ..\%DIR%
docker compose build %NOCACHE%
if errorlevel 1 ( popd & echo docker build failed for %DIR% & exit /b 1 )
popd
call :resolve_tag
docker tag esquire.%SVC%:latest esquire.%SVC%:%TS%
helm status esquire-%SVC% >nul 2>&1
if errorlevel 1 (
  echo [skip] esquire-%SVC% not deployed -- image rebuilt as esquire.%SVC%:%TS%; run k8s-up.bat to deploy.
  exit /b 0
)
echo [helm] upgrading esquire-%SVC% to tag %TS%...
call helm upgrade esquire-%SVC% charts\esquire-%SVC% --reuse-values --set image.tag=%TS%
if errorlevel 1 ( echo helm upgrade failed for esquire-%SVC% & exit /b 1 )
kubectl rollout status deploy/esquire-%SVC%-%SVC% --timeout=180s
exit /b 0

:resolve_tag
rem Subroutine: set TS based on whether esquire.%SVC%:%BASE_TS% already exists.
rem   Inputs:  SVC, BASE_TS, MM
rem   Output:  TS = %BASE_TS%       (first build of the hour for this service)
rem            TS = %BASE_TS%%MM%   (kubelet may have cached %BASE_TS% -- need fresh)
docker image inspect esquire.%SVC%:%BASE_TS% >nul 2>&1
if errorlevel 1 ( set "TS=%BASE_TS%" ) else ( set "TS=%BASE_TS%%MM%" )
exit /b 0

:end
echo.
echo === rebuild done. base tag=%BASE_TS% ===
kubectl get pods -n default
