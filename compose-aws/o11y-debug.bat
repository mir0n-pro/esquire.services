@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-debug.bat -- the DEVELOPING arm: no observability stack at all, and every log local at DEBUG.
rem
rem This lab keeps THREE arms and no more:
rem   o11y-debug.bat   no o11y, all logs local at DEBUG   -- developing, reading what a driver actually did
rem   o11y-off.bat     no o11y, normal log levels         -- the everyday stack
rem   o11y-full.bat    the whole viewing stack, armed     -- Grafana, Tempo, Loki, Prometheus
rem The classic stack also carries log-only isolation arms; those exist to measure the LOGGING share of the
rem observability bill in a perf matrix, and this lab runs no such matrix.
rem
rem Nothing is exported here: no collector, no scrape, no Loki. The logs are the container's own stdout
rem (docker compose logs -f <svc>) and the develop / msg files under ./logs.
rem ===========================================================================
set ESQ_OBSERVABILITY_ENABLED=false
set ESQ_METRICS_HISTOGRAMS=false

rem MIR0N is the application logger -- the one that carries what the code says. DEVELOP is the per-service
rem develop.* channel the transport drivers write to (tp-sqs / tp-sns / tp-kinesis log their queue, topic and
rem shard decisions there), and MSG is the wire log. All three at DEBUG, because that is what this arm is for.
set LOG_LEVEL_MIR0N=DEBUG
set LOG_LEVEL_DEVELOP=DEBUG
set LOG_LEVEL_MSG=DEBUG

echo --- tearing down the o11y viewing stack (this arm exports nothing)...
docker compose --profile o11y rm -sf loki alloy tempo prometheus otel-collector grafana postgres-exporter

echo --- recreating the app services: observability OFF, logs at DEBUG...
docker compose up -d --no-deps --force-recreate keycloak gateway biztree enyman pacman keysmith kcmaster aukeep backend || exit /b 1

echo --- restarting the gateway so it rediscovers the recreated services...
docker compose restart gateway

echo.
echo o11y DEBUG (docker AWS lab). No observability stack; MIR0N / DEVELOP / MSG at DEBUG.
echo   follow a service : docker compose logs -f enyman
echo   the driver lines : findstr "tp-sqs tp-sns tp-kinesis" logs\enyMan-develop.log
endlocal
