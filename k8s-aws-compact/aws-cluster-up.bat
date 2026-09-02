@echo off

rem ===========================================================================
rem aws-cluster-up.bat -- create the SUPER-COMPACT EKS cluster and its platform.
rem
rem   aws-cluster-up.bat
rem
rem THIS IS THE BILLABLE STEP. From the moment eksctl finishes, the account pays
rem $0.10/hour for the control plane and $0.0672 for the node -- and once step 4
rem creates the load balancer, $0.0225/hour for it plus $0.005/hour for EACH of the
rem three public IPv4 addresses it and the node hold. With the volumes that is
rem $0.2096/hour: $5.03 a day, about $153 a month. The deployment STANDS once it is
rem up; aws-down.bat is for a deliberate decision to stop paying, not for the end of
rem a working session.
rem
rem SIX THINGS, in an order that is forced:
rem   1. the cluster itself           eksctl, from cluster.yaml (~20 min)
rem   2. the gp3 StorageClass         nothing binds a PVC without it
rem   3. the bus IAM policy           on the node role, or every SNS call is denied
rem   4. ingress-nginx                asks AWS for the load balancer
rem   5. cert-manager + the issuer    needs the DNS record before it can issue
rem   6. system replicas to 1        a single node cannot host HA, and pays 420m for it
rem
rem IT STOPS AT THE LOAD BALANCER HOSTNAME, and that is deliberate. The DNS record
rem for aws-esquire.mir0n.pro is a manual step outside this account, and cert-manager
rem solves HTTP-01 by answering a real request to that name -- so the certificate
rem cannot issue until the record resolves. Running aws-up.bat before then produces
rem a stack that is up and unreachable, which reads as broken.
rem ===========================================================================

setlocal

set HERE=%~dp0
set CLUSTER_NAME=esquire-aws-compact
set AWS_REGION=us-east-1

echo === 1/6  the cluster (several minutes -- eksctl builds a CloudFormation stack)
rem SKIPPED IF IT ALREADY EXISTS, so this script can be re-run after a failure in any
rem later step. eksctl create on a live cluster fails outright, which would make a
rem resumable bring-up impossible and force the remaining steps to be run by hand.
set "HAVE_CLUSTER="
for /f "usebackq tokens=*" %%X in (`aws eks list-clusters --region %AWS_REGION% --query "clusters[?@=='%CLUSTER_NAME%']" --output text`) do set "HAVE_CLUSTER=%%X"
if defined HAVE_CLUSTER (
    echo --- %CLUSTER_NAME% already exists -- not recreating it
) else (
    eksctl create cluster -f "%HERE%cluster.yaml" || exit /b 1
)

rem THE CONTEXT eksctl WRITES IS NOT THE ONE THIS FOLDER GUARDS ON. eksctl names it
rem <user>@<cluster>.<region>.eksctl.io; every guard here -- and in k8s-aws -- wants the
rem ARN form, which is what `aws eks update-kubeconfig` writes. Assuming the create left
rem the ARN context behind fails with "no context exists with the name", AFTER a cluster
rem that took twenty minutes is already up and billing.
for /f "usebackq tokens=*" %%A in (`aws sts get-caller-identity --query Account --output text`) do set ACCT=%%A
set WANT=arn:aws:eks:%AWS_REGION%:%ACCT%:cluster/%CLUSTER_NAME%
aws eks update-kubeconfig --name %CLUSTER_NAME% --region %AWS_REGION% || exit /b 1
kubectl config use-context %WANT% || exit /b 1
echo === context: %WANT%

echo.
echo === 2/6  the gp3 StorageClass
rem EKS ships a `gp2` class backed by the IN-TREE kubernetes.io/aws-ebs provisioner,
rem which Kubernetes 1.34 removed. A claim against it is selectable, accepted, and
rem NEVER BOUND -- the pod sits in ContainerCreating with no error that names the class.
kubectl apply -f "%HERE%storageclass-gp3.yaml" || exit /b 1

echo.
echo === 3/6  the bus policy on the node role
rem The pods use the NODE role -- there is no IRSA service account here. Getting this
rem wrong fails SILENTLY in the direction that costs the most time: the driver logs an
rem AccessDenied and the bus simply never carries anything, which reads as "no traffic"
rem rather than "not allowed".
rem
rem THE POLICY GRANTS SNS AND SQS ONLY, NO KINESIS. This shape carries one bus, the
rem entity broadcast, and audit-off. If something ever named a Kinesis bus here it would
rem fail loudly instead of quietly creating a stream that bills by the hour.
for /f "usebackq tokens=*" %%G in (`aws eks list-nodegroups --cluster-name %CLUSTER_NAME% --region %AWS_REGION% --query "nodegroups[]" --output text`) do set NGS=%%G
if "%NGS%"=="" (
    echo [FAIL] no node groups found on %CLUSTER_NAME% -- did the cluster create finish?
    exit /b 1
)
for %%N in (%NGS%) do (
    for /f "usebackq tokens=*" %%R in (`aws eks describe-nodegroup --cluster-name %CLUSTER_NAME% --nodegroup-name %%N --region %AWS_REGION% --query "nodegroup.nodeRole" --output text`) do (
        for /f "tokens=2 delims=/" %%S in ("%%R") do (
            echo --- attaching esquire-bus to %%S
            aws iam put-role-policy --role-name %%S --policy-name esquire-bus --policy-document file://"%HERE%esquire-bus-policy.json" || exit /b 1
        )
    )
)

