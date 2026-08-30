@echo off
rem setlocal: this script SETS ESQ_OBSERVABILITY_ENABLED to carry the arm across the rebuild, and without
rem it that value survived into the caller's shell -- a later docker-compose-up.bat in the same window then
rem brought the whole stack up EMITTING with no viewing stack: the mirror image of the half-arm this file
rem forbids. Every other o11y script in the tree opens with setlocal.
setlocal
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
rem How it builds:
rem   - Each Java service is built from its OWN compose.yaml at services/<svc>/, which is where a service's
rem     build settings live; this script then uses the omnibus compose.yaml to recreate the containers.
rem   - backend and frontend are built from here, their contexts pointing outside services/compose/.
rem   - The omnibus file can build the Java services too -- every build context in it names the directory it
rem     builds from, so "docker compose build <svc>" works from here as well. Building from each service's own
rem     file is what this script does because that file is the one a service owns.

set TARGET=%1
if "%TARGET%"=="" set TARGET=all

set NOCACHE=
if /i "%2"=="--no-cache" set NOCACHE=--no-cache
if /i "%1"=="--no-cache" ( set "NOCACHE=--no-cache"&set "TARGET=all" )

echo === target=%TARGET% nocache=%NOCACHE% ===

rem === The other compose stacks, dropped first ===
rem All three stacks bind the same host ports (4200 / 8081 / 8161 / 5433 / 3009), so the second one to
rem start simply fails to bind. Dropping the other projects here is what deploy-compose.cmd already does,
rem so a hand-run brings the same result as a pipeline run. "not running" is the normal case and costs
rem nothing.
docker compose -p esq-omnibus down --remove-orphans >nul 2>&1
docker compose -p esq-compact down --remove-orphans >nul 2>&1

rem THE O11Y ARM, DECIDED ONCE FOR EVERY TARGET. If grafana is up now, observability was armed, and it comes
rem back armed. Without this a rebuild returns the services with ESQ_OBSERVABILITY_ENABLED unset:
rem /actuator/prometheus 404s, their scrape targets go DOWN, and the boards show a gap that reads as
rem 'nothing is happening' rather than as a fault. The viewing stack also lives behind the o11y compose
rem profile, so a plain `up` REMOVES the containers the rebuild found running.
rem
rem AN ARM IS TWO HALVES, and keeping only one is worse than keeping neither: the profile brings the VIEWERS
rem back, while ESQ_OBSERVABILITY_ENABLED is what makes the services EMIT.
set "O11Y="
set "O11Y_FULL="
rem The container names are esqa-*, NOT esq-*: this lab is named apart so it can sit beside the classic
rem stack. Detecting on esq-* here found the CLASSIC stack's containers or nothing at all, and a service
rem rebuilt while the full arm was up came back with observability OFF -- exporting nothing, into a stack
rem that was running and waiting for it.
docker ps -q -f name=esqa-grafana    | findstr . >nul && set "O11Y=--profile o11y"
docker ps -q -f name=esqa-prometheus | findstr . >nul && set "O11Y_FULL=1"

rem This lab has THREE arms -- debug / off / full -- and a rebuild must land a service back in the one that
rem is running. FULL is recognised by prometheus. The other two differ only in their LOG LEVELS, so rather
rem than enumerating them the levels are read off a service that is already up: whatever arm put them there,
rem a rebuilt service gets the same. Without this, rebuilding one service in the debug arm brought it back
rem at the compose defaults while its neighbours stayed at DEBUG.
if defined O11Y_FULL (
  echo [docker] observability is armed FULL -- keeping metrics + tracing across the rebuild
  set "ESQ_OBSERVABILITY_ENABLED=true"
  set "ESQ_METRICS_HISTOGRAMS=true"
) else (
  echo [docker] observability is OFF -- carrying the running fleet's log levels across the rebuild
  set "ESQ_OBSERVABILITY_ENABLED=false"
  set "ESQ_METRICS_HISTOGRAMS=false"
  for /f "tokens=1,* delims==" %%A in ('docker inspect -f "{{range .Config.Env}}{{println .}}{{end}}" esqa-enyman 2^>nul ^| findstr /b "LOG_LEVEL_"') do set "%%A=%%B"
)

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
rem The broker image carries our own activemq.xml AND the JMX exporter agent, so it must be rebuilt like any
rem other. It never was: `docker compose up -d` below does NOT rebuild on a Dockerfile change, so before this
rem line a broker change simply never reached the running stack.
echo [docker] rebuilding activemq (broker config + JMX exporter agent)...
docker compose build %NOCACHE% activemq
if errorlevel 1 ( echo activemq build failed & exit /b 1 )
echo [docker] recreating all containers...
docker compose %O11Y% up -d --force-recreate
if errorlevel 1 ( echo bring-up failed & exit /b 1 )
goto end

:target_java
echo [mvn] building %DIR%...
pushd ..
call mvn -q -DskipTests -pl %DIR% -am package
if errorlevel 1 ( popd & echo mvn failed for %DIR% & exit /b 1 )
popd
call :build_java
if errorlevel 1 exit /b 1
docker compose %O11Y% up -d --no-deps --force-recreate %TARGET%
if errorlevel 1 ( echo bring-up failed & exit /b 1 )
goto end

:build_java
rem Subroutine: docker-build a single Spring service from its OWN compose.yaml -- the file that service owns.
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
docker compose %O11Y% up -d --no-deps --force-recreate backend
if errorlevel 1 ( echo bring-up failed & exit /b 1 )
goto end

:target_frontend
echo [docker] rebuilding frontend (ng-serve container)...
docker compose build %NOCACHE% frontend
if errorlevel 1 ( echo frontend build failed & exit /b 1 )
docker compose %O11Y% up -d --no-deps --force-recreate frontend
if errorlevel 1 ( echo bring-up failed & exit /b 1 )
goto end

:end
echo.
echo === rebuild done ===
docker compose ps
