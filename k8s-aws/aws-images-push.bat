@echo off

rem ===========================================================================
rem aws-images-push.bat -- build and push the images T2 deploys on EKS.
rem
rem   aws-images-push.bat v1.2.14-2608.3022
rem
rem WHY THIS EXISTS SEPARATELY FROM CI. .github/scripts/oke-build-push.sh builds
rem the COMPACT set -- mesnie, gateward, pacman, backend, postgres, keycloak,
rem activemq -- because that is what OKE runs since v1.2.13. T2 runs CLASSIC, and
rem the classic images stopped being published at v1.2.12-2608.1121; auKeep was
rem never pushed at all. So there is no CI path that produces what EKS needs here.
rem
rem ARM64 ONLY, deliberately. CI builds linux/amd64,linux/arm64 because those
rem images serve everything. These serve one thing: a Graviton node group. Half
rem the build, and nothing in T2 wants amd64.
rem
rem WHAT IS **NOT** BUILT HERE, and why:
rem   backend / keycloak  -- neither carries the Esquire Java messaging jar, so
rem                          the published v1.2.13 images are still correct.
rem   postgres            -- T2 uses RDS; the seed goes in as a job, not an image.
rem   activemq            -- T2 has no broker of its own; the buses are SNS, SQS
rem                          and Kinesis.
rem
rem PREREQS
rem   - mvn -DskipTests package has run (the Dockerfiles COPY target jars, and
rem     Dockerfile.tp-aws COPYs both tp-* jars AND their target/aws-lib trees)
rem   - docker login ghcr.io
rem   - a buildx builder that can push cross-platform. The DEFAULT docker driver
rem     CANNOT: it has no way to export an image for another architecture, and
rem     the failure reads as a confusing export error rather than "wrong driver".
rem     This script makes a docker-container builder if one is not there.
rem ===========================================================================

setlocal enabledelayedexpansion

if "%~1"=="" (
    echo usage: aws-images-push.bat ^<tag^>     e.g. v1.2.14-2608.3022
    exit /b 1
)

set TAG=%~1
set REG=ghcr.io/mir0n-pro
set PLATFORM=linux/arm64
set HERE=%~dp0
set SERVICES=%HERE%..

cd /d "%SERVICES%" || exit /b 1

docker buildx inspect esq-builder >nul 2>&1
if errorlevel 1 (
    echo --- creating the docker-container builder ^(the default driver cannot push cross-platform^)
    docker buildx create --name esq-builder --driver docker-container --bootstrap || exit /b 1
)
docker buildx use esq-builder || exit /b 1

echo.
echo === the AWS driver carrier image =========================================
rem Context is services/ -- it reaches into two module targets.
call :bx esquire-tp-aws "%HERE%Dockerfile.tp-aws" .
if errorlevel 1 exit /b 1

echo.
echo === the seven classic services ===========================================
call :bx esquire.gateway  gateway\Dockerfile  gateway
if errorlevel 1 exit /b 1
call :bx esquire.keysmith keySmith\Dockerfile keySmith
if errorlevel 1 exit /b 1
call :bx esquire.enyman   enyMan\Dockerfile   enyMan
if errorlevel 1 exit /b 1
call :bx esquire.pacman   pacMan\Dockerfile   pacMan
if errorlevel 1 exit /b 1
call :bx esquire.biztree  bizTree\Dockerfile  bizTree
if errorlevel 1 exit /b 1
call :bx esquire.kcmaster kcMaster\Dockerfile kcMaster
if errorlevel 1 exit /b 1
call :bx esquire.aukeep   auKeep\Dockerfile   auKeep
if errorlevel 1 exit /b 1

echo.
echo === pushed 8 images to %REG% at %TAG% (%PLATFORM%) ===
goto :eof

rem --- bx  <image>  <dockerfile>  <context> ---------------------------------
:bx
echo --- %REG%/%~1:%TAG%
docker buildx build --platform %PLATFORM% -t "%REG%/%~1:%TAG%" --push -f "%~2" "%~3"
exit /b %errorlevel%
