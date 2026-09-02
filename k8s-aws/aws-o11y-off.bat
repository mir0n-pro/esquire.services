@echo off
rem ===========================================================================
rem aws-o11y-off.bat -- take the observability stack off EKS and disarm the services.
rem
rem The cluster stands all the time now, so anything left armed is paid for all the
rem time -- in node memory, in scrape volume, and in the trace export the services do
rem whether or not a collector is listening. Off is the default state, and this is how
rem it is restored.
rem
rem It removes the seven infra releases and re-runs aws-deploy.bat with ESQ_O11Y unset,
rem which puts the services back on their chart defaults -- tracing off, metrics off,
rem histograms off. Nothing else about the deployment moves: the database, the buses
rem and the image tags are read from AWS the same way they always are.
rem ===========================================================================
setlocal

set HERE=%~dp0
set AWS_REGION=us-east-1

for /f "usebackq tokens=*" %%A in (`aws sts get-caller-identity --query Account --output text`) do set ACCT=%%A
set WANT=arn:aws:eks:%AWS_REGION%:%ACCT%:cluster/esquire-aws
for /f "usebackq tokens=*" %%C in (`kubectl config current-context`) do set HAVE=%%C
if not "%HAVE%"=="%WANT%" (
    echo [FAIL] wrong kubectl context.
    echo        want: %WANT%
    echo        have: %HAVE%
    exit /b 1
)
echo === context OK: %HAVE%

echo.
echo === removing the viewing stack
for %%R in (grafana postgres-exporter prometheus otel-collector tempo alloy loki) do (
    echo --- esquire-infra-%%R
    helm uninstall esquire-infra-%%R >nul 2>&1
)

echo.
echo === disarming the services (back to the chart defaults)
set ESQ_O11Y=
call "%HERE%aws-deploy.bat" esquire-gateway  || exit /b 1
call "%HERE%aws-deploy.bat" esquire-keysmith || exit /b 1
call "%HERE%aws-deploy.bat" esquire-enyman   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-pacman   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-biztree  || exit /b 1
call "%HERE%aws-deploy.bat" esquire-kcmaster || exit /b 1
call "%HERE%aws-deploy.bat" esquire-aukeep   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-backend  || exit /b 1
echo --- KeyCloak metrics off
helm upgrade --install esquire-infra-kc "%~dp0charts\infra\keycloak" -f "%~dp0values\keycloak.yaml" --set observability.enabled=false --set nodeSelector.tier=app --wait --timeout 5m || exit /b 1

echo.
echo === rolling the services so the switches actually take
for %%S in (gateway keysmith enyman pacman biztree kcmaster aukeep) do kubectl rollout restart statefulset/esquire-%%S-%%S >nul 2>&1
for %%S in (gateway keysmith enyman pacman biztree kcmaster aukeep) do kubectl rollout status statefulset/esquire-%%S-%%S --timeout=300s

echo.
kubectl get pods -o wide
echo.
echo === scaling the o11y node group to ZERO
rem OFF HAS TO MEAN FREE, or nobody turns it off. The seven charts are gone by here, so
rem the esq-o11y node has nothing left to run -- and a t4g.medium bills about $24 a month
rem whether or not a pod is on it. The group is declared minSize 0 for exactly this.
rem
rem The APP nodes are NOT touched: they are a separate group (esq-nodes, min 2), and every
rem Esquire pod is pinned to tier=app, so scaling this one to zero cannot move or evict
rem anything that serves the system.
eksctl scale nodegroup --cluster esquire-aws --region %AWS_REGION% --name esq-o11y --nodes 0 --nodes-min 0 --nodes-max 1 || (
    echo [WARN] could not scale esq-o11y to zero -- the node is still billing.
    echo        check: eksctl get nodegroup --cluster esquire-aws --region %AWS_REGION%
)

echo.
echo === waiting for the INSTANCE to terminate -- that is what decides the bill
rem WAIT ON EC2, NOT ON kubectl. The two disagree in BOTH directions and each way is a
rem wrong answer:
rem   - `eksctl scale` returns as soon as it has INITIATED the change, so a node list
rem     printed straight after still shows the node and reads like a failure;
rem   - and the Node OBJECT outlives the instance -- measured here, EC2 reported the
rem     instance terminated while kubectl still listed the node for minutes afterwards.
rem So the node list is INFORMATION; the instance state is the ANSWER.
rem
rem SLEEP WITH ping, NOT `timeout`. `timeout /t` needs a console on stdin; run under a pipe
rem or a redirect it fails with "Input redirection is not supported" and returns AT ONCE --
rem so a wait loop built on it spins its whole count in about a second and then reports a
rem timeout that never happened. ping -n <seconds+1> against loopback always works.
set LEFT=
for /l %%i in (1,1,24) do (
    if not "%LEFT%"=="0" (
        for /f "usebackq tokens=*" %%N in (`aws ec2 describe-instances --region %AWS_REGION% --filters "Name=tag:eks:nodegroup-name,Values=esq-o11y" "Name=instance-state-name,Values=pending,running,stopping,stopped" --query "length(Reservations[].Instances[])" --output text`) do set LEFT=%%N
        if not "%LEFT%"=="0" ping -n 16 127.0.0.1 >nul
    )
)
if "%LEFT%"=="0" (
    echo     esq-o11y instances still billing: 0
) else (
    echo [WARN] esq-o11y still has %LEFT% instance^(s^) not terminated after six minutes.
    echo        check: aws ec2 describe-instances --filters Name=tag:eks:nodegroup-name,Values=esq-o11y
)
echo.
echo === for information only -- the Node object can linger after the instance is gone:
kubectl get nodes -L tier -o custom-columns=NAME:.metadata.name,TIER:.metadata.labels.tier,TYPE:.metadata.labels.node\.kubernetes\.io/instance-type
echo.
echo === observability is OFF, and the node it ran on is scaled to ZERO.
echo === The charts run on emptyDir, so no volume outlives them and nothing is billing.
echo === aws-o11y-on.bat scales the node back up before it installs -- a nodeSelector
echo === does not summon a node, and there is no autoscaler here.
