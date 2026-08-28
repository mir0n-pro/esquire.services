@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem === k8s-rebuild.bat -- rebuild Esquire images and redeploy on local k8s ===
rem
rem Usage:
rem   k8s-rebuild.bat                  rebuild all images (java services + backend)
rem   k8s-rebuild.bat backend          rebuild only explorer/backend (SPA + BFF)
rem   k8s-rebuild.bat <service>        rebuild a single Spring service
rem                                    (gateward, mesnie, pacman, aukeep)
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
call helm upgrade --install esquire-topology charts\esquire-topology --force-conflicts || exit /b 1

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
if /i "%TARGET%"=="gateward" ( set "SVC=gateward"&set "DIR=gateWard"&goto target_one )
if /i "%TARGET%"=="mesnie"   ( set "SVC=mesnie"&set "DIR=mesnie"&goto target_one )
if /i "%TARGET%"=="pacman"   ( set "SVC=pacman"&set "DIR=pacMan"&goto target_one )
if /i "%TARGET%"=="aukeep"   ( set "SVC=aukeep"&set "DIR=auKeep"&goto target_one )

echo ERROR: unknown target "%TARGET%"
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
call :read_pin ..\postgres\Dockerfile postgres
if errorlevel 1 exit /b 1
call :pin_have esquire-postgres
if "!PIN_HAVE!"=="1" goto pg_pinned
echo [docker] building infra image esquire-postgres (db.seed schema)...
docker build %NOCACHE% -f ..\postgres\Dockerfile -t esquire-postgres:17 -t esquire-postgres:!TS! ..\..
if errorlevel 1 ( echo postgres image build failed & exit /b 1 )
:pg_pinned
rem STAMPED AND DELIVERED, like the broker below. Building :17 alone changed NOTHING: values\postgres.yaml
rem carried a tag from an earlier stamp, so the chart kept pulling that older image and the SEED BAKED INTO
rem IT came with it. The failure is silent until a FRESH init replays the old schema -- a v1.2.12 column was
rem missing and the tree cache could not load, on a rebuild that had reported success.
call :patch_yaml postgres
helm status esquire-infra >nul 2>&1
if errorlevel 1 (
  echo [skip] esquire-infra not deployed -- yaml stamped %TS%; next k8s-up will deploy it.
) else (
  echo [helm] upgrading esquire-infra to tag %TS%...
  call helm upgrade esquire-infra charts\infra\postgres -f values\postgres.yaml --reset-then-reuse-values --set image.tag=!TS! --force-conflicts
  if errorlevel 1 ( echo helm upgrade failed for esquire-infra & exit /b 1 )
  kubectl rollout status statefulset/esquire-infra-postgres --timeout=180s
)

rem === Infra image: esquire-activemq (the broker config + the JMX exporter agent). Built HERE because
rem     NOTHING else did: compose only ever built it implicitly on a first `up`, and `docker compose up -d`
rem     does not rebuild on a Dockerfile change -- so a broker change reached neither target and the pod
rem     quietly kept running the old image.
rem     Stamped like the service images for the same reason they are: the chart's FIXED :6.1.4 tag hits the
rem     kubelet digest cache, so after a rebuild the kubelet keeps serving the OLD 6.1.4 it already resolved.
rem     A stale broker is silent -- the pod is Running, and only the missing scrape target gives it away. ===
call :read_pin ..\activemq\Dockerfile activemq
if errorlevel 1 exit /b 1
call :pin_have esquire-activemq
if "!PIN_HAVE!"=="1" goto amq_pinned
echo [docker] building infra image esquire-activemq (broker config + JMX exporter agent)...
docker build %NOCACHE% -t esquire-activemq:6.1.4 -t esquire-activemq:!TS! ..\activemq
if errorlevel 1 ( echo activemq image build failed & exit /b 1 )
:amq_pinned
call :patch_yaml activemq
helm status esquire-infra-amq >nul 2>&1
if errorlevel 1 (
  echo [skip] esquire-infra-amq not deployed -- yaml stamped %TS%; next k8s-up will deploy it.
) else (
  echo [helm] upgrading esquire-infra-amq to tag %TS%...
  call helm upgrade esquire-infra-amq charts\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set image.tag=!TS! --force-conflicts
  if errorlevel 1 ( echo helm upgrade failed for esquire-infra-amq & exit /b 1 )
  kubectl rollout status statefulset/esquire-infra-amq-activemq --timeout=180s
)

rem === Infra image: esquire-keycloak (the esquire theme + the realm import). Built HERE because NOTHING in
rem     the tree did: values\keycloak.yaml pinned a tag that existed only in one machine's docker cache, from
rem     a July build, while the Dockerfile had since moved on. A clean machine could not bring k8s up at all.
rem     BASE comes from the Dockerfile PIN (26.4.7); docker overrides it to 26.6.0 in its own compose file,
rem     and that override is the only place the two lines differ.
rem     Tagged with the release stamp for the same reason postgres and the broker are: a fixed tag hits the
rem     kubelet digest cache and the pod keeps the image it already resolved. What the image IS stays
rem     readable off its own labels, whatever the tag says. ===
call :read_pin ..\keycloak\Dockerfile.keycloak keycloak
if errorlevel 1 exit /b 1
call :pin_have esquire-keycloak
if "!PIN_HAVE!"=="1" goto kc_pinned
echo [docker] building infra image esquire-keycloak (theme + realm import)...
docker build %NOCACHE% -f ..\keycloak\Dockerfile.keycloak -t esquire-keycloak:!TS! ..\keycloak
if errorlevel 1 ( echo keycloak image build failed & exit /b 1 )
:kc_pinned
call :patch_yaml keycloak
helm status esquire-infra-kc >nul 2>&1
if errorlevel 1 (
  echo [skip] esquire-infra-kc not deployed -- yaml stamped !TS!; next k8s-up will deploy it.
) else (
  echo [helm] upgrading esquire-infra-kc to tag !TS!...
  call helm upgrade esquire-infra-kc charts\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set image.tag=!TS! --force-conflicts
  if errorlevel 1 ( echo helm upgrade failed for esquire-infra-kc & exit /b 1 )
  kubectl rollout status statefulset/esquire-infra-kc-keycloak --timeout=240s
)

