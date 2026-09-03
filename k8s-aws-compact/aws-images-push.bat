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
rem   backend             -- the BFF + SPA, built from the EXPLORER repo. It cannot be
rem                          built here at all, which is why a lab tag leaves it on
rem                          the last release: aws-up.bat takes that as backend_tag.
rem   keycloak            -- KeyCloak plus the realm. Pinned by 7a, moves on its own.
rem   postgres            -- PostgreSQL plus db.seed. Same reason.
rem   activemq / redis    -- not in this shape at all. The one bus is SNS, and the
rem                          BFF at x1 keeps its sessions in memory.
rem
rem THIS IS THE LAB ARM. It builds a tag that no pipeline publishes, for work on the
rem three services before there is a release to deploy. THE RELEASE ARM IS
rem aws-release.bat: at a release CI has already published the four images, and
rem pushing local arm64-only ones over its multi-arch manifests would replace the
rem released artifact everywhere, not only here. This script REFUSES a release tag
rem rather than trusting the operator to remember which arm they are on.
rem
rem esquire.backend is the discriminator, and it is an exact one: it is built from
rem the explorer repo, so a tag that has one is a tag CI made. Nothing built here can
rem produce it, which is also why a lab tag leaves the BFF on the last release.
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

rem --- refuse a release tag --------------------------------------------------
docker manifest inspect "%REG%/esquire.backend:%TAG%" >nul 2>&1
if not errorlevel 1 (
    echo [FAIL] %TAG% is a RELEASE tag -- CI published esquire.backend at it.
    echo        Building over it would replace CI's multi-arch images with local
    echo        arm64-only ones. To deploy a release:
    echo.
    echo          aws-release.bat %TAG%
    exit /b 1
)

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
