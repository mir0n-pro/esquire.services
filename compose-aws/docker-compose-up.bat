@echo off
cd /d "%~dp0"
rem === The other compose stacks, dropped first ===
rem All three stacks bind the same host ports (4200 / 8081 / 8161 / 5433 / 3009), so the second one to
rem start simply fails to bind. Dropping the other projects here is what deploy-compose.cmd already does,
rem so a hand-run brings the same result as a pipeline run. "not running" is the normal case and costs
rem nothing.
docker compose -p esq-omnibus down --remove-orphans >nul 2>&1
docker compose -p esq-compact down --remove-orphans >nul 2>&1
rem Containers from images that are already built. On a machine that has never built them, run
rem compose-rebuild.bat first -- it builds every image and recreates the stack in one go.
docker compose up -d



