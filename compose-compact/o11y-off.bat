@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-off.bat -- disable observability on the COMPACT docker compose stack and
rem remove the viewing stack. Mirror of compose\o11y-off.bat, over the four Java
rem processes -- gateward, mesnie, pacman, aukeep -- and the BFF.
rem ===========================================================================
set ESQ_OBSERVABILITY_ENABLED=false
set ESQ_METRICS_HISTOGRAMS=false

echo --- recreating the broker with observability OFF (the JMX exporter agent is not loaded)...
rem Broker first: recreating it drops every client connection, so the services below reconnect to a broker that
rem is already back up.
docker compose up -d --no-deps --force-recreate activemq || exit /b 1

echo --- recreating the app services with observability OFF...
docker compose up -d --no-deps --force-recreate keycloak gateward mesnie pacman aukeep backend || exit /b 1

echo --- tearing down the o11y viewing stack...
docker compose --profile o11y rm -sf loki alloy tempo prometheus otel-collector grafana postgres-exporter

echo.
echo o11y OFF (docker compact). Observability disabled; viewing stack removed.
endlocal
