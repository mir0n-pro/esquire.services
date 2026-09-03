# Release Report: v1.2.13 → v1.2.14

**Repo:** `esquire.services/develop`  
**Top commit:** `20fb44a`

---

## Release Notes

### doc/release_notes.txt


**v1.2.14-2609.0219**  v1.2.14 -- AWS: CI/CD scripts  
&nbsp;: Doc:         doc\Esquire.DevSetup.md  
&nbsp;                 doc\Esquire.DevProcess.md  
&nbsp;                 doc\Esquire.GitHubActions.md  
&nbsp;   Components:  k8s-aws-compact  

**v1.2.14-2609.0200**  v1.2.14 -- Finalization  
&nbsp;: Doc:         doc\Esquire.Vision.md  
&nbsp;                 doc\Esquire.DevSetup.md  
&nbsp;                 doc\Esquire.ObservabilityStack.md  
&nbsp;                 doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\Esquire.ContinuingDev.md  
&nbsp;                 doc\Esquire.TestingStack.md  
&nbsp;                 doc\v1.2.x.Planning.md  
&nbsp;                 doc\v1.2.x.Goal.md  
&nbsp;                 doc\research\CloudWatch.for.Esquire.md  
&nbsp;                 doc\research\Cognito.for.Esquire.md  
&nbsp;   Components:   doc  

**v1.2.14-2609.0119**  v1.2.14 -- the KeyCloak admin credential is required  
&nbsp;: Fix:         every deployment path stops when the KeyCloak admin credential is missing  
&nbsp;   Components:  .github (CI/CD),  
&nbsp;                k8s,  
&nbsp;                k8s-compact,  
&nbsp;                k8s-oci,  
&nbsp;                k8s-oci-compact  

**v1.2.14-2609.0113**  v1.2.14 -- AWS: the four-process shape runs on Amazon EKS  
&nbsp;: Config:      k8s-aws-compact -- its own deployment tree: the cluster, the deploy and teardown arms, the  
&nbsp;                 images it needs, and the browser suite over the public address  
&nbsp;: Doc:         doc\research\AWS.Pricing.md  
&nbsp;   Components:  k8s-aws-compact  

**v1.2.14-2609.0100**  v1.2.14 -- AWS: Esquire on Amazon EKS, and its observability on the AWS stack  
&nbsp;: Feature:     Esquire runs on Amazon EKS over real Amazon SNS, SQS and Kinesis  
&nbsp;: Feature:     a bus leg can name the broker user and password  
&nbsp;: Fix:         a created Kinesis stream is provisioned at one shard, not on-demand  
&nbsp;: Config:      k8s-aws -- the AWS deployment tree: the cluster, the images, the database seed, the deploy  
&nbsp;                 and teardown arms, and the browser suite over the public address  
&nbsp;: Config:      the observability stack on EKS, on a node of its own that is scaled to zero when it is off  
&nbsp;: Config:      the same three pillars carried by Amazon CloudWatch, X-Ray and CloudWatch Logs, with arms  
&nbsp;                 of their own  
&nbsp;: Doc:         doc\research\CloudWatch.for.Esquire.md  
&nbsp;                 doc\research\Cognito.for.Esquire.md  
&nbsp;                 doc\Esquire.ContinuingDev.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\Esquire.GitHubActions.md  
&nbsp;                 tp-kinesis\doc\tp-kinesis.md  
&nbsp;   Components:  k8s-aws,  
&nbsp;                tp-activemq,  
&nbsp;                tp-kinesis,  

**v1.2.14-2608.2919**  v1.2.14 -- AWS: the messaging bus carried by Amazon SNS, SQS and Kinesis  
&nbsp;: Feature:     the bus runs on Amazon SQS, Amazon SNS and Amazon Kinesis,  
&nbsp;: Config:      compose-aws -- a second docker sandbox running the bus on a local AWS stack, with its own  
&nbsp;                 observability arms:  
&nbsp;: Doc:         doc\Esquire.MessagingBus.md  
&nbsp;                 doc\Esquire.Messaging.md  
&nbsp;                 doc\Esquire.MessagingBus.Guides.md  
&nbsp;                 doc\Esquire.MessagingBus.Q&A.md  
&nbsp;                 doc\Esquire.MessagingBus.ContinuingDev.md  
&nbsp;                 doc\Esquire.AuditLoggingStack.md  
&nbsp;                 doc\Esquire.DevSetup.md  
&nbsp;                 doc\Esquire.TestingStack.md  
&nbsp;                 doc\services.configuring.md  
&nbsp;                 doc\v1.2.x.Goal.md  
&nbsp;                 tp-sqns\doc\tp-sqns.md  
&nbsp;                 tp-kinesis\doc\tp-kinesis.md  
&nbsp;   Components:  messaging,  
&nbsp;                tp-sqns,  
&nbsp;                tp-kinesis,  
&nbsp;                compose-aws,  

---

## Code Changes

### messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt


**08/29/2026** mir0n  v1.2.14 -- the two receive filters a transport applies when its vendor applies neither  
OwnExcluding  (new)  
&nbsp;- the own-exclusion filter -- what a broker does with noLocal, applied in code for a transport whose vendor  
&nbsp;   cannot. Its own filter, never folded into the subscription one: a leg carries whatever subscription its  
&nbsp;   consumer asked for, and this sits in front of any of them  
SelectingReceiver  (new)  
&nbsp;- the subscription filter -- what a broker does with a message selector, applied in code for a transport  
&nbsp;   whose vendor has none. A general filter over the header bag: any field, = and  and !=, IN and NOT IN,  
&nbsp;   joined by AND. A selector it cannot read is REFUSED when the leg opens, never treated as take-everything  

### tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt


**09/01/2026** mir0n  v1.2.14 -- the broker credentials as declared bus params  
**tp.activemq.TransportProvider**  
&nbsp;- PARAM_USERNAME ("userName") and PARAM_PASSWORD ("password") added: the broker credentials read from  
&nbsp;   transport.params, applied with setUserName/setPassword and BOTH excluded from withParams -- the broker  
&nbsp;   URI is written to the develop log on every open, so a credential carried as a URI option would be  
&nbsp;   written there too  

### tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/changes.txt

Esquire Amazon Kinesis transport provider classes  

**09/01/2026** mir0n  v1.2.14 -- the stream capacity mode as a declared bus param  
**tp.kinesis.TransportProvider**  
&nbsp;- stream.Mode and stream.ShardCount added as create-time attributes, read by streamMode() / shardCount()  
&nbsp;   and refused rather than guessed when unknown. A created stream is PROVISIONED at one shard instead of  
&nbsp;   ON_DEMAND: on-demand bills per STREAM-hour whether or not a record moves, provisioned per SHARD-hour.  
&nbsp;   An existing stream is left as it is, capacity mode included  

