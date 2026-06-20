@echo off
cd /d "%~dp0"

rem === compose-rebuild.bat -- rebuild Esquire images and recreate containers ===
rem
rem Usage:
rem   compose-rebuild.bat                  rebuild ALL service images, recreate
rem   compose-rebuild.bat backend          rebuild only the BFF (and baked SPA)
rem   compose-rebuild.bat frontend         rebuild only the ng-serve container
rem                                        (live-reload SPA on port 4200)
rem   compose-rebuild.bat <service>        rebuild a single Spring service
rem                                        (gateway, biztree, enyman, pacman,
rem                                         keysmith, kcmaster)
rem
rem Flags (must come AFTER the target):
rem   --no-cache                           pass --no-cache to docker compose build
rem
rem How it builds (and why):
rem   - The omnibus compose.yaml here has BROKEN build targets for Java services
rem     (context "." points at compose/, which has no Dockerfile). The working
rem     docker setup relies on each service's OWN compose.yaml at services/<svc>/
rem     to build the image; the omnibus compose.yaml just pulls those built
rem     images by tag (esquire.gateway etc.) at "docker compose up -d" time.
rem   - So this script builds Java images from each service's own dir, then
rem     uses the omnibus compose.yaml only to recreate the containers.
rem   - backend/frontend build targets in the omnibus ARE correct (their context
rem     points outside services/compose/), so we build them from here.

set TARGET=%1
if "%TARGET%"=="" set TARGET=all

set NOCACHE=
if /i "%2"=="--no-cache" set NOCACHE=--no-cache
if /i "%1"=="--no-cache" ( set "NOCACHE=--no-cache"&set "TARGET=all" )

echo === target=%TARGET% nocache=%NOCACHE% ===

if /i "%TARGET%"=="all"      goto target_all
if /i "%TARGET%"=="backend"  goto target_backend
if /i "%TARGET%"=="frontend" goto target_frontend
if /i "%TARGET%"=="gateway"  ( set "DIR=gateway"&goto target_java )
if /i "%TARGET%"=="biztree"  ( set "DIR=bizTree"&goto target_java )
if /i "%TARGET%"=="enyman"   ( set "DIR=enyMan"&goto target_java )
if /i "%TARGET%"=="pacman"   ( set "DIR=pacMan"&goto target_java )
if /i "%TARGET%"=="keysmith" ( set "DIR=keySmith"&goto target_java )
if /i "%TARGET%"=="kcmaster" ( set "DIR=kcMaster"&goto target_java )
if /i "%TARGET%"=="aukeep"   ( set "DIR=auKeep"&goto target_java )

echo ERROR: unknown target "%TARGET%"
echo Valid: all ^| backend ^| frontend ^| gateway ^| biztree ^| enyman ^| pacman ^| keysmith ^| kcmaster ^| aukeep
exit /b 1

:target_all
echo [mvn] building all Spring services...
pushd ..
call mvn -q -DskipTests clean package
if errorlevel 1 ( popd & echo mvn failed & exit /b 1 )
popd
set "DIR=gateway"  & call :build_java
if errorlevel 1 exit /b 1
set "DIR=bizTree"  & call :build_java
if errorlevel 1 exit /b 1
set "DIR=enyMan"   & call :build_java
if errorlevel 1 exit /b 1
set "DIR=pacMan"   & call :build_java
if errorlevel 1 exit /b 1
set "DIR=keySmith" & call :build_java
if errorlevel 1 exit /b 1
set "DIR=kcMaster" & call :build_java
if errorlevel 1 exit /b 1
set "DIR=auKeep" & call :build_java
if errorlevel 1 exit /b 1
echo [docker] rebuilding backend (BFF + baked SPA)...
docker compose build %NOCACHE% backend
if errorlevel 1 ( echo backend build failed & exit /b 1 )
echo [docker] rebuilding frontend (ng-serve)...
docker compose build %NOCACHE% frontend
if errorlevel 1 ( echo frontend build failed & exit /b 1 )
echo [docker] recreating all containers...
docker compose up -d --force-recreate
goto end

:target_java
echo [mvn] building %DIR%...
pushd ..
call mvn -q -DskipTests -pl %DIR% -am package
if errorlevel 1 ( popd & echo mvn failed for %DIR% & exit /b 1 )
popd
call :build_java
if errorlevel 1 exit /b 1
docker compose up -d --no-deps --force-recreate %TARGET%
goto end

:build_java
rem Subroutine: docker-build a single Spring service from its OWN compose.yaml
rem (the omnibus services/compose/compose.yaml has wrong context for Java).
rem Caller sets DIR (camelCase: gateway, bizTree, enyMan, pacMan, keySmith, kcMaster).
echo [docker] building %DIR% from services/%DIR%/...
pushd ..\%DIR%
docker compose build %NOCACHE%
if errorlevel 1 ( popd & echo docker build failed for %DIR% & exit /b 1 )
popd
exit /b 0

:target_backend
echo [docker] rebuilding backend (BFF + baked SPA)...
docker compose build %NOCACHE% backend
if errorlevel 1 ( echo backend build failed & exit /b 1 )
docker compose up -d --no-deps --force-recreate backend
goto end

:target_frontend
echo [docker] rebuilding frontend (ng-serve container)...
docker compose build %NOCACHE% frontend
if errorlevel 1 ( echo frontend build failed & exit /b 1 )
docker compose up -d --no-deps --force-recreate frontend
goto end

:end
echo.
echo === rebuild done ===
docker compose ps
