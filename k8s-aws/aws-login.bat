@echo off

rem ===========================================================================
rem aws-login.bat -- point kubectl at the EKS cluster. The AWS twin of
rem k8s-oci-compact\oke-login.bat.
rem
rem It needs nothing but a working AWS credential: `aws configure` has already
rem put the key in %USERPROFILE%\.aws, which is the SDK default chain and is
rem outside every repo. No key is read from, or written to, this tree.
rem
rem Creating the cluster is a separate, BILLABLE step -- see aws-up.bat.
rem ===========================================================================

setlocal

set CLUSTER_NAME=esquire-aws
set AWS_REGION=us-east-1

aws sts get-caller-identity --query Arn --output text || exit /b 1

aws eks update-kubeconfig --name %CLUSTER_NAME% --region %AWS_REGION% || exit /b 1

kubectl config current-context
kubectl get nodes -o wide
echo.
echo If nodes show ARCHITECTURE arm64 -- ready.
echo If amd64 -- the node group is the wrong shape; recreate with t4g (Graviton).
