@echo off
rem ===========================================================================
rem fleet-compact.bat -- the COMPACT fleet, declared once for every environment.
rem
rem Called (not run) by the o11y-test / o11y-verify launchers, so the values land
rem in the caller's environment: no setlocal here.
rem
rem These three are true on docker-compact, local k8s-compact and OKE compact
rem alike -- the compact profile runs the same four processes wherever it stands.
rem Addresses, ENVNAME, BASE_URL and LOKI_JOB stay with the launcher: those are
rem the environment, not the fleet.
rem ===========================================================================

rem SERVICES = the metrics `application` label -- the PROCESS, short name.
set SERVICES=gateward,mesnie,pacman,aukeep

rem The trace nodes are PROCESSES too (the collector rewrites service.name to
rem <app>.<instance>), so compact has a gateward node where classic has gateway
rem and biztree, and a mesnie node where it has three.
set TRACE_NODES=gateward,esq-backend
set TRACE_NODES_CONDITIONAL=mesnie,pacman,aukeep
