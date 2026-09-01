@echo off
rem ===========================================================================
rem aws-o11y-on.bat -- the OPT-IN observability stack on EKS, and the services armed
rem to feed it. The EKS twin of compose\o11y-on.bat: same topology (CLASSIC x1), same
rem three pillars, same single pane.
rem
rem   logs     Loki + Alloy
rem   traces   Tempo + OTel Collector
rem   metrics  Prometheus + postgres-exporter
rem   viewing  Grafana
rem
rem OFF BY DEFAULT, here as everywhere. The base stack ships with tracing off, and
rem aws-deploy.bat leaves it off unless ESQ_O11Y says otherwise. Turn it on to look at
rem something, and take it off again with aws-o11y-off.bat -- the cluster is standing
rem all the time now, so anything left armed is paid for all the time.
rem
rem   Grafana:  kubectl port-forward svc/esquire-infra-grafana 3010:3000
rem             http://localhost:3010  (admin/admin)  ->  Explore
rem     Logs:   {job="esq-k8s"} | json | correlationId = "<id>"
rem     Traces: Tempo -> Search by TraceID = the same correlationId
rem
rem 3010, AND THE PORT MATTERS. 3000 is the BFF's port on the docker lab and 3009 is
rem that lab's OWN Grafana (esqa-grafana publishes 3009->3000). Forward to either and
rem kubectl cannot bind, the forward dies, and localhost answers from DOCKER -- you are
rem then reading the wrong system's dashboards with no sign that anything is wrong.
rem That is the T2.10 failure exactly. Check the OWNING PROCESS, not whether the port
rem answers: Get-NetTCPConnection -LocalPort 3010 -State Listen, then Get-Process.
rem
rem THE SIBLING IS compose\o11y-on.bat. Classic x1 runs in exactly two places -- docker
rem and EKS -- so that is the one to keep this in step with, and the only one.
rem
rem Deltas that are EKS's own, not borrowed from anywhere:
rem   - no nodeSelector. Two general nodes, no tiers to pin to.
rem   - images go in on their short names. containerd resolves them.
rem   - the seven charts install with helm rather than a compose profile, so the order
rem     matters: loki and tempo before the things that write to them, grafana last.
rem   - auKeep IS armed. Classic x1 has it, and its audit rides the Kinesis bus.
rem ===========================================================================
setlocal

set HERE=%~dp0
set AWS_REGION=us-east-1
set CH=%HERE%charts

rem --- context guard. These charts are not the local ones; landing them on the wrong
rem     cluster produces something that looks like a deployment.
for /f "usebackq tokens=*" %%A in (`aws sts get-caller-identity --query Account --output text`) do set ACCT=%%A
set WANT=arn:aws:eks:%AWS_REGION%:%ACCT%:cluster/esquire-aws
for /f "usebackq tokens=*" %%C in (`kubectl config current-context`) do set HAVE=%%C
if not "%HAVE%"=="%WANT%" (
    echo [FAIL] wrong kubectl context.
    echo        want: %WANT%
    echo        have: %HAVE%
    echo        run 1st.bat
    exit /b 1
)
echo === context OK: %HAVE%

echo.
echo === making sure the o11y node exists (the off arm scales it to zero)
rem A nodeSelector does NOT summon a node -- there is no autoscaler here, so a chart
rem pinned to tier=o11y with the group at zero simply sits Pending, and helm --wait
rem times out after five minutes saying nothing about why. Scale first.
eksctl scale nodegroup --cluster esquire-aws --region %AWS_REGION% --name esq-o11y --nodes 1 --nodes-min 0 --nodes-max 1 >nul 2>&1
echo --- waiting for a READY node with tier=o11y ...
rem IT MUST BE READY, NOT MERELY PRESENT. When the group scales to zero the Node OBJECT
rem outlives the instance by minutes, so a check for "does a node with this label exist"
rem matches a DEAD one, skips the wait, and then `kubectl wait --for=Ready` sits on a
rem corpse for its full timeout. Measured: 303 seconds of waiting for a node that was
rem already gone.
rem
rem findstr /c:" Ready" is exact on purpose: a NotReady line reads "...  NotReady ...",
rem where the character before Ready is a 't', so it does not match.
rem
rem SLEEP WITH ping, NOT `timeout`. `timeout /t` needs a console on stdin; under a pipe it
rem fails with "Input redirection is not supported" and returns AT ONCE, so a wait loop
rem built on it spins its whole count in a second and reports a timeout that never was.
set NODEUP=
for /l %%i in (1,1,40) do (
    if not defined NODEUP (
        kubectl get nodes -l tier=o11y --no-headers 2>nul | findstr /c:" Ready" >nul && set NODEUP=1
        if not defined NODEUP ping -n 16 127.0.0.1 >nul
    )
)
if not defined NODEUP (
    echo [FAIL] no READY tier=o11y node after ten minutes -- the seven charts have nowhere to land.
    echo        check: eksctl get nodegroup --cluster esquire-aws --region %AWS_REGION%
    exit /b 1
)
kubectl get nodes -l tier=o11y --no-headers

