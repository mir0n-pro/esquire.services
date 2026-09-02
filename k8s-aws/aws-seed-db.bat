@echo off

rem ===========================================================================
rem aws-seed-db.bat -- apply db.seed to the RDS instance, once.
rem
rem Everywhere else PostgreSQL is a container built from db.seed and seeds itself
rem on an empty volume. A managed instance has no image, so this is that step --
rem run it AFTER the cluster exists and BEFORE the services are deployed, or every
rem service starts against a database with no schema and crashloops on a fault
rem that looks like a connection problem.
rem
rem It runs the ordinary esquire-postgres image as a Job inside the cluster --
rem which is also the only place that can reach RDS, since the instance is in the
rem private subnets with a security group that admits the node group and nothing
rem else. There is no path to it from this machine, by design.
rem
rem No delayed expansion: the RDS managed-secret ARN contains an exclamation mark.
rem ===========================================================================

setlocal

set HERE=%~dp0
set AWS_REGION=us-east-1

for /f "usebackq tokens=*" %%A in (`aws sts get-caller-identity --query Account --output text`) do set ACCT=%%A
set WANT=arn:aws:eks:%AWS_REGION%:%ACCT%:cluster/esquire-aws
for /f "usebackq tokens=*" %%C in (`kubectl config current-context`) do set HAVE=%%C
if not "%HAVE%"=="%WANT%" (
    echo [FAIL] wrong kubectl context -- run 1st.bat
    exit /b 1
)

for /f "usebackq tokens=*" %%E in (`aws rds describe-db-instances --region %AWS_REGION% --db-instance-identifier esquire-aws-pg --query "DBInstances[0].Endpoint.Address" --output text`) do set DB_HOST=%%E
for /f "usebackq tokens=*" %%S in (`aws rds describe-db-instances --region %AWS_REGION% --db-instance-identifier esquire-aws-pg --query "DBInstances[0].MasterUserSecret.SecretArn" --output text`) do set SECRET_ARN=%%S
for /f "usebackq tokens=*" %%W in (`powershell -NoProfile -Command "$j = aws secretsmanager get-secret-value --region %AWS_REGION% --secret-id '%SECRET_ARN%' --query SecretString --output text; (ConvertFrom-Json $j).password"`) do set DB_PASS=%%W

if "%DB_HOST%"=="" goto :nodb
if "%DB_HOST%"=="None" goto :nodb
if "%DB_PASS%"=="" goto :nodb

echo === seeding %DB_HOST%

rem QUOTE THE PASSWORD. An RDS-managed password contains & | ^ < > and cmd splits
rem the command on them long before kubectl sees it -- the error is "The system
rem cannot find the file specified", which names nothing useful. RDS excludes the
rem double quote from generated passwords, so quoting is safe and sufficient.
kubectl delete secret esquire-rds-admin --ignore-not-found >nul
kubectl create secret generic esquire-rds-admin ^
  --from-literal="DB_HOST=%DB_HOST%" ^
  --from-literal="DB_ADMIN_PASSWORD=%DB_PASS%" || exit /b 1

rem DELETE FIRST, AND SAY SO. A Job's pod template is IMMUTABLE: `kubectl apply` over
rem an existing Job does not change what it runs, so an edited manifest silently keeps
rem running the OLD script -- which cost three "why is my fix not taking effect" rounds.
rem --wait makes the delete finish before the apply, and the output is NOT sent to nul,
rem because that is what hid it.
kubectl delete job esquire-db-seed --ignore-not-found --wait=true
kubectl apply -f "%HERE%db-seed-job.yaml" || exit /b 1

echo === waiting for the seed to finish (up to 10 minutes) ...
kubectl wait --for=condition=complete job/esquire-db-seed --timeout=600s
set RC=%errorlevel%

echo.
echo === seed log
kubectl logs job/esquire-db-seed --tail=40

if not "%RC%"=="0" (
    echo.
    echo [FAIL] the seed did not complete. The log above says why.
    exit /b 1
)
echo.
echo === seeded. The services can be deployed now.
goto :eof

:nodb
echo [FAIL] could not read the RDS endpoint or password -- is esquire-aws-pg available?
exit /b 1
