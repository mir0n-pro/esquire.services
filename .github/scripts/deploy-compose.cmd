@echo off
setlocal
rem ===========================================================================
rem Esquire services -- local docker-compose deploy (phase 2, second local target).
rem Called by .github/workflows/deploy-local.yml on the SELF-HOSTED WINDOWS runner,
rem alongside the Docker Desktop k8s deploy, so a green pipeline proves BOTH local
rem targets (docker-compose + k8s) come up on the pending code.
rem
rem WHICH TOPOLOGY IT DEPLOYS: the one that is already running on this box.
rem   classic  -- compose\           (project esq-omnibus, containers esq-*)
rem   compact  -- compose-compact\   (project esq-compact,  containers esqc-*)
rem The two stacks publish the SAME host ports, so only one of them can run; that is
rem the design, not a limitation. The deploy therefore follows the shape the box is
rem in rather than forcing one: a dev box left on compact keeps being proven on
rem compact. Nothing running falls back to classic, the default shape, and classic
rem also wins if both are somehow up -- guessing between them is how a box ends in a
rem half state, and one deterministic answer is worth more than a clever one.
rem
rem It then STOPS the other stack before building. Without that the deploy dies at
rem "Bind for 0.0.0.0:5433 failed: port is already allocated", with every container
rem left in `created` and nothing saying why.
rem
rem Each stack's own compose-rebuild.bat stays the SINGLE SOURCE of its deploy logic:
rem   compose-rebuild.bat all -- mvn package + docker build every image (Java
rem                              services + explorer backend/BFF + frontend), then
rem                              docker compose up -d --force-recreate.
rem
rem Layout expected in the runner workspace (set by the workflow's checkouts):
rem   <workspace>\services\   (this repo; compose.yaml lives at services\compose\)
rem   <workspace>\explorer\   (sibling -- compose.yaml backend/frontend build
rem                            contexts point at ..\..\explorer\ )
rem
rem HERE = <workspace>\services\.github\scripts\  ->  compose is at ..\..\compose
rem ===========================================================================
set "HERE=%~dp0"

rem === Which topology is on this box? Read it from the RUNNING containers' compose
rem     project label -- the one thing that is true regardless of which working tree
rem     started them. ===
set "TOPOLOGY=classic"
for /f %%i in ('docker ps -q --filter "label=com.docker.compose.project=esq-compact" 2^>nul') do set "TOPOLOGY=compact"
for /f %%i in ('docker ps -q --filter "label=com.docker.compose.project=esq-omnibus" 2^>nul') do set "TOPOLOGY=classic"

echo === [deploy-compose] topology=%TOPOLOGY% ===

if /i "%TOPOLOGY%"=="compact" goto compact

:classic
echo --- [deploy-compose] stopping the compact stack (it holds the same host ports)...
docker compose -p esq-compact down --remove-orphans
echo === [deploy-compose] build all images + bring up the CLASSIC docker stack ===
call "%HERE%..\..\compose\compose-rebuild.bat" all
if errorlevel 1 ( echo compose-rebuild [classic] failed & exit /b 1 )
goto done

:compact
echo --- [deploy-compose] stopping the classic stack (it holds the same host ports)...
docker compose -p esq-omnibus down --remove-orphans
echo === [deploy-compose] build all images + bring up the COMPACT docker stack ===
call "%HERE%..\..\compose-compact\compose-rebuild.bat" all
if errorlevel 1 ( echo compose-rebuild [compact] failed & exit /b 1 )
goto done

:done
echo === [deploy-compose] done (%TOPOLOGY%) ===
exit /b 0
