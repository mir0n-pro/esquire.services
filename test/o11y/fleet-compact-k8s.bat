@echo off
rem ===========================================================================
rem fleet-compact-k8s.bat -- the compact fleet as KUBERNETES labels it.
rem
rem The fleet itself first, then the one label that is k8s-shaped. Called (not
rem run) by the local k8s-compact and OKE compact launchers: no setlocal here.
rem ===========================================================================
call "%~dp0fleet-compact.bat"

rem LOG_SERVICES = the Loki `service_name` label, which on k8s is the WORKLOAD name,
rem not the short meter name. On the compact charts that is the bare release name
rem (esquire-mesnie); the classic charts append the service (esquire-enyman-enyman).
rem Without this the log-stream sweep looks for streams that do not exist and FAILs
rem every service while the logs are being shipped perfectly well. Mesnie is ONE
rem workload for enyMan, keySmith and the identity work.
set LOG_SERVICES=esquire-gateward,esquire-mesnie,esquire-pacman,esquire-aukeep,esquire-backend
