@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-log-off.bat -- the BASELINE arm of the logging-cost isolation (I49).
rem
rem   tracing + metrics  OFF  (ESQ_OBSERVABILITY_ENABLED=false)
rem   logging            OFF  -- the APPLICATION LOGGER is off (LOG_LEVEL_MIR0N=OFF)
rem   viewing stack      NONE (loki / alloy / grafana / tempo / prometheus /
rem                            otel-collector / postgres-exporter all removed)
rem
rem THE KNOB IS LOG_LEVEL_MIR0N, NOT LOG_LEVEL_ROOT (mir0n). Root is the WRONG lever
rem and turning it measures nothing: in logback-spring.xml `pro.mir0n` carries its own
rem level and no appender of its own, so its events travel by ADDITIVITY to the root's
rem ECS CONSOLE appender -- and an ancestor's LEVEL is never re-checked on the way. So
rem LOG_LEVEL_ROOT=OFF does NOT silence the application: every pro.mir0n line is still
rem encoded and written. Root gates only third-party libraries that inherit it.
rem A first pass of I49 turned root, found "-4.7%", and that number was void: the app
rem logged identically in BOTH arms and cancelled out of the delta.
rem
rem Pair with o11y-log-on.bat (LOG_LEVEL_MIR0N=INFO + loki + alloy + grafana).
rem The delta between the two arms IS the isolated cost of the log pillar.
rem Mirror of k8s\o11y-log-off.bat.
rem ===========================================================================
set ESQ_OBSERVABILITY_ENABLED=false
set ESQ_METRICS_HISTOGRAMS=false
set LOG_LEVEL_MIR0N=OFF

rem ALL LOGS OFF in this arm (mir0n): nothing may write. Every logger that can reach an appender is named
rem here -- they are NOT all the same knob:
rem   MIR0N   the application (pro.mir0n) -- THE knob; INFO in the ON arm, OFF here.
rem   DEVELOP on docker (no 'console' profile) writes to a FILE under ./logs -- a BIND MOUNT onto the
rem           Windows filesystem, the slowest write on the box (it grew to 26 GB). Nothing ships those
rem           files, so they are not the log pillar; left on, the host disk I/O swamps the delta.
rem   MSG     same shape: its own file appender.
rem   AMQ/JMS org.apache.activemq + org.springframework.jms, INFO by default in 6 services. No appender of
rem           their own, so they reach the root's ECS CONSOLE by additivity and DO emit. OFF in BOTH arms
rem           so the ON arm is "only pro.mir0n at INFO" and nothing else (mir0n).
rem ROOT and SF stay at their ERROR default -- root is left alone by design, and a clean run raises no
rem errors, so they emit nothing either way.
set LOG_LEVEL_DEVELOP=OFF
set LOG_LEVEL_MSG=OFF
set LOG_LEVEL_AMQ=OFF
set LOG_LEVEL_JMS=OFF

echo --- recreating the broker with observability OFF (no JMX exporter agent)...
rem Broker BEFORE the services: recreating it drops every client connection, so it
rem goes first and the services below start against a broker that is already up.
docker compose up -d --no-deps --force-recreate activemq || exit /b 1

echo --- recreating the app services: tracing/metrics OFF, APP LOGGER OFF...
docker compose up -d --no-deps --force-recreate keycloak gateway biztree enyman pacman keysmith kcmaster aukeep backend || exit /b 1

echo --- restarting the gateway so it rediscovers the recreated services...
docker compose restart gateway

echo --- tearing down every viewing component (nothing may tail or scrape in the baseline)...
docker compose --profile o11y rm -sf loki alloy tempo prometheus otel-collector grafana postgres-exporter

echo.
echo LOGGING OFF (app logger off), tracing/metrics OFF (docker). The I49 baseline.
endlocal
