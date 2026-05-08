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

rem esq-angular client secret. Defaults to the realm-import value (same one
rem compose.yaml and k8s-up.bat already use as a literal). Override the env
rem var when you rotate the client secret in the production KC admin UI:
rem   set bff_kc_secret=^<rotated-value^>
if "%bff_kc_secret%"=="" set "bff_kc_secret=esq-angular-bff-dev-secret-rotate-in-prod"

rem BFF session-cookie HMAC secret. Lower-risk than the KC client secret:
rem leak alone doesn't grant access (session IDs are server-side random,
rem session data is in MemoryStore not in the cookie). Defaulted per-release
rem so it naturally rotates on every cutover; override the env var only if
rem you want to keep existing sessions alive across an upgrade.
if "%bff_session_secret%"=="" set "bff_session_secret=esq-bff-v1.2.3-session-secret"

set PG_PW=%mir0n_pwd%
set KC_PW=%mir0n_pwd%
set CHARTS=..\k8s\charts

rem === Infra ===
echo --- Installing postgres...
call helm upgrade --install esquire-infra %CHARTS%\infra\postgres ^
  -f values\postgres.yaml ^
  --set db.password=%PG_PW% || exit /b 1

echo --- Installing activemq...
call helm upgrade --install esquire-infra-amq %CHARTS%\infra\activemq ^
  -f values\activemq.yaml || exit /b 1

echo --- Installing keycloak...
call helm upgrade --install esquire-infra-kc %CHARTS%\infra\keycloak ^
  -f values\keycloak.yaml ^
  --set keycloak.adminPassword=%KC_PW% || exit /b 1

echo Waiting for postgres...
kubectl rollout status statefulset/esquire-infra-postgres -n default --timeout=180s
echo Waiting for activemq...
kubectl rollout status statefulset/esquire-infra-amq-activemq -n default --timeout=180s

rem === Services ===
echo --- Installing biztree...
call helm upgrade --install esquire-biztree %CHARTS%\esquire-biztree ^
  -f values\biztree.yaml ^
  --set db.password=%PG_PW% || exit /b 1

echo --- Installing enyman...
call helm upgrade --install esquire-enyman %CHARTS%\esquire-enyman ^
  -f values\enyman.yaml ^
  --set db.password=%PG_PW% || exit /b 1

echo --- Installing pacman...
call helm upgrade --install esquire-pacman %CHARTS%\esquire-pacman ^
  -f values\pacman.yaml ^
  --set db.password=%PG_PW% || exit /b 1

echo --- Installing keysmith...
call helm upgrade --install esquire-keysmith %CHARTS%\esquire-keysmith ^
  -f values\keysmith.yaml ^
  --set db.password=%PG_PW% || exit /b 1

echo Waiting for keycloak...
kubectl rollout status statefulset/esquire-infra-kc-keycloak -n default --timeout=240s

rem === KC-dependent ===
echo --- Installing kcmaster...
call helm upgrade --install esquire-kcmaster %CHARTS%\esquire-kcmaster ^
  -f values\kcmaster.yaml || exit /b 1

echo --- Installing gateway...
call helm upgrade --install esquire-gateway %CHARTS%\esquire-gateway ^
  -f values\gateway.yaml || exit /b 1

echo Waiting for gateway...
kubectl rollout status deployment/esquire-gateway-gateway -n default --timeout=120s

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
  --set keycloak.clientSecret=%bff_kc_secret% ^
  --set session.secret=%bff_session_secret% || exit /b 1

echo Waiting for backend...
kubectl rollout status deployment/esquire-backend-backend -n default --timeout=120s

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
