@echo off

rem ===========================================================================
rem aws-up.bat -- deploy the SUPER-COMPACT Esquire stack on EKS.
rem
rem   aws-up.bat
rem
rem FOUR APPLICATION PROCESSES instead of seven -- Mesnie (enyMan + keySmith + the
rem identity work), gateWard (the gate + the bizTree cache), pacMan and the BFF --
rem at ONE replica each. With Postgres and KeyCloak that is seven pods against the
rem thirteen the classic shape runs.
rem
rem SUPER-COMPACT = the compact composition with audit on option (a), DB triggers:
rem no auKeep process, no audit bus traffic, the audit stack out of the application
rem entirely.
rem
rem THE BUSES ARE ONE. esquire.entity on real Amazon SNS, and audit-off. There is no
rem identity request/response bus (Mesnie serves identity in process) and no audit
rem bus. So of the two AWS modules only tp-sqns is on loader.path, and of the two
rem providers it carries only sns is named -- but SQS still carries every entity
rem message, because a queue is what SNS delivers into. What this shape drops is the
rem sqs PROVIDER and the request/response bus it carried, not SQS itself. Kinesis
rem carries nothing and NO STREAM IS CREATED, which is the part that shows on the
rem bill: a stream is charged per hour whether or not a record is written.
rem
rem CHARTS COME FROM TWO PLACES, and the split is the whole design of this folder:
rem   charts\             mesnie, gateward, pacman -- copies that carry the AWS driver
rem                       attach (an initContainer and a PropertiesLauncher command
rem                       line, neither reachable through values)
rem   ..\k8s-compact      backend, topology, infra\postgres, infra\keycloak --
rem                       BORROWED UNCHANGED. They need no AWS delta, so copying them
rem                       would only create two files to keep in step.
rem
rem RUN aws-cluster-up.bat FIRST, and let the DNS record resolve before this.
rem ===========================================================================

setlocal
cd /d "%~dp0"

set HERE=%~dp0
set CLUSTER_NAME=esquire-aws-compact
set AWS_REGION=us-east-1
set CHARTS=..\k8s-compact\charts
set OWN=charts

rem --- context guard ---------------------------------------------------------
rem This machine runs a Docker Desktop cluster and can run the CLASSIC AWS cluster
rem too. These charts are not those charts: landing them on the wrong context
rem produces something that looks like a deployment. Refuse rather than trust.
for /f "usebackq tokens=*" %%A in (`aws sts get-caller-identity --query Account --output text`) do set ACCT=%%A
set WANT=arn:aws:eks:%AWS_REGION%:%ACCT%:cluster/%CLUSTER_NAME%
for /f "usebackq tokens=*" %%C in (`kubectl config current-context`) do set HAVE=%%C
if not "%HAVE%"=="%WANT%" (
    echo [FAIL] wrong kubectl context.
    echo        want: %WANT%
    echo        have: %HAVE%
    echo        run 1st.bat
    exit /b 1
)
echo === context OK: %HAVE%

rem --- what this needs, and it FAILS rather than substituting ----------------
rem oke-up.bat warns and carries on, because OKE is a standing deployment where a
rem release already holds a good value and overwriting it with a sentinel would be
rem worse than leaving it. This cluster is created fresh every time, so there is
rem never a good value to protect -- a missing secret here can only produce a stack
rem that comes up and cannot authenticate.
if "%image_tag%"=="" (
    echo [FAIL] image_tag is not set. The release tag pushed by aws-images-push.bat:
    echo          set image_tag=vMajor.Minor.Micro-YYMM.DDHH
    exit /b 1
)
if "%mir0n_pwd%"=="" (
    echo [FAIL] mir0n_pwd is not set -- the postgres and KeyCloak admin password.
    exit /b 1
)
if "%kcmaster_admin_secret%"=="" (
    echo [FAIL] kcmaster_admin_secret is not set. Mesnie carries the identity work in
    echo        process, so it is Mesnie that authenticates to KeyCloak with it.
    echo        KeyCloak: realm esquire, clients, esq-kcMaster, Credentials.
    exit /b 1
)
if "%bff_kc_secret%"=="" (
    echo [FAIL] bff_kc_secret is not set -- the esq-angular client secret. It must MATCH
    echo        the realm baked into the keycloak image, or sign-in fails at the token
    echo        exchange. KeyCloak: realm esquire, clients, esq-angular, Credentials.
    exit /b 1
)
if "%bff_session_secret%"=="" (
    echo [FAIL] bff_session_secret is not set. Any random value will do -- it only signs
    echo        the BFF cookie -- but it is required, so this refuses rather than
    echo        inventing one.
    exit /b 1
)

