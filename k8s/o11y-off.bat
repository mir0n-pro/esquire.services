@echo off
rem o11y-off.bat -- disable the opt-in observability stack on local Docker Desktop
rem k8s: set tracing OFF across the app services AND remove the viewing stack (Loki +
rem Alloy + Tempo + OTel Collector + Grafana). Leaves the base stack. Mirror of
rem compose\o11y-off.bat.
setlocal
cd /d "%~dp0"
echo --- Disabling tracing on the app services...
for %%s in (gateway enyman biztree pacman keysmith kcmaster aukeep backend) do (
  call helm upgrade esquire-%%s charts\esquire-%%s --reset-then-reuse-values --set tracing.enabled=false
  call kubectl rollout restart statefulset esquire-%%s-%%s
)
call helm uninstall esquire-infra-grafana
call helm uninstall esquire-infra-otel-collector
call helm uninstall esquire-infra-tempo
call helm uninstall esquire-infra-alloy
call helm uninstall esquire-infra-loki
endlocal
