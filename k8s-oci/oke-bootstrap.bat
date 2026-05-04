@echo off
rem ===========================================================================
rem One-time cluster bootstrap: ingress-nginx + cert-manager + ClusterIssuer.
rem Safe to re-run -- helm install becomes upgrade-or-skip on existing release.
rem ===========================================================================

setlocal
cd /d "%~dp0"

rem === ingress-nginx (creates the public OCI Network Load Balancer) ===
echo --- Installing ingress-nginx...
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx 2>nul
helm repo update
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx ^
  -n ingress-nginx --create-namespace ^
  --set controller.service.annotations."oci\.oraclecloud\.com/load-balancer-type"=nlb ^
  --wait --timeout 5m || exit /b 1

echo.
echo --- Waiting for OCI LB external IP...
:wait_lb
for /f "tokens=*" %%i in ('kubectl get svc ingress-nginx-controller -n ingress-nginx -o jsonpath^="{.status.loadBalancer.ingress[0].ip}"') do set LB_IP=%%i
if "%LB_IP%"=="" (
  timeout /t 10 /nobreak >nul
  goto wait_lb
)
echo.
echo *** OCI LB IP: %LB_IP% ***
echo *** Set DNS A record for esquire.mir0n.pro to this IP. ***
echo.

rem === cert-manager ===
echo --- Installing cert-manager...
helm repo add jetstack https://charts.jetstack.io 2>nul
helm repo update
helm upgrade --install cert-manager jetstack/cert-manager ^
  -n cert-manager --create-namespace ^
  --set installCRDs=true ^
  --wait --timeout 5m || exit /b 1

rem === ClusterIssuer (Let's Encrypt prod) ===
echo --- Applying letsencrypt-prod ClusterIssuer...
kubectl apply -f cluster\letsencrypt-prod.yaml || exit /b 1

echo.
echo Bootstrap complete.
echo   - ingress-nginx LB IP: %LB_IP%
echo   - DNS: point esquire.mir0n.pro at %LB_IP%
echo   - cert-manager ready; ClusterIssuer letsencrypt-prod applied.
echo.
echo Next: cluster\node-labels.bat   (label nodes for tier-based scheduling)
echo Then: oke-up.bat                (deploy the stack)
