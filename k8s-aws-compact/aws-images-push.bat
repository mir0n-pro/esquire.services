@echo off

rem ===========================================================================
rem aws-images-push.bat -- build and push the images T7 deploys on EKS.
rem
rem   aws-images-push.bat v1.2.14-2609.0210
rem
rem FOUR IMAGES, and the list is short for a reason. Super-compact runs seven
rem containers; only three of them carry Esquire Java that changed this sprint.
rem
rem   esquire.mesnie      the household -- enyMan + keySmith + the identity work
rem   esquire.gateward    the gate + the bizTree cache
rem   esquire.pacman      accounts
rem   esquire-tp-aws      the AWS driver carrier (see Dockerfile.tp-aws)
rem
rem WHAT IS **NOT** BUILT HERE, and why:
rem   backend / keycloak  -- Node, Angular and KeyCloak. None carries the Esquire
rem                          Java messaging jar, so the v1.2.14 change does not
rem                          touch them and v1.2.13-2608.2806 is still correct.
rem   postgres            -- PostgreSQL plus db.seed. Same reason.
rem   activemq / redis    -- not in this shape at all. The one bus is SNS, and the
rem                          BFF at x1 keeps its sessions in memory.
rem
rem WHY NOT CI. .github/scripts/oke-build-push.sh publishes the compact set at an
rem OKE RELEASE, and the last one was v1.2.13-2608.2805. The `Deploy local` workflow
rem that runs on a pending-** push builds on the runner and publishes nothing. So
rem there is no CI path that has a v1.2.14 mesnie or gateWard in it, and attaching a
rem v1.2.14 driver to a v1.2.13 image would fail at the messaging seam.
rem
rem ARM64 ONLY, deliberately. CI builds linux/amd64,linux/arm64 because those images
rem serve everything. These serve one thing: a Graviton node group.
rem
rem PREREQS
rem   - mvn -DskipTests package has run (the Dockerfiles COPY target jars, and
rem     Dockerfile.tp-aws COPYs both tp-* jars AND their target/aws-lib trees)
rem   - docker login ghcr.io
rem   - a buildx builder that can push cross-platform. The DEFAULT docker driver
rem     CANNOT: it has no way to export an image for another architecture, and the
rem     failure reads as a confusing export error rather than "wrong driver". This
rem     script makes a docker-container builder if one is not there.
rem ===========================================================================

setlocal enabledelayedexpansion

if "%~1"=="" (
    echo usage: aws-images-push.bat ^<tag^>     e.g. v1.2.14-2609.0210
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
rem Context is services/ -- it reaches into two module targets. The Dockerfile is the
rem CLASSIC folder's: the carrier is the same artifact for both shapes, and copying it
rem would mean two files to keep in step for no difference.
call :bx esquire-tp-aws "%SERVICES%\k8s-aws\Dockerfile.tp-aws" .
if errorlevel 1 exit /b 1

echo.
echo === the three super-compact services =====================================
call :bx esquire.mesnie   mesnie\Dockerfile   mesnie
if errorlevel 1 exit /b 1
call :bx esquire.gateward gateWard\Dockerfile gateWard
if errorlevel 1 exit /b 1
call :bx esquire.pacman   pacMan\Dockerfile   pacMan
if errorlevel 1 exit /b 1

echo.
echo === pushed 4 images to %REG% at %TAG% (%PLATFORM%) ===
goto :eof

rem --- bx  <image>  <dockerfile>  <context> ---------------------------------
:bx
echo --- %REG%/%~1:%TAG%
docker buildx build --platform %PLATFORM% -t "%REG%/%~1:%TAG%" --push -f "%~2" "%~3"
exit /b %errorlevel%
