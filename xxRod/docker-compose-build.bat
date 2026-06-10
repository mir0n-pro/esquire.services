@echo off
call docker--build.bat
docker compose build
rem --no-cache
