@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem oke-o11y-on.bat <OFF|LOG|FULL> -- ON-DEMAND observability for the OKE
rem SUPER-COMPACT stack (T12 routine, v1.2.13 fleet).
rem
rem The OKE twin of k8s-compact\o11y-{log,full}-on.bat. OKE runs with o11y OFF by default
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
rem   - app-service loop is the THREE compact processes: gateWard (the gate + the tree
rem     cache), Mesnie (enyMan + keySmith + the identity work) and pacMan. No aukeep line --
rem     this profile audits by DB triggers, option (a), so no auKeep exists to switch.
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
rem --force-conflicts on every upgrade: helm 4 applies SERVER-SIDE, and `kubectl scale` (the perf matrix,
rem and any hand scaling) takes ownership of .spec.replicas. An upgrade that then touches that field is
rem REFUSED -- and these calls are `call helm ... *> nul` with no exit-code check, so the arm would be
rem silently NOT applied and the run would measure the previous arm. This is what voided the first pass.
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this is the OKE o11y routine ^(production^).
  echo Refusing. Switch: kubectl config use-context context-czhlwnp27sq
  exit /b 1
)

rem A caller that also SCALES (the perf matrix) passes its replica count here, so helm SETS the value it
rem is about to be scaled to. Without it helm asks for the chart default, kubectl owns .spec.replicas from
rem the previous cell, the two disagree, and server-side apply REFUSES the upgrade -- silently, because the
rem calls are `call helm ... *> $null` with no exit-code check. The arm then never lands. Empty = unchanged.
set ESQ_REPS=
if defined ESQ_REPLICAS set ESQ_REPS=--set replicaCount=%ESQ_REPLICAS%
set MODEL=%~1
if "%MODEL%"=="" set MODEL=LOG
if /i "%MODEL%"=="OFF"  goto model_off
if /i "%MODEL%"=="LOG"  goto model_log
if /i "%MODEL%"=="FULL" goto model_full
echo ERROR: unknown model "%MODEL%". Use one of: OFF ^| LOG ^| FULL.
exit /b 1

rem ---------------------------------------------------------------------------
:model_log
set CH=..\k8s-compact\charts
echo === oke-o11y-on LOG  (context=%CTX%) ===
rem The transient paid nodes come up unlabeled after a scale-up; give any tier-less node
rem tier=o11y so the o11y nodeSelector below has a home (the original 4 already carry app/infra).
call kubectl label nodes -l "!tier" tier=o11y --overwrite 2>nul
echo --- Installing the LOG viewing stack only (loki + alloy + grafana)...
rem docker.io/ prefix: OKE nodes run cri-o with short-name-mode=enforcing, which rejects an
rem unqualified image ("grafana/alloy returns ambiguous list"). The shared charts use short
rem names (fine on Docker Desktop, which defaults to docker.io); OKE needs them fully qualified.
call helm upgrade --install esquire-infra-loki    %CH%\infra\loki    --set image.repository=docker.io/grafana/loki    --set nodeSelector.tier=o11y --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-alloy   %CH%\infra\alloy   --set image.repository=docker.io/grafana/alloy   --set nodeSelector.tier=o11y --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-grafana %CH%\infra\grafana --set image.repository=docker.io/grafana/grafana --set nodeSelector.tier=o11y --set ingress.enabled=false --set-file dashboardTopology=grafana/esquire-topology.json --set-file dashboardServices=grafana/esquire-services.json --set-file dashboardLogging=grafana/esquire-logging.json --force-conflicts || exit /b 1
echo --- Removing the tracing/metrics side (must not run -- LOG is logging alone)...
call helm uninstall esquire-infra-tempo             2>nul
call helm uninstall esquire-infra-otel-collector    2>nul
call helm uninstall esquire-infra-prometheus        2>nul
call helm uninstall esquire-infra-postgres-exporter 2>nul
rem INFRA FIRST, APPS AFTER -- as :model_full and :model_off already do. A broker roll INTERRUPTS every app
rem pod's messagingBus: the transport goes DOWN and the failover: wrapper brings it back by itself, about 20s
rem on local k8s. So the cost is a bus WINDOW, not a dead bus. Infra first puts that window beside the app
rem restart below rather than after it, and costs no extra restart.
rem SKIP_INFRA_ROLL (set by oke-perf-matrix): skip the kc/amq METRIC rolls. Rolling the broker costs a bus
rem window -- about 20s while the failover: transport reconnects -- which is harmless in an ordinary toggle but
rem lands INSIDE a toggle-in-place measurement and reads as app cost. Infra metrics are the broker's/kc's OWN,
rem not app o11y cost, so skipping them is measurement-neutral.
if not defined SKIP_INFRA_ROLL (
  echo --- keycloak / activemq: metrics OFF ^(no JMX exporter agent^)...
  call helm upgrade esquire-infra-kc %CH%\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
  call kubectl rollout restart statefulset esquire-infra-kc-keycloak
  rem THE BROKER TOO. FULL arms its in-JVM JMX exporter agent, and this model never disarmed it -- so a
  rem FULL -> LOG transition priced 'the log pillar alone' with the agent still loaded and :9404 live, while
  rem the echo below said tracing/metrics were off. :model_full and :model_off both roll it; only LOG did not.
  call helm upgrade esquire-infra-amq %CH%\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
  call kubectl rollout restart statefulset esquire-infra-amq-activemq
)