set "SVC=gateward"&set "DIR=gateWard"
call :one
if errorlevel 1 exit /b 1
set "SVC=mesnie"&set "DIR=mesnie"
call :one
if errorlevel 1 exit /b 1
set "SVC=pacman"&set "DIR=pacMan"
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
rem -f values\backend.yaml, the same reason the Spring path above passes it: :patch_yaml backend rewrote that
rem file seven lines up, and without the flag the new image ships with the previous ConfigMap. k8s-up.bat has
rem always passed it, so a full bring-up repaired the drift and only a single-component rebuild carried it.
call helm upgrade esquire-backend charts\esquire-backend -f values\backend.yaml --reset-then-reuse-values --set image.tag=%TS% --force-conflicts
if errorlevel 1 ( echo helm upgrade failed & exit /b 1 )
kubectl rollout status statefulset/esquire-backend --timeout=180s
goto end

:one
rem Subroutine: rebuild + redeploy a single Spring service.
rem Inputs:
rem   SVC = lowercase service name (matches image suffix and helm release suffix)
rem When called from target_all, mvn already built all jars.
rem When called directly (single-target), mvn the specific module first.
set "IMG=%SVC%"
if /i "%TARGET%" neq "all" (
  echo [mvn] building %DIR%...
  pushd ..
  rem clean AND -am, both required: without clean a service whose OWN classes are unchanged keeps its old
  rem fat jar and silently ships the PREVIOUS common; without -am, common comes from ~/.m2, older still.
  call mvn -q -DskipTests -pl %DIR% -am clean package
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
rem Required secrets that are NOT reusable on the first upgrade after they became helm-required
rem same dev value k8s-up.bat / compose use.
set "SECRETS="
rem -f values\%SVC%.yaml, exactly as the infra upgrades above do: a rebuild must carry the CONFIG that
rem changed with it, not only the image. Without it a values edit sat on disk while the release kept the
rem old ConfigMap, and the deployment looked correct while behaving like the previous one.
call helm upgrade esquire-%SVC% charts\esquire-%SVC% -f values\%SVC%.yaml --reset-then-reuse-values --set image.tag=%TS% %SECRETS% --force-conflicts
if errorlevel 1 ( echo helm upgrade failed for esquire-%SVC% & exit /b 1 )
rem The compact charts name the workload after the RELEASE alone (esquire-mesnie); only the classic ones
rem carry the chart suffix (esquire-mesnie-mesnie). Watching the classic name here waited on a
rem statefulset that does not exist, and the rebuild reported NotFound over a roll that was fine.
kubectl rollout status statefulset/esquire-%SVC% --timeout=180s
exit /b 0

:resolve_tag
rem Subroutine: set TS based on whether esquire.%IMG%:%BASE_TS% already exists.
rem   Inputs:  IMG, BASE_TS, MM
rem   Output:  TS = %BASE_TS%       (first build of the hour for this service)
rem            TS = %BASE_TS%%MM%   (kubelet may have cached %BASE_TS% -- need fresh)
docker image inspect esquire.%IMG%:%BASE_TS% >nul 2>&1
if errorlevel 1 ( set "TS=%BASE_TS%" ) else ( set "TS=%BASE_TS%%MM%" )
exit /b 0

:read_pin
rem Subroutine: read an infra image's PIN out of its Dockerfile.  %1 = dockerfile  %2 = label
rem
rem The pin is the release stamp of the release in which this image's Esquire content last changed. It is
rem declared where the image is defined, edited BY HAND, and it IS the tag -- so an image that gained
rem nothing keeps the tag it had, and a release cannot replace infrastructure it did not touch.
set "TS="
for /f "tokens=2 delims==" %%v in ('findstr /b /c:"ARG PIN=" %1') do set "TS=%%v"
if "!TS!"=="" ( echo [pin] ERROR %2: %1 declares no ARG PIN & exit /b 1 )
echo [pin]    %2: pin=!TS!
exit /b 0

:pin_have
rem Subroutine: is this pin already built?  %1 = image name  ->  PIN_HAVE=1 when the tag is here.
rem
rem That IS the rule: a pin names one image, so if the tag exists there is nothing to build and nothing to
rem re-tag. Bump ARG PIN when the content changes and the next run builds it; leave it and the next run is a
rem no-op -- which also spares a slow KeyCloak build on every rebuild that changes nothing.
set "PIN_HAVE=0"
docker image inspect %1:!TS! >nul 2>&1
if not errorlevel 1 ( set "PIN_HAVE=1" & echo [pin]    %1:!TS! already built -- nothing to do )
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
