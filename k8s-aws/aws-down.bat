@echo off

rem ===========================================================================
rem aws-down.bat -- tear the whole of T2 down. Teardown is the NORMAL state for
rem this target, not the end of the task: nothing here is meant to stand
rem overnight, and the cost model assumes it does not.
rem
rem THREE PARTS, and the ORDER is not a preference:
rem
rem   0. everything that LIVES IN THE CLUSTER VPC but is not in the cluster stack
rem      -- the Amazon MQ broker and the RDS instance. Each holds network
rem      interfaces in a subnet, so eksctl cannot delete the VPC while they are
rem      there, and the stack delete fails late and confusingly. They go FIRST,
rem      and the script WAITS for them to be gone before it touches the cluster.
rem
rem
rem   1. the CLUSTER -- one CloudFormation stack, removed by eksctl. This takes
rem      the VPC, subnets, internet gateway, route tables, node group, IAM
rem      roles and OIDC provider with it.
rem
rem   2. the BUS RESOURCES -- an SNS topic, the SQS queues and the Kinesis
rem      stream. THE DRIVERS MADE THESE ON FIRST USE AND THEY ARE NOT IN THE
rem      CLUSTER. Deleting the cluster does not touch them, they do not appear
rem      in any "is the cluster gone?" check, and a Kinesis stream left behind
rem      bills its shard-hour for as long as it exists -- about $11 a month for
rem      a stream nobody is reading. SQS and SNS cost nothing at rest, but they
rem      go too, so the next fresh init does not inherit stale ones.
rem
rem The script ENDS by listing all three, so the proof is on screen: three empty
rem lists, or it is not torn down.
rem ===========================================================================

setlocal

set CLUSTER_NAME=esquire-aws
set AWS_REGION=us-east-1
set HERE=%~dp0

echo === 0/3  what lives in the cluster VPC but not in the cluster stack ...

rem EVERY Amazon MQ broker whose name starts esquire-aws-mq, not one fixed name: T3.1 made three of
rem them (t3.micro on EFS, and an m5.large pair to compare EBS against EFS), and a broker left behind
rem bills whether or not anything is connected to it. The query carries no pipe on purpose -- see
rem aws-deploy.bat for why a pipe inside a for/f is a trap here.
for /f "usebackq tokens=*" %%B in (`aws mq list-brokers --region %AWS_REGION% --query "BrokerSummaries[?starts_with(BrokerName, 'esquire-aws-mq')].BrokerId" --output text`) do (
    for %%I in (%%B) do (
        echo   amazon mq     : %%I (deleting^)
        aws mq delete-broker --region %AWS_REGION% --broker-id %%I >nul
    )
)

rem RDS. --skip-final-snapshot: this is a demo database rebuilt from db.seed.
aws rds delete-db-instance --region %AWS_REGION% --db-instance-identifier esquire-aws-pg --skip-final-snapshot --delete-automated-backups >nul 2>&1
if not errorlevel 1 echo   rds instance  : esquire-aws-pg (deleting^)

echo   waiting for RDS to go ...
aws rds wait db-instance-deleted --region %AWS_REGION% --db-instance-identifier esquire-aws-pg 2>nul
rem The MSK cluster (T3.2). Its brokers hold interfaces in the same private subnets, so it goes with
rem everything else that does, and its own security group goes below with the Amazon MQ one.
for /f "usebackq tokens=*" %%M in (`aws kafka list-clusters --region %AWS_REGION% --query "ClusterInfoList[?ClusterName=='esquire-aws-msk'].ClusterArn" --output text`) do (
    echo   amazon msk    : esquire-aws-msk (deleting^)
    aws kafka delete-cluster --region %AWS_REGION% --cluster-arn %%M >nul
)

echo   waiting for the brokers to go ...
call :waitBrokers

echo === 1/3  deleting the cluster stack (this takes several minutes) ...
eksctl delete cluster -f "%HERE%cluster.yaml" --disable-nodegroup-eviction
if errorlevel 1 echo [WARN] eksctl reported a problem -- check CloudFormation before assuming it is gone.

echo.
echo === 2/3  deleting the bus resources the drivers made ...

for /f "usebackq tokens=*" %%S in (`aws kinesis list-streams --region %AWS_REGION% --query "StreamNames[]" --output text`) do (
    for %%N in (%%S) do (
        echo   kinesis stream : %%N
        aws kinesis delete-stream --region %AWS_REGION% --stream-name %%N --enforce-consumer-deletion
    )
)

