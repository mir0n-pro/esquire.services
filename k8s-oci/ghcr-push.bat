@echo off
setlocal

set REGISTRY=ghcr.io/mir0n-pro
set TAG=v1.2.3
set PLATFORMS=linux/amd64,linux/arm64

if "%GHCR_TOKEN%"=="" (
  echo ERROR: GHCR_TOKEN env var not set. Set it to a GitHub PAT with write:packages.
  exit /b 1
)

echo Logging in to ghcr.io...
echo %GHCR_TOKEN% | docker login ghcr.io -u mir0n-pro --password-stdin || goto :fail

rem === buildx setup (use existing default builder; supports linux/amd64+arm64) ===
docker buildx use desktop-linux 2>nul || docker buildx use default

rem Project layout (workspace; not the git repo):
rem   C:\MyProjects\esquire\
rem     db.seed\          (used as build input by postgres)
rem     services\         (this folder lives here)
rem       bizTree\        (Dockerfile + target\*.jar)
rem       enyMan\
rem       pacMan\
rem       keySmith\
rem       kcMaster\
rem       gateway\
rem       postgres\       (Dockerfile; context = ..\..\ to access db.seed)
rem       keycloak\       (Dockerfile.keycloak; context = .)
rem       activemq\       (Dockerfile; context = .)
rem       k8s-oci\        (this script)
rem     explorer\frontend\ (Dockerfile.k8s)

set SERVICES=%~dp0..
set ESQROOT=%SERVICES%\..

rem === Spring services (context = service dir; Dockerfile copies target\*.jar) ===
call :pushSpring biztree   bizTree
call :pushSpring enyman    enyMan
call :pushSpring pacman    pacMan
call :pushSpring keysmith  keySmith
call :pushSpring kcmaster  kcMaster
call :pushSpring gateway   gateway

rem === Backend / BFF (multi-stage; explorer/backend/Dockerfile builds frontend
rem        internally and bakes it into /app/public, then runs Node BFF on :3000)
rem        Build context = explorer/ so Dockerfile reaches both backend/ and frontend/.
rem        v1.2.3: replaces standalone esquire.frontend image. ===
echo --- Building backend (BFF + baked SPA, multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire.backend:%TAG% ^
  --push ^
  -f "%ESQROOT%\explorer\backend\Dockerfile" ^
  "%ESQROOT%\explorer" || goto :fail

rem === postgres (context = esquire root; Dockerfile copies db.seed and services/postgres/initdb) ===
echo --- Building postgres (multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire-postgres:%TAG% ^
  --push ^
  -f "%SERVICES%\postgres\Dockerfile" ^
  "%ESQROOT%" || goto :fail

rem === keycloak (context = services/keycloak; Dockerfile.keycloak) ===
echo --- Building keycloak (multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire-keycloak:%TAG% ^
  --push ^
  -f "%SERVICES%\keycloak\Dockerfile.keycloak" ^
  "%SERVICES%\keycloak" || goto :fail

rem === activemq (context = services/activemq) ===
echo --- Building activemq (multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire-activemq:%TAG% ^
  --push ^
  -f "%SERVICES%\activemq\Dockerfile" ^
  "%SERVICES%\activemq" || goto :fail

echo.
echo Done. All 10 images pushed to %REGISTRY% as %TAG% for platforms: %PLATFORMS%
echo (6 Spring services + backend [BFF + SPA] + postgres + keycloak + activemq)
echo Verify: docker manifest inspect %REGISTRY%/esquire.gateway:%TAG%
goto :eof

:pushSpring
set SVC_LOWER=%~1
set SVC_DIR=%~2
echo --- Building %SVC_LOWER% (multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/esquire.%SVC_LOWER%:%TAG% ^
  --push ^
  -f "%SERVICES%\%SVC_DIR%\Dockerfile" ^
  "%SERVICES%\%SVC_DIR%" || goto :fail
goto :eof

:fail
echo BUILD FAILED.
exit /b 1
