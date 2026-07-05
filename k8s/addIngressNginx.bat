@echo off
rem One-time install of ingress-nginx controller for local Docker Desktop k8s.
rem Runs once per cluster lifetime; survives k8s-down. k8s-up.bat checks for
rem its presence and aborts with a clear message if missing.
rem
rem Pair with addMetalLB.bat -- ingress-nginx is a LoadBalancer service and
rem needs MetalLB to allocate the EXTERNAL-IP that Docker Desktop forwards
rem localhost:80 to.
cd /d "%~dp0"

for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop".
  echo Refusing to run -- this script targets local Docker Desktop k8s only.
  exit /b 1
)

kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.11.3/deploy/static/provider/cloud/deploy.yaml
