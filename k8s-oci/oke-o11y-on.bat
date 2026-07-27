@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem oke-o11y-on.bat <OFF|LOG|FULL> -- ON-DEMAND observability for OKE (T12).
rem
rem The OKE twin of k8s\o11y-{log,full}-on.bat. OKE runs with o11y OFF by default
rem (near-saturated free tier holds it only TRANSIENTLY); this routine turns a
rem chosen model ON for a post-release smoke, and oke-o11y-off.bat removes it back
rem to defaults. Three models, and they ADD UP because only pro.mir0n ever moves:
rem
rem   OFF   pro.mir0n OFF   , no viewing stack                  (the Stage 2 baseline)
rem   LOG   pro.mir0n INFO  , loki + alloy + grafana            (log pillar alone)
rem   FULL  pro.mir0n INFO  , + tempo + otel-collector +        (all three pillars)
rem                           prometheus + postgres-exporter
rem
rem THE KNOB IS levelMir0n, NOT levelRoot (mir0n): pro.mir0n carries its own level
rem and no appender of its own, so its events reach the root ECS CONSOLE appender by
rem ADDITIVITY -- an ancestor's LEVEL is never re-checked, so root gates only
rem third-party libraries and cannot silence the application. develop/msg/amq/jms are
rem OFF in every model so none leaks into a delta.
rem
rem OKE DELTAS vs the local twin (T12 topology):
rem   - context guard INVERTED: refuses docker-desktop, runs only on the OKE cluster.
rem   - app-service loop EXCLUDES aukeep: OKE has NO auKeep (audit = DB triggers).
rem   - app charts flipped with --reset-then-reuse-values, so the OKE overlay/tag/
rem     db-password already applied by oke-up.bat are kept; only the o11y switches move.
rem   - o11y infra installed with the SHARED charts as-is: emptyDir (no PVC -- the
rem     200GB free block-volume budget is tight) and 6h loki retention are the chart
rem     defaults, already free-tier-right.
rem   - PLACEMENT PINNED (T12): the 7 o11y deployments carry --set nodeSelector.tier=o11y,
rem     so they land ONLY on the two paid tier=o11y nodes. The app is already hard-pinned
rem     to tier=app and the core infra to tier=infra, so turning o11y on/off never touches
rem     an esquire pod -- o11y rides the transient paid capacity alone. (esquire on free,
rem     o11y infra on paid: the plan. No taint needed -- the tier selectors isolate.)
rem   - Grafana is ClusterIP: reach it with `kubectl port-forward svc/esquire-infra-
rem     grafana 3009:3000` (transient; no ingress).
rem
rem Usage:  oke-o11y-on.bat LOG        (first smoke model, LOCKED)
rem         oke-o11y-on.bat FULL
rem         oke-o11y-on.bat OFF        (idempotent baseline; same end state as off)
rem ===========================================================================
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this is the OKE o11y routine ^(production^).
  echo Refusing. Switch: kubectl config use-context context-czhlwnp27sq
  exit /b 1
)

set MODEL=%~1
if "%MODEL%"=="" set MODEL=LOG
if /i "%MODEL%"=="OFF"  goto model_off
if /i "%MODEL%"=="LOG"  goto model_log
if /i "%MODEL%"=="FULL" goto model_full
echo ERROR: unknown model "%MODEL%". Use one of: OFF ^| LOG ^| FULL.
exit /b 1

