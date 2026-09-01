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
for %%s in (gateway biztree enyman pacman keysmith kcmaster backend) do (
  call :ensure_tag %%s
)
call :ensure_tag aukeep

rem === Shared messaging-bus topology (the one ConfigMap every service mounts at /etc/esquire/topology.yml) ===
echo --- Installing topology...
rem === DROP THE OTHER PROFILE FIRST ===
rem The two stacks are MUTUALLY EXCLUSIVE: the gateway and the identity trio answer the same ingress hosts gateWard and Mesnie do,
rem so a machine that ran the other one keeps serving from BOTH shapes at once -- and helm never notices,
rem because they are different releases. k8s-down.bat drops them, but nothing forces anyone to run it
rem before a bring-up. "not found" is the normal case here and is ignored.
echo --- Dropping the COMPACT releases (mutually exclusive with this stack)...
call helm uninstall esquire-gateward 2>nul
call helm uninstall esquire-mesnie 2>nul

rem Every install below carries --reset-then-reuse-values: a bring-up must NOT silently disarm what an
rem operator switched on. Without it the release falls back to the chart default, so a stack that had
rem observability armed comes back with its services dark -- the boards red while the fleet is healthy.
rem The flag keeps the previous release OWN values and still lets this command -f / --set win, so an
rem image tag or a config change still lands.
call helm upgrade --install esquire-topology  charts\esquire-topology --reset-then-reuse-values --force-conflicts || exit /b 1

rem === Infra ===
echo --- Installing postgres...
call helm upgrade --install esquire-infra     charts\infra\postgres  -f values\postgres.yaml --reset-then-reuse-values --force-conflicts || exit /b 1
echo --- Installing activemq...
call helm upgrade --install esquire-infra-amq charts\infra\activemq  -f values\activemq.yaml --reset-then-reuse-values --force-conflicts || exit /b 1
rem kafka + redis back the audit (ck)/(d)/(dk) sinks -- the topology defines them so any sink is selectable
rem on local/dev k8s. (OKE ships neither: it audits via DB triggers.)
echo --- Installing kafka...
call helm upgrade --install esquire-infra-kafka charts\infra\kafka --reset-then-reuse-values --force-conflicts || exit /b 1
echo --- Installing redis...
call helm upgrade --install esquire-infra-redis charts\infra\redis --reset-then-reuse-values --force-conflicts || exit /b 1
echo --- Installing keycloak...
call helm upgrade --install esquire-infra-kc  charts\infra\keycloak  -f values\keycloak.yaml --reset-then-reuse-values --force-conflicts || exit /b 1

echo Waiting for postgres...
kubectl rollout status statefulset/esquire-infra-postgres -n default --timeout=120s
echo Waiting for activemq...
kubectl rollout status statefulset/esquire-infra-amq-activemq -n default --timeout=120s
echo Waiting for kafka...
kubectl rollout status deployment/esquire-infra-kafka -n default --timeout=150s
echo Waiting for redis...
kubectl rollout status deployment/esquire-infra-redis -n default --timeout=60s

rem === Services (depend on postgres + amq) ===
echo --- Installing biztree...
call helm upgrade --install esquire-biztree   charts\esquire-biztree   -f values\biztree.yaml   --reset-then-reuse-values --force-conflicts || exit /b 1
echo --- Installing enyman...
call helm upgrade --install esquire-enyman    charts\esquire-enyman    -f values\enyman.yaml    --reset-then-reuse-values --force-conflicts || exit /b 1
echo --- Installing pacman...
call helm upgrade --install esquire-pacman    charts\esquire-pacman    -f values\pacman.yaml    --reset-then-reuse-values --force-conflicts || exit /b 1
echo --- Installing keysmith...
call helm upgrade --install esquire-keysmith  charts\esquire-keysmith  -f values\keysmith.yaml  --reset-then-reuse-values --force-conflicts || exit /b 1
echo --- Installing aukeep ^(audit consumer, option c default^)...
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
rem esq-kcMaster is the ONE published client holding realm-management realm-admin -- every
rem other service account (esq-rest, esq-hauberk, esq-hauberk-S, esq-hauberk-M) has none. Its
rem secret is therefore the realm's master key, not one credential among seven.
rem
rem NO FALLBACK AND NO SKIP, on any target, deliberately. A default reinstates the published
rem value; a skip leaves the release running the OLD value after a rotation -- and both report
rem success. Set it once in the system environment, the way mir0n_pwd already is.
if "%KCMASTER_ADMIN_SECRET%"=="" (
    echo [FAIL] KCMASTER_ADMIN_SECRET is not set.
    echo        esq-kcMaster is the KeyCloak realm-admin service account, and it has no
    echo        fallback on any target on purpose.
    echo        KeyCloak: realm esquire -^> clients -^> esq-kcMaster -^> Credentials.
    exit /b 1
)
set "SET_KCMASTER_ADMIN=--set keycloak.adminClientSecret=%KCMASTER_ADMIN_SECRET%"
if not "%GW_EXCHANGE_SECRET%"=="" set "SET_GW_EXCHANGE=--set tokenRelay.phantom.exchangeClientSecret=%GW_EXCHANGE_SECRET%"
if not "%BFF_KC_SECRET%"=="" set "SET_BFF_KC=--set keycloak.clientSecret=%BFF_KC_SECRET%"
if not "%BFF_SESSION_SECRET%"=="" set "SET_BFF_SESSION=--set session.secret=%BFF_SESSION_SECRET%"
if "%BFF_KC_SECRET%"=="" echo [!] BFF_KC_SECRET is not set -- the release keeps what it holds; on a FIRST install the browser login will FAIL. KeyCloak: realm esquire -^> clients -^> esq-angular -^> Credentials.
if "%GW_EXCHANGE_SECRET%"=="" echo [!] GW_EXCHANGE_SECRET is not set -- the release keeps what it holds; on a FIRST install the phantom token relay will FAIL. KeyCloak: realm esquire -^> clients -^> esq-gw-exchange -^> Credentials.
if "%BFF_SESSION_SECRET%"=="" echo [!] BFF_SESSION_SECRET is not set -- the release keeps what it holds. Set any random string.

rem === KC-dependent ===
echo --- Installing kcmaster...
call helm upgrade --install esquire-kcmaster  charts\esquire-kcmaster  -f values\kcmaster.yaml --reset-then-reuse-values --force-conflicts ^
  %SET_KCMASTER_ADMIN% || exit /b 1

rem Gateway: dev exchange-client secret passed via --set (matches realm import).
echo --- Installing gateway...
call helm upgrade --install esquire-gateway   charts\esquire-gateway   -f values\gateway.yaml --reset-then-reuse-values --force-conflicts ^
  %SET_GW_EXCHANGE% || exit /b 1

echo Waiting for gateway...
kubectl rollout status statefulset/esquire-gateway-gateway -n default --timeout=60s

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