**08/29/2026** mir0n  v1.2.14 -- the Amazon Kinesis transport provider  
tp.kinesis.TransportProvider  (new)  
&nbsp;- the Amazon Kinesis ITransportProvider, both legs. An ABSENT partition-by means FIFO -- every record under  
&nbsp;   one key, one shard, one ordered sequence -- and naming a header opts in to spreading, which is safe only  
&nbsp;   where records do not depend on one another. poll-millis is the delivery latency (GetRecords is capped at  
&nbsp;   five a second per shard, so 200ms is the floor). The stream is ensured on first use, never when a leg opens  
tp.kinesis.KinesisConsumer  (new)  
&nbsp;- the Kinesis receive leg -- one poll thread per shard over GetRecords. A stream keeps nobody position, so  
&nbsp;   the leg holds its own in memory and iterator-type says where a restart begins. A failed iterator is  
&nbsp;   RETRIED, and the stream re-made if it is gone; a shard ends only when a successful GetRecords hands back  
&nbsp;   no next iterator  
pom.xml  (new)  
&nbsp;- the tp-kinesis module: esquire-messaging provided, the AWS SDK kinesis client, and copy-dependencies into  
&nbsp;   target/aws-lib so a deployment can attach the driver instead of building it into an image  
doc/tp-kinesis.md  (new)  
&nbsp;- the driver's own description: shards and what order means on a stream, why an absent partition-by is FIFO  
&nbsp;   and why FIFO also needs a receive pool of one, the position nobody keeps, the 200ms floor, configuration,  
&nbsp;   the two receive filters and health  

### tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/changes.txt

Esquire Amazon SQS / Amazon SNS transport provider classes  

**08/29/2026** mir0n  v1.2.14 -- the Amazon SQS and Amazon SNS transport providers  
tp.sqns.SqsSupport  (new)  
&nbsp;- the pieces the sqs and sns providers share -- the client build (region, the endpoint override LocalStack  
&nbsp;   needs, the SDK default credential chain), the queue-name rules (route-by and the character set SQS allows),  
&nbsp;   the create-or-get queue URL cache, the transport.params prefix groups (client. / queue. / topic. /  
&nbsp;   subscription.) and the gate that REFUSES a param naming no AWS call rather than dropping it  
tp.sqns.SnsSupport  (new)  
&nbsp;- the SNS pieces -- CreateTopic (create-or-get, topic. attributes), and putting a queue onto a topic: the  
&nbsp;   queue policy that lets the topic write to it, raw message delivery, and the filter policy CLEARED, since  
&nbsp;   Subscribe applies attributes only when it creates a subscription and one left behind goes on dropping  
&nbsp;   messages  
tp.sqns.SqsConsumer  (new)  
&nbsp;- the long-poll receive leg both providers use -- named poll threads, the delete as the acknowledgement (sent  
&nbsp;   only after the handler returned), and a queue that went away made again on the next turn, then re-wired by  
&nbsp;   the hook the leg supplies  
tp.sqs.TransportProvider  (new)  
&nbsp;- the Amazon SQS ITransportProvider. route-by turns the filter a JMS selector used to apply into a  
&nbsp;   DESTINATION -- a queue per rod-id or per slot-id; the whole header bag rides as the message body, since SQS  
&nbsp;   allows ten attributes and the bag carries twenty. Nothing is asked of AWS when a leg opens: the queue is  
&nbsp;   made on the first poll  
tp.sns.TransportProvider  (new)  
&nbsp;- the Amazon SNS ITransportProvider. SNS delivers and holds nothing, so a consuming leg owns an SQS queue  
&nbsp;   subscribed to the topic and named from its rod-id; the subscription selector and noLocal are applied in code  
&nbsp;   by the framework filters. A topic that goes away is resolved again on both legs. Nothing is asked of AWS  
&nbsp;   when a leg opens  
pom.xml  (new)  
&nbsp;- the tp-sqns module: esquire-messaging provided, the AWS SDK sqs + sns clients, and copy-dependencies into  
&nbsp;   target/aws-lib so a deployment can attach the driver instead of building it into an image  
doc/tp-sqns.md  (new)  
&nbsp;- the driver's own description: the subscription protocol and why an sqs subscription needs no confirmation  
&nbsp;   token, the wiring when a leg opens, a broadcast end to end, request/response over SQS, the queue and topic  
&nbsp;   names, the two receive filters, what rides on the wire, configuration and health  

---

## Commits

