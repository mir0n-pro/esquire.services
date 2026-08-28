@echo off
rem ===========================================================================
rem OKE login: refresh kubeconfig and switch kubectl context to the cluster.
rem
rem Pre-requisite (one-time):
rem   1. oci setup config            -- configure OCI CLI
rem   2. Get cluster OCID:
rem      OCI Console > Developer Services > Kubernetes Clusters > <cluster>
rem      Copy the OCID; paste below.
rem ===========================================================================

setlocal

set CLUSTER_OCID=ocid1.cluster.oc1.ca-toronto-1.aaaaaaaa4huiktilus7i4amfutbdsa3lflemr7ekbakyqpq73czhlwnp27sq

oci ce cluster create-kubeconfig ^
  --cluster-id %CLUSTER_OCID% ^
  --file "%USERPROFILE%\.kube\config" ^
  --region ca-toronto-1 ^
  --token-version 2.0.0 ^
  --kube-endpoint PUBLIC_ENDPOINT || exit /b 1

kubectl config current-context
kubectl get nodes -o wide
echo.
echo If nodes show ARCHITECTURE arm64 -- ready.
echo If amd64 -- you provisioned wrong shape; recreate with VM.Standard.A1.Flex.
