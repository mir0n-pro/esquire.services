@echo off
cd /d "%~dp0"
rem ===========================================================================
rem Take the AWS lab down -- ALL of it.
rem
rem --profile o11y IS REQUIRED, and leaving it off is a teardown that lies. The
rem observability seven (loki, alloy, tempo, prometheus, otel-collector, grafana,
rem postgres-exporter) sit behind that profile, and a plain `docker compose down`
rem does not touch a profiled service. What happens then is not an error anyone
rem reads: the services stop, the network delete fails with "Resource is still in
rem use" because the seven still hold it, and the script exits having left seven
rem containers running. Seen 2026-09-01, after they had been up 43 hours.
rem
rem --remove-orphans: a container whose service has since been removed from
rem compose.yaml keeps running otherwise, and a plain down leaves it behind --
rem redis, kafka and redisinsight left the compact file exactly that way.
rem
rem Volumes are NOT removed: postgres-data is a named volume, and wiping it is the
rem separate, deliberate reseed step.
rem ===========================================================================
docker compose --profile o11y down --remove-orphans || exit /b 1

echo.
echo --- proof: nothing of the lab is left running
docker ps --filter "name=esqa-" --format "  {{.Names}}  {{.Status}}"
echo     (no lines above = the lab is down)