```

-- 2026-09-02 | commit: 20fb44a | mir0n.the.programmer | v1.2.14 -- AWS: CI/CD scripts --
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.GitHubActions.md
M	doc/release_notes.txt
M	k8s-aws-compact/aws-images-push.bat
A	k8s-aws-compact/aws-release.bat
M	k8s-aws-compact/aws-up.bat
M	k8s-aws-compact/values/backend.yaml
 8 files changed, 300 insertions(+), 20 deletions(-)


-- 2026-09-02 | commit: e347cf5 | mir0n.the.programmer | v1.2.14 -- Finalization --
M	README.md
M	Releases.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
A	doc/logo/cw-cd.svg
D	doc/logo/jacoco.png
A	doc/logo/jacoco.svg
A	doc/logo/msk-cd.svg
A	doc/logo/x-ray-cd.svg
M	doc/media/ComponentModel.Compact.png
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/research/CloudWatch.for.Esquire.md
M	doc/research/Cognito.for.Esquire.md
M	doc/v1.2.x.Goal.md
M	doc/v1.2.x.Planning.md
M	k8s-aws-compact/aws-down.bat
M	k8s-aws-compact/aws-up.bat
M	k8s-aws-compact/cluster.yaml
 25 files changed, 618 insertions(+), 279 deletions(-)

-- 2026-09-01 | commit: 04b4c84 | mir0n.the.programmer | v1.2.14 -- the KeyCloak admin credential is required --
M	.github/scripts/deploy-oke.sh
M	.github/workflows/deploy-local.yml
M	.github/workflows/deploy-oke.yml
M	doc/release_notes.txt
M	k8s-compact/k8s-up.bat
M	k8s-oci-compact/oke-up.bat
M	k8s-oci/oke-up.bat
M	k8s/k8s-up.bat
 8 files changed, 117 insertions(+), 11 deletions(-)

-- 2026-09-01 | commit: 1bdcf6a | mir0n.the.programmer |  v1.2.14 -- AWS: the four-process shape runs on Amazon EKS --
M	compose-aws/docker-compose-down.bat
M	doc/release_notes.txt
A	k8s-aws-compact/aws-cluster-up.bat
A	k8s-aws-compact/aws-down.bat
A	k8s-aws-compact/aws-e2e-public.bat
A	k8s-aws-compact/aws-images-push.bat
A	k8s-aws-compact/aws-login.bat
A	k8s-aws-compact/aws-public-origin.ps1
A	k8s-aws-compact/aws-up.bat
A	k8s-aws-compact/charts/esquire-gateward/Chart.yaml
A	k8s-aws-compact/charts/esquire-gateward/templates/configmap.yaml
A	k8s-aws-compact/charts/esquire-gateward/templates/deployment.yaml
A	k8s-aws-compact/charts/esquire-gateward/templates/secret.yaml
A	k8s-aws-compact/charts/esquire-gateward/templates/service.yaml
A	k8s-aws-compact/charts/esquire-gateward/values.yaml
A	k8s-aws-compact/charts/esquire-mesnie/Chart.yaml
A	k8s-aws-compact/charts/esquire-mesnie/templates/configmap.yaml
A	k8s-aws-compact/charts/esquire-mesnie/templates/deployment.yaml
A	k8s-aws-compact/charts/esquire-mesnie/templates/secret.yaml
A	k8s-aws-compact/charts/esquire-mesnie/templates/service.yaml
A	k8s-aws-compact/charts/esquire-mesnie/values.schema.json
A	k8s-aws-compact/charts/esquire-mesnie/values.yaml
A	k8s-aws-compact/charts/esquire-pacman/Chart.yaml
A	k8s-aws-compact/charts/esquire-pacman/templates/configmap.yaml
A	k8s-aws-compact/charts/esquire-pacman/templates/deployment.yaml
A	k8s-aws-compact/charts/esquire-pacman/templates/secret.yaml
A	k8s-aws-compact/charts/esquire-pacman/templates/service.yaml
A	k8s-aws-compact/charts/esquire-pacman/values.yaml
A	k8s-aws-compact/cluster-issuer.yaml
A	k8s-aws-compact/cluster.yaml
A	k8s-aws-compact/cluster/ingress.yaml
A	k8s-aws-compact/esquire-bus-policy.json
A	k8s-aws-compact/esquire-topology.yml
A	k8s-aws-compact/show.them.all.bat
A	k8s-aws-compact/storageclass-gp3.yaml
A	k8s-aws-compact/values/backend.yaml
A	k8s-aws-compact/values/gateward.yaml
A	k8s-aws-compact/values/keycloak.yaml
A	k8s-aws-compact/values/mesnie.yaml
A	k8s-aws-compact/values/pacman.yaml
A	k8s-aws-compact/values/postgres.yaml
 41 files changed, 2939 insertions(+), 4 deletions(-)

-- 2026-09-01 | commit: 40c940a | mir0n.the.programmer | v1.2.14 -- AWS: Esquire on Amazon EKS, and its observability on the AWS stack --
M	.github/scripts/deploy-oke.sh
M	.github/workflows/deploy-oke.yml
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/resources/application.yml
M	compose-aws/o11y/prometheus.yml
M	compose-compact/o11y/prometheus.yml
M	compose/o11y/prometheus.yml
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/release_notes.txt
A	doc/research/CloudWatch.for.Esquire.md
A	doc/research/Cognito.for.Esquire.md
M	doc/review/Esquire.PerfMatrix-07-17.md
M	enyMan/src/main/resources/application.yml
M	gateWard/src/main/resources/application.yml
M	gateway/src/main/resources/application.yml
A	k8s-aws/Dockerfile.tp-aws
A	k8s-aws/aws-deploy.bat
A	k8s-aws/aws-down.bat
A	k8s-aws/aws-e2e-public.bat
A	k8s-aws/aws-images-push.bat
A	k8s-aws/aws-login.bat
A	k8s-aws/aws-mq-persistent.bat
A	k8s-aws/aws-o11y-cw-off.bat
A	k8s-aws/aws-o11y-cw-on.bat
A	k8s-aws/aws-o11y-full.bat
A	k8s-aws/aws-o11y-off.bat
A	k8s-aws/aws-o11y-on.bat
A	k8s-aws/aws-public-origin.ps1
A	k8s-aws/aws-seed-db.bat
A	k8s-aws/charts/cw/fluent-bit/Chart.yaml
A	k8s-aws/charts/cw/fluent-bit/templates/configmap.yaml
A	k8s-aws/charts/cw/fluent-bit/templates/daemonset.yaml
A	k8s-aws/charts/cw/fluent-bit/templates/rbac.yaml
A	k8s-aws/charts/cw/fluent-bit/values.yaml
A	k8s-aws/charts/cw/otel-cw/Chart.yaml
A	k8s-aws/charts/cw/otel-cw/templates/configmap.yaml
A	k8s-aws/charts/cw/otel-cw/templates/deployment.yaml
A	k8s-aws/charts/cw/otel-cw/templates/rbac.yaml
A	k8s-aws/charts/cw/otel-cw/templates/service.yaml
A	k8s-aws/charts/cw/otel-cw/values.yaml
A	k8s-aws/charts/esquire-aukeep/Chart.yaml
A	k8s-aws/charts/esquire-aukeep/templates/configmap.yaml
A	k8s-aws/charts/esquire-aukeep/templates/deployment.yaml
A	k8s-aws/charts/esquire-aukeep/templates/secret.yaml
A	k8s-aws/charts/esquire-aukeep/templates/service.yaml
A	k8s-aws/charts/esquire-aukeep/values.yaml
A	k8s-aws/charts/esquire-backend/Chart.yaml
A	k8s-aws/charts/esquire-backend/templates/configmap.yaml
A	k8s-aws/charts/esquire-backend/templates/deployment.yaml
A	k8s-aws/charts/esquire-backend/templates/secret.yaml
A	k8s-aws/charts/esquire-backend/templates/service.yaml
A	k8s-aws/charts/esquire-backend/templates/spa-config.yaml
A	k8s-aws/charts/esquire-backend/values.yaml
A	k8s-aws/charts/esquire-biztree/Chart.yaml
A	k8s-aws/charts/esquire-biztree/templates/configmap.yaml
A	k8s-aws/charts/esquire-biztree/templates/deployment.yaml
A	k8s-aws/charts/esquire-biztree/templates/secret.yaml
A	k8s-aws/charts/esquire-biztree/templates/service.yaml
A	k8s-aws/charts/esquire-biztree/values.yaml
A	k8s-aws/charts/esquire-enyman/Chart.yaml
A	k8s-aws/charts/esquire-enyman/templates/configmap.yaml
A	k8s-aws/charts/esquire-enyman/templates/deployment.yaml
A	k8s-aws/charts/esquire-enyman/templates/secret.yaml
A	k8s-aws/charts/esquire-enyman/templates/service.yaml
A	k8s-aws/charts/esquire-enyman/values.schema.json
A	k8s-aws/charts/esquire-enyman/values.yaml
A	k8s-aws/charts/esquire-gateway/Chart.yaml
A	k8s-aws/charts/esquire-gateway/templates/configmap.yaml
A	k8s-aws/charts/esquire-gateway/templates/deployment.yaml
A	k8s-aws/charts/esquire-gateway/templates/secret.yaml
A	k8s-aws/charts/esquire-gateway/templates/service.yaml
A	k8s-aws/charts/esquire-gateway/values.yaml
A	k8s-aws/charts/esquire-kcmaster/Chart.yaml
A	k8s-aws/charts/esquire-kcmaster/templates/configmap.yaml
A	k8s-aws/charts/esquire-kcmaster/templates/deployment.yaml
A	k8s-aws/charts/esquire-kcmaster/templates/secret.yaml
A	k8s-aws/charts/esquire-kcmaster/templates/service.yaml
A	k8s-aws/charts/esquire-kcmaster/values.yaml
A	k8s-aws/charts/esquire-keysmith/Chart.yaml
A	k8s-aws/charts/esquire-keysmith/templates/configmap.yaml
A	k8s-aws/charts/esquire-keysmith/templates/deployment.yaml
A	k8s-aws/charts/esquire-keysmith/templates/secret.yaml
A	k8s-aws/charts/esquire-keysmith/templates/service.yaml
A	k8s-aws/charts/esquire-keysmith/values.yaml
A	k8s-aws/charts/esquire-pacman/Chart.yaml
A	k8s-aws/charts/esquire-pacman/templates/configmap.yaml
A	k8s-aws/charts/esquire-pacman/templates/deployment.yaml
A	k8s-aws/charts/esquire-pacman/templates/secret.yaml
A	k8s-aws/charts/esquire-pacman/templates/service.yaml
A	k8s-aws/charts/esquire-pacman/values.yaml
A	k8s-aws/charts/esquire-topology/Chart.yaml
A	k8s-aws/charts/esquire-topology/esquire-topology.yml
A	k8s-aws/charts/esquire-topology/templates/configmap.yaml
A	k8s-aws/charts/esquire-topology/values.yaml
A	k8s-aws/charts/infra/activemq/Chart.yaml
A	k8s-aws/charts/infra/activemq/templates/service.yaml
A	k8s-aws/charts/infra/activemq/templates/statefulset.yaml
A	k8s-aws/charts/infra/activemq/values.yaml
A	k8s-aws/charts/infra/alloy/Chart.yaml
A	k8s-aws/charts/infra/alloy/templates/configmap.yaml
A	k8s-aws/charts/infra/alloy/templates/deployment.yaml
A	k8s-aws/charts/infra/alloy/templates/pvc.yaml
A	k8s-aws/charts/infra/alloy/templates/rbac.yaml
A	k8s-aws/charts/infra/alloy/values.yaml
A	k8s-aws/charts/infra/grafana/Chart.yaml
A	k8s-aws/charts/infra/grafana/dashboards/esquire-logging.json
A	k8s-aws/charts/infra/grafana/dashboards/esquire-services.json
A	k8s-aws/charts/infra/grafana/dashboards/esquire-topology.json
A	k8s-aws/charts/infra/grafana/icons/aukeep.svg
A	k8s-aws/charts/infra/grafana/icons/biztree.svg
A	k8s-aws/charts/infra/grafana/icons/enyman.svg
A	k8s-aws/charts/infra/grafana/icons/explorer.svg
A	k8s-aws/charts/infra/grafana/icons/gateway.svg
A	k8s-aws/charts/infra/grafana/icons/kcmaster.svg
A	k8s-aws/charts/infra/grafana/icons/keycloak.svg
A	k8s-aws/charts/infra/grafana/icons/keysmith.svg
A	k8s-aws/charts/infra/grafana/icons/kinesis.svg
A	k8s-aws/charts/infra/grafana/icons/pacman.svg
A	k8s-aws/charts/infra/grafana/icons/postgres.svg
A	k8s-aws/charts/infra/grafana/icons/sns.svg
A	k8s-aws/charts/infra/grafana/icons/sqs.svg
A	k8s-aws/charts/infra/grafana/templates/configmap-dashboards.yaml
A	k8s-aws/charts/infra/grafana/templates/configmap-datasource.yaml
A	k8s-aws/charts/infra/grafana/templates/configmap-icons.yaml
A	k8s-aws/charts/infra/grafana/templates/deployment.yaml
A	k8s-aws/charts/infra/grafana/templates/ingress.yaml
A	k8s-aws/charts/infra/grafana/templates/pvc.yaml
A	k8s-aws/charts/infra/grafana/templates/service.yaml
A	k8s-aws/charts/infra/grafana/values.yaml
A	k8s-aws/charts/infra/kafka/Chart.yaml
A	k8s-aws/charts/infra/kafka/templates/deployment.yaml
A	k8s-aws/charts/infra/kafka/templates/service.yaml
A	k8s-aws/charts/infra/kafka/values.yaml
A	k8s-aws/charts/infra/keycloak/Chart.yaml
A	k8s-aws/charts/infra/keycloak/templates/secret.yaml
A	k8s-aws/charts/infra/keycloak/templates/service.yaml
A	k8s-aws/charts/infra/keycloak/templates/statefulset.yaml
A	k8s-aws/charts/infra/keycloak/values.yaml
A	k8s-aws/charts/infra/loki/Chart.yaml
A	k8s-aws/charts/infra/loki/templates/configmap.yaml
A	k8s-aws/charts/infra/loki/templates/deployment.yaml
A	k8s-aws/charts/infra/loki/templates/pvc.yaml
A	k8s-aws/charts/infra/loki/templates/service.yaml
A	k8s-aws/charts/infra/loki/values.yaml
A	k8s-aws/charts/infra/otel-collector/Chart.yaml
A	k8s-aws/charts/infra/otel-collector/templates/configmap.yaml
A	k8s-aws/charts/infra/otel-collector/templates/deployment.yaml
A	k8s-aws/charts/infra/otel-collector/templates/service.yaml
A	k8s-aws/charts/infra/otel-collector/values.yaml
A	k8s-aws/charts/infra/postgres-exporter/Chart.yaml
A	k8s-aws/charts/infra/postgres-exporter/templates/deployment.yaml
A	k8s-aws/charts/infra/postgres-exporter/templates/service.yaml
A	k8s-aws/charts/infra/postgres-exporter/values.yaml
A	k8s-aws/charts/infra/postgres/Chart.yaml
A	k8s-aws/charts/infra/postgres/templates/secret.yaml
A	k8s-aws/charts/infra/postgres/templates/service.yaml
A	k8s-aws/charts/infra/postgres/templates/statefulset.yaml
A	k8s-aws/charts/infra/postgres/values.yaml
A	k8s-aws/charts/infra/prometheus/Chart.yaml
A	k8s-aws/charts/infra/prometheus/rules.yml
A	k8s-aws/charts/infra/prometheus/templates/configmap.yaml
A	k8s-aws/charts/infra/prometheus/templates/deployment.yaml
A	k8s-aws/charts/infra/prometheus/templates/pvc.yaml
A	k8s-aws/charts/infra/prometheus/templates/rbac.yaml
A	k8s-aws/charts/infra/prometheus/templates/service.yaml
A	k8s-aws/charts/infra/prometheus/values.yaml
A	k8s-aws/charts/infra/redis/Chart.yaml
A	k8s-aws/charts/infra/redis/templates/deployment.yaml
A	k8s-aws/charts/infra/redis/templates/service.yaml
A	k8s-aws/charts/infra/redis/values.yaml
A	k8s-aws/charts/infra/tempo/Chart.yaml
A	k8s-aws/charts/infra/tempo/templates/configmap.yaml
A	k8s-aws/charts/infra/tempo/templates/deployment.yaml
A	k8s-aws/charts/infra/tempo/templates/pvc.yaml
A	k8s-aws/charts/infra/tempo/templates/service.yaml
A	k8s-aws/charts/infra/tempo/values.yaml
A	k8s-aws/cluster-issuer.yaml
A	k8s-aws/cluster.yaml
A	k8s-aws/cognito/pre-token.py
A	k8s-aws/cognito/probe-ids.txt
A	k8s-aws/cognito/trust-policy.json
A	k8s-aws/cognito/user-pool.json
A	k8s-aws/coredns-mir0n-pro.md
A	k8s-aws/db-seed-job.yaml
A	k8s-aws/esquire-bus-policy.json
A	k8s-aws/esquire-cw-policy.json
A	k8s-aws/esquire-topology.yml
A	k8s-aws/ingress.yaml
A	k8s-aws/mq-probe/MqSendProbe.java
A	k8s-aws/msk-cluster.json
A	k8s-aws/msk-probe/MskTopicProbe.java
A	k8s-aws/show.them.all.bat
A	k8s-aws/storageclass-gp3.yaml
A	k8s-aws/values/activemq.yaml
A	k8s-aws/values/aukeep.yaml
A	k8s-aws/values/backend.yaml
A	k8s-aws/values/biztree.yaml
A	k8s-aws/values/cw-fluent-bit.yaml
A	k8s-aws/values/cw-otel.yaml
A	k8s-aws/values/enyman.yaml
A	k8s-aws/values/gateway.yaml
A	k8s-aws/values/grafana.yaml
A	k8s-aws/values/kcmaster.yaml
A	k8s-aws/values/keycloak.yaml
A	k8s-aws/values/keysmith.yaml
A	k8s-aws/values/pacman.yaml
A	k8s-aws/values/postgres-exporter.yaml
A	k8s-aws/values/postgres.yaml
M	k8s-oci-compact/values/gateward.yaml
M	k8s-oci/esquire-topology.yml
M	k8s-oci/values/gateway.yaml
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/resources/application.yml
M	mesnie/src/main/resources/application.yml
M	pacMan/src/main/resources/application.yml
M	tp-activemq/pom.xml
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
M	tp-kinesis/doc/tp-kinesis.md
M	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/TransportProvider.java
M	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/changes.txt
A	tp-kinesis/src/test/java/pro/mir0n/esquire/tp/kinesis/StreamModeParamsTest.java
 225 files changed, 19948 insertions(+), 75 deletions(-)

-- 2026-08-29 | commit: f08e735 | mir0n.the.programmer | v1.2.14 -- AWS: the messaging bus carried by Amazon SNS, SQS and Kinesis --
M	README.md
A	compose-aws/aws-spec.bat
A	compose-aws/compose-rebuild.bat
A	compose-aws/compose.ha-smoke.yaml
A	compose-aws/compose.yaml
A	compose-aws/data/keycloak/-placeholder-
A	compose-aws/data/localstack/-placeholder-
A	compose-aws/docker-compose-down.bat
A	compose-aws/docker-compose-start.bat
A	compose-aws/docker-compose-stop.bat
A	compose-aws/docker-compose-up.bat
A	compose-aws/logs/-placeholder-
A	compose-aws/o11y-debug.bat
A	compose-aws/o11y-full.bat
A	compose-aws/o11y-off.bat
A	compose-aws/o11y-verify.bat
A	compose-aws/o11y/alloy-config.alloy
A	compose-aws/o11y/grafana/gen-dashboard.py
A	compose-aws/o11y/grafana/gen-datasources.py
A	compose-aws/o11y/grafana/gen-topology.py
A	compose-aws/o11y/grafana/icons/aukeep.svg
A	compose-aws/o11y/grafana/icons/biztree.svg
A	compose-aws/o11y/grafana/icons/enyman.svg
A	compose-aws/o11y/grafana/icons/explorer.svg
A	compose-aws/o11y/grafana/icons/gateway.svg
A	compose-aws/o11y/grafana/icons/kcmaster.svg
A	compose-aws/o11y/grafana/icons/keycloak.svg
A	compose-aws/o11y/grafana/icons/keysmith.svg
A	compose-aws/o11y/grafana/icons/kinesis.svg
A	compose-aws/o11y/grafana/icons/pacman.svg
A	compose-aws/o11y/grafana/icons/postgres.svg
A	compose-aws/o11y/grafana/icons/sns.svg
A	compose-aws/o11y/grafana/icons/sqs.svg
A	compose-aws/o11y/grafana/provisioning/dashboards/dashboards.yaml
A	compose-aws/o11y/grafana/provisioning/dashboards/esquire-logging.json
A	compose-aws/o11y/grafana/provisioning/dashboards/esquire-services.json
A	compose-aws/o11y/grafana/provisioning/dashboards/esquire-topology.json
A	compose-aws/o11y/grafana/provisioning/datasources/loki.yaml
A	compose-aws/o11y/grafana/provisioning/datasources/prometheus.yaml
A	compose-aws/o11y/grafana/provisioning/datasources/tempo.yaml
A	compose-aws/o11y/loki-config.yaml
A	compose-aws/o11y/otel-collector-config.yaml
A	compose-aws/o11y/prometheus.yml
A	compose-aws/o11y/rules.yml
A	compose-aws/o11y/tempo-config.yaml
A	compose-aws/request-for-region.ps
A	compose-aws/topology/esquire-topology.aws.yml
A	compose-aws/topology/esquire-topology.yml
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.Messaging.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.Guides.md
M	doc/Esquire.MessagingBus.Q&A.md
M	doc/Esquire.MessagingBus.md
M	doc/Esquire.TestingStack.md
A	doc/logo/MQ-cd.svg
A	doc/logo/RDS-cd.svg
A	doc/logo/aurora-cd.svg
A	doc/logo/kinesis-cd.svg
A	doc/logo/sns-cd.svg
A	doc/logo/sqs-cd.svg
M	doc/release_notes.txt
M	doc/services.configuring.md
M	doc/v1.2.x.Goal.md
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/OwnExcluding.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/SelectingReceiver.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/OwnExcludingTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/SelectingReceiverTest.java
M	pom.xml
A	test/aws/aws-spec.py
A	tp-kinesis/doc/tp-kinesis.md
A	tp-kinesis/pom.xml
A	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/KinesisConsumer.java
A	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/TransportProvider.java
A	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/changes.txt
A	tp-sqns/doc/tp-sqns.md
A	tp-sqns/pom.xml
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sns/TransportProvider.java
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SnsSupport.java
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsConsumer.java
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsSupport.java
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/changes.txt
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqs/TransportProvider.java
A	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamGroupTest.java
A	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamsTest.java
A	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportTest.java
 88 files changed, 19977 insertions(+), 48 deletions(-)

-- 2026-08-29 | commit: eb6bb5f | mir0n.the.programmer | v1.2.14 -- version bump --
M	.github/workflows/deploy-oke.yml
M	README.md
M	doc/Esquire.ContinuingDev.md
M	doc/v1.2.x.Planning.md
M	pom.xml
 5 files changed, 18 insertions(+), 12 deletions(-)

-- 2026-08-28 | commit: a2d0a99 | mir0n.the.programmer | Create report_v1.2.13.md --
A	doc/reports/report_v1.2.13.md
 1 file changed, 2333 insertions(+)

```

