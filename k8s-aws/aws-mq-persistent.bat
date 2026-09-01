@echo off

rem ===========================================================================
rem aws-mq-persistent.bat -- change the ActiveMQ delivery mode on the RUNNING
rem system. No pod roll, no restart, no redeploy of any service.
rem
rem   aws-mq-persistent.bat true      every send waits for a broker ack
rem   aws-mq-persistent.bat false     sends do not wait
rem   aws-mq-persistent.bat           show what is set now
rem
rem HOW IT WORKS, and why nothing has to restart. The value lives as one key of
rem the esquire-topology ConfigMap, which every service ALREADY mounts at
rem /etc/esquire. The kubelet rewrites a mounted key in place, and tp-activemq
rem re-reads the file when its timestamp moves (at most one look a second), so a
rem helm upgrade of this ONE chart reaches every running leg.
rem
rem THE TOPOLOGY STILL DECLARES THE VALUE. This file only overrides it, and only
rem while it is there: delete the key, or run aws-deploy.bat, and every leg goes
rem back to what esquire-topology.yml says. That is deliberate -- the value a
rem reader sees in the topology has to be the one that governs by default.
rem
rem PROPAGATION IS NOT INSTANT. The kubelet refreshes a mounted ConfigMap on its
rem own sync period, which is up to about a minute. The script waits for the
rem change to actually show up in a pod rather than claiming it landed.
rem ===========================================================================

setlocal

set HERE=%~dp0
set AWS_REGION=us-east-1
set WANT=%~1

for /f "usebackq tokens=*" %%C in (`kubectl config current-context`) do set HAVE=%%C
echo %HAVE% | findstr /C:"cluster/esquire-aws" >nul
if errorlevel 1 (
    echo [FAIL] wrong kubectl context: %HAVE%
    echo        run 1st.bat
    exit /b 1
)

if "%WANT%"=="" (
    echo === the ConfigMap says:
    kubectl get configmap esquire-topology -o jsonpath="{.data.mq-persistent}"
    echo.
    echo === the pods say:
    call :show
    exit /b 0
)
if /i not "%WANT%"=="true" if /i not "%WANT%"=="false" (
    echo [FAIL] give it true or false, not "%WANT%".
    exit /b 1
)

set "TOPO=%HERE%esquire-topology.yml"
set "TOPO=%TOPO:\=/%"
echo === setting mq-persistent=%WANT% ...
helm upgrade --install esquire-topology "%HERE%charts\esquire-topology" ^
  --set-file topologyContent="%TOPO%" ^
  --set mqPersistent=%WANT% >nul || exit /b 1

echo === waiting for the kubelet to put it in front of the pods ...
for /l %%i in (1,1,24) do (
    call :check %WANT%
    if not errorlevel 1 goto :landed
rem SLEEP WITH ping, NOT `timeout`. `timeout /t` needs a console on stdin; run under a pipe
rem or a redirect it fails with "Input redirection is not supported" and returns AT ONCE --
rem so a wait loop built on it spins its whole count in about a second and then reports a
rem timeout that never happened. ping -n <seconds+1> against loopback always works.
    ping -n 6 127.0.0.1 >nul
)
echo [FAIL] the pods still do not see %WANT% after two minutes.
call :show
exit /b 1

:landed
echo === landed. Every leg is now persistent=%WANT%, and nothing restarted:
call :show
kubectl get pods --no-headers -o custom-columns=NAME:.metadata.name,RESTARTS:.status.containerStatuses[0].restartCount,AGE:.metadata.creationTimestamp
exit /b 0

rem --- check <want> : errorlevel 0 when enyMan's mounted file says <want> -----
:check
for /f "usebackq tokens=*" %%V in (`kubectl exec esquire-enyman-enyman-0 -c enyman -- cat /etc/esquire/mq-persistent 2^>nul`) do set SEEN=%%V
if /i "%SEEN%"=="%~1" exit /b 0
exit /b 1

rem --- show : what each service has mounted right now ------------------------
:show
for %%P in (enyman keysmith kcmaster pacman biztree aukeep) do (
    for /f "usebackq tokens=*" %%V in (`kubectl exec esquire-%%P-%%P-0 -c %%P -- cat /etc/esquire/mq-persistent 2^>nul`) do echo     %%P : %%V
)
exit /b 0
