@echo off
rem Convenience shim: EntitySmoke against the LOCAL Docker Desktop k8s deployment.
rem
rem THE PROFILE FOLLOWS THE TOPOLOGY THAT IS ACTUALLY DEPLOYED. Local k8s runs classic OR compact --
rem deploy-local.cmd installs whichever the box already has -- and the two shapes drive a different
rem cache holder: classic has esquire-biztree, compact keeps the bizTree cache inside gateWard. Reading
rem the releases here is what deploy-local.cmd already does, so the smoke can never be pointed at a
rem profile whose deployment does not exist.
rem
rem Requires the hauberk fat jar (`mvn -pl hauberk install`).
setlocal

set "CFG=hauberk-k8s.properties"
helm status esquire-gateward >nul 2>&1
if not errorlevel 1 set "CFG=hauberk-k8s-compact.properties"
helm status esquire-biztree >nul 2>&1
if not errorlevel 1 set "CFG=hauberk-k8s.properties"

echo [hauberk] local k8s profile: %CFG%
call "%~dp0hauberk.cmd" run entity-smoke --config %CFG% %*
