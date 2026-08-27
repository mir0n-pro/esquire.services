@echo off
rem ===========================================================================
rem k8s-up.bat -- deploy the Esquire stack to local Docker Desktop k8s.
rem Direct mirror of ../k8s-oci/oke-up.bat: helm upgrade --install per chart
rem with -f values/<svc>.yaml carrying the image tag (and all per-environment
rem knobs). Secrets passed via --set. No stamp recomputation at up time --
rem the yaml's image.tag is the source of truth (set by k8s-rebuild.bat).
rem
rem The one local-specific quirk vs OKE: the image lives in the local Docker
rem daemon (no registry pull). Before each install, alias esquire.<svc>:latest
rem to esquire.<svc>:<yaml-tag> if the tagged image doesn't already exist --
rem this lets the kubelet find the image under the tag the yaml references.
rem
rem Usage: k8s-up.bat
rem ===========================================================================

setlocal enabledelayedexpansion
cd /d "%~dp0"

rem === Context safety guard ===
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop".
  echo Refusing to run k8s-up.bat -- this script targets local Docker Desktop k8s only.
  echo Switch with: kubectl config use-context docker-desktop
  exit /b 1
)

rem === Cluster prerequisites (MetalLB + ingress-nginx) ===
rem One-time installs (run by hand, survive k8s-down):
rem   addMetalLB.bat        -- LoadBalancer EXTERNAL-IP allocator
rem   addIngressNginx.bat   -- ingress controller binds localhost:80
kubectl get ns metallb-system >nul 2>&1
if errorlevel 1 (
  echo WARNING: metallb-system namespace not found. Run addMetalLB.bat first.
) else (
  echo --- Applying MetalLB pool config ^(metallb-config.yaml^)...
  kubectl apply -f metallb-config.yaml
)
kubectl get ns ingress-nginx >nul 2>&1
if errorlevel 1 (
  echo WARNING: ingress-nginx namespace not found. Run addIngressNginx.bat first.
)

rem === Local tag-alias safety net ===
rem For each Esquire component: read the tag from values/<svc>.yaml; if the
rem :tag image doesn't exist in the local Docker daemon, alias :latest to it.
rem Lets the kubelet pull the image when the yaml references a stamp that
rem only exists as :latest (typical for first-time install after a clean
rem `docker compose build` with no prior k8s-rebuild stamping).
for %%s in (gateward mesnie pacman aukeep backend) do (
  call :ensure_tag %%s
)

rem === Shared messaging-bus topology (the one ConfigMap every service mounts at /etc/esquire/topology.yml) ===
echo --- Installing topology...
rem === DROP THE OTHER PROFILE FIRST ===
rem The two stacks are MUTUALLY EXCLUSIVE: gateWard and Mesnie answer the same ingress hosts the gateway and the identity trio do,
rem so a machine that ran the other one keeps serving from BOTH shapes at once -- and helm never notices,
rem because they are different releases. k8s-down.bat drops them, but nothing forces anyone to run it
rem before a bring-up. "not found" is the normal case here and is ignored.
echo --- Dropping the CLASSIC releases (mutually exclusive with this stack)...
call helm uninstall esquire-gateway 2>nul
call helm uninstall esquire-biztree 2>nul
call helm uninstall esquire-enyman 2>nul
call helm uninstall esquire-keysmith 2>nul
call helm uninstall esquire-kcmaster 2>nul

call helm upgrade --install esquire-topology  charts\esquire-topology --reset-then-reuse-values --force-conflicts || exit /b 1

rem Every install below carries --reset-then-reuse-values: a bring-up must NOT silently disarm what an
rem operator switched on. Without it the release falls back to the chart default, so a stack that had
rem observability armed came back with ActiveMQ and KeyCloak dark -- the boards red while the fleet was
rem healthy, twice in one evening. The flag keeps the previous release's OWN values and still lets this
rem command's -f / --set win, so an image tag or a config change still lands. NOT --reuse-values: that one
rem also beats the CHART's new defaults (it silently dropped the grafana ingress gate).

