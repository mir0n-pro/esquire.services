@echo off
cd /d "%~dp0"

kubectl delete -f cluster\ingress.yaml --ignore-not-found

call helm uninstall esquire-frontend
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
