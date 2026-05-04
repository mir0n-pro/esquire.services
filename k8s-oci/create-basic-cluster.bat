@echo off
rem ===========================================================================
rem Create Basic OKE cluster + node pool in YYZ via OCI CLI.
rem Bypasses Console UI restrictions that force Enhanced.
rem
rem Cluster: --type BASIC_CLUSTER, Flannel CNI
rem Node pool: 3x VM.Standard.A1.Flex (1 OCPU, 8 GB) -- Always Free ARM
rem Region: ca-toronto-1 (Toronto)
rem VCN: esquire-yyz-vcn3 (built by VCN Wizard)
rem
rem Cluster create takes ~10 min; node pool another ~5-10 min.
rem ===========================================================================

setlocal
cd /d "%~dp0"

set TENANCY=ocid1.tenancy.oc1..aaaaaaaauobtkr7pv2rql5xmc5ekwsqeptzrlktplum3itvrnb4iciyst3aa
set VCN=ocid1.vcn.oc1.ca-toronto-1.amaaaaaahl6yymyar56dszceqdakshj4wwcbpsnx2d53twqbre7axgohlrla
set PUB_SUBNET=ocid1.subnet.oc1.ca-toronto-1.aaaaaaaaj5tl7uy5evnrw2hfvqkeco3qfryevfrhzperueir4bun3odscipa
set REGION=ca-toronto-1
set K8S_VER=v1.35.2
set CLUSTER_NAME=esquire
set POOL_NAME=pool1

echo === Phase A: Create Basic cluster (~10 min) ===
echo Cluster name: %CLUSTER_NAME%
echo Region: %REGION%
echo K8s version: %K8S_VER%
echo Type: BASIC_CLUSTER (Flannel CNI)
echo.

call oci ce cluster create ^
  --compartment-id %TENANCY% ^
  --kubernetes-version %K8S_VER% ^
  --name %CLUSTER_NAME% ^
  --vcn-id %VCN% ^
  --type BASIC_CLUSTER ^
  --endpoint-subnet-id %PUB_SUBNET% ^
  --endpoint-public-ip-enabled true ^
  --service-lb-subnet-ids file://cluster/service-lb-subnets.json ^
  --cluster-pod-network-options file://cluster/pod-network-options.json ^
  --pods-cidr 10.244.0.0/16 ^
  --services-cidr 10.96.0.0/16 ^
  --dashboard-enabled false ^
  --tiller-enabled false ^
  --region %REGION% ^
  --wait-for-state SUCCEEDED ^
  --max-wait-seconds 1500 || (
    echo CLUSTER CREATE FAILED
    exit /b 1
  )

echo.
echo === Looking up cluster OCID ===
for /f "delims=" %%i in ('oci ce cluster list --compartment-id %TENANCY% --name %CLUSTER_NAME% --lifecycle-state ACTIVE --region %REGION% --query "data[0].id" --raw-output') do set CLUSTER_ID=%%i

if "%CLUSTER_ID%"=="" (
  echo ERROR: could not find cluster OCID
  exit /b 1
)
echo CLUSTER_ID=%CLUSTER_ID%
echo.

echo === Phase B: Create node pool (~5-10 min) ===
echo Pool name: %POOL_NAME%
echo Shape: VM.Standard.A1.Flex (1 OCPU, 8 GB) x 3 nodes = 3 OCPU / 24 GB total
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
echo CLUSTER_ID=%CLUSTER_ID%
echo Next: edit oke-login.bat with this CLUSTER_OCID and --region ca-toronto-1, then run it.
