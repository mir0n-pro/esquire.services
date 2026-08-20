@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-on.bat -- enable observability on the COMPACT docker compose stack:
rem   1. bring up the viewing stack (Loki + Alloy + Tempo + OTel Collector +
rem      Grafana + Prometheus -- the "o11y" compose profile), and
rem   2. recreate the app services with observability ON (ESQ_OBSERVABILITY_ENABLED=true).
rem The base stack keeps running; only the app services are recreated. Opt-in --
rem the everyday stack default is observability OFF. Mirror of compose\o11y-on.bat,
rem over the FOUR compact processes: gateward, mesnie, pacman and the BFF.
rem
rem   Grafana: http://localhost:3009  (admin/admin) -> Explore
rem ===========================================================================
set ESQ_OBSERVABILITY_ENABLED=true
rem The HISTOGRAM buckets. Percentiles and EXEMPLARS ride on them: an exemplar is stamped onto a histogram
rem bucket sample, so with buckets off there is nothing for a trace id to attach to and the metric->trace hop
rem is a dead link.
set ESQ_METRICS_HISTOGRAMS=true

echo --- bringing up the o11y viewing stack (loki/alloy/tempo/prometheus/otel-collector/grafana + postgres-exporter)...
docker compose --profile o11y up -d loki alloy tempo prometheus otel-collector grafana postgres-exporter || exit /b 1

echo --- recreating the broker with observability ON (loads the JMX exporter agent on :9404)...
rem Broker BEFORE the services on purpose: recreating it drops every client connection, so it goes first and
rem the services below then start against a broker that is already up.
docker compose up -d --no-deps --force-recreate activemq || exit /b 1

echo --- recreating the app services with observability ON...
docker compose up -d --no-deps --force-recreate keycloak gateward mesnie pacman aukeep backend || exit /b 1

echo.
echo o11y ON (docker compact). Grafana: http://localhost:3009  (admin/admin)
endlocal
