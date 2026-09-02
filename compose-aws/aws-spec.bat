@echo off
setlocal
cd /d "%~dp0"
rem ===========================================================================
rem aws-spec.bat -- verify THIS lab: the bus carried on Amazon SNS, SQS and Kinesis.
rem
rem The generic checks (test\o11y\o11y-verify.py, driven by o11y-verify.bat) ask what is true of EVERY
rem deployment and are shared with the classic stack -- so they are left exactly as they are. This one asks
rem what is true only here: that AWS is attached and not built in, that the drivers made the topic, the
rem queues and the stream, that a subscription is wired the three ways that fail silently, that every AWS
rem leg is metered, and that the trace context survives the AWS hop.
rem
rem Needs the lab running. For group E to have anything to read, drive some traffic first
rem (hauberk entity-smoke, or the e2e suite).
rem
rem GROUP F STOPS LocalStack for about a minute -- it is the AWS half of the classic health-smoke, which
rem names esq-activemq and is shared with the classic stack, so it is left alone and the equivalent lives
rem here. Set AWS_SPEC_CHAOS=0 to skip it.
rem ===========================================================================
set LAB_PREFIX=esqa
set LAB_NETWORK=esq-aws_esquirenet
set PROM_URL=http://localhost:9090
set TEMPO_URL=http://localhost:3200
set SERVICES=gateway,biztree,enyman,pacman,keysmith,kcmaster,aukeep

python ..\test\aws\aws-spec.py
endlocal
