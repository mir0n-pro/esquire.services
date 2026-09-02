@echo off

rem ===========================================================================
rem aws-login.bat -- point kubectl at the SUPER-COMPACT EKS cluster.
rem
rem THE CLUSTER IS esquire-aws-compact, NOT esquire-aws. k8s-aws runs the classic
rem shape on a cluster of its own, and the two are separate deployments: neither
rem folder's scripts can reach the other's cluster, which is what keeps the classic
rem tree untouched by this one.
rem
rem It needs nothing but a working AWS credential: `aws configure` has already put
rem the key in %USERPROFILE%\.aws, which is the SDK default chain and is outside
rem every repo. No key is read from, or written to, this tree.
rem
rem Creating the cluster is a separate, BILLABLE step -- see aws-cluster-up.bat.
rem ===========================================================================

setlocal

set CLUSTER_NAME=esquire-aws-compact
set AWS_REGION=us-east-1

aws sts get-caller-identity --query Arn --output text || exit /b 1

aws eks update-kubeconfig --name %CLUSTER_NAME% --region %AWS_REGION% || exit /b 1

kubectl config current-context
kubectl get nodes -o wide
echo.
echo If nodes show ARCHITECTURE arm64 -- ready.
echo If amd64 -- the node group is the wrong shape; recreate with t4g (Graviton).
