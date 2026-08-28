@echo off
cd /d "%~dp0"
rem === The other compose stack, dropped first ===
rem Both stacks bind the same host ports (4200 / 8081 / 8161 / 5433 / 3009), so the second one to start
rem simply fails to bind. Dropping the other project here is what deploy-compose.cmd already does, so a
rem hand-run brings the same result as a pipeline run. "not running" is the normal case and costs nothing.
docker compose -p esq-compact down --remove-orphans >nul 2>&1
rem Containers from images that are already built. On a machine that has never built them, run
rem compose-rebuild.bat first -- it builds every image and recreates the stack in one go.
docker compose up -d