echo.
echo === 4/6  ingress-nginx (this is what asks AWS for the load balancer)
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >nul 2>&1
helm repo update ingress-nginx >nul 2>&1
rem An NLB, not the classic ELB the chart would otherwise ask for: it is cheaper, it
rem passes the client address through, and it is what the classic AWS deployment used.
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx ^
  --namespace ingress-nginx --create-namespace ^
  --set controller.service.type=LoadBalancer ^
  --set controller.service.annotations."service\.beta\.kubernetes\.io/aws-load-balancer-type"=nlb ^
  --set controller.nodeSelector.tier=app ^
  --wait --timeout 10m || exit /b 1

echo.
echo === 5/6  cert-manager and the Let's Encrypt issuers
helm repo add jetstack https://charts.jetstack.io >nul 2>&1
helm repo update jetstack >nul 2>&1
helm upgrade --install cert-manager jetstack/cert-manager ^
  --namespace cert-manager --create-namespace ^
  --set crds.enabled=true ^
  --set nodeSelector.tier=app ^
  --wait --timeout 10m || exit /b 1
rem Applied AFTER cert-manager, because a ClusterIssuer is one of its own CRDs.
kubectl apply -f "%HERE%cluster-issuer.yaml" || exit /b 1

echo.
echo === 6/6  reclaiming the redundancy a one-node cluster cannot deliver
rem MEASURED ON THE LIVE CLUSTER, not assumed. A t4g.large allocates 1930m of CPU, and
rem kube-system asks for 800m of it before a single Esquire pod is scheduled:
rem
rem   coredns             x2   200m      metrics-server      x2   200m
rem   ebs-csi-controller  x2   120m      ebs-csi-node/aws-node/kube-proxy  180m
rem   ingress-nginx       x1   100m
rem
rem THE x2 COMPONENTS ARE HA DEFAULTS WRITTEN FOR A MULTI-NODE CLUSTER. Here both
rem replicas of each land on the SAME node -- the only node -- so they cost 420m and
rem survive nothing a single replica would not. With them left alone the stack comes to
rem exactly 1930m and the last pod sits Pending, which reads as a broken deploy.
rem
rem Scaling the three to one frees 260m and brings the whole stack to about 81% of the
rem node. What it gives up is real but small: a coredns restart is a brief cluster-wide
rem DNS gap instead of a covered one. On a proving ground that is the right trade; on a
rem cluster with more than one node it would not be.
rem
rem THESE ARE EKS MANAGED ADDONS, so an addon update would restore replicas: 2. Nothing
rem here updates an addon, and the cluster does not outlive the question it answers.
for %%D in (coredns metrics-server ebs-csi-controller) do (
    echo --- scaling %%D to 1
    kubectl scale deployment %%D -n kube-system --replicas=1 || exit /b 1
)

echo.
echo === the load balancer
rem It can take a minute or two after the chart reports ready before AWS returns the
rem hostname, so this polls rather than reading once and printing an empty line.
call :waitLB
echo.
echo ===========================================================================
echo   THE CLUSTER IS UP AND IT IS BILLING -- $0.2096/hour, $5.03/day, ~$153/month.
echo.
echo   NEXT, AND IT IS A MANUAL STEP: point aws-esquire.mir0n.pro at the load
echo   balancer hostname above (a CNAME), and wait for it to resolve HERE:
echo.
echo       nslookup aws-esquire.mir0n.pro
echo.
echo   cert-manager answers a real HTTP-01 request to that name, so nothing can be
echo   issued until it resolves. THEN run aws-up.bat.
echo ===========================================================================
goto :eof

rem --- waitLB ---------------------------------------------------------------
rem SLEEP WITH ping, NOT `timeout`. `timeout /t` needs a console on stdin; run under a
rem pipe or a redirect it fails with "Input redirection is not supported" and returns AT
rem ONCE -- so a wait loop built on it spins its whole count in about a second and then
rem reports a timeout that never happened.
:waitLB
for /f "usebackq tokens=*" %%L in (`kubectl get svc ingress-nginx-controller -n ingress-nginx -o jsonpath^="{.status.loadBalancer.ingress[0].hostname}"`) do set "LB=%%L"
if not "%LB%"=="" (
    echo   load balancer: %LB%
    exit /b 0
)
echo   waiting for AWS to assign the load balancer hostname ...
ping -n 16 127.0.0.1 >nul
goto :waitLB
