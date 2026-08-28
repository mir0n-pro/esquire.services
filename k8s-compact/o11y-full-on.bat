@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem o11y-full-on.bat -- the IN-FULL arm on local k8s: ALL THREE PILLARS really on.
rem
rem   logging            ON   (logging.levelMir0n=INFO)
rem   tracing + metrics  ON   (observability.enabled=true, histograms, sampling 1.0)
rem   viewing stack      FULL (loki + alloy + tempo + otel-collector + prometheus +
rem                            postgres-exporter + grafana)
rem
rem WHY THIS ARM EXISTS (mir0n): the three modes are OFF / ONLY-LOGGING / IN-FULL, and
rem IN-FULL had never been measured by anything. T10's "o11y ON" is the closest thing,
rem but it runs pro.mir0n at its DEBUG chart default -- the stack as-shipped, not a
rem controlled arm. Holding pro.mir0n at INFO here, exactly as o11y-log-on.bat does,
rem is what makes the three modes ADD UP:
rem
rem   OFF  -> LOG   = the log pillar alone
rem   LOG  -> FULL  = tracing + metrics alone
rem   OFF  -> FULL  = the whole observability bill
rem
rem Every logger except pro.mir0n is OFF in ALL THREE arms, so the ONLY thing that ever
rem moves is the pillar under test. On k8s that matters more than it sounds: the pods run
rem SPRING_PROFILES_ACTIVE=console, so develop AND msg go to STDOUT, where Alloy ships
rem them to Loki -- left at DEBUG they would land in this arm's shipped volume and be
rem encoded in the OFF arm too, contaminating both ends of the comparison.
rem
rem levelRoot is NOT touched (mir0n): pro.mir0n carries its own level and reaches the
rem root's ECS console appender by additivity, so root gates only third-party libraries
rem and cannot silence the application. Turning root is what voided the 07-16 runs.
rem
rem Pair with o11y-log-off.bat (OFF) and o11y-log-on.bat (ONLY-LOGGING).
rem ===========================================================================
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop". Refusing to run.
  exit /b 1
)

echo --- Installing the FULL viewing stack (logs + traces + metrics)...
call helm upgrade --install esquire-infra-loki              charts\infra\loki              --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-alloy             charts\infra\alloy             --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-tempo             charts\infra\tempo             --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-otel-collector    charts\infra\otel-collector    --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-prometheus        charts\infra\prometheus        --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-postgres-exporter charts\infra\postgres-exporter --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-grafana           charts\infra\grafana           --force-conflicts || exit /b 1

rem INFRA FIRST, APPS AFTER. A broker roll drops every app pod's messagingBus connection and that does NOT
rem self-heal, so with the apps restarted first the bounce lands on pods that have ALREADY rolled and nothing
rem restarts them again -- an ordinary toggle then leaves the entity bus dead until someone kicks them by
rem hand. Rolling the broker first costs no extra restarts: the app restart below is the one that reconnects.
rem The OKE scripts and both docker twins already do it this way; these four were the last holdouts.
echo --- keycloak / activemq: metrics ON (JMX exporter agent)...
call helm upgrade esquire-infra-kc  charts\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=true --force-conflicts
call kubectl rollout restart statefulset esquire-infra-kc-keycloak
call helm upgrade esquire-infra-amq charts\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=true --force-conflicts
call kubectl rollout restart statefulset esquire-infra-amq-activemq

echo --- App services: tracing/metrics ON, ONLY pro.mir0n at INFO...
for %%s in (gateward mesnie pacman aukeep) do (
  call helm upgrade esquire-%%s charts\esquire-%%s --reset-then-reuse-values --set observability.enabled=true --set observability.metricsHistograms=true --set logging.levelMir0n=INFO --set logging.levelDevelop=OFF --set logging.levelMsg=OFF --set logging.levelAmq=OFF --set logging.levelJms=OFF --force-conflicts
  call kubectl rollout restart statefulset esquire-%%s
)
rem The BFF has no pro.mir0n knob (pino, its own default) -- a CONSTANT in every arm, so it cancels.
call helm upgrade esquire-backend charts\esquire-backend --reset-then-reuse-values --set observability.enabled=true --force-conflicts
call kubectl rollout restart statefulset esquire-backend

echo.
echo IN-FULL: logging (pro.mir0n INFO) + tracing + metrics + the full viewing stack (local k8s).
endlocal
