@echo off
rem ===========================================================================
rem Deploy the Esquire stack to OKE with prod values overrides.
rem Mirrors ../k8s/k8s-up.bat but with -f values/<chart>.yaml per chart.
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
rem Applied to postgres + the 6 services + backend + activemq (all pushed at the release
rem tag by ghcr-push.bat). Only KC keeps its own pinned tag in values\keycloak.yaml
rem (hand-rolled -- changes only when the baked realm/theme changes; bump it there).
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
if "%bff_kc_secret%"=="" set "bff_kc_secret=esq-angular-bff-dev-secret-rotate-in-prod"

rem Phantom Token Relay -- esq-gw-exchange (confidential) client secret
rem used by the gateway to authenticate to KC /token for RFC 8693 exchange.
if "%gw_exchange_secret%"=="" set "gw_exchange_secret=esq-gw-exchange-dev-secret-rotate-in-prod"

rem BFF session-cookie HMAC secret. Lower-risk than the KC client secret:
rem leak alone doesn't grant access (session IDs are server-side random,
rem session data is in MemoryStore not in the cookie). SAME default as the GHA
rem path (deploy-oke.sh) so switching deploy paths does not invalidate sessions;
rem override the env var to rotate it (or to keep sessions alive across an upgrade).
if "%bff_session_secret%"=="" set "bff_session_secret=esq-bff-session-secret"

rem esq-kcMaster KC admin service-account client secret (client_credentials -> KC admin REST API). Set the same
rem way as the BFF / gateway KC secrets: --set at install, defaulting to the realm-import value. Override the env
rem var if you rotate the esq-kcMaster secret in the production KC admin UI.
rem esq-kcMaster is the ONE published client holding realm-management realm-admin -- every
rem other service account (esq-rest, esq-hauberk, esq-hauberk-S, esq-hauberk-M) has none. Its
rem secret is therefore the realm's master key, not one credential among seven.
rem
rem NO FALLBACK AND NO SKIP, on any target, deliberately. A default reinstates the published
rem value; a skip leaves the release running the OLD value after a rotation -- and both report
rem success. Set it once in the system environment, the way mir0n_pwd already is.
if "%kcmaster_admin_secret%"=="" (
    echo [FAIL] KCMASTER_ADMIN_SECRET is not set.
    echo        esq-kcMaster is the KeyCloak realm-admin service account, and it has no
    echo        fallback on any target on purpose.
    echo        KeyCloak: realm esquire -^> clients -^> esq-kcMaster -^> Credentials.
    exit /b 1
)
rem the value is used directly below, on the helm upgrade line

set PG_PW=%mir0n_pwd%
set KC_PW=%mir0n_pwd%
set CHARTS=..\k8s\charts

rem === Infra ===
echo --- Installing postgres...
call helm upgrade --install esquire-infra %CHARTS%\infra\postgres ^
  -f values\postgres.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% || exit /b 1

echo --- Installing activemq...
call helm upgrade --install esquire-infra-amq %CHARTS%\infra\activemq ^
  -f values\activemq.yaml ^
  --set image.tag=%IMAGE_TAG% || exit /b 1

echo --- Installing keycloak...
call helm upgrade --install esquire-infra-kc %CHARTS%\infra\keycloak ^
  -f values\keycloak.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set keycloak.adminPassword=%KC_PW% || exit /b 1

echo Waiting for postgres...
kubectl rollout status statefulset/esquire-infra-postgres -n default --timeout=180s
echo Waiting for activemq...
kubectl rollout status statefulset/esquire-infra-amq-activemq -n default --timeout=180s

rem === Shared messaging-bus topology (the ConfigMap every service mounts at /etc/esquire/topology.yml as a
rem     REQUIRED volume -- it MUST exist before the services or their pods hang in ContainerCreating). Portable:
rem     the bus endpoints are in-cluster service names (esquire-infra-amq-activemq), the same on OKE; the
rem     kafka/redis buses are defined-but-unused (OKE audit is off). No -f values -- chart defaults apply. ===
echo --- Installing topology...
call helm upgrade --install esquire-topology %CHARTS%\esquire-topology || exit /b 1

rem === Services ===
echo --- Installing biztree...
call helm upgrade --install esquire-biztree %CHARTS%\esquire-biztree ^
  -f values\biztree.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% || exit /b 1

echo --- Installing enyman...
call helm upgrade --install esquire-enyman %CHARTS%\esquire-enyman ^
  -f values\enyman.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% || exit /b 1

echo --- Installing pacman...
call helm upgrade --install esquire-pacman %CHARTS%\esquire-pacman ^
  -f values\pacman.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% || exit /b 1

echo --- Installing keysmith...
call helm upgrade --install esquire-keysmith %CHARTS%\esquire-keysmith ^
  -f values\keysmith.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% || exit /b 1

echo Waiting for keycloak...
kubectl rollout status statefulset/esquire-infra-kc-keycloak -n default --timeout=240s

rem === KC-dependent ===
echo --- Installing kcmaster...
call helm upgrade --install esquire-kcmaster %CHARTS%\esquire-kcmaster ^
  -f values\kcmaster.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set keycloak.adminClientSecret=%kcmaster_admin_secret% || exit /b 1

echo --- Installing gateway...
call helm upgrade --install esquire-gateway %CHARTS%\esquire-gateway ^
  -f values\gateway.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set tokenRelay.phantom.exchangeClientSecret=%gw_exchange_secret% || exit /b 1

echo Waiting for gateway...
kubectl rollout status statefulset/esquire-gateway-gateway -n default --timeout=120s

rem === Backend (BFF) ===
rem v1.2.3: BFF replaces the standalone frontend. Serves baked SPA on /, owns
rem /auth/* (OIDC code+PKCE) and /api/* (server-to-server proxy to gateway).
rem
rem The legacy esquire-frontend chart is intentionally NOT installed here.
rem If a previous oke-up.bat installed it, the release stays orphaned (no
rem ingress traffic) -- uninstall manually after the v1.2.3 cutover is
rem confirmed stable: helm uninstall esquire-frontend
echo --- Installing backend (BFF)...
call helm upgrade --install esquire-backend %CHARTS%\esquire-backend ^
  -f values\backend.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set keycloak.clientSecret=%bff_kc_secret% ^
  --set session.secret=%bff_session_secret% || exit /b 1

echo Waiting for backend...
kubectl rollout status statefulset/esquire-backend-backend -n default --timeout=120s

rem === Public ingress ===
rem Applied AFTER backend is ready -- the v1.2.3 ingress routes / to the BFF,
rem so we don't want it live before the BFF deployment is healthy.
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
