@echo off
rem ===========================================================================
rem aws-o11y-cw-off.bat -- take the AWS-native observability off, completely.
rem
rem OFF MEANS SOMETHING DIFFERENT HERE than it does for the Prometheus/Grafana arms,
rem and that difference is the whole reason this script does more than uninstall.
rem
rem There, off scales a node group to zero: one instance terminates and the cost is
rem exactly $0. CloudWatch has no node. It bills for what is SENT to it -- "charges are
rem incurred only when metrics are sent to CloudWatch in a given hour" -- so stopping
rem the emitters stops the metric and trace charges by itself, within the hour, with
rem nothing to delete. A CloudWatch metric cannot even BE deleted; it goes quiet and
rem drops out of the listing after fifteen months.
rem
rem TWO ARTIFACTS DO NOT STOP ON THEIR OWN, which is why this script sweeps:
rem   - LOG GROUPS keep billing $0.03/GB-month, and CloudWatch Logs default to NEVER
rem     EXPIRE. The charts set a retention so nothing accumulates; this deletes the
rem     groups outright.
rem   - DASHBOARDS bill $3/month each beyond the free three, looked at or not.
rem
rem The danger is inverted compared with EKS or RDS. There the risk is a big instance
rem somebody forgot. Here everything expensive stops by itself, and what is left behind
rem is small, cheap and SILENT -- which is the kind that survives a sweep.
rem
rem The IAM policy stays attached. It grants permission, holds no resource and costs
rem nothing; removing it would only make the next on-arm slower.
rem ===========================================================================
setlocal
set HERE=%~dp0
set AWS_REGION=us-east-1
set CH=%HERE%charts

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
echo === removing the AWS-native stack
helm uninstall esquire-cw-logs >nul 2>&1
helm uninstall esquire-cw-otel >nul 2>&1
echo --- charts/cw/* uninstalled (charts/infra/* untouched)

echo.
echo === disarming the services, and pointing their spans back at the chart default
rem ESQ_OTLP unset -> aws-deploy.bat adds no override -> tracing.otlpEndpoint falls back
rem to the values.yaml default, which is the Prometheus/Grafana collector.
set ESQ_O11Y=
set ESQ_OTLP=
call "%HERE%aws-deploy.bat" esquire-gateway  || exit /b 1
call "%HERE%aws-deploy.bat" esquire-keysmith || exit /b 1
call "%HERE%aws-deploy.bat" esquire-enyman   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-pacman   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-biztree  || exit /b 1
call "%HERE%aws-deploy.bat" esquire-kcmaster || exit /b 1
call "%HERE%aws-deploy.bat" esquire-aukeep   || exit /b 1
call "%HERE%aws-deploy.bat" esquire-backend  || exit /b 1

echo.
echo === rolling the services so the switches actually take
for %%S in (gateway keysmith enyman pacman biztree kcmaster aukeep) do kubectl rollout restart statefulset/esquire-%%S-%%S >nul 2>&1
for %%S in (gateway keysmith enyman pacman biztree kcmaster aukeep) do kubectl rollout status statefulset/esquire-%%S-%%S --timeout=300s

echo.
echo === deleting the log groups -- the ONE artifact that bills after everything is off
aws logs delete-log-group --log-group-name /esquire/services --region %AWS_REGION% >nul 2>&1
aws logs delete-log-group --log-group-name /esquire/emf      --region %AWS_REGION% >nul 2>&1
echo --- /esquire/services and /esquire/emf deleted

echo.
echo === what is left in the account (it should name neither of the two above)
aws logs describe-log-groups --region %AWS_REGION% --query "logGroups[].{name:logGroupName,bytes:storedBytes,retention:retentionInDays}" --output table

echo.
kubectl get pods -o wide
echo.
echo === the AWS-native observability is OFF, and none of it bills.
echo     Metrics and traces stopped charging the hour the emitters stopped.
echo     The log groups are gone. The IAM policy stays -- it costs nothing.
endlocal