echo === the viewing stack -- all seven, in dependency order
call :infra loki             esquire-infra-loki
call :infra alloy            esquire-infra-alloy
call :infra tempo            esquire-infra-tempo
call :infra otel-collector   esquire-infra-otel-collector
call :infra prometheus       esquire-infra-prometheus
call :infra postgres-exporter esquire-infra-postgres-exporter
call :infra grafana          esquire-infra-grafana

rem Grafana provisions its datasources at boot only, so a newly added source or dashboard
rem is invisible until the pod restarts -- and a ConfigMap change does not restart a pod.
echo --- restarting grafana so it re-reads its datasources and dashboards
kubectl rollout restart deployment/esquire-infra-grafana >nul 2>&1

echo.
echo === arming the services (observability ON, as shipped -- histograms stay off)
set ESQ_O11Y=on
call "%HERE%aws-deploy.bat" esquire-gateway  || exit /b 1
call "%HERE%aws-deploy.bat" esquire-keysmith || exit /b 1
call "%HERE%aws-deploy.bat" esquire-enyman   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-pacman   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-biztree  || exit /b 1
call "%HERE%aws-deploy.bat" esquire-kcmaster || exit /b 1
call "%HERE%aws-deploy.bat" esquire-aukeep   || exit /b 1
rem THE BFF AND KEYCLOAK ARE PART OF THE FLEET. Left out of this list they keep
rem observability OFF while everything around them is armed, and Prometheus reports two
rem targets returning 404 -- which reads as a broken scrape config rather than as two
rem services nobody turned on.
call "%HERE%aws-deploy.bat" esquire-backend  || exit /b 1
echo --- KeyCloak metrics (KC_METRICS_ENABLED is a BOOT option -- the StatefulSet rolls)
helm upgrade --install esquire-infra-kc "%CH%\infra\keycloak" -f "%HERE%values\keycloak.yaml" --set observability.enabled=true --set nodeSelector.tier=app --wait --timeout 5m || exit /b 1

echo.
echo === rolling the services so they pick the switches up
rem A ConfigMap change does NOT restart a pod: helm reports "deployed", the map is right,
rem and the running container keeps its mounted copy. Roll them, or nothing is armed.
for %%S in (gateway keysmith enyman pacman biztree kcmaster aukeep) do kubectl rollout restart statefulset/esquire-%%S-%%S >nul 2>&1
for %%S in (gateway keysmith enyman pacman biztree kcmaster aukeep) do kubectl rollout status statefulset/esquire-%%S-%%S --timeout=300s

echo.
kubectl get pods -o wide
echo.
echo === observability is ON. To look at it:
echo     kubectl port-forward svc/esquire-infra-grafana 3010:3000
echo     http://localhost:3010   admin/admin
echo === take it off again with aws-o11y-off.bat
goto :eof

rem --- infra <chart> <release> ----------------------------------------------
rem A values file is used only where the chart needs telling something about EKS;
rem the rest go in on their defaults, which are already right here.
:infra
echo --- %~2
rem PINNED to the o11y node. With the seven on the app nodes the cluster sat at 95%% and
rem 99%% of CPU REQUESTS, and a rolling update could not schedule its replacement: helm
rem reported a timeout while the OLD pod kept serving the OLD config -- a deploy that says
rem it failed and changes nothing. tier=o11y keeps them off the app nodes entirely, so
rem turning observability on never disturbs an Esquire pod.
if exist "%HERE%values\%~1.yaml" (
    helm upgrade --install %~2 "%CH%\infra\%~1" -f "%HERE%values\%~1.yaml" --set nodeSelector.tier=o11y --wait --timeout 5m || exit /b 1
) else (
    helm upgrade --install %~2 "%CH%\infra\%~1" --set nodeSelector.tier=o11y --wait --timeout 5m || exit /b 1
)
exit /b 0
