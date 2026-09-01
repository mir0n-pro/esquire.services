@echo off

rem ===========================================================================
rem aws-deploy.bat -- put the CLASSIC topology on EKS, on real SNS / SQS / Kinesis.
rem
rem   aws-deploy.bat            deploy everything
rem   aws-deploy.bat <chart>    just one, e.g. aws-deploy.bat esquire-enyman
rem
rem WHY k8s-aws HAS ITS OWN CHARTS. k8s-oci reuses k8s/charts because OKE runs the
rem same shape with different values. EKS cannot: the AWS drivers are ATTACHED at
rem deployment, which needs an initContainer and a PropertiesLauncher command line,
rem and neither is reachable through values. k8s/ is not this task's to change, so
rem k8s-aws carries its own copies -- the same way k8s-compact does for its topology.
rem
rem NOTHING SECRET LIVES IN THIS TREE. RDS was created with an AWS-MANAGED master
rem password, so the password exists only in Secrets Manager. This script reads it at
rem run time and passes it to helm through a values file written to TEMP and deleted
rem when the run ends -- never inside this tree, never on a command line, never
rem committed. See the note beside AWSVALS for why a command line is the wrong place
rem for it, which cost a full failed deploy to learn.
rem
rem The DB ENDPOINT is read from AWS for the same reason -- a written-down endpoint
rem goes stale the first time the instance is recreated.
rem ===========================================================================

rem NO enabledelayedexpansion here, deliberately. The RDS managed-secret ARN contains
rem an exclamation mark -- "secret:rds!db-<uuid>" -- and with delayed expansion on, cmd
rem treats ! as a variable delimiter and silently eats the middle of the ARN. The call
rem then fails with "Secrets Manager can't find the specified secret", which points at
rem the wrong thing entirely.
setlocal

set HERE=%~dp0
set AWS_REGION=us-east-1
set RELEASE=esquire
set ONLY=%~1

rem --- context guard. This machine also runs a Docker Desktop cluster, and these
rem     charts are NOT the k8s ones: landing them on the wrong context produces a
rem     mess that looks like a deployment. Refuse rather than trust.
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

rem --- the database. IN THE CLUSTER, and there is no other option any more.
rem
rem     T3.4 measured the three: the pod, RDS and Aurora. The pod won on the only two
rem     grounds that matter here -- about 16x cheaper than RDS for 20% fewer writes per
rem     second, on the same PostgreSQL 17.11 -- and it is what T7 runs. RDS and Aurora
rem     were then deleted, so the switches that selected them are gone with them: a
rem     branch that names a resource nobody will recreate is a failure waiting for the
rem     next person who sets the variable. The measurements live in
rem     doc/plans/tasks1214.md and doc/research; the code does not need to carry them.
rem
rem     THE PASSWORD COMES FROM THE CLUSTER. It was read from the RDS managed secret
rem     while RDS was the database, and that broke the moment RDS was deleted. The
rem     in-cluster instance carries its own Kubernetes Secret and that is the only copy.
echo === the database is IN THE CLUSTER ^(esquire-infra-pg-postgres^)
set DB_HOST=esquire-infra-pg-postgres
rem The jsonpath MUST be quoted. Unquoted, PowerShell reads {.data.password} as a SCRIPT
rem BLOCK and hands kubectl something that is not a path -- the error names a format
rem parameter and says nothing about braces.
for /f "usebackq tokens=*" %%P in (`powershell -NoProfile -Command "$b = kubectl get secret esquire-infra-pg-postgres-secret -o jsonpath='{.data.password}'; [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b))"`) do set DB_PASS=%%P
if "%DB_PASS%"=="" (
    echo [FAIL] could not read the password from the esquire-infra-pg-postgres-secret Secret.
    echo        Is the database deployed? helm list ^| findstr esquire-infra-pg
    exit /b 1
)

echo     endpoint: %DB_HOST%
echo     password: (read from Secrets Manager, not shown^)

