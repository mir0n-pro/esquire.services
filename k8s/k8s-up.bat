@echo off
cd /d "%~dp0"

rem === Infra ===
call helm install esquire-infra     charts\infra\postgres
call helm install esquire-infra-amq charts\infra\activemq
call helm install esquire-infra-kc  charts\infra\keycloak

echo Waiting for postgres...
kubectl rollout status statefulset/esquire-infra-postgres -n default --timeout=120s
echo Waiting for activemq...
kubectl rollout status statefulset/esquire-infra-amq-activemq -n default --timeout=120s

rem === Services (depend on postgres + amq) ===
call helm install esquire-biztree   charts\esquire-biztree
call helm install esquire-enyman    charts\esquire-enyman
call helm install esquire-pacman    charts\esquire-pacman
call helm install esquire-keysmith  charts\esquire-keysmith

echo Waiting for keycloak...
kubectl rollout status statefulset/esquire-infra-kc-keycloak -n default --timeout=180s

rem === KC-dependent (depend on keycloak) ===
call helm install esquire-kcmaster  charts\esquire-kcmaster
call helm install esquire-gateway   charts\esquire-gateway

echo Waiting for gateway...
kubectl rollout status deployment/esquire-gateway-gateway -n default --timeout=60s

rem === Frontend (depends on gateway) ===
call helm install esquire-frontend  charts\esquire-frontend

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
