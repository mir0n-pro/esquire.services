@echo off
rem ===========================================================================
rem Build + push + redeploy a single component to the YYZ Basic OKE cluster.
rem
rem Usage:
rem   publish.bat <name>            -- one of: frontend, gateway, biztree,
rem                                   enyman, pacman, keysmith, kcmaster
rem   publish.bat all-services      -- all 7 components above (skips infra)
rem
rem Requires:
rem   - GHCR_TOKEN env var (PAT with write:packages)
rem   - kubectl context = OKE cluster (oke-login.bat first if needed)
rem   - Spring services: target\*.jar already built (mvn package)
rem
rem What it does:
rem   1. docker buildx build --platform amd64+arm64 --push  (overwrites :v1.2.2)
rem   2. kubectl rollout restart  (forces pod to re-pull -- pullPolicy: Always)
rem   3. kubectl rollout status   (waits for new pods Ready)
rem
rem Infra (postgres, keycloak, activemq) deliberately NOT here -- they require
rem --set passwords at helm time and are not updated on every code change.
rem ===========================================================================

setlocal
cd /d "%~dp0"

set REGISTRY=ghcr.io/mir0n-pro
set TAG=v1.2.2
set PLATFORMS=linux/amd64,linux/arm64
set SERVICES=%~dp0..
set ESQROOT=%SERVICES%\..

if "%~1"=="" (
  echo Usage: publish.bat ^<name^>
  echo   names: frontend ^| gateway ^| biztree ^| enyman ^| pacman ^| keysmith ^| kcmaster ^| all-services
  exit /b 1
)

if "%GHCR_TOKEN%"=="" (
  echo ERROR: GHCR_TOKEN env var not set. Set to a GitHub PAT with write:packages.
  exit /b 1
)

echo Logging in to ghcr.io...
echo %GHCR_TOKEN% | docker login ghcr.io -u mir0n-pro --password-stdin || goto :fail

docker buildx use desktop-linux 2>nul || docker buildx use default

if /i "%~1"=="all-services" (
  call :one frontend
  call :one gateway
  call :one biztree
  call :one enyman
  call :one pacman
  call :one keysmith
  call :one kcmaster
  goto :done
)

call :one %~1
goto :done

rem -----------------------------------------------------------------------
:one
set NAME=%~1
echo.
echo === Publishing %NAME% ===

if /i "%NAME%"=="frontend" (
  call :buildAndPush esquire.frontend "%ESQROOT%\explorer\frontend\Dockerfile.k8s" "%ESQROOT%\explorer\frontend"
  call :rollout deployment esquire-frontend-frontend
  goto :eof
)

if /i "%NAME%"=="gateway"  ( call :spring %NAME% gateway  & call :rollout deployment esquire-gateway-gateway   & goto :eof )
if /i "%NAME%"=="biztree"  ( call :spring %NAME% bizTree  & call :rollout deployment esquire-biztree-biztree   & goto :eof )
if /i "%NAME%"=="enyman"   ( call :spring %NAME% enyMan   & call :rollout deployment esquire-enyman-enyman     & goto :eof )
if /i "%NAME%"=="pacman"   ( call :spring %NAME% pacMan   & call :rollout deployment esquire-pacman-pacman     & goto :eof )
if /i "%NAME%"=="keysmith" ( call :spring %NAME% keySmith & call :rollout deployment esquire-keysmith-keysmith & goto :eof )
if /i "%NAME%"=="kcmaster" ( call :spring %NAME% kcMaster & call :rollout deployment esquire-kcmaster-kcmaster & goto :eof )

echo ERROR: unknown component "%NAME%"
exit /b 1

rem -----------------------------------------------------------------------
:spring
set SVC_LOWER=%~1
set SVC_DIR=%~2
call :buildAndPush esquire.%SVC_LOWER% "%SERVICES%\%SVC_DIR%\Dockerfile" "%SERVICES%\%SVC_DIR%"
goto :eof

rem -----------------------------------------------------------------------
:buildAndPush
set IMG=%~1
set DOCKERFILE=%~2
set CONTEXT=%~3
echo --- Building %REGISTRY%/%IMG%:%TAG% (multi-arch)...
docker buildx build --platform %PLATFORMS% ^
  -t %REGISTRY%/%IMG%:%TAG% ^
  --push ^
  -f "%DOCKERFILE%" ^
  "%CONTEXT%" || goto :fail
goto :eof

rem -----------------------------------------------------------------------
:rollout
set KIND=%~1
set RESOURCE=%~2
echo --- Restarting %KIND%/%RESOURCE%...
kubectl rollout restart %KIND%/%RESOURCE% || goto :fail
echo --- Waiting for rollout (timeout 5m)...
kubectl rollout status %KIND%/%RESOURCE% --timeout=5m || goto :fail
goto :eof

rem -----------------------------------------------------------------------
:done
echo.
echo Publish complete.
goto :eof

:fail
echo PUBLISH FAILED.
exit /b 1
