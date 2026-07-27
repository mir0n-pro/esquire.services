@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem oke-grafana-forward.bat -- reach the OKE Grafana from the host.
rem
rem OKE Grafana is a ClusterIP (transient, no ingress by design), so it is reached
rem by a port-forward. Grafana talks to its Prometheus/Loki/Tempo datasources
rem IN-CLUSTER, so this ONE forward is all you need to VIEW every dashboard --
rem the o11y-verify backends (prometheus/loki/tempo) only need forwarding when the
rem host itself queries them (oke-o11y-verify.bat); for the panes, grafana alone.
rem
rem Port 13009 (the "1xxxx band", same as k8s\o11y-forward.bat): a docker Grafana
rem publishes on :3009, so forwarding onto 3009 could bind whatever docker owns and
rem answer from the wrong instance. The 1-prefixed port cannot collide.
rem
rem   OKE grafana -> http://localhost:13009   (admin/admin)
rem
rem Stop it by closing the "esq-oke-grafana" window (or Ctrl+C in it).
rem ===========================================================================
for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "docker-desktop" -- this forwards the OKE Grafana. Refusing.
  echo Switch: kubectl config use-context context-czhlwnp27sq
  exit /b 1
)

rem Guard: the local port must be FREE, or the forward would not bind and queries
rem would be answered by whatever already owns 13009.
netstat -ano | findstr /r /c:"LISTENING" | findstr /c:":13009 " >nul
if not errorlevel 1 (
  echo ERROR: local port 13009 is already in use ^(a stale forward, or another grafana^).
  echo        Close that window / free the port and re-run.
  exit /b 1
)

echo === OKE Grafana port-forward  (context=%CTX%) ===
start "esq-oke-grafana" /min kubectl port-forward svc/esquire-infra-grafana 13009:3000 -n default
echo.
echo   OKE Grafana  http://localhost:13009      (admin/admin)
echo   Topology     http://localhost:13009/d/esq-topology/
echo   Services     http://localhost:13009/d/esq-services/
echo   Logging      http://localhost:13009/d/esq-logging/
echo.
echo   Stop it by closing the minimized "esq-oke-grafana" window.
endlocal
