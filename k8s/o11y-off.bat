@echo off
rem o11y-off.bat -- disable the opt-in observability stack on local Docker Desktop
rem k8s: set tracing OFF across the app services AND remove the viewing stack (Loki +
rem Alloy + Tempo + OTel Collector + Grafana). Leaves the base stack. Mirror of
rem compose\o11y-off.bat.
setlocal
cd /d "%~dp0"
echo --- Disabling tracing on the app services...
for %%s in (gateway enyman biztree pacman keysmith kcmaster aukeep) do (
  call helm upgrade esquire-%%s charts\esquire-%%s --reset-then-reuse-values --set observability.enabled=false --set observability.metricsHistograms=false --force-conflicts
  call kubectl rollout restart statefulset esquire-%%s-%%s
)
rem The BFF (Node) is out of the loop: no histogram sub-switch, so its chart never reads
rem observability.metricsHistograms. It takes observability.enabled ONLY (mirror of o11y-on.bat).
call helm upgrade esquire-backend charts\esquire-backend --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-backend-backend
echo --- Disabling metrics on keycloak...
call helm upgrade esquire-infra-kc charts\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-infra-kc-keycloak
echo --- Disabling metrics on activemq (the JMX exporter agent is no longer loaded)...
call helm upgrade esquire-infra-amq charts\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-infra-amq-activemq
call helm uninstall esquire-infra-grafana
call helm uninstall esquire-infra-postgres-exporter
call helm uninstall esquire-infra-prometheus
call helm uninstall esquire-infra-otel-collector
call helm uninstall esquire-infra-tempo
call helm uninstall esquire-infra-alloy
call helm uninstall esquire-infra-loki
endlocal
