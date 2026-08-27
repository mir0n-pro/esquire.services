@echo off
rem ===========================================================================
rem Deploy the SUPER-COMPACT Esquire stack to OKE with prod values overrides.
rem
rem Four application processes instead of seven -- Mesnie (enyMan + keySmith +
rem the identity work), gateWard (the gate + the bizTree cache), pacMan and the
rem BFF -- so 7 application pods where classic runs 13.
rem
rem SUPER-COMPACT = the compact composition with audit on option (a), DB triggers:
rem no auKeep process, no audit bus traffic, the audit stack out of the application
rem entirely. Zero code, one environment variable -- see values\mesnie.yaml.
rem
rem Charts are BORROWED from ..\k8s-compact\charts, exactly as ..\k8s-oci\oke-up.bat
rem borrows ..\k8s\charts. This folder owns only the prod values.
rem
rem Reads the postgres + Keycloak admin password from the %mir0n_pwd% system
rem env var. Same password used for both (single secret to rotate).
rem
rem Usage: oke-up.bat
rem ===========================================================================

setlocal
cd /d "%~dp0"

rem === Context safety guard ===
rem Refuses to run unless kubectl context is the OKE cluster.
rem Prevents pointing this prod-bound script at local Docker Desktop k8s.
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this is oke-up.bat ^(production^).
  echo Refusing to run. Switch context with: kubectl config use-context context-czhlwnp27sq
  exit /b 1
)

if "%mir0n_pwd%"=="" (
  echo ERROR: mir0n_pwd env var not set. Set the postgres + Keycloak admin password:
  echo   set mir0n_pwd=^<your-password^>
  exit /b 1
)

rem Release image tag pushed to GHCR (multi-arch amd64+arm64 via ghcr-push.bat).
rem Applied to postgres + the 3 services + backend + activemq. Only KC keeps its own
rem pinned tag in values\keycloak.yaml (hand-rolled -- changes only when the baked
rem realm/theme changes; bump it there).
if "%image_tag%"=="" (
  echo ERROR: image_tag env var not set. Set the release tag pushed to GHCR:
  echo   set image_tag=^<vMajor.Minor.Micro-YYMM.DDHH^>
  exit /b 1
)
set IMAGE_TAG=%image_tag%

rem esq-angular client secret. Defaults to the realm-import value (same one
rem compose.yaml and k8s-up.bat already use as a literal). Override the env
rem var when you rotate the client secret in the production KC admin UI:
rem   set bff_kc_secret=^<rotated-value^>
rem A MISSING SECRET MUST NOT OVERWRITE A GOOD ONE. --set beats --reset-then-reuse-values, so a sentinel
rem passed here REPLACES the value the previous install stored and the release comes back holding a
rem credential that cannot authenticate, while the deploy reports success. An unset variable therefore
rem contributes NO --set at all and what the release already holds stands.
set "SET_BFF_KC="
set "SET_GW_EXCHANGE="
set "SET_BFF_SESSION="
set "SET_KCMASTER_ADMIN="
if not "%bff_kc_secret%"=="" set "SET_BFF_KC=--set keycloak.clientSecret=%bff_kc_secret%"
if "%bff_kc_secret%"=="" echo [!] BFF_KC_SECRET is not set -- the release keeps what it holds; on a FIRST install the browser login will FAIL. KeyCloak: realm esquire -^> clients -^> esq-angular -^> Credentials.

rem Phantom Token Relay -- esq-gw-exchange (confidential) client secret used by the
rem gate to authenticate to KC /token for RFC 8693 exchange. Both relay allowlists
rem are EMPTY on OKE (values\gateward.yaml), so this is set but dormant.
if not "%gw_exchange_secret%"=="" set "SET_GW_EXCHANGE=--set tokenRelay.phantom.exchangeClientSecret=%gw_exchange_secret%"
if "%gw_exchange_secret%"=="" echo [!] GW_EXCHANGE_SECRET is not set -- the release keeps what it holds; on a FIRST install the phantom token relay will FAIL. KeyCloak: realm esquire -^> clients -^> esq-gw-exchange -^> Credentials.

rem BFF session-cookie HMAC secret. Lower-risk than the KC client secret:
rem leak alone does not grant access (session IDs are server-side random,
rem session data is in MemoryStore not in the cookie).
rem
rem THE TWO DEPLOY PATHS FALL BACK TO DIFFERENT VALUES, on purpose and with different
rem jobs: this one substitutes a sentinel that cannot work and says so, because a person
rem is watching; deploy-oke.sh substitutes the published development value, because the
rem pipeline has to bring the demonstration up with nothing configured. Set
rem BFF_SESSION_SECRET on BOTH to the same value if you switch paths and want sessions
rem to survive -- unset, the signing key changes with the path and every session is
rem invalidated.
if not "%bff_session_secret%"=="" set "SET_BFF_SESSION=--set session.secret=%bff_session_secret%"
if "%bff_session_secret%"=="" echo [!] BFF_SESSION_SECRET is not set -- the release keeps what it holds; on a FIRST install sessions are signed with a known value. Set any random string.

