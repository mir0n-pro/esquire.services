@echo off
rem Convenience shim: EntitySmoke x5 against the LOCAL docker stack.
rem
rem THE PROFILE FOLLOWS THE TOPOLOGY THAT IS ACTUALLY UP. The two docker stacks are mutually exclusive
rem and hold the same host ports: classic (project esq-omnibus, containers esq-*) keeps the bizTree cache
rem in its own service, compact (project esq-compact, containers esqc-*) keeps it inside gateWard. The
rem default profile is the classic one, so a bare run against a compact stack drives a container that is
rem not there -- and only the resilience arms notice.
setlocal

set "CFG="
docker ps -q -f name=esqc-gateward | findstr . >nul && set "CFG=--config hauberk-compact.properties"

if defined CFG (echo [hauberk] docker profile: hauberk-compact.properties) else (echo [hauberk] docker profile: hauberk.properties ^(default^))
call "%~dp0hauberk.cmd" run entity-smoke --times 5 %CFG% %*
