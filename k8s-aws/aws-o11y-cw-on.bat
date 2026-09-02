@echo off
rem ===========================================================================
rem aws-o11y-cw-on.bat -- Esquire's observability on the AWS-NATIVE stack.
rem
rem   logs     CloudWatch Logs   (fluent-bit DaemonSet)
rem   traces   AWS X-Ray         (otel-cw collector, awsxray exporter)
rem   metrics  CloudWatch        (the same /actuator/prometheus page, awsemf exporter)
rem   viewing  the AWS console
rem
rem THIS IS THE PROOF OF PORTABILITY, and it is worth being clear about what it proves.
rem The services are not rebuilt, not reconfigured in code, and not even aware of the
rem change. Their image digests are the ones that ran against Prometheus, Grafana, Loki
rem and Tempo. What moves is ONE deployment value -- where the spans are posted -- plus
rem two charts that read what the services were already publishing.
rem
rem ISOLATED FROM THE PROMETHEUS/GRAFANA ARMS BY DESIGN. This script installs only
rem charts\cw\*, and aws-o11y-on.bat installs only charts\infra\*. Neither reads the
rem other's values and neither uninstalls the other's releases. The two stacks are
rem alternatives, and either can be proven without putting the other at risk.
rem
rem THE ONE THING THAT IS NOT FREE: both backends cannot receive the TRACES, because a
rem service posts OTLP to ONE endpoint. Metrics are pulled and logs are tailed, so those
rem two pillars can feed both at once; traces go where this script points them. Run
rem aws-o11y-off.bat first if the other stack is up.
rem
rem   X-Ray:      console -> CloudWatch -> X-Ray traces -> Traces
rem   Metrics:    console -> CloudWatch -> Metrics -> Esquire
rem   Logs:       console -> CloudWatch -> Logs Insights -> /esquire/services
rem
rem NO o11y NODE IS NEEDED. CloudWatch stores nothing here -- there is no Prometheus, no
rem Loki, no Tempo, no Grafana to host -- so the esq-o11y node group stays at zero and
rem the only compute added is one small collector and a log shipper on the app nodes.
rem That is the shape difference between renting a backend and running one.
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
echo === IAM: the node role must be allowed to write traces, metrics and logs
rem The pods use the NODE role. This is what the whole port rests on, and getting it
rem wrong fails SILENTLY -- the exporter logs an AccessDenied and the console simply
rem stays empty, which reads as "nothing is being sent" rather than "not allowed".
for /f "usebackq tokens=*" %%G in (`aws eks list-nodegroups --cluster-name esquire-aws --region %AWS_REGION% --query "nodegroups[]" --output text`) do set NGS=%%G
for %%N in (%NGS%) do (
    for /f "usebackq tokens=*" %%R in (`aws eks describe-nodegroup --cluster-name esquire-aws --nodegroup-name %%N --region %AWS_REGION% --query "nodegroup.nodeRole" --output text`) do (
        for /f "tokens=2 delims=/" %%S in ("%%R") do (
            echo --- attaching esquire-cw to %%S
            aws iam put-role-policy --role-name %%S --policy-name esquire-cw --policy-document file://"%HERE%esquire-cw-policy.json" || exit /b 1
        )
    )
)

echo.
echo === the CloudWatch collector (traces -^> X-Ray, metrics -^> CloudWatch)
helm upgrade --install esquire-cw-otel "%CH%\cw\otel-cw" -f "%HERE%values\cw-otel.yaml" --wait --timeout 5m || exit /b 1

echo.
echo === the log shipper (pod stdout -^> CloudWatch Logs)
helm upgrade --install esquire-cw-logs "%CH%\cw\fluent-bit" -f "%HERE%values\cw-fluent-bit.yaml" --wait --timeout 5m || exit /b 1

echo.
echo === arming the services, and pointing their spans at the CloudWatch collector
set ESQ_O11Y=on
set ESQ_OTLP=http://esquire-cw-otel:4318/v1/traces
call "%HERE%aws-deploy.bat" esquire-gateway  || exit /b 1
call "%HERE%aws-deploy.bat" esquire-keysmith || exit /b 1
call "%HERE%aws-deploy.bat" esquire-enyman   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-pacman   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-biztree  || exit /b 1
call "%HERE%aws-deploy.bat" esquire-kcmaster || exit /b 1
call "%HERE%aws-deploy.bat" esquire-aukeep   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-backend  || exit /b 1

echo.
echo === rolling the services so they pick the switches up
rem A ConfigMap change does NOT restart a pod: helm reports "deployed", the map is right,
rem and the running container keeps its mounted copy. Roll them, or nothing is armed.
for %%S in (gateway keysmith enyman pacman biztree kcmaster aukeep) do kubectl rollout restart statefulset/esquire-%%S-%%S >nul 2>&1
for %%S in (gateway keysmith enyman pacman biztree kcmaster aukeep) do kubectl rollout status statefulset/esquire-%%S-%%S --timeout=300s

echo.
kubectl get pods -o wide
echo.
echo === observability is ON, on AWS's own stack. Nothing was rebuilt to do it.
echo     Metrics  aws cloudwatch list-metrics --namespace Esquire
echo     Traces   aws xray get-trace-summaries --start-time ^<t^> --end-time ^<t^>
echo     Logs     aws logs describe-log-streams --log-group-name /esquire/services
echo.
echo     aws-o11y-cw-off.bat removes it AND deletes the log groups -- a group left
echo     behind bills at $0.03/GB-month forever and never looks like a running thing.
endlocal