rem esq-kcMaster KC admin service-account client secret (client_credentials -> KC admin
rem REST API). Mesnie carries the identity work in process, so it is Mesnie that takes
rem this now -- the same secret, handed to one workload instead of a kcMaster of its own.
if not "%kcmaster_admin_secret%"=="" set "SET_KCMASTER_ADMIN=--set keycloak.adminClientSecret=%kcmaster_admin_secret%"
if "%kcmaster_admin_secret%"=="" echo [!] KCMASTER_ADMIN_SECRET is not set -- the release keeps what it holds; on a FIRST install the identity sync will FAIL to authenticate. Get the value from KeyCloak: realm esquire -^> clients -^> esq-kcMaster -^> Credentials.

set PG_PW=%mir0n_pwd%
set KC_PW=%mir0n_pwd%
set CHARTS=..\k8s-compact\charts

rem === Drop the shape this one replaces, FIRST ===
rem One cluster, one shape. A classic release left running is how a cluster ends up serving from
rem two shapes at once -- and on the Always-Free tier there is no room for 13 classic pods and 7
rem compact ones side by side, so the old ones have to go before the new ones ask for capacity.
rem
rem THIS MEANS THE SWITCH IS NOT ZERO-DOWNTIME. The site is down from here until the BFF is
rem ready again. The tier decides that, not the design: freeing the room first is the only
rem order that fits. "not found" is the normal case on a cluster already running compact.
echo --- Removing the classic releases this stack replaces...
call helm uninstall esquire-gateway  2>nul
call helm uninstall esquire-biztree  2>nul
call helm uninstall esquire-enyman   2>nul
call helm uninstall esquire-keysmith 2>nul
call helm uninstall esquire-kcmaster 2>nul
rem auKeep belongs to the 5-process compact profile, not to this one: audit here is option (a).
call helm uninstall esquire-aukeep   2>nul

rem === Infra ===
rem INSTALL when absent, do NOT re-roll on a routine deploy -- the same guard deploy-oke.sh carries, and for
rem the same reason it states: all three images are rebuilt and pushed per release, so passing IMAGE_TAG
rem changes their spec EVERY run and helm re-rolls them. RE-ROLLING THE BROKER IS WHAT BREAKS DEPLOYS -- a
rem bounce drops every app pod's messaging bus and the services do NOT self-heal, so a mid-deploy roll leaves
rem the not-yet-rolled services wedged at readiness 503 and stalls their StatefulSet rollouts.
rem
rem Set DEPLOY_INFRA=true ONLY when a postgres / activemq / keycloak IMAGE itself changed (schema, realm,
rem broker config) -- and expect to kick the app pods afterwards (see the OKE runbook).
if not defined DEPLOY_INFRA set "DEPLOY_INFRA=false"

call :need_infra esquire-infra
if not defined NEED_INFRA goto skip_postgres
echo --- Installing postgres...
rem --force-conflicts: helm 4 applies SERVER-SIDE, and anything that scales OUTSIDE helm (the perf
rem matrix does) owns .spec.replicas -- an upgrade that then sets replicas is REFUSED. Seen on OKE 08-19.
call helm upgrade --install esquire-infra %CHARTS%\infra\postgres --force-conflicts ^
  -f values\postgres.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% || exit /b 1
:skip_postgres

call :need_infra esquire-infra-amq
if not defined NEED_INFRA goto skip_activemq
echo --- Installing activemq...
call helm upgrade --install esquire-infra-amq %CHARTS%\infra\activemq --force-conflicts ^
  -f values\activemq.yaml ^
  --set image.tag=%IMAGE_TAG% || exit /b 1
:skip_activemq

call :need_infra esquire-infra-kc
if not defined NEED_INFRA goto skip_keycloak
echo --- Installing keycloak...
call helm upgrade --install esquire-infra-kc %CHARTS%\infra\keycloak --force-conflicts ^
  -f values\keycloak.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set keycloak.adminPassword=%KC_PW% || exit /b 1
:skip_keycloak

rem The BFF session store, pinned to the infra node (values\redis.yaml). It is what lets the BFF run
rem TWO replicas here: without a shared store the in-memory one would round-robin-split logins.
rem Not an audit sink on this profile -- audit is option (a), DB triggers.
echo --- Installing redis (BFF session store)...
call helm upgrade --install esquire-infra-redis %CHARTS%\infra\redis --force-conflicts ^
  -f values\redis.yaml || exit /b 1