rem --- Amazon MQ (T3.1). OPTIONAL: with no broker these stay empty, the ActiveMQ
rem     buses in the topology stay unusable, and every SNS / SQS / Kinesis bus is
rem     untouched. NOTE the query has NO pipe in it on purpose -- a | inside a for/f
rem     backtick command must be written ^| for cmd, and the caret is NOT stripped
rem     inside a quoted PowerShell -Command, so it arrives as ^| and fails to parse.
echo === looking for an Amazon MQ broker ...
for /f "usebackq tokens=*" %%B in (`aws mq list-brokers --region %AWS_REGION% --query "BrokerSummaries[?BrokerName=='esquire-aws-mq'].BrokerId" --output text`) do set MQ_ID=%%B
if "%MQ_ID%"=="" (
    echo     no broker -- the ActiveMQ buses stay unusable
) else (
    for /f "usebackq tokens=*" %%E in (`aws mq describe-broker --region %AWS_REGION% --broker-id %MQ_ID% --query "BrokerInstances[0].Endpoints[0]" --output text`) do set MQ_URL=%%E
    rem The secret holds the PASSWORD ALONE as a plain string, not JSON. PowerShell strips
    rem the double quotes when it hands an argument to a native exe, so a JSON secret
    rem written that way is stored unquoted and cannot be read back.
    for /f "usebackq tokens=*" %%W in (`aws secretsmanager get-secret-value --region %AWS_REGION% --secret-id esquire-aws-mq --query SecretString --output text`) do set MQ_PASS=%%W
)

rem --- Amazon MSK (T3.2). OPTIONAL in exactly the same way: no cluster, no bootstrap list,
rem     and the audit-msk bus stays unusable while every other bus is untouched.
echo === looking for an Amazon MSK cluster ...
for /f "usebackq tokens=*" %%A in (`aws kafka list-clusters --region %AWS_REGION% --query "ClusterInfoList[?ClusterName=='esquire-aws-msk'].ClusterArn" --output text`) do set MSK_ARN=%%A
if "%MSK_ARN%"=="" (
    echo     no cluster -- the audit-msk bus stays unusable
) else (
    rem The PLAINTEXT list. TLS would be BootstrapBrokerStringTls, SASL/SCRAM BootstrapBrokerStringSaslScram;
    rem the cluster carries all of them, so which one is used is a choice made in the topology, not here.
    for /f "usebackq tokens=*" %%K in (`aws kafka get-bootstrap-brokers --region %AWS_REGION% --cluster-arn %MSK_ARN% --query "BootstrapBrokerString" --output text`) do set MSK_BOOTSTRAP=%%K
    echo     bootstrap: read from AWS
)


rem THE SAME FILE CARRIES THE MSK BOOTSTRAP LIST, and for the same reason: it is COMMA
rem SEPARATED, and helm's --set splits on commas -- "key ...:9092 has no value". Not a secret,
rem just unpassable on a command line. Every value here is single-quoted in the yaml.
rem The password NEVER goes on a command line. An RDS-managed password may contain
rem & | ^ < > , and \ -- cmd splits on the shell metacharacters, and helm's --set
rem splits on commas and eats backslashes. Both failures look like "The system
rem cannot find the file specified", which points nowhere near the real cause.
rem So it is written to a values file OUTSIDE the tree, in TEMP, and removed at the
rem end. PowerShell writes it, so the value is never re-parsed by a shell.
set "AWSVALS=%TEMP%\esquire-aws-values.%RANDOM%.yaml"
powershell -NoProfile -Command "$q = [char]39; $lines = @('db:', ('  host: ' + $env:DB_HOST), ('  password: ' + $q + ($env:DB_PASS -replace $q, ($q+$q)) + $q), 'mq:', ('  url: ' + $env:MQ_URL), ('  password: ' + $q + ($env:MQ_PASS -replace $q, ($q+$q)) + $q), 'msk:', ('  bootstrap: ' + $q + ($env:MSK_BOOTSTRAP -replace $q, ($q+$q)) + $q)); Set-Content -Path $env:AWSVALS -Encoding ascii -Value $lines"
if not exist "%AWSVALS%" (
    echo [FAIL] could not write the temporary values file.
    exit /b 1
)

