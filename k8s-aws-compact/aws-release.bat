@echo off

rem ===========================================================================
rem aws-release.bat -- put the standing EKS deployment on a RELEASE tag.
rem
rem   aws-release.bat v1.2.14-2609.0223
rem
rem ONE COMMAND, and it is the normal way this cluster is updated. A sprint is
rem released, CI publishes the images, and this moves aws-esquire.mir0n.pro onto
rem them. What it does NOT do is rebuild them.
rem
rem WHY THIS IS NOT aws-images-push.bat. That script is the LAB arm: it builds the
rem three service images from the working tree, arm64 only, for a tag nothing else
rem publishes. Pointing it at a release tag would push local arm64-only images over
rem CI's multi-arch manifests -- the released artifact would stop being the released
rem artifact, on every target, not only this one. So the two arms are separate
rem scripts and neither can be mistaken for the other.
rem
rem WHAT IS BUILT HERE: esquire-tp-aws, the AWS driver carrier, and nothing else.
rem No pipeline builds it -- the drivers are attached at deployment and belong to no
rem service image, which is the property the whole AWS design rests on. IT IS BUILT
rem FROM THE WORKING TREE, so run this from a tree that matches the tag being
rem released; the check below prints the jar dates so a stale build is visible.
rem
rem WHAT COMES FROM CI: esquire.mesnie, esquire.gateward, esquire.pacman (this repo)
rem and esquire.backend (the explorer repo). All four are verified present with an
rem arm64 image before anything is pushed or deployed -- the node group is Graviton,
rem and an amd64-only tag would fail as a pod that never starts.
rem
rem AFTERWARDS: aws-e2e-public.bat.
rem ===========================================================================

setlocal

cd /d "%~dp0"

set REG=ghcr.io/mir0n-pro
set AWS_REGION=us-east-1
set CLUSTER_NAME=esquire-aws-compact
set PLATFORM=linux/arm64
set HERE=%~dp0
set SERVICES=%HERE%..

if "%~1"=="" (
    echo usage: aws-release.bat ^<release tag^>     e.g. v1.2.14-2609.0223
    echo.
    echo        The tag CI published. For a tag built from this tree, the lab arm is
    echo        aws-images-push.bat -- it is a different job and a different script.
    exit /b 1
)
set TAG=%~1

rem --- context guard ---------------------------------------------------------
rem FIRST, before a byte is pushed. This machine runs a Docker Desktop cluster and
rem can run the classic AWS cluster too, and these charts are not those charts.
rem Landing them on the wrong context produces something that looks like a
rem deployment. Refuse rather than trust.
for /f "usebackq tokens=*" %%A in (`aws sts get-caller-identity --query Account --output text`) do set ACCT=%%A
if "%ACCT%"=="" (
    echo [FAIL] no AWS credential -- run aws configure, then aws-login.bat
    exit /b 1
)
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

rem --- the deploy inputs, checked HERE ---------------------------------------
rem aws-up.bat checks these too and its messages say where each value lives. They
rem are checked again up front because everything between here and there -- the
rem registry probes and a cross-platform image build -- costs minutes, and finding
rem out afterwards that a password was missing wastes all of it.
set MISSING=
if "%mir0n_pwd%"=="" set MISSING=%MISSING% mir0n_pwd
if "%kcmaster_admin_secret%"=="" set MISSING=%MISSING% kcmaster_admin_secret
if "%bff_kc_secret%"=="" set MISSING=%MISSING% bff_kc_secret
if "%bff_session_secret%"=="" set MISSING=%MISSING% bff_session_secret
if not "%MISSING%"=="" (
    echo [FAIL] not set:%MISSING%
    echo        aws-up.bat names what each one is and where it is read from.
    exit /b 1
)
echo === deploy inputs present

echo.
echo === the images CI published, at %TAG%
call :ci esquire.mesnie
if errorlevel 1 exit /b 1
call :ci esquire.gateward
if errorlevel 1 exit /b 1
call :ci esquire.pacman
if errorlevel 1 exit /b 1
call :ci esquire.backend
if errorlevel 1 exit /b 1

echo.
echo === the driver jars this build carries
rem Dockerfile.tp-aws COPYs both tp-* jars AND their target/aws-lib trees, so a tree
rem that has not been packaged fails at the COPY. The dates are printed because the
rem jar is built from the working tree: an old one builds a carrier that is a release
rem behind, and nothing downstream notices.
if not exist "%SERVICES%\tp-sqns\target\esquire-tp-sqns.jar" goto :nojars
if not exist "%SERVICES%\tp-kinesis\target\esquire-tp-kinesis.jar" goto :nojars
for %%F in ("%SERVICES%\tp-sqns\target\esquire-tp-sqns.jar") do echo   %%~tF  %%~nxF
for %%F in ("%SERVICES%\tp-kinesis\target\esquire-tp-kinesis.jar") do echo   %%~tF  %%~nxF

echo.
echo === the AWS driver carrier -- the one image no pipeline builds
docker buildx inspect esq-builder >nul 2>&1
if errorlevel 1 (
    echo --- creating the docker-container builder ^(the default driver cannot push cross-platform^)
    docker buildx create --name esq-builder --driver docker-container --bootstrap || exit /b 1
)
docker buildx use esq-builder || exit /b 1
docker buildx build --platform %PLATFORM% -t "%REG%/esquire-tp-aws:%TAG%" --push ^
  -f "%SERVICES%\k8s-aws\Dockerfile.tp-aws" "%SERVICES%" || exit /b 1

echo.
echo === deploying at %TAG%
rem ONE TAG FOR THE WHOLE STACK. On a release the three service images, the BFF and
rem the carrier are all published under the same name, so image_tag and backend_tag
rem are the same value here. They are two inputs because on a lab tag they are not.
set image_tag=%TAG%
set backend_tag=%TAG%
rem THE FULL PATH, not the bare name. With NoDefaultCurrentDirectoryInExePath set --
rem and it is set on this machine -- cmd does not search the current directory, so
rem `call aws-up.bat` fails with "not recognized" from inside its own folder.
call "%HERE%aws-up.bat" || exit /b 1

echo.
echo === %CLUSTER_NAME% is on %TAG%. Verify with aws-e2e-public.bat
goto :eof

rem --- ci  <image> -----------------------------------------------------------
rem Present AND arm64. `docker manifest inspect` on a missing tag writes to stderr
rem and prints nothing, so the findstr fails either way -- missing and amd64-only
rem land on the same refusal, which is the right one: neither can run here.
:ci
set _try=0
:ci_again
set /a _try+=1
docker manifest inspect "%REG%/%~1:%TAG%" 2>nul | findstr /C:"arm64" >nul
if not errorlevel 1 (
    echo   ok %REG%/%~1:%TAG%
    exit /b 0
)
rem RETRIED, because this is a network call and one blip must not refuse a release.
rem It was seen: four probes in a row, the fourth came back empty on an image the
rem previous command had just listed.
rem ping, not `timeout /t` -- timeout returns AT ONCE when the script runs under a
rem pipe, so it would spend the retries in the same instant it started.
if %_try% lss 3 (
    ping -n 3 127.0.0.1 >nul
    goto :ci_again
)
echo.
echo [FAIL] %REG%/%~1:%TAG% is not on GHCR with an arm64 image, after 3 tries.
echo        Either the tag is not a released one, or the release did not publish
echo        this image. Nothing has been pushed or deployed.
exit /b 1

:nojars
echo [FAIL] the tp-* jars are not built. From %SERVICES%:
echo          mvn -DskipTests -pl tp-sqns,tp-kinesis -am package
exit /b 1
