@echo off
cd /d "%~dp0"
rem --remove-orphans: a container whose service has since been removed from compose.yaml keeps running
rem otherwise, and a plain down leaves it behind -- redis, kafka and redisinsight left the compact file
rem exactly that way. Volumes are NOT removed: postgres-data is a named volume, and wiping it is the
rem separate, deliberate reseed step.
docker compose down --remove-orphans
