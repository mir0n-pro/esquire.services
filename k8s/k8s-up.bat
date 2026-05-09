@echo off
cd /d "%~dp0"

rem === Context safety guard ===
rem Refuses to run if kubectl context is anything other than docker-desktop.
rem Prevents the 2026-05-06 disaster where helm install hit OKE production.
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop".
  echo Refusing to run k8s-up.bat -- this script targets local Docker Desktop k8s only.
  echo Switch with: kubectl config use-context docker-desktop
  exit /b 1
)

rem === Fresh image tags ===
rem Docker Desktop's kubelet caches digests per tag. With chart default
rem image.tag=latest + imagePullPolicy=IfNotPresent, helm install picks up
rem whatever digest the kubelet last cached for :latest, NOT what's in the
rem local Docker daemon now. To force the kubelet to see the freshly-built
rem image, we retag every Esquire image with a unique YYMM.DDHH tag here
rem and pass --set image.tag=<tag> to each helm install.
rem
rem Tag granularity: YYMM.DDHH (matches release_notes.txt version stamps).
rem If any Esquire image already carries the base tag from an earlier build
rem in this hour, the kubelet has cached its digest -- we'd hit the same
rem :latest trap. In that case, append minutes (YYMM.DDHHmm) for a tag the
rem kubelet has never resolved.
setlocal enabledelayedexpansion
for /f %%t in ('powershell -nop -c "Get-Date -Format yyMM.ddHH"') do set "BASE_TS=%%t"
for /f %%m in ('powershell -nop -c "Get-Date -Format mm"') do set "MM=%%m"
set "TS=%BASE_TS%"
for %%s in (gateway biztree enyman pacman keysmith kcmaster backend) do (
  docker image inspect esquire.%%s:%BASE_TS% >nul 2>&1
  if not errorlevel 1 set "TS=%BASE_TS%!MM!"
)
echo === using image tag %TS% for all Esquire services ===
for %%s in (gateway biztree enyman pacman keysmith kcmaster backend) do (
  docker image inspect esquire.%%s:latest >nul 2>&1
  if errorlevel 1 (
    echo WARNING: esquire.%%s:latest not found in Docker -- helm install will fail. Run k8s-rebuild.bat first.
  ) else (
    docker tag esquire.%%s:latest esquire.%%s:%TS%
  )
)
endlocal & set "TS=%TS%"

rem === Infra ===
call helm install esquire-infra     charts\infra\postgres
call helm install esquire-infra-amq charts\infra\activemq
call helm install esquire-infra-kc  charts\infra\keycloak

echo Waiting for postgres...
kubectl rollout status statefulset/esquire-infra-postgres -n default --timeout=120s
echo Waiting for activemq...
kubectl rollout status statefulset/esquire-infra-amq-activemq -n default --timeout=120s

rem === Services (depend on postgres + amq) ===
rem springProfilesActive defaults baked into each chart's values.yaml:
rem   biztree   = console,dev-postgres,cache-h2
rem   enyman    = console,dev-postgres
rem   pacman    = console,dev-postgres
rem   keysmith  = console,dev-postgres
rem   kcmaster  = console
rem   gateway   = console
rem (OKE values/<chart>.yaml override these for production.)
call helm install esquire-biztree   charts\esquire-biztree   --set image.tag=%TS%
call helm install esquire-enyman    charts\esquire-enyman    --set image.tag=%TS%
call helm install esquire-pacman    charts\esquire-pacman    --set image.tag=%TS%
call helm install esquire-keysmith  charts\esquire-keysmith  --set image.tag=%TS%

echo Waiting for keycloak...
kubectl rollout status statefulset/esquire-infra-kc-keycloak -n default --timeout=180s

rem === KC-dependent (depend on keycloak) ===
call helm install esquire-kcmaster  charts\esquire-kcmaster  --set image.tag=%TS%
call helm install esquire-gateway   charts\esquire-gateway   --set image.tag=%TS%

echo Waiting for gateway...
kubectl rollout status deployment/esquire-gateway-gateway -n default --timeout=60s

rem === Backend / BFF (depends on gateway + keycloak; serves the SPA + /api + /auth) ===
rem KC_CLIENT_SECRET must match the esq-angular client secret in the realm import.
rem SESSION_SECRET signs the session cookie -- rotate in prod.
rem keycloak.issuer / publicBaseUrl come from chart defaults (host.docker.internal).
call helm install esquire-backend charts\esquire-backend ^
  --set image.tag=%TS% ^
  --set keycloak.clientSecret=esq-angular-bff-dev-secret-rotate-in-prod ^
  --set session.secret=esq-bff-dev-session-secret-rotate-in-prod ^
  --set nodeEnv=development

rem === Final readiness loop ===
echo Waiting for all pods to be ready...
:wait_loop
kubectl get pods -n default --no-headers
kubectl wait --for=condition=ready pod --all -n default --timeout=10s >nul 2>&1
if %ERRORLEVEL% equ 0 goto ready
echo Not ready -- retrying in 10s...
timeout /t 10 /nobreak >nul
goto wait_loop
:ready
echo All pods ready.
kubectl get pods -n default
