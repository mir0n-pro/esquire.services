@echo off
setlocal
rem ===========================================================================
rem Esquire services -- local docker-compose deploy (phase 2, second local target).
rem Called by .github/workflows/deploy-local.yml on the SELF-HOSTED WINDOWS runner,
rem alongside the Docker Desktop k8s deploy, so a green pipeline proves BOTH local
rem targets (docker-compose + k8s) come up on the pending code.
rem
rem Reuses the proven compose\compose-rebuild.bat as the SINGLE SOURCE of compose
rem deploy logic:
rem   compose-rebuild.bat all -- mvn package + docker build every image (Java
rem                              services + explorer backend/BFF + frontend), then
rem                              docker compose up -d --force-recreate.
rem
rem Layout expected in the runner workspace (set by the workflow's checkouts):
rem   <workspace>\services\   (this repo; compose.yaml lives at services\compose\)
rem   <workspace>\explorer\   (sibling -- compose.yaml backend/frontend build
rem                            contexts point at ..\..\explorer\ )
rem The docker-compose stack uses the HOST Postgres (host.docker.internal:5432), so
rem it needs NO db.seed checkout (the bundled Postgres image is a k8s-only concern).
rem
rem HERE = <workspace>\services\.github\scripts\  ->  compose is at ..\..\compose
rem ===========================================================================
set "HERE=%~dp0"

echo === [deploy-compose] build all images + bring up the docker-compose stack ===
call "%HERE%..\..\compose\compose-rebuild.bat" all
if errorlevel 1 ( echo compose-rebuild failed & exit /b 1 )

echo === [deploy-compose] done ===
exit /b 0