set IMAGE_TAG=%image_tag%
set PG_PW=%mir0n_pwd%
set KC_PW=%mir0n_pwd%

echo.
echo === the messaging-bus topology -- TWO buses: esquire.entity on SNS, and audit-off
rem The ConfigMap every service mounts at /etc/esquire/topology.yml as a REQUIRED volume.
rem It MUST exist before the services, or their pods hang in ContainerCreating.
rem --set-file treats BACKSLASHES AS ESCAPES, so a Windows path arrives with its
rem separators eaten ("C:MyProjects..."). Hand it forward slashes.
set "TOPO=%HERE%esquire-topology.yml"
set "TOPO=%TOPO:\=/%"
helm upgrade --install esquire-topology %CHARTS%\esquire-topology --force-conflicts ^
  --set-file topologyContent="%TOPO%" || exit /b 1

echo.
echo === postgres (in-cluster, on a gp3 volume; it seeds itself on an empty PGDATA)
rem --force-conflicts: helm 4 applies SERVER-SIDE, and anything that scales OUTSIDE helm
rem owns .spec.replicas -- an upgrade that then sets replicas is REFUSED.
helm upgrade --install esquire-infra %CHARTS%\infra\postgres --force-conflicts ^
  -f values\postgres.yaml ^
  --set db.password=%PG_PW% ^
  --wait --timeout 10m || exit /b 1

echo.
echo === keycloak (in-cluster, embedded database, its own gp3 volume)
helm upgrade --install esquire-infra-kc %CHARTS%\infra\keycloak --force-conflicts ^
  -f values\keycloak.yaml ^
  --set keycloak.adminPassword=%KC_PW% ^
  --wait --timeout 10m || exit /b 1

echo.
echo === the audit triggers
rem SUPER-COMPACT AUDITS WITH DATABASE TRIGGERS AND NOTHING ELSE, and the image does
rem not install them. services/postgres/initdb/init.sh applies create/all.sql and
rem fill/all.sql on an empty PGDATA -- triggers/all.sql is COPIED INTO THE IMAGE and
rem never run. So audit-off on a fresh volume means no audit at all, silently: every
rem write succeeds, no *_log row appears, and nothing reports a problem.
rem
rem It is applied here, on every deploy, because the trigger DDL drops and recreates:
rem a redeploy onto an existing volume lands the same state rather than an error.
rem
rem The \i includes in all.sql are RELATIVE (../triggers/x.sql), so psql has to run
rem with /db-seed/triggers as its working directory or every include misses.
kubectl exec esquire-infra-postgres-0 -- sh -c "cd /db-seed/triggers && psql -v ON_ERROR_STOP=1 -U esq2025 -d esq2025 -f all.sql" || exit /b 1
echo --- audit triggers applied

echo.
echo === mesnie  (the household: enyMan + keySmith + the identity work)
helm upgrade --install esquire-mesnie %OWN%\esquire-mesnie --force-conflicts ^
  -f values\mesnie.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set awsDrivers.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% ^
  --set keycloak.adminClientSecret=%kcmaster_admin_secret% ^
  --wait --timeout 5m || exit /b 1

echo.
echo === pacman
helm upgrade --install esquire-pacman %OWN%\esquire-pacman --force-conflicts ^
  -f values\pacman.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set awsDrivers.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% ^
  --wait --timeout 5m || exit /b 1

echo.
echo === gateward  (the gate + the bizTree cache)
rem It loads the whole tree from the database at start, so it comes up after postgres is
rem serving; its readiness gate is what keeps a cold cache from answering.
helm upgrade --install esquire-gateward %OWN%\esquire-gateward --force-conflicts ^
  -f values\gateward.yaml ^
  --set image.tag=%IMAGE_TAG% ^
  --set awsDrivers.tag=%IMAGE_TAG% ^
  --set db.password=%PG_PW% ^
  --wait --timeout 5m || exit /b 1

echo.
echo === backend  (the BFF: the SPA on /, /auth/* and the /api/* proxy)
helm upgrade --install esquire-backend %CHARTS%\esquire-backend --force-conflicts ^
  -f values\backend.yaml ^
  --set keycloak.clientSecret=%bff_kc_secret% ^
  --set session.secret=%bff_session_secret% ^
  --wait --timeout 5m || exit /b 1

echo.
echo === the public ingress
rem Applied AFTER the BFF is ready -- the ingress routes / to it, and a live route to a
rem workload that is not serving yet is a 503 somebody will read as a broken deploy.
kubectl apply -f cluster\ingress.yaml || exit /b 1

echo.
kubectl get pods -o wide
echo.
echo --- certificate (pending until aws-esquire.mir0n.pro resolves to the load balancer):
kubectl get certificate
echo.
echo === super-compact is up. Verify with aws-e2e-public.bat
