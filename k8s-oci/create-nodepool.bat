@echo off
rem ===========================================================================
rem Create node pool for the existing Basic OKE cluster (cluster already exists).
rem Use this after deleting a failed node pool, or to add additional pools.
rem
rem Pool: 3x VM.Standard.A1.Flex (1 OCPU, 8 GB) -- Always Free ARM
rem Pre-requisite: oke-rules security list attached (see add-oke-security-rules.bat)
rem ===========================================================================

setlocal
cd /d "%~dp0"

set TENANCY=ocid1.tenancy.oc1..aaaaaaaauobtkr7pv2rql5xmc5ekwsqeptzrlktplum3itvrnb4iciyst3aa
set CLUSTER_ID=ocid1.cluster.oc1.ca-toronto-1.aaaaaaaa4huiktilus7i4amfutbdsa3lflemr7ekbakyqpq73czhlwnp27sq
set REGION=ca-toronto-1
set K8S_VER=v1.35.2
set POOL_NAME=pool1

echo === Creating node pool (~5-10 min) ===
echo Pool name: %POOL_NAME%
echo Cluster:   %CLUSTER_ID%
echo Shape:     VM.Standard.A1.Flex (1 OCPU, 8 GB) x 3 nodes = 3 OCPU / 24 GB total
echo.

call oci ce node-pool create ^
  --cluster-id %CLUSTER_ID% ^
  --compartment-id %TENANCY% ^
  --name %POOL_NAME% ^
  --kubernetes-version %K8S_VER% ^
  --node-shape VM.Standard.A1.Flex ^
  --node-shape-config "{\"ocpus\":1.0,\"memoryInGBs\":8.0}" ^
  --node-source-details file://cluster/create-nodepool-source.json ^
  --placement-configs file://cluster/create-nodepool-placement.json ^
  --size 3 ^
  --region %REGION% ^
  --wait-for-state SUCCEEDED ^
  --max-wait-seconds 1500 || (
    echo NODE POOL CREATE FAILED
    exit /b 1
  )

echo.
echo === Done ===
echo Now run oke-login.bat to fetch kubeconfig, then verify with:
echo   kubectl get nodes -o wide
