@echo off
cd /d "%~dp0"

rem === Context safety guard ===
rem Refuses to run if kubectl context is anything other than docker-desktop.
rem Prevents the 2026-05-06 disaster where helm uninstall hit OKE production.
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop".
  echo Refusing to run k8s-down.bat -- this script targets local Docker Desktop k8s only.
  echo Switch with: kubectl config use-context docker-desktop
  exit /b 1
)

rem Drop the public ingress first so traffic stops being routed to the
rem soon-to-be-deleted backends. Idempotent: ignore "not found".
echo --- Deleting public ingress...
kubectl delete -f cluster\ingress.yaml --ignore-not-found=true

call helm uninstall esquire-backend
call helm uninstall esquire-gateway

call helm uninstall esquire-xxrod
call helm uninstall esquire-kcmaster
call helm uninstall esquire-keysmith
call helm uninstall esquire-pacman
call helm uninstall esquire-enyman
call helm uninstall esquire-biztree

call helm uninstall esquire-infra-kc
call helm uninstall esquire-infra-amq
call helm uninstall esquire-infra

rem Note: MetalLB + ingress-nginx are NOT uninstalled. They're cluster-wide
rem prerequisites that survive Esquire teardown (managed by addMetalLB.bat
rem + addIngressNginx.bat one-time installs).
