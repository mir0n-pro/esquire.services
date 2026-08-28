@echo off
cd /d "%~dp0"
rem ===========================================================================
rem Uninstall the SUPER-COMPACT Esquire stack from OKE.
rem
rem Also uninstalls the CLASSIC releases this stack replaces, deliberately: the
rem cluster is one cluster, and a release left behind is how it ends up serving
rem from two shapes at once. "not found" is the normal case here and is ignored.
rem ===========================================================================

rem === Context safety guard ===
rem Refuses to run unless kubectl context is the OKE cluster.
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this is oke-down.bat ^(production^).
  echo Refusing to run. Switch context with: kubectl config use-context context-czhlwnp27sq
  exit /b 1
)

rem Drop the public ingress first so traffic stops being routed to the
rem soon-to-be-deleted backends. Idempotent: ignore "not found".
echo --- Deleting public ingress...
kubectl delete -f cluster\ingress.yaml --ignore-not-found

call helm uninstall esquire-backend
call helm uninstall esquire-gateward

call helm uninstall esquire-mesnie
call helm uninstall esquire-pacman

rem The classic releases this stack replaces. A cluster that ran classic before still holds
rem them: the gate and the tree cache that gateWard absorbed, and the identity trio that
rem Mesnie absorbed.
call helm uninstall esquire-gateway
call helm uninstall esquire-biztree
call helm uninstall esquire-enyman
call helm uninstall esquire-keysmith
call helm uninstall esquire-kcmaster

rem auKeep is not part of this profile -- audit is option (a), DB triggers -- but a cluster
rem that ran a 5-process compact stack would hold it.
call helm uninstall esquire-aukeep

call helm uninstall esquire-infra-redis
call helm uninstall esquire-infra-kc
call helm uninstall esquire-infra-amq
call helm uninstall esquire-infra

echo.
echo Application stack uninstalled.
echo (ingress-nginx, cert-manager, ClusterIssuer, PVCs left intact.)
echo To wipe storage: kubectl delete pvc --all -n default
