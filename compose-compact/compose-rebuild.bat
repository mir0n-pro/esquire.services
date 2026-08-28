@echo off
rem setlocal: this script SETS ESQ_OBSERVABILITY_ENABLED to carry the arm across the rebuild, and without
rem it that value survived into the caller's shell -- a later docker-compose-up.bat in the same window then
rem brought the whole stack up EMITTING with no viewing stack: the mirror image of the half-arm this file
rem forbids. Every other o11y script in the tree opens with setlocal.
setlocal
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

rem === The other compose stack, dropped first ===
rem Both stacks bind the same host ports (4200 / 8081 / 8161 / 5433 / 3009), so the second one to start
rem simply fails to bind. Dropping the other project here is what deploy-compose.cmd already does, so a
rem hand-run brings the same result as a pipeline run. "not running" is the normal case and costs nothing.
docker compose -p esq-omnibus down --remove-orphans >nul 2>&1

rem THE O11Y ARM, DECIDED ONCE FOR EVERY TARGET. If grafana is up now, observability was armed, and it
rem comes back armed -- for a SINGLE-service rebuild exactly as for `all`. It used to live only in the
rem all-path, so `compose-rebuild.bat mesnie` returned that service with ESQ_OBSERVABILITY_ENABLED unset:
rem /actuator/prometheus then 404s, its scrape target goes DOWN, and the board shows a gap that reads as
rem 'nothing is happening' rather than as a fault. Found on 2026-08-25 with four services dark at once.
rem
rem AN ARM IS TWO HALVES, and keeping only one is worse than keeping neither: the profile brings the
rem VIEWERS back, while ESQ_OBSERVABILITY_ENABLED is what makes the services EMIT.
set "O11Y="
set "O11Y_FULL="
docker ps -q -f name=esqc-grafana    | findstr . >nul && set "O11Y=--profile o11y"
docker ps -q -f name=esqc-prometheus | findstr . >nul && set "O11Y_FULL=1"
rem GRAFANA UP IS NOT THE SAME AS FULLY ARMED. It is up in the LOG-only isolation arm too, which runs it
rem with ESQ_OBSERVABILITY_ENABLED=false and REMOVES tempo/prometheus/otel-collector on purpose. Reading
rem grafana alone as 'armed' brought a rebuilt service back with tracing + metrics + histograms on,
rem exporting to a collector the arm had deleted -- one process in the other arm, mid-matrix, while the
rem echo line claimed the opposite. PROMETHEUS is what separates them.
if defined O11Y_FULL (
  echo [docker] observability is armed FULL -- keeping metrics + tracing across the rebuild
  set "ESQ_OBSERVABILITY_ENABLED=true"
  set "ESQ_METRICS_HISTOGRAMS=true"
) else if defined O11Y (
  echo [docker] the LOG-only arm is up -- keeping metrics + tracing OFF, ONLY pro.mir0n at INFO
  set "ESQ_OBSERVABILITY_ENABLED=false"
  set "ESQ_METRICS_HISTOGRAMS=false"
  rem EVERY knob the arm owns, not just levelMir0n. o11y-log-on.bat sets five: with only the first
  rem restated, a rebuilt service came back with develop at the compose default DEBUG (writing to the
  rem bind mount) and amq/jms reaching the console -- extra log volume the other three do not have, so
  rem the cell is contaminated by the rebuild. ONLY pro.mir0n differs between the arms.
  set "LOG_LEVEL_MIR0N=INFO"
  set "LOG_LEVEL_DEVELOP=OFF"
  set "LOG_LEVEL_MSG=OFF"
  set "LOG_LEVEL_AMQ=OFF"
  set "LOG_LEVEL_JMS=OFF"
)

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
  rem PLAIN &, never ^&. Inside a ( ) block the caret ESCAPES the ampersand, so it stops being a command
  rem separator: cmd runs the whole thing as one command `popd & echo ... & exit /b 1`, popd rejects the extra
  rem arguments, and the echo and the exit never run. The guard silently did nothing -- the image was then
  rem built from the PREVIOUS jar and the script printed 'rebuild done'. Twelve lines below, the all-path
  rem guard uses a plain & and works.
  if errorlevel 1 ( popd & echo mvn failed for %MOD% & exit /b 1 )
  popd
)

docker compose build %NOCACHE% %TARGET%
if errorlevel 1 ( echo build failed for %TARGET% & exit /b 1 )
docker compose %O11Y% up -d --no-deps --force-recreate %TARGET%
if errorlevel 1 ( echo bring-up failed & exit /b 1 )
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
rem The viewing stack lives behind the "o11y" compose profile, so a plain up leaves it out and REMOVES the
rem containers a rebuild found running -- a rebuild that silently disarms observability. If grafana is up now,
rem it was armed, and it comes back armed. Same rule the k8s bring-up follows.
rem
rem AN ARM IS TWO HALVES, and keeping only one is worse than keeping neither: the profile brings the VIEWERS
rem back, while ESQ_OBSERVABILITY_ENABLED is what makes the services EMIT. Restoring the profile alone leaves
rem Grafana and Prometheus running against services that publish nothing -- dashboards up and empty, and every
rem scrape target down, which reads as "nothing is happening" rather than as a fault. Both halves, or neither.

echo [docker] recreating all containers...
docker compose %O11Y% up -d --force-recreate
if errorlevel 1 ( echo bring-up failed & exit /b 1 )

:end
echo.
echo === rebuild done ===
docker compose ps
