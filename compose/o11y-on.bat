@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-on.bat -- enable observability on the local docker compose stack:
rem   1. bring up the viewing stack (Loki + Alloy + Tempo + OTel Collector +
rem      Grafana + Prometheus -- the "o11y" compose profile), and
rem   2. recreate the app services with observability ON (ESQ_OBSERVABILITY_ENABLED=true).
rem The base stack keeps running; only the app services are recreated. Opt-in --
rem the everyday stack default is tracing OFF. Mirror of k8s\o11y-on.bat.
rem
rem   Grafana: http://localhost:3009  (admin/admin) -> Explore
rem     Logs:   {job="esq-docker"} | json | correlationId = "<id>"
rem     Traces: Tempo -> Search by TraceID = the same correlationId
rem ===========================================================================
set ESQ_OBSERVABILITY_ENABLED=true
rem The HISTOGRAM buckets. Percentiles and -- the reason it is here -- EXEMPLARS ride on them: an exemplar is
rem stamped onto a histogram bucket sample, so with buckets off there is nothing for a trace id to attach to
rem and the metric->trace hop is a dead link. It stays its own switch (off for the everyday stack, where the
rem extra series are not free), but the observability-ON path must turn it on or the single pane has a hole.
set ESQ_METRICS_HISTOGRAMS=true

echo --- bringing up the o11y viewing stack (loki/alloy/tempo/prometheus/otel-collector/grafana + postgres-exporter)...
docker compose --profile o11y up -d loki alloy tempo prometheus otel-collector grafana postgres-exporter || exit /b 1

echo --- recreating the broker with observability ON (loads the JMX exporter agent on :9404)...
rem Broker BEFORE the services on purpose: recreating it drops every client connection, so it goes first and
rem the services below then start against a broker that is already up.
docker compose up -d --no-deps --force-recreate activemq || exit /b 1

echo --- recreating the app services with observability ON...
docker compose up -d --no-deps --force-recreate keycloak gateway biztree enyman pacman keysmith kcmaster aukeep backend || exit /b 1

echo --- restarting the gateway so it rediscovers the recreated services...
docker compose restart gateway

echo.
echo o11y ON (docker). Grafana: http://localhost:3009  (admin/admin)
endlocal
