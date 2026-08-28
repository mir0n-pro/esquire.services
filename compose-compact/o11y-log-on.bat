@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-log-on.bat -- LOGGING ONLY on the COMPACT docker stack: the log pillar
rem on, tracing + metrics OFF.
rem
rem THE POINT (I49, mir0n): what does LOGGING cost, BY ITSELF? An o11y matrix that
rem measures ON vs OFF as one lump cannot say which pillar bought what.
rem
rem This is the ON arm of the isolation:
rem   tracing + metrics  OFF  (ESQ_OBSERVABILITY_ENABLED=false)
rem   logging            ON   (LOG_LEVEL_MIR0N=INFO in every service)
rem   viewing stack      loki + alloy + grafana  -- and NOTHING else
rem
rem THE KNOB IS LOG_LEVEL_MIR0N, NOT LOG_LEVEL_ROOT (mir0n). `pro.mir0n` carries its
rem own level in logback-spring.xml and no appender of its own, so its events reach the
rem root's ECS CONSOLE appender by ADDITIVITY -- and an ancestor's LEVEL is never
rem re-checked. Root therefore gates only third-party libraries, never the application.
rem A first pass of I49 turned root, reported "-4.7%", and it was void: the app logged
rem identically in both arms and cancelled out. The OFF arm (o11y-log-off.bat) sets
rem LOG_LEVEL_MIR0N=OFF, so the delta is the real thing -- the ECS-JSON encode per line
rem in every service, plus Alloy tailing stdout and shipping to Loki.
rem
rem WHY loki + alloy + GRAFANA and not just loki + alloy (mir0n): grafana is
rem how a human READS the logs. A log pillar nobody can look at is not the
rem thing we ship, so it belongs in the measurement. Tempo / prometheus /
rem otel-collector / postgres-exporter stay OUT -- with tracing and metrics off
rem they would sit there scraping and receiving nothing, and muddy the very
rem isolation this exists to get.
rem
rem Mirror of compose\o11y-log-on.bat and k8s-compact\o11y-log-on.bat, over the four Java
rem processes -- gateward, mesnie, pacman, aukeep -- and the BFF.
rem ===========================================================================
set ESQ_OBSERVABILITY_ENABLED=false
set ESQ_METRICS_HISTOGRAMS=false

rem The whole point of the ON arm: the APPLICATION logs at INFO, so there is a real
rem stream to encode and ship. This is the knob the OFF arm turns off; root is left
rem alone in both arms (see the header).
set LOG_LEVEL_MIR0N=INFO

rem ONLY pro.mir0n is INFO in this arm (mir0n) -- everything else is OFF, exactly as in o11y-log-off.bat,
rem so the ONE difference between the arms is the application logger. devLog/msgLog write to FILES on a
rem Windows BIND MOUNT that nothing ships; AMQ/JMS reach the ECS console by additivity and would add log
rem volume that is not the thing being priced. See o11y-log-off.bat for the full reasoning.
set LOG_LEVEL_DEVELOP=OFF
set LOG_LEVEL_MSG=OFF
set LOG_LEVEL_AMQ=OFF
set LOG_LEVEL_JMS=OFF

echo --- bringing up the LOG viewing stack only (loki + alloy + grafana)...
docker compose --profile o11y up -d loki alloy grafana || exit /b 1

echo --- tearing down the tracing/metrics side (it must not be running, or this is not an isolation)...
docker compose --profile o11y rm -sf tempo prometheus otel-collector postgres-exporter

echo --- recreating the broker with observability OFF (no JMX exporter agent)...
rem Broker BEFORE the services: recreating it drops every client connection, so it
rem goes first and the services below start against a broker that is already up.
docker compose up -d --no-deps --force-recreate activemq || exit /b 1

echo --- recreating the app services: tracing/metrics OFF, LOG_LEVEL_MIR0N=INFO...
docker compose up -d --no-deps --force-recreate keycloak gateward mesnie pacman aukeep backend || exit /b 1

echo.
echo LOGGING ON, tracing/metrics OFF (docker compact). Grafana: http://localhost:3009  (admin/admin)
echo   Logs:  {job="esq-docker"}
endlocal
