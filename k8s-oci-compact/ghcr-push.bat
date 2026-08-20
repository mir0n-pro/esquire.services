@echo off
setlocal

rem === ghcr-push.bat -- multi-arch build + push to ghcr.io/mir0n-pro (SUPER-COMPACT) ===
rem
rem Usage:
rem   ghcr-push.bat                  push ALL 7 images
rem   ghcr-push.bat backend          push only the BFF (multi-stage; explorer SPA baked in)
rem   ghcr-push.bat <service>        push a single Spring service
rem                                  (mesnie | gateward | pacman)
rem   ghcr-push.bat <infra>          push a single infra image
rem                                  (postgres | keycloak | activemq)
rem
rem SEVEN images, not ten: Mesnie is the one image for enyMan, keySmith and the
rem identity work, and gateWard is the one image for the gate and the tree cache.
rem
rem Why target-aware: full run takes minutes even with buildx cache; for a
rem one-component change (e.g. an SPA edit) only the BFF needs re-push. Same
rem target-list shape as k8s-rebuild.bat / compose-rebuild.bat for muscle memory.

set REGISTRY=ghcr.io/mir0n-pro
rem Release tag -- reads the image_tag env var (the same var oke-up.bat consumes), so one
rem `set image_tag=vX.Y.Z-YYMM.DDHH` drives both the push here and the deploy there.
if "%image_tag%"=="" (
  echo ERROR: image_tag env var not set. Example:  set image_tag=v1.2.13-2608.1921
  exit /b 1
)
set TAG=%image_tag%
set PLATFORMS=linux/amd64,linux/arm64

set TARGET=%1
if "%TARGET%"=="" set TARGET=all

if "%GHCR_TOKEN%"=="" (
  echo ERROR: GHCR_TOKEN env var not set. Set it to a GitHub PAT with write:packages.
  exit /b 1
)

echo === target=%TARGET% tag=%TAG% platforms=%PLATFORMS% ===

echo Logging in to ghcr.io...
echo %GHCR_TOKEN% | docker login ghcr.io -u mir0n-pro --password-stdin || goto :fail

rem === buildx setup (use existing default builder; supports linux/amd64+arm64) ===
docker buildx use desktop-linux 2>nul || docker buildx use default

rem Project layout (workspace; not the git repo):
rem   C:\MyProjects\esquire\
rem     db.seed\          (used as build input by postgres)
rem     services\         (this folder lives here)
rem       mesnie\         (Dockerfile + target\*.jar)
rem       gateWard\
rem       pacMan\
rem       postgres\       (Dockerfile; context = ..\..\ to access db.seed)
rem       keycloak\       (Dockerfile.keycloak; context = .)
rem       activemq\       (Dockerfile; context = .)
rem       k8s-oci-compact\ (this script)
rem     explorer\backend\ (Dockerfile -- multi-stage; SPA baked in)

set SERVICES=%~dp0..
set ESQROOT=%SERVICES%\..

if /i "%TARGET%"=="all"        goto target_all
if /i "%TARGET%"=="backend"    goto target_backend
if /i "%TARGET%"=="postgres"   goto target_postgres
if /i "%TARGET%"=="keycloak"   goto target_keycloak
if /i "%TARGET%"=="activemq"   goto target_activemq
if /i "%TARGET%"=="mesnie"     ( set "SVC_LOWER=mesnie"   & set "SVC_DIR=mesnie"   & goto target_spring )
if /i "%TARGET%"=="gateward"   ( set "SVC_LOWER=gateward" & set "SVC_DIR=gateWard" & goto target_spring )
if /i "%TARGET%"=="pacman"     ( set "SVC_LOWER=pacman"   & set "SVC_DIR=pacMan"   & goto target_spring )

echo ERROR: unknown target "%TARGET%"
echo Valid: all ^| backend ^| mesnie ^| gateward ^| pacman ^| postgres ^| keycloak ^| activemq
exit /b 1

:target_all
rem === Spring services (context = service dir; Dockerfile copies target\*.jar) ===
call :pushSpring mesnie    mesnie
call :pushSpring pacman    pacMan
call :pushSpring gateward  gateWard

call :pushBackend
call :pushPostgres
call :pushKeycloak
call :pushActivemq

echo.
echo Done. All 7 images pushed to %REGISTRY% as %TAG% for platforms: %PLATFORMS%
echo (3 Spring services + backend [BFF + SPA] + postgres + keycloak + activemq)
echo Verify: docker manifest inspect %REGISTRY%/esquire.gateward:%TAG%
goto :eof

:target_spring
echo --- Building %SVC_LOWER% (multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire.%SVC_LOWER%:%TAG% ^
  --push ^
  -f "%SERVICES%\%SVC_DIR%\Dockerfile" ^
  "%SERVICES%\%SVC_DIR%" || goto :fail
goto :done

:target_backend
call :pushBackend
goto :done

:target_postgres
call :pushPostgres
goto :done

:target_keycloak
call :pushKeycloak
goto :done

:target_activemq
call :pushActivemq
goto :done

:done
echo.
echo Done. %TARGET% pushed to %REGISTRY% as %TAG% for platforms: %PLATFORMS%
goto :eof

rem === Subroutines (called by both target_all and individual targets) ===

:pushSpring
set SVC_LOWER=%~1
set SVC_DIR=%~2
echo --- Building %SVC_LOWER% (multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire.%SVC_LOWER%:%TAG% ^
  --push ^
  -f "%SERVICES%\%SVC_DIR%\Dockerfile" ^
  "%SERVICES%\%SVC_DIR%" || goto :fail
exit /b 0

:pushBackend
rem Backend / BFF (multi-stage; explorer/backend/Dockerfile builds frontend
rem internally and bakes it into /app/public, then runs Node BFF on :3000).
rem Build context = explorer/ so Dockerfile reaches both backend/ and frontend/.
echo --- Building backend (BFF + baked SPA, multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire.backend:%TAG% ^
  --push ^
  -f "%ESQROOT%\explorer\backend\Dockerfile" ^
  "%ESQROOT%\explorer" || goto :fail
exit /b 0

:pushPostgres
rem Context = esquire root; Dockerfile copies db.seed and services/postgres/initdb.
echo --- Building postgres (multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire-postgres:%TAG% ^
  --push ^
  -f "%SERVICES%\postgres\Dockerfile" ^
  "%ESQROOT%" || goto :fail
exit /b 0

:pushKeycloak
rem Context = services/keycloak; Dockerfile.keycloak.
echo --- Building keycloak (multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire-keycloak:%TAG% ^
  --push ^
  -f "%SERVICES%\keycloak\Dockerfile.keycloak" ^
  "%SERVICES%\keycloak" || goto :fail
exit /b 0

:pushActivemq
rem Context = services/activemq.
echo --- Building activemq (multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire-activemq:%TAG% ^
  --push ^
  -f "%SERVICES%\activemq\Dockerfile" ^
  "%SERVICES%\activemq" || goto :fail
exit /b 0

:fail
echo BUILD FAILED.
exit /b 1
