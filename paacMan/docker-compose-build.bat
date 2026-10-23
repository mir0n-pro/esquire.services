@echo off
rem docker build --secret id=GH_TOKEN,src=./git-token -t frontend .
call docker--build.bat
docker compose build 
rem --no-cache

rem #docker compose stop
rem #docker compose start -d
rem #docker compose exec <service> <command>
rem #docker compose run <service> <command>
rem #docker compose build
rem #docker compose up --build -d

