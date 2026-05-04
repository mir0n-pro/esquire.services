@echo off
rem ===========================================================================
rem Force-restart Keycloak after a chart/env change. Re-applies values with
rem the admin password from %mir0n_pwd% env var, then bounces the pod.
rem ===========================================================================

if "%mir0n_pwd%"=="" (
  echo ERROR: mir0n_pwd env var not set.
  exit /b 1
)

helm upgrade esquire-infra-kc ..\k8s\charts\infra\keycloak -f values\keycloak.yaml --set keycloak.adminPassword=%mir0n_pwd% || exit /b 1
kubectl rollout restart statefulset/esquire-infra-kc-keycloak
kubectl get pods -w