rem === Infra ===
echo --- Installing postgres...
call helm upgrade --install esquire-infra     charts\infra\postgres  -f values\postgres.yaml --reset-then-reuse-values --force-conflicts || exit /b 1
echo --- Installing activemq...
call helm upgrade --install esquire-infra-amq charts\infra\activemq  -f values\activemq.yaml --reset-then-reuse-values --force-conflicts || exit /b 1
rem redis is the BFF's SHARED SESSION STORE -- the browser tier runs two replicas here, so a login on one
rem pod has to be visible to the other. This profile audits over a (DB triggers), b (in-process keep) and
rem c (AMQ -> auKeep); kafka belongs to the classic local stacks.
echo --- Installing redis...
call helm upgrade --install esquire-infra-redis charts\infra\redis --reset-then-reuse-values --force-conflicts || exit /b 1
echo --- Installing keycloak...
call helm upgrade --install esquire-infra-kc  charts\infra\keycloak  -f values\keycloak.yaml --reset-then-reuse-values --force-conflicts || exit /b 1

echo Waiting for postgres...
kubectl rollout status statefulset/esquire-infra-postgres -n default --timeout=120s
echo Waiting for activemq...
kubectl rollout status statefulset/esquire-infra-amq-activemq -n default --timeout=120s
echo Waiting for redis...
kubectl rollout status deployment/esquire-infra-redis -n default --timeout=60s

rem === Services (depend on postgres + amq) ===
rem No biztree here: gateWard holds the tree cache in the gate's own process, and it is installed with the
rem other KC-dependent services below (it needs KeyCloak for the JWKS its security chain fetches).
echo --- Installing pacman...
call helm upgrade --install esquire-pacman    charts\esquire-pacman    -f values\pacman.yaml    --reset-then-reuse-values --force-conflicts || exit /b 1
rem auKeep drains the audit bus. It stays its OWN workload on the compact profile: what compact composes is
rem the request path, and the audit sink is not on it.
call helm upgrade --install esquire-aukeep    charts\esquire-aukeep    -f values\aukeep.yaml    --reset-then-reuse-values --force-conflicts || exit /b 1

echo Waiting for keycloak...
kubectl rollout status statefulset/esquire-infra-kc-keycloak -n default --timeout=180s

rem === Secrets, taken from the machine ===
rem Set each once (setx <NAME> <value>) and every path uses it -- these scripts, the compose stacks, the OKE
rem scripts and the deploy workflow. Unset, the variable contributes no --set and the release keeps the value
rem it already holds; the lines below say where to get the real one. The three KeyCloak ones live on their clients in the realm; the
rem session secret is any random value, it only signs the BFF's own cookie. The realm import
rem (keycloak\import\esquire.json) is the demonstration seed those client secrets come from.
rem A MISSING SECRET MUST NOT OVERWRITE A GOOD ONE. --set beats --reset-then-reuse-values, so a placeholder
rem passed here REPLACES the value the previous install stored, and the release comes back holding a secret
rem that cannot work -- the browser answering unauthorized_client while the deploy itself reported success.
rem An unset variable therefore contributes NO --set at all, and what the release already holds stands.
set "SET_KCMASTER_ADMIN="
set "SET_GW_EXCHANGE="
set "SET_BFF_KC="
set "SET_BFF_SESSION="
if not "%KCMASTER_ADMIN_SECRET%"=="" set "SET_KCMASTER_ADMIN=--set keycloak.adminClientSecret=%KCMASTER_ADMIN_SECRET%"
if not "%GW_EXCHANGE_SECRET%"=="" set "SET_GW_EXCHANGE=--set tokenRelay.phantom.exchangeClientSecret=%GW_EXCHANGE_SECRET%"
if not "%BFF_KC_SECRET%"=="" set "SET_BFF_KC=--set keycloak.clientSecret=%BFF_KC_SECRET%"
if not "%BFF_SESSION_SECRET%"=="" set "SET_BFF_SESSION=--set session.secret=%BFF_SESSION_SECRET%"
if "%KCMASTER_ADMIN_SECRET%"=="" echo [!] KCMASTER_ADMIN_SECRET is not set -- the release keeps what it holds; on a FIRST install the identity sync will FAIL. KeyCloak: realm esquire -^> clients -^> esq-kcMaster -^> Credentials.
if "%BFF_KC_SECRET%"=="" echo [!] BFF_KC_SECRET is not set -- the release keeps what it holds; on a FIRST install the browser login will FAIL. KeyCloak: realm esquire -^> clients -^> esq-angular -^> Credentials.
if "%GW_EXCHANGE_SECRET%"=="" echo [!] GW_EXCHANGE_SECRET is not set -- the release keeps what it holds; on a FIRST install the phantom token relay will FAIL. KeyCloak: realm esquire -^> clients -^> esq-gw-exchange -^> Credentials.
if "%BFF_SESSION_SECRET%"=="" echo [!] BFF_SESSION_SECRET is not set -- the release keeps what it holds. Set any random string.

