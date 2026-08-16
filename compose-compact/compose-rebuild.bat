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
