@echo off
cd /d "%~dp0"

rem === compose-rebuild.bat -- rebuild compact images and recreate containers ===
rem
rem Usage:
rem   compose-rebuild.bat                  rebuild ALL images, recreate
rem   compose-rebuild.bat mesnie           rebuild only Mesnie
rem   compose-rebuild.bat <service>        gateward | pacman | backend | frontend
rem
rem Flags (after the target):  --no-cache
rem
rem The compact stack builds Java services from THIS file (the contexts point at
rem services/<svc>/, so they are correct here) -- there is no per-service detour.

set TARGET=%1
if "%TARGET%"=="" set TARGET=all

set NOCACHE=
if /i "%2"=="--no-cache" set NOCACHE=--no-cache
if /i "%1"=="--no-cache" ( set "NOCACHE=--no-cache"&set "TARGET=all" )

echo === target=%TARGET% nocache=%NOCACHE% ===

if /i "%TARGET%"=="all" goto target_all

rem Build the jar FIRST -- the image only COPYs it. clean AND -am, both required: without clean a
rem service whose OWN classes are unchanged keeps its old fat jar and silently ships the PREVIOUS
rem common; without -am, common comes from ~/.m2, older still. "backend" and "frontend" are not
rem maven modules, so they skip straight to the image build.
set "MOD="
if /i "%TARGET%"=="mesnie"   set "MOD=mesnie"
if /i "%TARGET%"=="gateward" set "MOD=gateWard"
if /i "%TARGET%"=="pacman"   set "MOD=pacMan"
if /i "%TARGET%"=="aukeep"   set "MOD=auKeep"
if defined MOD (
  echo [mvn] building %MOD%...
  pushd ..
  call mvn -q -DskipTests -pl %MOD% -am clean package
  if errorlevel 1 ( popd ^& echo mvn failed for %MOD% ^& exit /b 1 )
  popd
)

docker compose build %NOCACHE% %TARGET%
if errorlevel 1 ( echo build failed for %TARGET% & exit /b 1 )
docker compose up -d --no-deps --force-recreate %TARGET%
goto end

:target_all
echo [mvn] building the Spring services...
pushd ..
call mvn -q -DskipTests clean package
if errorlevel 1 ( popd & echo mvn failed & exit /b 1 )
popd
echo [docker] building every image in the compact stack...
docker compose build %NOCACHE%
if errorlevel 1 ( echo build failed & exit /b 1 )
echo [docker] recreating all containers...
docker compose up -d --force-recreate

:end
echo.
echo === rebuild done ===
docker compose ps
