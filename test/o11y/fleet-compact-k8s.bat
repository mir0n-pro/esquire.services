@echo off
rem ===========================================================================
rem fleet-compact-k8s.bat -- the compact fleet as KUBERNETES labels it.
rem
rem The fleet itself first, then the one label that is k8s-shaped. Called (not
rem run) by the local k8s-compact and OKE compact launchers: no setlocal here.
rem ===========================================================================
call "%~dp0fleet-compact.bat"

rem LOG_SERVICES = the Loki `service_name` label, which on k8s is the FULL workload
rem name (esquire-<svc>-<svc>), NOT the short meter name. Without it the log-stream
rem sweep looks for streams that do not exist and FAILs every service while the logs
rem are being shipped perfectly well. Mesnie is ONE workload for enyMan, keySmith
rem and the identity work.
set LOG_SERVICES=esquire-gateward,esquire-mesnie,esquire-pacman,esquire-aukeep,esquire-backend