echo --- App services ^(gateWard, Mesnie, pacMan^): tracing/metrics OFF, ONLY pro.mir0n at INFO...
for %%s in (gateward mesnie pacman) do (
  call helm upgrade esquire-%%s %CH%\esquire-%%s --reset-then-reuse-values --set observability.enabled=false --set observability.metricsHistograms=false --set logging.levelMir0n=INFO --set logging.levelDevelop=OFF --set logging.levelMsg=OFF --set logging.levelAmq=OFF --set logging.levelJms=OFF %ESQ_REPS% --force-conflicts
  call kubectl rollout restart statefulset esquire-%%s
)
rem BFF has no pro.mir0n knob (pino, its own default) -- a CONSTANT in every model, so it cancels.
call helm upgrade esquire-backend %CH%\esquire-backend --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-backend
echo.
echo LOG on: pro.mir0n INFO, tracing/metrics OFF, loki+alloy+grafana up (OKE).
echo Reach Grafana: kubectl port-forward svc/esquire-infra-grafana 3009:3000 -n default   (admin/admin)
goto done

rem ---------------------------------------------------------------------------
:model_full
set CH=..\k8s-compact\charts
echo === oke-o11y-on FULL  (context=%CTX%) ===
rem The transient paid nodes come up unlabeled after a scale-up; give any tier-less node
rem tier=o11y so the o11y nodeSelector below has a home (the original 4 already carry app/infra).
call kubectl label nodes -l "!tier" tier=o11y --overwrite 2>nul
echo --- Installing the FULL viewing stack (logs + traces + metrics)...
rem docker.io/ prefix for OKE cri-o short-name enforcing (see the LOG block). postgres-exporter
rem is already fully qualified (quay.io/...), so it is left as-is.
call helm upgrade --install esquire-infra-loki              %CH%\infra\loki              --set image.repository=docker.io/grafana/loki                       --set nodeSelector.tier=o11y --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-alloy             %CH%\infra\alloy             --set image.repository=docker.io/grafana/alloy                      --set nodeSelector.tier=o11y --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-tempo             %CH%\infra\tempo             --set image.repository=docker.io/grafana/tempo                      --set nodeSelector.tier=o11y --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-otel-collector    %CH%\infra\otel-collector    --set image.repository=docker.io/otel/opentelemetry-collector-contrib --set nodeSelector.tier=o11y --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-prometheus        %CH%\infra\prometheus        --set image.repository=docker.io/prom/prometheus                    --set nodeSelector.tier=o11y --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-postgres-exporter %CH%\infra\postgres-exporter --set nodeSelector.tier=o11y --force-conflicts || exit /b 1
call helm upgrade --install esquire-infra-grafana           %CH%\infra\grafana           --set image.repository=docker.io/grafana/grafana                    --set nodeSelector.tier=o11y --set ingress.enabled=false --set-file dashboardTopology=grafana/esquire-topology.json --set-file dashboardServices=grafana/esquire-services.json --set-file dashboardLogging=grafana/esquire-logging.json --force-conflicts || exit /b 1
rem INFRA FIRST, APPS AFTER -- the order stated just below. A broker roll INTERRUPTS every app pod's
rem messagingBus: the transport goes DOWN and the failover: wrapper reconnects it on its own, about 20s on
rem local k8s. The cost is a bus WINDOW, not a dead bus. Rolling the broker first puts that window beside the
rem app restart below rather than after it, and costs no extra restarts.
rem SKIP_INFRA_ROLL (see the LOG block): the matrix suppresses the kc/amq metric rolls so a broker bounce --
rem and the ~20s bus window while the failover: transport reconnects -- never lands inside a measurement.
if not defined SKIP_INFRA_ROLL (
  echo --- keycloak / activemq: metrics ON ^(JMX exporter agent^)...
  call helm upgrade esquire-infra-kc  %CH%\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=true --force-conflicts
  call kubectl rollout restart statefulset esquire-infra-kc-keycloak
  call helm upgrade esquire-infra-amq %CH%\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=true --force-conflicts
  call kubectl rollout restart statefulset esquire-infra-amq-activemq
)
echo --- App services ^(gateWard, Mesnie, pacMan^): tracing/metrics ON, ONLY pro.mir0n at INFO...
for %%s in (gateward mesnie pacman) do (
  call helm upgrade esquire-%%s %CH%\esquire-%%s --reset-then-reuse-values --set observability.enabled=true --set observability.metricsHistograms=true --set logging.levelMir0n=INFO --set logging.levelDevelop=OFF --set logging.levelMsg=OFF --set logging.levelAmq=OFF --set logging.levelJms=OFF %ESQ_REPS% --force-conflicts
  call kubectl rollout restart statefulset esquire-%%s
)
call helm upgrade esquire-backend %CH%\esquire-backend --reset-then-reuse-values --set observability.enabled=true --force-conflicts
call kubectl rollout restart statefulset esquire-backend
echo.
echo FULL on: pro.mir0n INFO + tracing + metrics + the full viewing stack (OKE).
echo Reach Grafana: kubectl port-forward svc/esquire-infra-grafana 3009:3000 -n default   (admin/admin)
goto done