---

## Files Modified

```
M	.github/scripts/deploy-oke.sh
M	.github/workflows/deploy-local.yml
M	.github/workflows/deploy-oke.yml
M	README.md
M	Releases.md
M	auKeep/src/main/resources/application.yml
M	bizTree/src/main/resources/application.yml
A	compose-aws/aws-spec.bat
A	compose-aws/compose-rebuild.bat
A	compose-aws/compose.ha-smoke.yaml
A	compose-aws/compose.yaml
A	compose-aws/data/keycloak/-placeholder-
A	compose-aws/data/localstack/-placeholder-
A	compose-aws/docker-compose-down.bat
A	compose-aws/docker-compose-start.bat
A	compose-aws/docker-compose-stop.bat
A	compose-aws/docker-compose-up.bat
A	compose-aws/logs/-placeholder-
A	compose-aws/o11y-debug.bat
A	compose-aws/o11y-full.bat
A	compose-aws/o11y-off.bat
A	compose-aws/o11y-verify.bat
A	compose-aws/o11y/alloy-config.alloy
A	compose-aws/o11y/grafana/gen-dashboard.py
A	compose-aws/o11y/grafana/gen-datasources.py
A	compose-aws/o11y/grafana/gen-topology.py
A	compose-aws/o11y/grafana/icons/aukeep.svg
A	compose-aws/o11y/grafana/icons/biztree.svg
A	compose-aws/o11y/grafana/icons/enyman.svg
A	compose-aws/o11y/grafana/icons/explorer.svg
A	compose-aws/o11y/grafana/icons/gateway.svg
A	compose-aws/o11y/grafana/icons/kcmaster.svg
A	compose-aws/o11y/grafana/icons/keycloak.svg
A	compose-aws/o11y/grafana/icons/keysmith.svg
A	compose-aws/o11y/grafana/icons/kinesis.svg
A	compose-aws/o11y/grafana/icons/pacman.svg
A	compose-aws/o11y/grafana/icons/postgres.svg
A	compose-aws/o11y/grafana/icons/sns.svg
A	compose-aws/o11y/grafana/icons/sqs.svg
A	compose-aws/o11y/grafana/provisioning/dashboards/dashboards.yaml
A	compose-aws/o11y/grafana/provisioning/dashboards/esquire-logging.json
A	compose-aws/o11y/grafana/provisioning/dashboards/esquire-services.json
A	compose-aws/o11y/grafana/provisioning/dashboards/esquire-topology.json
A	compose-aws/o11y/grafana/provisioning/datasources/loki.yaml
A	compose-aws/o11y/grafana/provisioning/datasources/prometheus.yaml
A	compose-aws/o11y/grafana/provisioning/datasources/tempo.yaml
A	compose-aws/o11y/loki-config.yaml
A	compose-aws/o11y/otel-collector-config.yaml
A	compose-aws/o11y/prometheus.yml
A	compose-aws/o11y/rules.yml
A	compose-aws/o11y/tempo-config.yaml
A	compose-aws/request-for-region.ps
A	compose-aws/topology/esquire-topology.aws.yml
A	compose-aws/topology/esquire-topology.yml
M	compose-compact/o11y/prometheus.yml
M	compose/o11y/prometheus.yml
M	doc/Esquire.AuditLoggingStack.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.HighAvailability.md
M	doc/Esquire.Messaging.md
M	doc/Esquire.MessagingBus.ContinuingDev.md
M	doc/Esquire.MessagingBus.Guides.md
M	doc/Esquire.MessagingBus.Q&A.md
M	doc/Esquire.MessagingBus.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
A	doc/logo/MQ-cd.svg
A	doc/logo/RDS-cd.svg
A	doc/logo/aurora-cd.svg
A	doc/logo/cw-cd.svg
D	doc/logo/jacoco.png
A	doc/logo/jacoco.svg
A	doc/logo/kinesis-cd.svg
A	doc/logo/msk-cd.svg
A	doc/logo/sns-cd.svg
A	doc/logo/sqs-cd.svg
A	doc/logo/x-ray-cd.svg
M	doc/media/ComponentModel.Compact.png
M	doc/media/ComponentModel.png
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
A	doc/reports/report_v1.2.13.md
A	doc/research/CloudWatch.for.Esquire.md
A	doc/research/Cognito.for.Esquire.md
M	doc/review/Esquire.PerfMatrix-07-17.md
M	doc/services.configuring.md
M	doc/v1.2.x.Goal.md
M	doc/v1.2.x.Planning.md
M	enyMan/src/main/resources/application.yml
M	gateWard/src/main/resources/application.yml
M	gateway/src/main/resources/application.yml
A	k8s-aws-compact/aws-cluster-up.bat
A	k8s-aws-compact/aws-down.bat
A	k8s-aws-compact/aws-e2e-public.bat
A	k8s-aws-compact/aws-images-push.bat
A	k8s-aws-compact/aws-login.bat
A	k8s-aws-compact/aws-public-origin.ps1
A	k8s-aws-compact/aws-release.bat
A	k8s-aws-compact/aws-up.bat
A	k8s-aws-compact/charts/esquire-gateward/Chart.yaml
A	k8s-aws-compact/charts/esquire-gateward/templates/configmap.yaml
A	k8s-aws-compact/charts/esquire-gateward/templates/deployment.yaml
A	k8s-aws-compact/charts/esquire-gateward/templates/secret.yaml
A	k8s-aws-compact/charts/esquire-gateward/templates/service.yaml
A	k8s-aws-compact/charts/esquire-gateward/values.yaml
A	k8s-aws-compact/charts/esquire-mesnie/Chart.yaml
A	k8s-aws-compact/charts/esquire-mesnie/templates/configmap.yaml
A	k8s-aws-compact/charts/esquire-mesnie/templates/deployment.yaml
A	k8s-aws-compact/charts/esquire-mesnie/templates/secret.yaml
A	k8s-aws-compact/charts/esquire-mesnie/templates/service.yaml
A	k8s-aws-compact/charts/esquire-mesnie/values.schema.json
A	k8s-aws-compact/charts/esquire-mesnie/values.yaml
A	k8s-aws-compact/charts/esquire-pacman/Chart.yaml
A	k8s-aws-compact/charts/esquire-pacman/templates/configmap.yaml
A	k8s-aws-compact/charts/esquire-pacman/templates/deployment.yaml
A	k8s-aws-compact/charts/esquire-pacman/templates/secret.yaml
A	k8s-aws-compact/charts/esquire-pacman/templates/service.yaml
A	k8s-aws-compact/charts/esquire-pacman/values.yaml
A	k8s-aws-compact/cluster-issuer.yaml
A	k8s-aws-compact/cluster.yaml
A	k8s-aws-compact/cluster/ingress.yaml
A	k8s-aws-compact/esquire-bus-policy.json
A	k8s-aws-compact/esquire-topology.yml
A	k8s-aws-compact/show.them.all.bat
A	k8s-aws-compact/storageclass-gp3.yaml
A	k8s-aws-compact/values/backend.yaml
A	k8s-aws-compact/values/gateward.yaml
A	k8s-aws-compact/values/keycloak.yaml
A	k8s-aws-compact/values/mesnie.yaml
A	k8s-aws-compact/values/pacman.yaml
A	k8s-aws-compact/values/postgres.yaml
A	k8s-aws/Dockerfile.tp-aws
A	k8s-aws/aws-deploy.bat
A	k8s-aws/aws-down.bat
A	k8s-aws/aws-e2e-public.bat
A	k8s-aws/aws-images-push.bat
A	k8s-aws/aws-login.bat
A	k8s-aws/aws-mq-persistent.bat
A	k8s-aws/aws-o11y-cw-off.bat
A	k8s-aws/aws-o11y-cw-on.bat
A	k8s-aws/aws-o11y-full.bat
A	k8s-aws/aws-o11y-off.bat
A	k8s-aws/aws-o11y-on.bat
A	k8s-aws/aws-public-origin.ps1
A	k8s-aws/aws-seed-db.bat
A	k8s-aws/charts/cw/fluent-bit/Chart.yaml
A	k8s-aws/charts/cw/fluent-bit/templates/configmap.yaml
A	k8s-aws/charts/cw/fluent-bit/templates/daemonset.yaml
A	k8s-aws/charts/cw/fluent-bit/templates/rbac.yaml
A	k8s-aws/charts/cw/fluent-bit/values.yaml
A	k8s-aws/charts/cw/otel-cw/Chart.yaml
A	k8s-aws/charts/cw/otel-cw/templates/configmap.yaml
A	k8s-aws/charts/cw/otel-cw/templates/deployment.yaml
A	k8s-aws/charts/cw/otel-cw/templates/rbac.yaml
A	k8s-aws/charts/cw/otel-cw/templates/service.yaml
A	k8s-aws/charts/cw/otel-cw/values.yaml
A	k8s-aws/charts/esquire-aukeep/Chart.yaml
A	k8s-aws/charts/esquire-aukeep/templates/configmap.yaml
A	k8s-aws/charts/esquire-aukeep/templates/deployment.yaml
A	k8s-aws/charts/esquire-aukeep/templates/secret.yaml
A	k8s-aws/charts/esquire-aukeep/templates/service.yaml
A	k8s-aws/charts/esquire-aukeep/values.yaml
A	k8s-aws/charts/esquire-backend/Chart.yaml
A	k8s-aws/charts/esquire-backend/templates/configmap.yaml
A	k8s-aws/charts/esquire-backend/templates/deployment.yaml
A	k8s-aws/charts/esquire-backend/templates/secret.yaml
A	k8s-aws/charts/esquire-backend/templates/service.yaml
A	k8s-aws/charts/esquire-backend/templates/spa-config.yaml
A	k8s-aws/charts/esquire-backend/values.yaml
A	k8s-aws/charts/esquire-biztree/Chart.yaml
A	k8s-aws/charts/esquire-biztree/templates/configmap.yaml
A	k8s-aws/charts/esquire-biztree/templates/deployment.yaml
A	k8s-aws/charts/esquire-biztree/templates/secret.yaml
A	k8s-aws/charts/esquire-biztree/templates/service.yaml
A	k8s-aws/charts/esquire-biztree/values.yaml
A	k8s-aws/charts/esquire-enyman/Chart.yaml
A	k8s-aws/charts/esquire-enyman/templates/configmap.yaml
A	k8s-aws/charts/esquire-enyman/templates/deployment.yaml
A	k8s-aws/charts/esquire-enyman/templates/secret.yaml
A	k8s-aws/charts/esquire-enyman/templates/service.yaml
A	k8s-aws/charts/esquire-enyman/values.schema.json
A	k8s-aws/charts/esquire-enyman/values.yaml
A	k8s-aws/charts/esquire-gateway/Chart.yaml
A	k8s-aws/charts/esquire-gateway/templates/configmap.yaml
A	k8s-aws/charts/esquire-gateway/templates/deployment.yaml
A	k8s-aws/charts/esquire-gateway/templates/secret.yaml
A	k8s-aws/charts/esquire-gateway/templates/service.yaml
A	k8s-aws/charts/esquire-gateway/values.yaml
A	k8s-aws/charts/esquire-kcmaster/Chart.yaml
A	k8s-aws/charts/esquire-kcmaster/templates/configmap.yaml
A	k8s-aws/charts/esquire-kcmaster/templates/deployment.yaml
A	k8s-aws/charts/esquire-kcmaster/templates/secret.yaml
A	k8s-aws/charts/esquire-kcmaster/templates/service.yaml
A	k8s-aws/charts/esquire-kcmaster/values.yaml
A	k8s-aws/charts/esquire-keysmith/Chart.yaml
A	k8s-aws/charts/esquire-keysmith/templates/configmap.yaml
A	k8s-aws/charts/esquire-keysmith/templates/deployment.yaml
A	k8s-aws/charts/esquire-keysmith/templates/secret.yaml
A	k8s-aws/charts/esquire-keysmith/templates/service.yaml
A	k8s-aws/charts/esquire-keysmith/values.yaml
A	k8s-aws/charts/esquire-pacman/Chart.yaml
A	k8s-aws/charts/esquire-pacman/templates/configmap.yaml
A	k8s-aws/charts/esquire-pacman/templates/deployment.yaml
A	k8s-aws/charts/esquire-pacman/templates/secret.yaml
A	k8s-aws/charts/esquire-pacman/templates/service.yaml
A	k8s-aws/charts/esquire-pacman/values.yaml
A	k8s-aws/charts/esquire-topology/Chart.yaml
A	k8s-aws/charts/esquire-topology/esquire-topology.yml
A	k8s-aws/charts/esquire-topology/templates/configmap.yaml
A	k8s-aws/charts/esquire-topology/values.yaml
A	k8s-aws/charts/infra/activemq/Chart.yaml
A	k8s-aws/charts/infra/activemq/templates/service.yaml
A	k8s-aws/charts/infra/activemq/templates/statefulset.yaml
A	k8s-aws/charts/infra/activemq/values.yaml
A	k8s-aws/charts/infra/alloy/Chart.yaml
A	k8s-aws/charts/infra/alloy/templates/configmap.yaml
A	k8s-aws/charts/infra/alloy/templates/deployment.yaml
A	k8s-aws/charts/infra/alloy/templates/pvc.yaml
A	k8s-aws/charts/infra/alloy/templates/rbac.yaml
A	k8s-aws/charts/infra/alloy/values.yaml
A	k8s-aws/charts/infra/grafana/Chart.yaml
A	k8s-aws/charts/infra/grafana/dashboards/esquire-logging.json
A	k8s-aws/charts/infra/grafana/dashboards/esquire-services.json
A	k8s-aws/charts/infra/grafana/dashboards/esquire-topology.json
A	k8s-aws/charts/infra/grafana/icons/aukeep.svg
A	k8s-aws/charts/infra/grafana/icons/biztree.svg
A	k8s-aws/charts/infra/grafana/icons/enyman.svg
A	k8s-aws/charts/infra/grafana/icons/explorer.svg
A	k8s-aws/charts/infra/grafana/icons/gateway.svg
A	k8s-aws/charts/infra/grafana/icons/kcmaster.svg
A	k8s-aws/charts/infra/grafana/icons/keycloak.svg
A	k8s-aws/charts/infra/grafana/icons/keysmith.svg
A	k8s-aws/charts/infra/grafana/icons/kinesis.svg
A	k8s-aws/charts/infra/grafana/icons/pacman.svg
A	k8s-aws/charts/infra/grafana/icons/postgres.svg
A	k8s-aws/charts/infra/grafana/icons/sns.svg
A	k8s-aws/charts/infra/grafana/icons/sqs.svg
A	k8s-aws/charts/infra/grafana/templates/configmap-dashboards.yaml
A	k8s-aws/charts/infra/grafana/templates/configmap-datasource.yaml
A	k8s-aws/charts/infra/grafana/templates/configmap-icons.yaml
A	k8s-aws/charts/infra/grafana/templates/deployment.yaml
A	k8s-aws/charts/infra/grafana/templates/ingress.yaml
A	k8s-aws/charts/infra/grafana/templates/pvc.yaml
A	k8s-aws/charts/infra/grafana/templates/service.yaml
A	k8s-aws/charts/infra/grafana/values.yaml
A	k8s-aws/charts/infra/kafka/Chart.yaml
A	k8s-aws/charts/infra/kafka/templates/deployment.yaml
A	k8s-aws/charts/infra/kafka/templates/service.yaml
A	k8s-aws/charts/infra/kafka/values.yaml
A	k8s-aws/charts/infra/keycloak/Chart.yaml
A	k8s-aws/charts/infra/keycloak/templates/secret.yaml
A	k8s-aws/charts/infra/keycloak/templates/service.yaml
A	k8s-aws/charts/infra/keycloak/templates/statefulset.yaml
A	k8s-aws/charts/infra/keycloak/values.yaml
A	k8s-aws/charts/infra/loki/Chart.yaml
A	k8s-aws/charts/infra/loki/templates/configmap.yaml
A	k8s-aws/charts/infra/loki/templates/deployment.yaml
A	k8s-aws/charts/infra/loki/templates/pvc.yaml
A	k8s-aws/charts/infra/loki/templates/service.yaml
A	k8s-aws/charts/infra/loki/values.yaml
A	k8s-aws/charts/infra/otel-collector/Chart.yaml
A	k8s-aws/charts/infra/otel-collector/templates/configmap.yaml
A	k8s-aws/charts/infra/otel-collector/templates/deployment.yaml
A	k8s-aws/charts/infra/otel-collector/templates/service.yaml
A	k8s-aws/charts/infra/otel-collector/values.yaml
A	k8s-aws/charts/infra/postgres-exporter/Chart.yaml
A	k8s-aws/charts/infra/postgres-exporter/templates/deployment.yaml
A	k8s-aws/charts/infra/postgres-exporter/templates/service.yaml
A	k8s-aws/charts/infra/postgres-exporter/values.yaml
A	k8s-aws/charts/infra/postgres/Chart.yaml
A	k8s-aws/charts/infra/postgres/templates/secret.yaml
A	k8s-aws/charts/infra/postgres/templates/service.yaml
A	k8s-aws/charts/infra/postgres/templates/statefulset.yaml
A	k8s-aws/charts/infra/postgres/values.yaml
A	k8s-aws/charts/infra/prometheus/Chart.yaml
A	k8s-aws/charts/infra/prometheus/rules.yml
A	k8s-aws/charts/infra/prometheus/templates/configmap.yaml
A	k8s-aws/charts/infra/prometheus/templates/deployment.yaml
A	k8s-aws/charts/infra/prometheus/templates/pvc.yaml
A	k8s-aws/charts/infra/prometheus/templates/rbac.yaml
A	k8s-aws/charts/infra/prometheus/templates/service.yaml
A	k8s-aws/charts/infra/prometheus/values.yaml
A	k8s-aws/charts/infra/redis/Chart.yaml
A	k8s-aws/charts/infra/redis/templates/deployment.yaml
A	k8s-aws/charts/infra/redis/templates/service.yaml
A	k8s-aws/charts/infra/redis/values.yaml
A	k8s-aws/charts/infra/tempo/Chart.yaml
A	k8s-aws/charts/infra/tempo/templates/configmap.yaml
A	k8s-aws/charts/infra/tempo/templates/deployment.yaml
A	k8s-aws/charts/infra/tempo/templates/pvc.yaml
A	k8s-aws/charts/infra/tempo/templates/service.yaml
A	k8s-aws/charts/infra/tempo/values.yaml
A	k8s-aws/cluster-issuer.yaml
A	k8s-aws/cluster.yaml
A	k8s-aws/cognito/pre-token.py
A	k8s-aws/cognito/probe-ids.txt
A	k8s-aws/cognito/trust-policy.json
A	k8s-aws/cognito/user-pool.json
A	k8s-aws/coredns-mir0n-pro.md
A	k8s-aws/db-seed-job.yaml
A	k8s-aws/esquire-bus-policy.json
A	k8s-aws/esquire-cw-policy.json
A	k8s-aws/esquire-topology.yml
A	k8s-aws/ingress.yaml
A	k8s-aws/mq-probe/MqSendProbe.java
A	k8s-aws/msk-cluster.json
A	k8s-aws/msk-probe/MskTopicProbe.java
A	k8s-aws/show.them.all.bat
A	k8s-aws/storageclass-gp3.yaml
A	k8s-aws/values/activemq.yaml
A	k8s-aws/values/aukeep.yaml
A	k8s-aws/values/backend.yaml
A	k8s-aws/values/biztree.yaml
A	k8s-aws/values/cw-fluent-bit.yaml
A	k8s-aws/values/cw-otel.yaml
A	k8s-aws/values/enyman.yaml
A	k8s-aws/values/gateway.yaml
A	k8s-aws/values/grafana.yaml
A	k8s-aws/values/kcmaster.yaml
A	k8s-aws/values/keycloak.yaml
A	k8s-aws/values/keysmith.yaml
A	k8s-aws/values/pacman.yaml
A	k8s-aws/values/postgres-exporter.yaml
A	k8s-aws/values/postgres.yaml
M	k8s-compact/k8s-up.bat
M	k8s-oci-compact/oke-up.bat
M	k8s-oci-compact/values/gateward.yaml
M	k8s-oci/esquire-topology.yml
M	k8s-oci/oke-up.bat
M	k8s-oci/values/gateway.yaml
M	k8s/k8s-up.bat
M	kcMaster/src/main/resources/application.yml
M	keySmith/src/main/resources/application.yml
M	mesnie/src/main/resources/application.yml
M	messaging/src/main/java/pro/mir0n/esquire/messaging/changes.txt
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/OwnExcluding.java
A	messaging/src/main/java/pro/mir0n/esquire/messaging/transport/SelectingReceiver.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/OwnExcludingTest.java
A	messaging/src/test/java/pro/mir0n/esquire/messaging/transport/SelectingReceiverTest.java
M	pacMan/src/main/resources/application.yml
M	pom.xml
A	test/aws/aws-spec.py
M	tp-activemq/pom.xml
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/TransportProvider.java
M	tp-activemq/src/main/java/pro/mir0n/esquire/tp/activemq/changes.txt
A	tp-kinesis/doc/tp-kinesis.md
A	tp-kinesis/pom.xml
A	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/KinesisConsumer.java
A	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/TransportProvider.java
A	tp-kinesis/src/main/java/pro/mir0n/esquire/tp/kinesis/changes.txt
A	tp-kinesis/src/test/java/pro/mir0n/esquire/tp/kinesis/StreamModeParamsTest.java
A	tp-sqns/doc/tp-sqns.md
A	tp-sqns/pom.xml
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sns/TransportProvider.java
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SnsSupport.java
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsConsumer.java
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/SqsSupport.java
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqns/changes.txt
A	tp-sqns/src/main/java/pro/mir0n/esquire/tp/sqs/TransportProvider.java
A	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamGroupTest.java
A	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportParamsTest.java
A	tp-sqns/src/test/java/pro/mir0n/esquire/tp/sqns/SqsSupportTest.java
 366 files changed, 45987 insertions(+), 186 deletions(-)
```

---

*From `v1.2.13` till `v1.2.14`*