echo.
echo === the shared bus catalog -- every bus, on every broker; a service names the one it uses
rem --set-file treats BACKSLASHES AS ESCAPES, so a Windows path arrives with its
rem separators eaten ("C:MyProjects..."). Hand it forward slashes.
set "TOPO=%HERE%esquire-topology.yml"
set "TOPO=%TOPO:\=/%"
helm upgrade --install %RELEASE%-topology "%HERE%charts\esquire-topology" ^
  --set-file topologyContent="%TOPO%" || exit /b 1

echo.
echo === KeyCloak (in-cluster, on its own volume -- PostgreSQL is in-cluster too now)
if "%ONLY%"=="" call :deployInfra keycloak
if "%ONLY%"=="keycloak" call :deployInfra keycloak

echo.
echo === the seven classic services
call :deploy esquire-gateway  gateway.yaml
call :deploy esquire-keysmith keysmith.yaml
call :deploy esquire-enyman   enyman.yaml
call :deploy esquire-pacman   pacman.yaml
call :deploy esquire-biztree  biztree.yaml
call :deploy esquire-kcmaster kcmaster.yaml
call :deploy esquire-aukeep   aukeep.yaml

echo.
echo === the BFF / explorer  (v1.2.13 image: it carries no Esquire Java jar)
call :deploy esquire-backend  backend.yaml

if exist "%AWSVALS%" del /q "%AWSVALS%"
echo.
kubectl get pods -o wide
goto :eof

rem --- deploy <chart> <valuesFile> ------------------------------------------
rem kcMaster needs the KeyCloak admin client secret, which must MATCH the one baked
rem into the realm in the keycloak image. It comes from the environment, never from
rem a file here -- same as k8s-up.bat does it. Set it before running:
rem     set KCMASTER_ADMIN_SECRET=...
:deploy
if not "%ONLY%"=="" if /i not "%ONLY%"=="%~1" exit /b 0
set "EXTRA="
if /i "%~1"=="esquire-kcmaster" (
    if "%KCMASTER_ADMIN_SECRET%"=="" (
        echo [FAIL] KCMASTER_ADMIN_SECRET is not set -- kcMaster cannot be deployed without it.
        exit /b 1
    )
    set "EXTRA=--set keycloak.adminClientSecret=%KCMASTER_ADMIN_SECRET%"
)
rem The BFF needs TWO. BFF_KC_SECRET is the esq-angular client secret and must MATCH
rem the realm baked into the keycloak image, or sign-in fails at the token exchange.
rem BFF_SESSION_SECRET is any random value -- it only signs the BFF's own cookie --
rem but it is still required, so the deploy refuses rather than inventing one.
if /i "%~1"=="esquire-backend" (
    if "%BFF_KC_SECRET%"=="" (
        echo [FAIL] BFF_KC_SECRET is not set -- the BFF cannot be deployed without it.
        exit /b 1
    )
    if "%BFF_SESSION_SECRET%"=="" (
        echo [FAIL] BFF_SESSION_SECRET is not set -- any random value will do, but it must be set.
        exit /b 1
    )
    set "EXTRA=--set keycloak.clientSecret=%BFF_KC_SECRET% --set session.secret=%BFF_SESSION_SECRET%"
)
rem --- PLACEMENT. Every Esquire pod is pinned to tier=app, and it is not cosmetic.
rem
rem     The o11y node group (esq-o11y) has its OWN IAM role, and the bus policy -- SNS,
rem     SQS, Kinesis -- is attached to the APP node role only. An Esquire pod that lands
rem     on the o11y node therefore comes up and then cannot reach the bus at all:
rem       tp-sqs: receive failed on esquire-kc-response-keysmith-0:
rem       User: arn:aws:sts::...:assumed-role/eksctl-esquire-aws-... is not authorized
rem     The pod stays NotReady, the service loses its endpoint, and the gateway answers
rem     503 -- which reads as a broken service, not as a scheduling accident.
rem
rem     It happened the moment the o11y node was added: the app nodes were full, so the
rem     scheduler put keySmith and KeyCloak on the only node with room. Pinning is what
rem     makes "turn observability on" incapable of moving an Esquire pod.
set "EXTRA=%EXTRA% --set nodeSelector.tier=app"