rem ---------------------------------------------------------------------------
:model_log
set CH=..\k8s\charts
echo === oke-o11y-on LOG  (context=%CTX%) ===
rem The transient paid nodes come up unlabeled after a scale-up; give any tier-less node
rem tier=o11y so the o11y nodeSelector below has a home (the original 4 already carry app/infra).
call kubectl label nodes -l "!tier" tier=o11y --overwrite 2>nul
echo --- Installing the LOG viewing stack only (loki + alloy + grafana)...
rem docker.io/ prefix: OKE nodes run cri-o with short-name-mode=enforcing, which rejects an
rem unqualified image ("grafana/alloy returns ambiguous list"). The shared charts use short
rem names (fine on Docker Desktop, which defaults to docker.io); OKE needs them fully qualified.
call helm upgrade --install esquire-infra-loki    %CH%\infra\loki    --set image.repository=docker.io/grafana/loki    --set nodeSelector.tier=o11y || exit /b 1
call helm upgrade --install esquire-infra-alloy   %CH%\infra\alloy   --set image.repository=docker.io/grafana/alloy   --set nodeSelector.tier=o11y || exit /b 1
call helm upgrade --install esquire-infra-grafana %CH%\infra\grafana --set image.repository=docker.io/grafana/grafana --set nodeSelector.tier=o11y || exit /b 1
echo --- Removing the tracing/metrics side (must not run -- LOG is logging alone)...
call helm uninstall esquire-infra-tempo             2>nul
call helm uninstall esquire-infra-otel-collector    2>nul
call helm uninstall esquire-infra-prometheus        2>nul
call helm uninstall esquire-infra-postgres-exporter 2>nul
echo --- App services (NO aukeep): tracing/metrics OFF, ONLY pro.mir0n at INFO...
for %%s in (gateway enyman biztree pacman keysmith kcmaster) do (
  call helm upgrade esquire-%%s %CH%\esquire-%%s --reset-then-reuse-values --set observability.enabled=false --set observability.metricsHistograms=false --set logging.levelMir0n=INFO --set logging.levelDevelop=OFF --set logging.levelMsg=OFF --set logging.levelAmq=OFF --set logging.levelJms=OFF
  call kubectl rollout restart statefulset esquire-%%s-%%s
)
rem BFF has no pro.mir0n knob (pino, its own default) -- a CONSTANT in every model, so it cancels.
call helm upgrade esquire-backend %CH%\esquire-backend --reset-then-reuse-values --set observability.enabled=false
call kubectl rollout restart statefulset esquire-backend-backend
rem SKIP_INFRA_ROLL (set by oke-perf-matrix): skip the kc/amq METRIC rolls. Rolling the broker drops
rem the app pods' messagingBus connection, which does NOT self-heal (needs a pod restart) -- so a
rem toggle-in-place matrix must never roll it. Infra metrics are the broker's/kc's OWN, not app o11y
rem cost, so skipping them is measurement-neutral.
if not defined SKIP_INFRA_ROLL (
  echo --- keycloak: metrics OFF ^(no JMX exporter agent^)...
  call helm upgrade esquire-infra-kc %CH%\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=false
  call kubectl rollout restart statefulset esquire-infra-kc-keycloak
)
echo.
echo LOG on: pro.mir0n INFO, tracing/metrics OFF, loki+alloy+grafana up (OKE).
echo Reach Grafana: kubectl port-forward svc/esquire-infra-grafana 3009:3000 -n default   (admin/admin)
goto done

