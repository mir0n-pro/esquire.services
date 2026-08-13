@echo off
rem One-time install of MetalLB LoadBalancer controller for local Docker
rem Desktop k8s. Runs once per cluster lifetime; survives k8s-down. k8s-up.bat
rem checks for its presence and aborts with a clear message if missing.
rem
rem Pair with addIngressNginx.bat -- MetalLB allocates EXTERNAL-IPs for any
rem type=LoadBalancer service (today: only ingress-nginx-controller).
rem k8s-up.bat applies metallb-config.yaml (IPAddressPool + L2Advertisement)
rem on every install.
cd /d "%~dp0"

for /f "delims=" %%i in ('kubectl config current-context') do set CTX=%%i
if not "%CTX%"=="docker-desktop" (
  echo ERROR: kubectl context is "%CTX%", not "docker-desktop".
  echo Refusing to run -- this script targets local Docker Desktop k8s only.
  exit /b 1
)

kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/v0.14.9/config/manifests/metallb-native.yaml