rem --- observability (T5). OFF unless asked for, on every target. The two arms:
rem       ESQ_O11Y=on     tracing + metrics on, as shipped -- histograms stay off
rem       ESQ_O11Y=full   the same, plus histograms and sampling 1.0
rem     Histograms are their own switch because they cost about 2.3x the scrape
rem     (4,597 -> 10,686 series) and are paid on EVERY scrape whether or not anyone
rem     looks. They also carry the EXEMPLARS: a trace id hangs off a bucket sample,
rem     so with buckets off the metric-to-trace hop is wired and inert.
if /i "%ESQ_O11Y%"=="on"   set "EXTRA=%EXTRA% --set observability.enabled=true"
if /i "%ESQ_O11Y%"=="full" set "EXTRA=%EXTRA% --set observability.enabled=true --set observability.metricsHistograms=true --set tracing.samplingRatio=1.0"

rem --- where the spans GO (T6). The services push OTLP and know nothing about what is
rem     on the other end -- that is the portability seam, and this one optional switch is
rem     the whole of what it takes to move them. Unset, the chart default points at the
rem     Prometheus/Grafana collector; ESQ_OTLP points them at the CloudWatch one instead.
rem     Nothing else changes: same image, same jar, same bytes.
if defined ESQ_OTLP set "EXTRA=%EXTRA% --set tracing.otlpEndpoint=%ESQ_OTLP%"

rem --- which broker carries the three buses (T3.1) --------------------------
rem ESQ_BUS=mq points them at the Amazon MQ broker; anything else leaves them on
rem SNS / SQS / Kinesis, which is the T2 shape. --set beats a values file, and a
rem value no template reads is simply ignored, so the same three flags are safe on
rem every service that carries a bus at all. The gateway and the BFF carry none.
if /i "%~1"=="esquire-gateway" goto :busDone
if /i "%~1"=="esquire-backend" goto :busDone
if /i "%ESQ_BUS%"=="mq" set "EXTRA=%EXTRA% --set entity.busId=esquire.entity-mq --set kc.busId=esquire.kc-mq --set audit.busId=audit-mq"
rem ESQ_AUDIT_BUS moves the AUDIT bus ALONE, on top of whatever ESQ_BUS chose -- e.g. audit-msk to put
rem auKeep on Amazon MSK while the entity and identity buses stay where they are. Set after ESQ_BUS so it
rem wins; helm takes the LAST --set for a key.
if not "%ESQ_AUDIT_BUS%"=="" set "EXTRA=%EXTRA% --set audit.busId=%ESQ_AUDIT_BUS%"
:busDone

rem RELEASE NAME == CHART NAME, matching k8s-up.bat. Prefixing anything here gives
rem "esquire-esquire-biztree-biztree-0", because the chart already starts with
rem esquire- and then appends its own component name and the StatefulSet ordinal.
echo --- %~1
helm upgrade --install %~1 "%HERE%charts\%~1" ^
  -f "%HERE%values\%~2" ^
  -f "%AWSVALS%" ^
  %EXTRA% ^
  --wait --timeout 5m
exit /b %errorlevel%

rem --- deployInfra <name> ---------------------------------------------------
rem esquire-infra-kc, the name k8s-up.bat uses for the same chart.
:deployInfra
echo --- infra/%~1
helm upgrade --install esquire-infra-kc "%HERE%charts\infra\%~1" ^
  -f "%HERE%values\%~1.yaml" ^
  --wait --timeout 5m
exit /b %errorlevel%