rem ---------------------------------------------------------------------------
:model_off
set CH=..\k8s-compact\charts
echo === oke-o11y-on OFF  (context=%CTX%) ===
rem INFRA FIRST, APPS AFTER -- the rule stated just below. A broker roll INTERRUPTS every app pod's
rem messagingBus; the failover: wrapper reconnects it by itself, about 20s on local k8s. A window, not a dead
rem bus. Rolling the broker first puts that window beside the app restart, and costs no extra restarts.
rem SKIP_INFRA_ROLL (see the LOG block): the matrix suppresses the kc/amq metric rolls so a broker bounce --
rem and the ~20s bus window while the failover: transport reconnects -- never lands inside a measurement.
if not defined SKIP_INFRA_ROLL (
  call helm upgrade esquire-infra-kc  %CH%\infra\keycloak -f values\keycloak.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
  call kubectl rollout restart statefulset esquire-infra-kc-keycloak
  call helm upgrade esquire-infra-amq %CH%\infra\activemq -f values\activemq.yaml --reset-then-reuse-values --set observability.enabled=false --force-conflicts
  call kubectl rollout restart statefulset esquire-infra-amq-activemq
)
echo --- App services ^(gateWard, Mesnie, pacMan^): tracing/metrics OFF, pro.mir0n OFF...
for %%s in (gateward mesnie pacman) do (
  call helm upgrade esquire-%%s %CH%\esquire-%%s --reset-then-reuse-values --set observability.enabled=false --set observability.metricsHistograms=false --set logging.levelMir0n=OFF --set logging.levelDevelop=OFF --set logging.levelMsg=OFF --set logging.levelAmq=OFF --set logging.levelJms=OFF %ESQ_REPS% --force-conflicts
  call kubectl rollout restart statefulset esquire-%%s
)
call helm upgrade esquire-backend %CH%\esquire-backend --reset-then-reuse-values --set observability.enabled=false --force-conflicts
call kubectl rollout restart statefulset esquire-backend
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