rem === KC-dependent ===
rem Mesnie carries the KeyCloak admin credential: the identity gateway drives the admin API in-process,
rem so the secret belongs to this process rather than to a separate kcMaster.
call helm upgrade --install esquire-mesnie    charts\esquire-mesnie    -f values\mesnie.yaml --reset-then-reuse-values --force-conflicts ^
  %SET_KCMASTER_ADMIN% || exit /b 1

rem gateWard: the gate AND the tree cache in one process. Dev exchange-client secret passed via --set
rem (matches realm import), as the gateway's was.
echo --- Installing gateward...
call helm upgrade --install esquire-gateward  charts\esquire-gateward  -f values\gateward.yaml --reset-then-reuse-values --force-conflicts ^
  %SET_GW_EXCHANGE% || exit /b 1

rem A longer wait than the gateway's 60s on purpose: this pod reports ready only once the tree cache has
rem loaded from the database (the readiness group carries cacheReadiness), so the wait covers a whole
rem cache load, not just a Netty bind.
echo Waiting for gateward...
kubectl rollout status statefulset/esquire-gateward -n default --timeout=180s

rem === Backend / BFF ===
rem Secrets passed via --set (same dev literals as compose.yaml + realm import).
echo --- Installing backend ^(BFF^)...
call helm upgrade --install esquire-backend   charts\esquire-backend   -f values\backend.yaml --reset-then-reuse-values --force-conflicts ^
  %SET_BFF_KC% %SET_BFF_SESSION% || exit /b 1

rem === Public ingress (applied AFTER backend is ready -- mirror of oke-up.bat) ===
echo --- Applying public ingress ^(cluster\ingress.yaml^)...
kubectl apply -f cluster\ingress.yaml

rem === Final readiness loop ===
echo Waiting for all pods to be ready...
:wait_loop
kubectl get pods -n default --no-headers
kubectl wait --for=condition=ready pod --all -n default --timeout=10s >nul 2>&1
if %ERRORLEVEL% equ 0 goto ready
echo Not ready -- retrying in 10s...
rem 'timeout' aborts under a redirected stdin (self-hosted runner) -- use Start-Sleep instead
powershell -NoProfile -Command "Start-Sleep -Seconds 10"
goto wait_loop
:ready
echo All pods ready.
kubectl get pods -n default
goto :eof

rem ---------------------------------------------------------------------------
:ensure_tag
rem Local tag-alias: read the tag from values\%1.yaml; if esquire.<img>:<tag>
rem doesn't exist locally, alias esquire.<img>:latest to it. %2 = image base
rem (defaults to %1).
set "_SVC=%~1"
set "_IMG=%~2"
if "%_IMG%"=="" set "_IMG=%_SVC%"
set "_TAG="
for /f tokens^=2^ delims^=^" %%t in ('findstr /R "^[ ]*tag:" values\%_SVC%.yaml') do set "_TAG=%%t"
if "%_TAG%"=="" (
  echo WARNING: no image.tag found in values\%_SVC%.yaml -- skipping alias.
  goto :eof
)
docker image inspect esquire.%_IMG%:%_TAG% >nul 2>&1
if errorlevel 1 (
  docker image inspect esquire.%_IMG%:latest >nul 2>&1
  if errorlevel 1 (
    echo WARNING: neither esquire.%_IMG%:%_TAG% nor :latest exists -- run k8s-rebuild.bat %_SVC% first.
  ) else (
    echo --- aliasing esquire.%_IMG%:latest -^> :%_TAG%
    docker tag esquire.%_IMG%:latest esquire.%_IMG%:%_TAG%
  )
)
goto :eof
