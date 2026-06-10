@echo off
setlocal
rem ===========================================================================
rem Esquire services -- local full-stack deploy (phase 2).
rem Called by .github/workflows/deploy-local.yml on a SELF-HOSTED WINDOWS runner
rem with Docker Desktop k8s (kubectl context = docker-desktop).
rem
rem Reuses the proven k8s\*.bat as the SINGLE SOURCE of deploy logic:
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

echo === [deploy-local] build + stamp all images (Java services + explorer backend) ===
call "%HERE%..\..\k8s\k8s-rebuild.bat" all
if errorlevel 1 ( echo k8s-rebuild failed & exit /b 1 )

echo === [deploy-local] bring up the full stack on Docker Desktop k8s ===
call "%HERE%..\..\k8s\k8s-up.bat"
if errorlevel 1 ( echo k8s-up failed & exit /b 1 )

echo === [deploy-local] done ===
exit /b 0
