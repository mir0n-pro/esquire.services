@echo off
rem ===========================================================================
rem fleet-supercompact-k8s.bat -- the SUPER-COMPACT fleet as kubernetes labels it.
rem
rem Super-compact is the compact composition with audit on option (a): DB triggers,
rem so no auKeep process exists and no audit bus carries traffic. Four processes --
rem Mesnie, gateWard, pacMan and the BFF. This is the cloud model (OKE, AWS).
rem
rem Called (not run) by the OKE compact launchers: no setlocal here.
rem ===========================================================================

rem SERVICES = the metrics `application` label -- the PROCESS, short name.
set SERVICES=gateward,mesnie,pacman

rem The trace nodes are PROCESSES too (the collector rewrites service.name to
rem <app>.<instance>), so compact has a gateward node where classic has gateway
rem and biztree, and a mesnie node where it has three.
set TRACE_NODES=gateward,esq-backend
set TRACE_NODES_CONDITIONAL=mesnie,pacman

rem LOG_SERVICES = the Loki `service_name` label, which on k8s is the FULL workload
rem name (esquire-<svc>-<svc>), NOT the short meter name. Without it the log-stream
rem sweep looks for streams that do not exist and FAILs every service while the logs
rem are being shipped perfectly well.
set LOG_SERVICES=esquire-gateward,esquire-mesnie,esquire-pacman,esquire-backend

rem No auKeep process on this profile, so the keep-write meters and the keep-apply gauge never
rem exist -- asserting them would be a permanent false FAIL. The trace side needs no exclusion:
rem TRACE_NODES / TRACE_NODES_CONDITIONAL above REPLACE the engine's defaults outright, and
rem neither names aukeep.
set EXCLUDE_METERS=esq_biz_keep_write_total,esq_biz_keep_write_duration_seconds,esq_keep_apply_seconds

rem Super-compact declares ONE destination, the entity broadcast TOPIC, and no queue at all (kcMaster is inside
rem Mesnie so the identity request/reply pair is never drained, and audit is written by DB triggers). The broker
rem therefore publishes activemq_topic_* and no activemq_queue_* whatsoever, and the panels that read the queue
rem family are empty here BY DESIGN -- asserting them would be a permanent false FAIL, the same trap the meter
rem exclusions above avoid.
set EXCLUDE_DEPS=activemq_queue_