rem ---------------------------------------------------------------------------
:model_full
set CH=..\k8s\charts
echo === oke-o11y-on FULL  (context=%CTX%) ===
rem The transient paid nodes come up unlabeled after a scale-up; give any tier-less node
rem tier=o11y so the o11y nodeSelector below has a home (the original 4 already carry app/infra).
call kubectl label nodes -l "!tier" tier=o11y --overwrite 2>nul
echo --- Installing the FULL viewing stack (logs + traces + metrics)...
rem docker.io/ prefix for OKE cri-o short-name enforcing (see the LOG block). postgres-exporter
rem is already fully qualified (quay.io/...), so it is left as-is.
call helm upgrade --install esquire-infra-loki              %CH%\infra\loki              --set image.repository=docker.io/grafana/loki                       --set nodeSelector.tier=o11y || exit /b 1
call helm upgrade --install esquire-infra-alloy             %CH%\infra\alloy             --set image.repository=docker.io/grafana/alloy                      --set nodeSelector.tier=o11y || exit /b 1
call helm upgrade --install esquire-infra-tempo             %CH%\infra\tempo             --set image.repository=docker.io/grafana/tempo                      --set nodeSelector.tier=o11y || exit /b 1
call helm upgrade --install esquire-infra-otel-collector    %CH%\infra\otel-collector    --set image.repository=docker.io/otel/opentelemetry-collector-contrib --set nodeSelector.tier=o11y || exit /b 1
call helm upgrade --install esquire-infra-prometheus        %CH%\infra\prometheus        --set image.repository=docker.io/prom/prometheus                    --set nodeSelector.tier=o11y || exit /b 1
call helm upgrade --install esquire-infra-postgres-exporter %CH%\infra\postgres-exporter --set nodeSelector.tier=o11y || exit /b 1
call helm upgrade --install esquire-infra-grafana           %CH%\infra\grafana           --set image.repository=docker.io/grafana/grafana                    --set nodeSelector.tier=o11y || exit /b 1
echo --- App services (NO aukeep): tracing/metrics ON, ONLY pro.mir0n at INFO...
for %%s in (gateway enyman biztree pacman keysmith kcmaster) do (
  call helm upgrade esquire-%%s %CH%\esquire-%%s --reset-then-reuse-values --set observability.enabled=true --set observability.metricsHistograms=true --set logging.levelMir0n=INFO --set logging.levelDevelop=OFF --set logging.levelMsg=OFF --set logging.levelAmq=OFF --set logging.levelJms=OFF
  call kubectl rollout restart statefulset esquire-%%s-%%s
)
call helm upgrade esquire-backend %CH%\esquire-backend --reset-then-reuse-values --set observability.enabled=true
call kubectl rollout restart statefulset esquire-backend-backend
rem SKIP_INFRA_ROLL (see the LOG block): the matrix suppresses the kc/amq metric rolls so the broker
rem is never bounced under running app pods (messagingBus does not self-heal a broker bounce).
if not defined SKIP_INFRA_ROLL (
  echo --- keycloak / activemq: metrics ON ^(JMX exporter agent^)...
  call helm upgrade esquire-infra-kc  %CH%\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=true
  call kubectl rollout restart statefulset esquire-infra-kc-keycloak
  call helm upgrade esquire-infra-amq %CH%\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=true
  call kubectl rollout restart statefulset esquire-infra-amq-activemq
)
echo.
echo FULL on: pro.mir0n INFO + tracing + metrics + the full viewing stack (OKE).
echo Reach Grafana: kubectl port-forward svc/esquire-infra-grafana 3009:3000 -n default   (admin/admin)
goto done

rem ---------------------------------------------------------------------------
:model_off
set CH=..\k8s\charts
echo === oke-o11y-on OFF  (context=%CTX%) ===
echo --- App services (NO aukeep): tracing/metrics OFF, pro.mir0n OFF...
for %%s in (gateway enyman biztree pacman keysmith kcmaster) do (
  call helm upgrade esquire-%%s %CH%\esquire-%%s --reset-then-reuse-values --set observability.enabled=false --set observability.metricsHistograms=false --set logging.levelMir0n=OFF --set logging.levelDevelop=OFF --set logging.levelMsg=OFF --set logging.levelAmq=OFF --set logging.levelJms=OFF
  call kubectl rollout restart statefulset esquire-%%s-%%s
)
call helm upgrade esquire-backend %CH%\esquire-backend --reset-then-reuse-values --set observability.enabled=false
call kubectl rollout restart statefulset esquire-backend-backend
rem SKIP_INFRA_ROLL (see the LOG block): the matrix suppresses the kc/amq metric rolls so the broker
rem is never bounced under running app pods (messagingBus does not self-heal a broker bounce).
if not defined SKIP_INFRA_ROLL (
  call helm upgrade esquire-infra-kc  %CH%\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=false
  call kubectl rollout restart statefulset esquire-infra-kc-keycloak
  call helm upgrade esquire-infra-amq %CH%\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=false
  call kubectl rollout restart statefulset esquire-infra-amq-activemq
)
echo --- Removing the viewing stack (back to OKE defaults)...
call helm uninstall esquire-infra-grafana           2>nul
call helm uninstall esquire-infra-postgres-exporter 2>nul
call helm uninstall esquire-infra-prometheus        2>nul
call helm uninstall esquire-infra-otel-collector    2>nul
call helm uninstall esquire-infra-tempo             2>nul
call helm uninstall esquire-infra-alloy             2>nul
call helm uninstall esquire-infra-loki              2>nul
echo.
echo OFF: pro.mir0n OFF, no viewing stack (OKE default). Same end state as oke-o11y-off.bat.
goto done

:done
endlocal
