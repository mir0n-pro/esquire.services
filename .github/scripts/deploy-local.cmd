@echo off
setlocal
rem ===========================================================================
rem Esquire services -- local full-stack deploy (phase 2).
rem Called by .github/workflows/deploy-local.yml on a SELF-HOSTED WINDOWS runner
rem with Docker Desktop k8s (kubectl context = docker-desktop).
rem
rem WHICH TOPOLOGY IT DEPLOYS: the one already installed in the cluster.
rem   classic  -- k8s\          (enyMan + keySmith + kcMaster + auKeep as their own pods)
rem   compact  -- k8s-compact\  (Mesnie in place of the three; no auKeep)
rem Read from the releases themselves: esquire-mesnie present means compact,
rem esquire-enyman present means classic. An empty cluster falls back to classic,
rem the default shape, and classic also wins if BOTH are somehow installed --
rem guessing between them is how a cluster ends in a half state, and one
rem deterministic answer is worth more than a clever one.
rem
rem It then UNINSTALLS the releases belonging to the other topology. Without that
rem both shapes run at once on one bus and one database: Mesnie and enyMan/keySmith
rem/kcMaster all subscribed to the entity-broadcast topic, with nothing failing to
rem say so. The releases the two shapes SHARE (gateway, biztree, pacman, backend and
rem the infra) keep their names in both trees, so the up-script simply upgrades them
rem into the chosen shape -- only the shape-specific ones have to go.
rem
rem Each stack's own *.bat stays the SINGLE SOURCE of its deploy logic:
rem   k8s-rebuild.bat all  -- mvn package + docker build + stamp every image
rem                           (Java services + the explorer backend/BFF)
rem   k8s-up.bat           -- helm upgrade --install the full stack, wait ready
rem
rem Layout expected in the runner workspace (set by the workflow's two checkouts):
rem   <workspace>\services\   (this repo)
rem   <workspace>\explorer\   (sibling -- k8s-rebuild builds ..\..\explorer\backend)
rem
rem HERE = <workspace>\services\.github\scripts\  ->  k8s is at ..\..\k8s
rem ===========================================================================
set "HERE=%~dp0"

rem === Context safety guard. Both up-scripts carry their own, but this script
rem     UNINSTALLS releases before either of them runs, so it must guard first. ===
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop".
  echo Refusing to run deploy-local.cmd -- this script targets local Docker Desktop k8s only.
  exit /b 1
)

rem === Which topology is in the cluster? ===
set "TOPOLOGY=classic"
helm status esquire-mesnie >nul 2>&1
if not errorlevel 1 set "TOPOLOGY=compact"
helm status esquire-enyman >nul 2>&1
if not errorlevel 1 set "TOPOLOGY=classic"

echo === [deploy-local] topology=%TOPOLOGY% ===

if /i "%TOPOLOGY%"=="compact" goto compact

:classic
echo --- [deploy-local] removing compact-only releases...
call :drop esquire-mesnie
call :drop esquire-gateward
echo === [deploy-local] build + stamp all images (CLASSIC) ===
call "%HERE%..\..\k8s\k8s-rebuild.bat" all
if errorlevel 1 ( echo k8s-rebuild [classic] failed & exit /b 1 )
echo === [deploy-local] bring up the CLASSIC stack on Docker Desktop k8s ===
call "%HERE%..\..\k8s\k8s-up.bat"
if errorlevel 1 ( echo k8s-up [classic] failed & exit /b 1 )
goto done

:compact
echo --- [deploy-local] removing classic-only releases...
call :drop esquire-enyman
call :drop esquire-keysmith
call :drop esquire-kcmaster
call :drop esquire-gateway
call :drop esquire-biztree
echo === [deploy-local] build + stamp all images (COMPACT) ===
call "%HERE%..\..\k8s-compact\k8s-rebuild.bat" all
if errorlevel 1 ( echo k8s-rebuild [compact] failed & exit /b 1 )
echo === [deploy-local] bring up the COMPACT stack on Docker Desktop k8s ===
call "%HERE%..\..\k8s-compact\k8s-up.bat"
if errorlevel 1 ( echo k8s-up [compact] failed & exit /b 1 )
goto done

:done
echo === [deploy-local] done (%TOPOLOGY%) ===
exit /b 0

rem Uninstall one release if it is there. Asking first keeps a clean run quiet and
rem keeps "it was not installed" from reading like a failure.
:drop
helm status %1 >nul 2>&1
if errorlevel 1 exit /b 0
echo     removing %1 (belongs to the other topology)
call helm uninstall %1 >nul 2>&1
exit /b 0
