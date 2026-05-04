@echo off
rem ===========================================================================
rem Create OKE-permissive security list and attach to both subnets in
rem esquire-yyz-vcn3. Required because the VCN Wizard's default security
rem lists are not OKE-aware -- workers cannot reach the K8s API (6443/12250)
rem and external traffic cannot reach service LBs (80/443).
rem
rem Adds a NEW security list (does not modify existing). Subnets can have
rem up to 5 security lists; rules are additive.
rem ===========================================================================

setlocal
cd /d "%~dp0"

set TENANCY=ocid1.tenancy.oc1..aaaaaaaauobtkr7pv2rql5xmc5ekwsqeptzrlktplum3itvrnb4iciyst3aa
set VCN=ocid1.vcn.oc1.ca-toronto-1.amaaaaaahl6yymyar56dszceqdakshj4wwcbpsnx2d53twqbre7axgohlrla
set PUB_SUBNET=ocid1.subnet.oc1.ca-toronto-1.aaaaaaaaj5tl7uy5evnrw2hfvqkeco3qfryevfrhzperueir4bun3odscipa
set PRV_SUBNET=ocid1.subnet.oc1.ca-toronto-1.aaaaaaaardvgtlrx37ktlmt26wosm7krcyw3ye3hszeazlonchb5nr7mmscq
set REGION=ca-toronto-1

echo === Creating OKE security list ===
for /f "delims=" %%i in ('oci network security-list create ^
  --compartment-id %TENANCY% ^
  --vcn-id %VCN% ^
  --display-name oke-rules ^
  --ingress-security-rules file://cluster/oke-ingress-rules.json ^
  --egress-security-rules file://cluster/oke-egress-rules.json ^
  --region %REGION% ^
  --wait-for-state AVAILABLE ^
  --query "data.id" --raw-output') do set OKE_SL=%%i

if "%OKE_SL%"=="" (
  echo ERROR: failed to create oke-rules security list
  exit /b 1
)
echo Created: %OKE_SL%
echo.

echo === Attaching oke-rules to public subnet ===
for /f "delims=" %%i in ('oci network subnet get --subnet-id %PUB_SUBNET% --region %REGION% --query "data.\"security-list-ids\" | join(',', @)" --raw-output') do set PUB_EXISTING=%%i
echo Existing public subnet sec lists: %PUB_EXISTING%

call oci network subnet update ^
  --subnet-id %PUB_SUBNET% ^
  --security-list-ids "[\"%PUB_EXISTING%\",\"%OKE_SL%\"]" ^
  --region %REGION% ^
  --force || (
    echo ERROR: failed to attach to public subnet
    exit /b 1
  )

echo.
echo === Attaching oke-rules to private subnet ===
for /f "delims=" %%i in ('oci network subnet get --subnet-id %PRV_SUBNET% --region %REGION% --query "data.\"security-list-ids\" | join(',', @)" --raw-output') do set PRV_EXISTING=%%i
echo Existing private subnet sec lists: %PRV_EXISTING%

call oci network subnet update ^
  --subnet-id %PRV_SUBNET% ^
  --security-list-ids "[\"%PRV_EXISTING%\",\"%OKE_SL%\"]" ^
  --region %REGION% ^
  --force || (
    echo ERROR: failed to attach to private subnet
    exit /b 1
  )

echo.
echo === Done ===
echo OKE security list attached to both subnets.
echo Next: delete the failed node pool from Console (Developer Services ^> Kubernetes Clusters ^> esquire ^> Node pools ^> pool1 ^> Delete),
echo then run create-nodepool.bat to recreate.
