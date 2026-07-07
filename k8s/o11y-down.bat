@echo off
rem o11y-down.bat -- remove the opt-in observability stack (Loki + Alloy + Grafana)
rem from local Docker Desktop k8s. Leaves the base Esquire stack untouched.
setlocal
cd /d "%~dp0"
call helm uninstall esquire-infra-grafana
call helm uninstall esquire-infra-alloy
call helm uninstall esquire-infra-loki
endlocal
