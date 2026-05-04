@echo off
echo === NODES ===
kubectl get nodes -o wide
echo.
echo === PODS (default) ===
kubectl get pods -n default -o wide
echo.
echo === PVC ===
kubectl get pvc -n default
echo.
echo === SERVICES ===
kubectl get svc -A
echo.
echo === INGRESS ===
kubectl get ingress -A
echo.
echo === CERTIFICATES ===
kubectl get certificate -A
echo.
echo === CLUSTERISSUER ===
kubectl get clusterissuer
