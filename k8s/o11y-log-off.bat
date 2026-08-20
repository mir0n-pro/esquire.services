@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-log-off.bat -- the BASELINE arm of the logging-cost isolation on local
rem k8s (I49). The mirror of compose\o11y-log-off.bat.
rem
rem   tracing + metrics  OFF  (observability.enabled=false)
rem   logging            OFF  -- the APPLICATION LOGGER is off (logging.levelMir0n=OFF)
rem   viewing stack      NONE (loki / alloy / grafana / tempo / prometheus /
rem                            otel-collector / postgres-exporter all uninstalled)
rem
rem THE KNOB IS levelMir0n, NOT levelRoot (mir0n). Root is the WRONG lever and turning
rem it measures nothing: in logback-spring.xml `pro.mir0n` carries its own level and no
rem appender of its own, so its events reach the root's ECS CONSOLE appender by
rem ADDITIVITY -- and an ancestor's LEVEL is never re-checked on the way. levelRoot=OFF
rem therefore does NOT silence the application; it gates only third-party libraries.
rem A first pass of I49 turned root, reported "-4.7%", and that number was void: the app
rem logged identically in BOTH arms and cancelled out of the delta.
rem
rem devLog / msgLog are NOT touched (levelDevelop=DEBUG, levelMsg=DEBUG): identical
rem in both arms, so they cancel out of the delta by design (mir0n). They write to
rem FILES, not stdout, and are not part of the log o11y stack being measured.
rem
rem Pair with o11y-log-on.bat. The delta between the arms IS the isolated cost of
rem the log pillar: the ECS-JSON encode per line in every service, plus Alloy
rem tailing and shipping to Loki.
rem ===========================================================================
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop". Refusing to run.
  exit /b 1
)

echo --- App services: tracing/metrics OFF, ALL LOGGERS OFF...
for %%s in (gateway enyman biztree pacman keysmith kcmaster aukeep) do (
  call helm upgrade esquire-%%s charts\esquire-%%s --reset-then-reuse-values --set observability.enabled=false --set observability.metricsHistograms=false --set logging.levelMir0n=OFF --set logging.levelDevelop=OFF --set logging.levelMsg=OFF --set logging.levelAmq=OFF --set logging.levelJms=OFF --force-conflicts
  call kubectl rollout restart statefulset esquire-%%s-%%s
)
call helm upgrade esquire-backend charts\esquire-backend --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-backend-backend

echo --- keycloak / activemq: metrics OFF...
call helm upgrade esquire-infra-kc charts\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-infra-kc-keycloak
call helm upgrade esquire-infra-amq charts\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-infra-amq-activemq

echo --- Uninstalling every viewing component (nothing may tail or scrape in the baseline)...
call helm uninstall esquire-infra-grafana           2>nul
call helm uninstall esquire-infra-postgres-exporter 2>nul
call helm uninstall esquire-infra-prometheus        2>nul
call helm uninstall esquire-infra-otel-collector    2>nul
call helm uninstall esquire-infra-tempo             2>nul
call helm uninstall esquire-infra-alloy             2>nul
call helm uninstall esquire-infra-loki              2>nul

echo.
echo LOGGING OFF (root disabled), tracing/metrics OFF (local k8s). The I49 baseline.
endlocal
