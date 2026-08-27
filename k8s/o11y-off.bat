@echo off
rem o11y-off.bat -- disable the opt-in observability stack on local Docker Desktop
rem k8s: set tracing OFF across the app services AND remove the viewing stack (Loki +
rem Alloy + Tempo + OTel Collector + Grafana). Leaves the base stack. Mirror of
rem compose\o11y-off.bat.
setlocal
cd /d "%~dp0"

rem The context guard every mutating script in this tree carries -- and the one this script did NOT, while
rem being the only one that UNINSTALLS. Seven helm uninstalls below, and the OKE observability releases
rem carry the same names, so with the context left on OKE (the normal state after any oke-*.bat session)
rem this tore down production observability. Same reason k8s-down.bat states: prevents the 2026-05-06
rem disaster where helm uninstall hit OKE production.
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop". Refusing to run.
  exit /b 1
)
rem INFRA FIRST, APPS AFTER -- the same order the arm scripts use. A broker roll INTERRUPTS every app pod's
rem messagingBus: the transport goes DOWN and the failover: wrapper on the endpoint brings it back on its own,
rem measured at about 20s on local k8s (DOWN 21:44:01 -> UP 21:44:21). TopologyDriftGuardTest is what keeps that
rem wrapper on every target. So the cost of a roll is a bus WINDOW, not a dead bus. Infra first puts that window
rem beside the app restart below instead of after everything has settled, and it costs no extra restart.
echo --- Disabling metrics on keycloak...
call helm upgrade esquire-infra-kc charts\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-infra-kc-keycloak
echo --- Disabling metrics on activemq (the JMX exporter agent is no longer loaded)...
call helm upgrade esquire-infra-amq charts\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-infra-amq-activemq

echo --- Disabling tracing on the app services...
for %%s in (gateway enyman biztree pacman keysmith kcmaster aukeep) do (
rem THE SHIPPED LEVELS ARE RESTATED HERE, for the reason o11y-on.bat gives: --reset-then-reuse-values
rem KEEPS whatever a previous arm set. o11y-log-off sets levelMir0n/Develop/Msg to OFF, so a disarm that
rem named only the observability switches left the everyday stack with the application logger SILENT --
rem and Loki torn down, so nothing witnessed it. An arm must state every knob it owns; so must a disarm.
  call helm upgrade esquire-%%s charts\esquire-%%s --reset-then-reuse-values --set observability.enabled=false --set observability.metricsHistograms=false --set logging.levelMir0n=DEBUG --set logging.levelDevelop=DEBUG --set logging.levelMsg=DEBUG --set logging.levelAmq=INFO --set logging.levelJms=INFO --force-conflicts
  call kubectl rollout restart statefulset esquire-%%s-%%s
)
rem The BFF (Node) is out of the loop: no histogram sub-switch, so its chart never reads
rem observability.metricsHistograms. It takes observability.enabled ONLY (mirror of o11y-on.bat).
call helm upgrade esquire-backend charts\esquire-backend --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-backend-backend
call helm uninstall esquire-infra-grafana
call helm uninstall esquire-infra-postgres-exporter
call helm uninstall esquire-infra-prometheus
call helm uninstall esquire-infra-otel-collector
call helm uninstall esquire-infra-tempo
call helm uninstall esquire-infra-alloy
call helm uninstall esquire-infra-loki
endlocal