for /f "usebackq tokens=*" %%Q in (`aws sqs list-queues --region %AWS_REGION% --query "QueueUrls[]" --output text`) do (
    for %%U in (%%Q) do (
        echo   sqs queue      : %%U
        aws sqs delete-queue --region %AWS_REGION% --queue-url %%U
    )
)

for /f "usebackq tokens=*" %%T in (`aws sns list-topics --region %AWS_REGION% --query "Topics[].TopicArn" --output text`) do (
    for %%A in (%%T) do (
        echo   sns topic      : %%A
        aws sns delete-topic --region %AWS_REGION% --topic-arn %%A
    )
)

echo.
echo === 2b/3  the ECR repositories, and what the broker left behind
rem The MQ security group lives in the cluster VPC, so it has to go before the VPC can;
rem the secret is force-deleted because a secret in its recovery window KEEPS ITS NAME
rem reserved, and the next create-secret with the same name is refused.
for /f "usebackq tokens=*" %%G in (`aws ec2 describe-security-groups --region %AWS_REGION% --filters "Name=group-name,Values=esquire-aws-mq,esquire-aws-msk" --query "SecurityGroups[].GroupId" --output text`) do (
    echo   bus sec.group : %%G
    aws ec2 delete-security-group --region %AWS_REGION% --group-id %%G >nul 2>&1
)
aws secretsmanager delete-secret --region %AWS_REGION% --secret-id esquire-aws-mq --force-delete-without-recovery >nul 2>&1
if not errorlevel 1 echo   mq secret     : esquire-aws-mq
rem ECR holds the two images GHCR keeps private. Storage is small but it is not nothing,
rem and a repo left behind outlives the account cleanup it was made for.
for %%R in (esquire-tp-aws esquire.aukeep) do (
    aws ecr delete-repository --region %AWS_REGION% --repository-name %%R --force >nul 2>&1
    if not errorlevel 1 echo   ecr repo      : %%R
)

echo.
echo === 3/3  proof -- all three must come back EMPTY
echo --- kinesis:
aws kinesis list-streams --region %AWS_REGION% --query "StreamNames[]" --output text
echo --- sqs:
aws sqs list-queues --region %AWS_REGION% --query "QueueUrls[]" --output text
echo --- sns:
aws sns list-topics --region %AWS_REGION% --query "Topics[].TopicArn" --output text
echo --- amazon msk:
aws kafka list-clusters --region %AWS_REGION% --query "ClusterInfoList[].ClusterName" --output text
echo --- amazon mq:
aws mq list-brokers --region %AWS_REGION% --query "BrokerSummaries[].BrokerName" --output text
echo --- eks:
aws eks list-clusters --region %AWS_REGION% --query "clusters[]" --output text
echo --- rds:
aws rds describe-db-instances --region %AWS_REGION% --query "DBInstances[].DBInstanceIdentifier" --output text
echo --- ecr:
aws ecr describe-repositories --region %AWS_REGION% --query "repositories[].repositoryName" --output text
echo --- rds subnet group / security group ^(free, but they block a VPC delete^):
aws rds describe-db-subnet-groups --region %AWS_REGION% --query "DBSubnetGroups[].DBSubnetGroupName" --output text
echo.
echo Anything printed above still exists and is still billing.

goto :eof

rem --- waitBrokers ----------------------------------------------------------
rem There is no "aws mq wait" verb, so this polls the list until no esquire broker
rem is left. A deleting broker still appears, in state DELETION_IN_PROGRESS, so an
rem empty LIST is the only honest signal that the subnet is free.
:waitBrokers
set "LEFT="
for /f "usebackq tokens=*" %%B in (`aws mq list-brokers --region %AWS_REGION% --query "BrokerSummaries[?starts_with(BrokerName, 'esquire-aws-mq')].BrokerId" --output text`) do set "LEFT=%%B"
if "%LEFT%"=="" (
    echo   brokers gone
    exit /b 0
)
rem SLEEP WITH ping, NOT `timeout`. `timeout /t` needs a console on stdin; run under a pipe
rem or a redirect it fails with "Input redirection is not supported" and returns AT ONCE --
rem so a wait loop built on it spins its whole count in about a second and then reports a
rem timeout that never happened. ping -n <seconds+1> against loopback always works.
ping -n 21 127.0.0.1 >nul
goto :waitBrokers
