@echo off

rem ===========================================================================
rem aws-down.bat -- tear the whole of T7 down.
rem
rem NOT THE END OF A WORKING SESSION. The deployment is left up while there is
rem something to show, and taking it down costs the DNS record its meaning until
rem somebody points it at a new load balancer. Run this when there is no longer a
rem reason to keep paying the ~$153/month -- then bring it back on request with
rem aws-cluster-up.bat. (k8s-aws, the CLASSIC shape, tears down after every use.)
rem
rem FOUR PARTS, and the ORDER is not a preference:
rem
rem   0. THE LOAD BALANCER. It belongs to the ingress-nginx CONTROLLER SERVICE, not
rem      to the Ingress objects -- deleting the Ingresses leaves it up. It holds
rem      network interfaces in the subnets, so the VPC delete stalls late and
rem      confusingly, long after the thing that caused it scrolled past. A helm
rem      uninstall of the controller releases it immediately.
rem
rem   1. the CLUSTER -- one CloudFormation stack, removed by eksctl. This takes the
rem      VPC, subnets, internet gateway, route tables, node group, IAM roles and
rem      OIDC provider with it, and the two EBS volumes behind the PVCs.
rem
rem   2. the BUS RESOURCES -- the SNS topic and the SQS queues. THE DRIVERS MADE
rem      THESE ON FIRST USE AND THEY ARE NOT IN THE CLUSTER. Deleting the cluster
rem      does not touch them and they appear in no "is the cluster gone?" check.
rem      Neither costs anything at rest, but they go, so the next fresh init does
rem      not inherit stale ones. This shape creates NO Kinesis stream -- the one
rem      resource that would bill by the hour -- and the proof below asserts that
rem      rather than assuming it.
rem
rem   3. PROOF. Every list must come back empty, and the script says so.
rem
rem IT FAILS LOUDLY. The classic aws-down.bat exited 0 when every command inside it
rem had failed -- run where `aws` was not on PATH, all three teardown sections
rem errored with "aws is not recognized" and it still reported success, while a
rem Kinesis stream went on billing. A teardown that cannot fail is one that cannot
rem be trusted, so this checks its tools first and counts what went wrong.
rem ===========================================================================

setlocal enabledelayedexpansion

set CLUSTER_NAME=esquire-aws-compact
set AWS_REGION=us-east-1
set HERE=%~dp0
set FAILED=0

rem --- the tools, before anything claims to have done something --------------
where aws >nul 2>&1    || (echo [FAIL] aws is not on PATH -- nothing would be deleted, and this would have reported success. & exit /b 1)
where eksctl >nul 2>&1 || (echo [FAIL] eksctl is not on PATH. & exit /b 1)
where helm >nul 2>&1   || (echo [FAIL] helm is not on PATH. & exit /b 1)
aws sts get-caller-identity --query Arn --output text || (echo [FAIL] no working AWS credential. & exit /b 1)

echo.
echo === 0/3  releasing the load balancer
rem kubectl may already be pointed elsewhere, or the cluster may be half gone. Either
rem way this is best-effort: what matters is that it runs BEFORE eksctl, not that a
rem cluster exists to run it against.
helm uninstall ingress-nginx -n ingress-nginx 2>nul
if errorlevel 1 (
    echo   ingress-nginx not installed, or the cluster is already unreachable -- continuing
) else (
    echo   ingress-nginx uninstalled; AWS releases the NLB with it
    rem The delete is asynchronous. Giving it a moment here saves a VPC delete that
    rem stalls on interfaces that were seconds from going away.
    ping -n 31 127.0.0.1 >nul
)

echo.
echo === 1/3  deleting the cluster stack (this takes several minutes)
eksctl delete cluster -f "%HERE%cluster.yaml" --disable-nodegroup-eviction
if errorlevel 1 (
    echo [FAIL] eksctl reported a problem -- check CloudFormation before assuming it is gone.
    set /a FAILED+=1
)

echo.
echo === 2/3  deleting the bus resources the drivers made
for /f "usebackq tokens=*" %%Q in (`aws sqs list-queues --region %AWS_REGION% --query "QueueUrls[]" --output text`) do (
    for %%U in (%%Q) do (
        echo   sqs queue      : %%U
        aws sqs delete-queue --region %AWS_REGION% --queue-url %%U
        if errorlevel 1 set /a FAILED+=1
    )
)

for /f "usebackq tokens=*" %%T in (`aws sns list-topics --region %AWS_REGION% --query "Topics[].TopicArn" --output text`) do (
    for %%A in (%%T) do (
        echo   sns topic      : %%A
        aws sns delete-topic --region %AWS_REGION% --topic-arn %%A
        if errorlevel 1 set /a FAILED+=1
    )
)

echo.
echo === 3/3  proof -- every list below must come back EMPTY
echo --- eks:
aws eks list-clusters --region %AWS_REGION% --query "clusters[]" --output text
echo --- sqs:
aws sqs list-queues --region %AWS_REGION% --query "QueueUrls[]" --output text
echo --- sns:
aws sns list-topics --region %AWS_REGION% --query "Topics[].TopicArn" --output text
echo --- kinesis ^(this shape creates none -- anything here is a bug, not a leftover^):
aws kinesis list-streams --region %AWS_REGION% --query "StreamNames[]" --output text
echo --- ebs volumes ^(the PVCs go with the cluster; a leftover bills $0.08/GB-month^):
aws ec2 describe-volumes --region %AWS_REGION% --query "Volumes[].VolumeId" --output text
echo --- load balancers ^(a leftover NLB bills by the hour^):
aws elbv2 describe-load-balancers --region %AWS_REGION% --query "LoadBalancers[].LoadBalancerName" --output text

echo.
echo Anything printed above still exists and is still billing.
if not "%FAILED%"=="0" (
    echo.
    echo [FAIL] %FAILED% teardown step^(s^) reported an error. NOT torn down -- read the output above.
    exit /b 1
)
echo.
echo === teardown reported no errors.
exit /b 0
