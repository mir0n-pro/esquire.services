@echo off
cd /d "%~dp0"

rem === Context safety guard ===
rem Refuses to run unless kubectl context is the OKE cluster.
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this is oke-down.bat ^(production^).
  echo Refusing to run. Switch context with: kubectl config use-context context-czhlwnp27sq
  exit /b 1
)

kubectl delete -f cluster\ingress.yaml --ignore-not-found

call helm uninstall esquire-backend
rem call helm uninstall esquire-frontend
call helm uninstall esquire-gateway

call helm uninstall esquire-kcmaster
call helm uninstall esquire-keysmith
call helm uninstall esquire-pacman
call helm uninstall esquire-enyman
call helm uninstall esquire-biztree

call helm uninstall esquire-infra-kc
call helm uninstall esquire-infra-amq
call helm uninstall esquire-infra

echo.
echo Application stack uninstalled.
echo (ingress-nginx, cert-manager, ClusterIssuer, PVCs left intact.)
echo To wipe storage: kubectl delete pvc --all -n default