echo Waiting for postgres...
kubectl rollout status statefulset/esquire-infra-postgres -n default --timeout=180s
echo Waiting for activemq...
kubectl rollout status statefulset/esquire-infra-amq-activemq -n default --timeout=180s

rem === Shared messaging-bus topology (the ConfigMap every service mounts at
rem     /etc/esquire/topology.yml as a REQUIRED volume -- it MUST exist before the services or
rem     their pods hang in ContainerCreating). Portable: the bus endpoints are in-cluster
rem     service names (esquire-infra-amq-activemq), the same on OKE. It also defines the
rem     audit-off DISABLED bus this profile points its audit ref at. ===
rem
rem This folder supplies its OWN topology: TWO buses, because super-compact references exactly two --
rem esquire.entity, and audit-off for the audit ref. No esquire.kc (Mesnie serves identity in process)
rem and no audit-c/ck/d/dk (no auKeep, audit is in the database). --set-file overrides the chart's file.
echo --- Installing topology (super-compact: entity + audit-off)...
call helm upgrade --install esquire-topology %CHARTS%\esquire-topology --force-conflicts ^
  --set-file topologyContent=esquire-topology.yml || exit /b 1

rem === Services ===
rem No biztree release and no enyman/keysmith/kcmaster releases: gateWard answers the tree
rem routes from its own cache, and Mesnie answers for enyMan, keySmith and the identity work
rem in one workload.
echo --- Installing mesnie...
call helm upgrade --install esquire-mesnie %CHARTS%\esquire-mesnie --force-conflicts ^
  -f values\mesnie.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% ^
  %SET_KCMASTER_ADMIN% || exit /b 1

echo --- Installing pacman...
call helm upgrade --install esquire-pacman %CHARTS%\esquire-pacman --force-conflicts ^
  -f values\pacman.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% || exit /b 1

echo Waiting for keycloak...
kubectl rollout status statefulset/esquire-infra-kc-keycloak -n default --timeout=240s

rem === The gate ===
rem gateWard loads the whole tree from the database at start, so it comes up after postgres is
rem serving; its readiness gate is what keeps a cold cache from answering.
echo --- Installing gateward...
call helm upgrade --install esquire-gateward %CHARTS%\esquire-gateward --force-conflicts ^
  -f values\gateward.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% ^
  %SET_GW_EXCHANGE% || exit /b 1

echo Waiting for gateward...
kubectl rollout status statefulset/esquire-gateward -n default --timeout=180s

rem === Backend (BFF) ===
rem Serves the baked SPA on /, owns /auth/* (OIDC code+PKCE) and /api/* (server-to-server proxy
rem to the gate). It reaches gateWard by the chart-default service name -- values\backend.yaml
rem carries NO gateway block on purpose.
echo --- Installing backend (BFF)...
call helm upgrade --install esquire-backend %CHARTS%\esquire-backend --force-conflicts ^
  -f values\backend.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  %SET_BFF_KC% %SET_BFF_SESSION% || exit /b 1

echo Waiting for backend...
kubectl rollout status statefulset/esquire-backend -n default --timeout=120s

rem === Public ingress ===
rem Applied AFTER backend is ready -- the ingress routes / to the BFF, so we do not want it live
rem before the BFF deployment is healthy. api.esquire.mir0n.pro points at gateWard here.
echo --- Applying public ingress...
kubectl apply -f cluster\ingress.yaml || exit /b 1

rem === Wait for everything ===
echo.
echo Waiting for all pods Ready...
:wait_loop
kubectl wait --for=condition=ready pod --all -n default --timeout=15s >nul 2>&1
if %ERRORLEVEL% equ 0 goto ready
kubectl get pods -n default --no-headers
echo Not ready -- retrying in 15s...
timeout /t 15 /nobreak >nul
goto wait_loop
:ready

echo.
echo --- All pods ready. Cert status:
kubectl get certificate -A
echo.
echo --- Ingress:
kubectl get ingress -A
echo.
echo Open https://esquire.mir0n.pro

rem End the main flow HERE. Without it a successful run walks straight into :need_infra below and
rem runs the subroutine with no argument.
goto :eof

:need_infra
rem %1 = helm release. Sets NEED_INFRA when it must be installed: either it is absent (first deploy) or
rem DEPLOY_INFRA=true was asked for. Otherwise it is left running, and says so.
set "NEED_INFRA=1"
if /i "%DEPLOY_INFRA%"=="true" exit /b 0
helm status %~1 >nul 2>&1
if errorlevel 1 exit /b 0
set "NEED_INFRA="
echo --- infra: %~1 left running (DEPLOY_INFRA=false -- not re-rolled, keeps the app buses up)
exit /b 0
