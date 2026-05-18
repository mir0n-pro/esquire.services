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

rem === Infra ===
echo --- Installing postgres...
call helm upgrade --install esquire-infra     charts\infra\postgres  -f values\postgres.yaml || exit /b 1
echo --- Installing activemq...
call helm upgrade --install esquire-infra-amq charts\infra\activemq  -f values\activemq.yaml || exit /b 1
echo --- Installing keycloak...
call helm upgrade --install esquire-infra-kc  charts\infra\keycloak  -f values\keycloak.yaml || exit /b 1

echo Waiting for postgres...
kubectl rollout status statefulset/esquire-infra-postgres -n default --timeout=120s
echo Waiting for activemq...
kubectl rollout status statefulset/esquire-infra-amq-activemq -n default --timeout=120s

rem === Services (depend on postgres + amq) ===
echo --- Installing biztree...
call helm upgrade --install esquire-biztree   charts\esquire-biztree   -f values\biztree.yaml   || exit /b 1
echo --- Installing enyman...
call helm upgrade --install esquire-enyman    charts\esquire-enyman    -f values\enyman.yaml    || exit /b 1
echo --- Installing pacman...
call helm upgrade --install esquire-pacman    charts\esquire-pacman    -f values\pacman.yaml    || exit /b 1
echo --- Installing keysmith...
call helm upgrade --install esquire-keysmith  charts\esquire-keysmith  -f values\keysmith.yaml  || exit /b 1

echo Waiting for keycloak...
kubectl rollout status statefulset/esquire-infra-kc-keycloak -n default --timeout=180s

rem === KC-dependent ===
echo --- Installing kcmaster...
call helm upgrade --install esquire-kcmaster  charts\esquire-kcmaster  -f values\kcmaster.yaml  || exit /b 1

rem Gateway: dev exchange-client secret passed via --set (matches realm import).
echo --- Installing gateway...
call helm upgrade --install esquire-gateway   charts\esquire-gateway   -f values\gateway.yaml ^
  --set tokenRelay.phantom.exchangeClientSecret=esq-gw-exchange-dev-secret-rotate-in-prod || exit /b 1

echo Waiting for gateway...
kubectl rollout status deployment/esquire-gateway-gateway -n default --timeout=60s

rem === Backend / BFF ===
rem Secrets passed via --set (same dev literals as compose.yaml + realm import).
echo --- Installing backend ^(BFF^)...
call helm upgrade --install esquire-backend   charts\esquire-backend   -f values\backend.yaml ^
  --set keycloak.clientSecret=esq-angular-bff-dev-secret-rotate-in-prod ^
  --set session.secret=esq-bff-dev-session-secret-rotate-in-prod || exit /b 1

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
timeout /t 10 /nobreak >nul
goto wait_loop
:ready
echo All pods ready.
kubectl get pods -n default
goto :eof

rem ---------------------------------------------------------------------------
:ensure_tag
rem Local tag-alias: read the tag from values\%1.yaml; if esquire.%1:<tag>
rem doesn't exist locally, alias esquire.%1:latest to it.
set "_SVC=%~1"
set "_TAG="
for /f tokens^=2^ delims^=^" %%t in ('findstr /R "^[ ]*tag:" values\%_SVC%.yaml') do set "_TAG=%%t"
if "%_TAG%"=="" (
  echo WARNING: no image.tag found in values\%_SVC%.yaml -- skipping alias.
  goto :eof
)
docker image inspect esquire.%_SVC%:%_TAG% >nul 2>&1
if errorlevel 1 (
  docker image inspect esquire.%_SVC%:latest >nul 2>&1
  if errorlevel 1 (
    echo WARNING: neither esquire.%_SVC%:%_TAG% nor :latest exists -- run k8s-rebuild.bat %_SVC% first.
  ) else (
    echo --- aliasing esquire.%_SVC%:latest -^> :%_TAG%
    docker tag esquire.%_SVC%:latest esquire.%_SVC%:%_TAG%
  )
)
goto :eof
