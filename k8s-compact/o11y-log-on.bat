@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-log-on.bat -- LOGGING ONLY on local k8s: the log pillar on, tracing +
rem metrics OFF. The k8s mirror of compose\o11y-log-on.bat (I49).
rem
rem THE POINT (mir0n): what does LOGGING cost, BY ITSELF? T10's perf matrix
rem measured o11y as ONE lump -- OFF vs ON -- so its -11.5% / -24% is all three
rem pillars together and no one can say which bought what. (T10 is SOUND: the app
rem logged at DEBUG throughout and its ON arm really did ship those lines to Loki.
rem Only the split between the pillars was missing.)
rem
rem This is the ON arm:
rem   tracing + metrics  OFF  (observability.enabled=false)
rem   logging            ON   (logging.levelMir0n=INFO in every service)
rem   viewing stack      loki + alloy + grafana  -- and NOTHING else
rem
rem THE KNOB IS levelMir0n, NOT levelRoot (mir0n). `pro.mir0n` carries its own level
rem and no appender of its own, so its events reach the root's ECS CONSOLE appender by
rem ADDITIVITY and an ancestor's LEVEL is never re-checked -- root gates only
rem third-party libraries, never the application. A first pass of I49 turned root,
rem reported "-4.7%", and it was void: the app logged identically in both arms.
rem The OFF arm (o11y-log-off.bat) sets levelMir0n=OFF, so the delta is the real thing.
rem
rem devLog / msgLog are pinned OFF in BOTH arms, so they are identical at both ends and
rem cancel out of the delta. Pinning them is not a formality: the pods run
rem SPRING_PROFILES_ACTIVE=console, so develop and msg go to STDOUT, where Alloy ships
rem them to Loki -- left at DEBUG they would ride inside the very volume this measures.
rem
rem Tempo / otel-collector / prometheus / postgres-exporter stay OUT: with
rem tracing and metrics off they would receive and scrape nothing, and muddy the
rem very isolation this exists to get.
rem ===========================================================================
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop". Refusing to run.
  exit /b 1
)

echo --- Installing the LOG viewing stack only (loki + alloy + grafana)...
call helm upgrade --install esquire-infra-loki    charts\infra\loki    --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-alloy   charts\infra\alloy   --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-grafana charts\infra\grafana --force-conflicts || exit /b 1

echo --- Removing the tracing/metrics side (it must not run, or this is not an isolation)...
call helm uninstall esquire-infra-tempo             2>nul
call helm uninstall esquire-infra-otel-collector    2>nul
call helm uninstall esquire-infra-prometheus        2>nul
call helm uninstall esquire-infra-postgres-exporter 2>nul

rem INFRA FIRST, APPS AFTER. A broker roll drops every app pod's messagingBus connection and that does NOT
rem self-heal, so with the apps restarted first the bounce lands on pods that have ALREADY rolled and nothing
rem restarts them again -- an ordinary toggle then leaves the entity bus dead until someone kicks them by
rem hand. Rolling the broker first costs no extra restarts: the app restart below is the one that reconnects.
rem The OKE scripts and both docker twins already do it this way; these four were the last holdouts.
echo --- keycloak / activemq: metrics OFF (no JMX exporter agent)...
call helm upgrade esquire-infra-kc charts\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-infra-kc-keycloak
rem The BROKER too, and it is the half that was missing: FULL-on arms its in-JVM JMX exporter agent, and
rem this arm never disarmed it -- so full-on -> log-on measured 'the log pillar alone' with the agent
rem still loaded and its HTTP server live on :9404. The echo above said otherwise. Both twins
rem (o11y-log-off, o11y-full-on) always had this line; the docker twin recreates activemq correctly.
call helm upgrade esquire-infra-amq charts\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-infra-amq-activemq

echo --- App services: tracing/metrics OFF, ONLY pro.mir0n at INFO...
for %%s in (gateward mesnie pacman aukeep) do (
  call helm upgrade esquire-%%s charts\esquire-%%s --reset-then-reuse-values --set observability.enabled=false --set observability.metricsHistograms=false --set logging.levelMir0n=INFO --set logging.levelDevelop=OFF --set logging.levelMsg=OFF --set logging.levelAmq=OFF --set logging.levelJms=OFF --force-conflicts
  call kubectl rollout restart statefulset esquire-%%s
)
rem The BFF has no root-level knob (pino, its own default) -- it is a CONSTANT in both arms, so it cancels.
call helm upgrade esquire-backend charts\esquire-backend --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-backend

echo.
echo LOGGING ON (pro.mir0n=INFO), tracing/metrics OFF (local k8s). The I49 ON arm.
endlocal
