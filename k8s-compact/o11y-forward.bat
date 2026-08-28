@echo off
setlocal
cd /d "%~dp0"

rem === o11y-forward.bat -- reach the LOCAL K8S observability backends from the host ===
rem
rem THE TRAP THIS REMOVES (it cost an afternoon once, do not re-discover it):
rem   The docker o11y stack PUBLISHES its backends on the host -- tempo :3200, prometheus :9090, loki :3100.
rem   So the obvious command
rem       kubectl port-forward svc/esquire-infra-tempo 3200:3200
rem   cannot bind (the port is already taken by the docker container) -- and then every query you send to
rem   localhost:3200 is answered by the DOCKER Tempo instead of the k8s one. Nothing errors in your face; the
rem   k8s traces simply read as "missing" while they are in fact perfectly fine. Same for prometheus and loki.
rem
rem   The rule, so it cannot happen again: k8s o11y forwards ALWAYS live on the 1xxxx band -- the same port with
rem   a "1" in front. There is no overlap with anything docker publishes, and the number is not a thing you have
rem   to remember, it is a thing you can derive.
rem
rem       k8s tempo       -> http://localhost:13200      (docker tempo      is on :3200)
rem       k8s prometheus  -> http://localhost:19090      (docker prometheus is on :9090)
rem       k8s loki        -> http://localhost:13100      (docker loki       is on :3100)
rem       k8s grafana     -> http://localhost:13009      (docker grafana    is on :3009)
rem
rem   Grafana was reached via its ingress (grafana.localhost) before, but *.localhost resolves for curl only
rem   (RFC 6761) -- python's getaddrinfo does not, so o11y-verify's live datasource check could not reach it.
rem   A forward on the 1xxxx band works for every client.
rem
rem   And the script REFUSES to start if one of those local ports is already busy, rather than forwarding into a
rem   port someone else owns -- which is the whole failure mode, restated.
rem
rem Stop the forwards with o11y-forward-stop.bat.

echo.
echo === Esquire -- local k8s o11y port-forwards ===

rem --- guard 1: the right cluster. A forward against the WRONG context is the other way to read the wrong data.
for /f "tokens=*" %%c in ('kubectl config current-context 2^>nul') do set CTX=%%c
if not "%CTX%"=="docker-desktop" (
  echo.
  echo   ABORT: kubectl context is "%CTX%", expected "docker-desktop".
  echo          Switch with:  kubectl config use-context docker-desktop
  exit /b 1
)
echo   context: %CTX%

rem --- guard 2: every local port must be FREE. If it is taken, we would silently query whatever owns it.
call :checkport 13200 tempo      || exit /b 1
call :checkport 19090 prometheus || exit /b 1
call :checkport 13100 loki       || exit /b 1
call :checkport 13009 grafana    || exit /b 1

echo.
echo   forwarding...
start "esq-k8s-tempo"      /min kubectl port-forward svc/esquire-infra-tempo      13200:3200
start "esq-k8s-prometheus" /min kubectl port-forward svc/esquire-infra-prometheus 19090:9090
start "esq-k8s-loki"       /min kubectl port-forward svc/esquire-infra-loki       13100:3100
start "esq-k8s-grafana"    /min kubectl port-forward svc/esquire-infra-grafana    13009:3000

echo.
echo   k8s tempo       http://localhost:13200      (docker tempo      stays on :3200)
echo   k8s prometheus  http://localhost:19090      (docker prometheus stays on :9090)
echo   k8s loki        http://localhost:13100      (docker loki       stays on :3100)
echo   k8s grafana     http://localhost:13009      (docker grafana    stays on :3009; admin/admin)
echo.
echo   stop them with:  o11y-forward-stop.bat
endlocal
exit /b 0

:checkport
netstat -ano | findstr /r /c:"LISTENING" | findstr /c:":%1 " >nul
if not errorlevel 1 (
  echo.
  echo   ABORT: local port %1 ^(for k8s %2^) is already in use.
  echo          Forwarding into a busy port is exactly the bug this script exists to prevent:
  echo          the forward would not bind, and your queries would be answered by whatever owns %1.
  echo          Free the port ^(or stop a stale forward with o11y-forward-stop.bat^) and re-run.
  exit /b 1
)
exit /b 0
