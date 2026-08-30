# -*- coding: utf-8 -*-
"""THE AWS SPEC -- what must be true of the lab that carries the bus on Amazon SNS, SQS and Kinesis.

The generic o11y checks (test/o11y/o11y-verify.py) ask what is true of EVERY deployment. This asks what is
true only here, and it exists so the generic ones do not have to be bent to fit: a shared script that names
`activemq` reports a target scraping dark forever on a stack that has no broker, and a reader who learns to
ignore that line will ignore it on the day it means something.

Five groups, and each one earns its place by having already caught something:

  A  ATTACH     AWS is mounted, never built in -- the promise that a non-AWS deployment carries none of it
  B  RESOURCES  what the drivers make in AWS: the topic, a queue per consumer, the stream and its shards
  C  WIRING     the parts of a subscription that fail SILENTLY when absent (policy, raw delivery, filter)
  D  BUSES      every AWS leg is open, reporting, and metered
  E  o11y       the trace context survives the AWS hop, and the id resolves in Tempo
  F  DEPOOL     AWS goes away: readiness drops, liveness holds, and the legs come back by themselves

Group F STOPS LocalStack for about a minute. It is the AWS half of what test/o11y's classic companion
health-smoke does against ActiveMQ -- that script names esq-activemq and cannot address this lab, and it is
shared with the classic stack, so it is left alone and the equivalent lives here instead.
Skip it with AWS_SPEC_CHAOS=0.

Run: python aws-spec.py       (through compose-aws\\aws-spec.bat, which supplies the addresses)
"""
import io
import json
import os
import subprocess
import sys
import time
import urllib.parse
import urllib.request

SVC = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
os.chdir(SVC)

PROM = os.environ.get("PROM_URL", "http://localhost:9090").rstrip("/")
TEMPO = os.environ.get("TEMPO_URL", "http://localhost:3200").rstrip("/")
PREFIX = os.environ.get("LAB_PREFIX", "esqa")
LOCALSTACK = PREFIX + "-localstack"
NETWORK = os.environ.get("LAB_NETWORK", "esq-aws_esquirenet")
SERVICES = [s.strip() for s in os.environ.get(
    "SERVICES", "gateway,biztree,enyman,pacman,keysmith,kcmaster,aukeep").split(",") if s.strip()]

RESULTS = []


def rec(gid, name, ok, detail=""):
    RESULTS.append((gid, name, "PASS" if ok else "FAIL", detail))


def skip(gid, name, detail):
    RESULTS.append((gid, name, "SKIP", detail))


def head(title):
    print("\n=== %s ===" % title)


def sh(cmd):
    """Run a command, return stdout or "" -- a check reports the absence, it does not crash on it."""
    ret = ""
    try:
        out = subprocess.run(cmd, shell=True, capture_output=True, timeout=90)
        ret = out.stdout.decode("utf-8", "replace").strip()
    except Exception:
        ret = ""
    return ret


def awslocal(args):
    return sh('docker exec %s awslocal %s' % (LOCALSTACK, args))


def http(url, timeout=10):
    ret = None
    try:
        ret = urllib.request.urlopen(url, timeout=timeout).read().decode("utf-8", "replace")
    except Exception:
        ret = None
    return ret


def promq(q):
    ret = []
    body = http("%s/api/v1/query?query=%s" % (PROM, urllib.parse.quote(q)))
    if body:
        try:
            ret = json.loads(body)["data"]["result"]
        except Exception:
            ret = []
    return ret


def running(name):
    return sh('docker ps --format "{{.Names}}" --filter "name=^%s$"' % name) == name


# --------------------------------------------------------------------------- A. ATTACH
def group_a():
    head("A. ATTACH -- AWS is mounted at deployment, never built into an image")

    # A1: the driver jars are actually mounted where loader.path names them.
    #
    # The lab MOUNTS build output, so `mvn clean` without a following `package` empties it under the running
    # containers. Two shapes, both seen: the path is simply gone, or docker created a DIRECTORY in its place
    # for a container started while it was missing. Neither kills a service that is already up -- it loaded
    # its classes at startup -- so the lab looks healthy right until the next restart, when every service dies
    # with "no transport provider class ... on the classpath". That delay is why this is checked and not
    # assumed.
    missing = []
    for svc in SERVICES:
        c = "%s-%s" % (PREFIX, svc)
        if not running(c):
            continue
        cmd = sh('docker inspect -f "{{range .Config.Cmd}}{{.}} {{end}}" %s' % c)
        if "loader.path" not in cmd:
            continue
        for jar in ("/app/tp-sqns.jar", "/app/tp-kinesis.jar"):
            if jar in cmd:
                # a FILE, not a directory -- the failure mode is a directory silently taking its place
                probe = sh('docker exec %s sh -c "test -f %s && echo file || echo NOT-A-FILE"' % (c, jar))
                if probe != "file":
                    missing.append("%s:%s=%s" % (svc, jar, probe or "absent"))
    rec("A1", "driver jars are mounted as files", not missing, "; ".join(missing) or "all mounted")

    # A2: the IMAGE carries no AWS. This is the promise that a non-AWS deployment of the same image has none
    # of it -- so it is checked on the image, not on the running container (whose mounts add the jars).
    dirty = []
    for svc in SERVICES:
        c = "%s-%s" % (PREFIX, svc)
        if not running(c):
            continue
        img = sh('docker inspect -f "{{.Config.Image}}" %s' % c)
        if not img:
            continue
        n = sh('docker run --rm --entrypoint sh %s -c "unzip -l /app/app.jar 2>/dev/null '
               '| grep -c software.amazon || true"' % img)
        if n and n.isdigit() and int(n) > 0:
            dirty.append("%s has %s AWS entries" % (svc, n))
    rec("A2", "service images carry no AWS client", not dirty, "; ".join(dirty) or "clean")

    # A3: this lab runs AWS messaging and nothing else -- a broker left in the compose file is a broker
    # somebody will point a bus at by accident.
    compose = io.open("compose-aws/compose.yaml", encoding="utf-8").read()
    brokers = [b for b in ("activemq:", "kafka:", "redis:") if ("\n  " + b) in compose]
    rec("A3", "no broker of our own in the lab", not brokers, ", ".join(brokers) or "sns/sqs/kinesis only")

    # A4: the lab's dashboard generators are forks of the classic ones. A fork that kept the classic OUTPUT
    # paths overwrites compose/, k8s/ and k8s-oci/ every time it runs -- silently, and only noticed by a
    # later diff against the mirror.
    stray = []
    for gen in ("gen-dashboard.py", "gen-topology.py"):
        p = "compose-aws/o11y/grafana/" + gen
        if os.path.exists(p):
            src = io.open(p, encoding="utf-8").read()
            for other in ('"compose", "o11y"', '"k8s", "charts"', '"k8s-oci"'):
                if other in src:
                    stray.append("%s writes %s" % (gen, other))
    rec("A4", "lab generators write only the lab's tree", not stray, "; ".join(stray) or "isolated")


# --------------------------------------------------------------------------- B. RESOURCES
def group_b():
    head("B. RESOURCES -- what the drivers made in AWS")

    topics = awslocal('sns list-topics --query "Topics[].TopicArn" --output text')
    queues = awslocal('sqs list-queues --query "QueueUrls[]" --output text')
    streams = awslocal('kinesis list-streams --query "StreamNames[]" --output text')

    rec("B1", "an SNS topic exists", bool(topics.strip()),
        topics.replace("\t", " ") or "none -- no leg opened an sns publisher")

    qs = [q.rsplit("/", 1)[-1] for q in queues.split() if q.strip()]
    rec("B2", "SQS queues exist", bool(qs), "%d: %s" % (len(qs), ", ".join(sorted(qs)[:6])) if qs else "none")

    # B3: route-by is the whole R&R design -- the filter a JMS selector used to apply became a DESTINATION.
    # A request queue per SLOT and a response queue per ROD is what makes a reply reach its requester.
    req = [q for q in qs if "request" in q]
    resp = [q for q in qs if "response" in q]
    rec("B3", "route-by split the R&R destinations", bool(req) and bool(resp),
        "request=%s response=%s" % (sorted(req), sorted(resp)))

    # B4: SNS keeps nothing, so a broadcast consumer owns a queue named from its rod-id. One queue = one
    # instance getting the WHOLE broadcast; competing consumers would be the bug.
    bcast = [q for q in qs if "broadcast" in q]
    rec("B4", "a queue per broadcast consumer", bool(bcast), ", ".join(sorted(bcast)) or "none")

    if streams.strip():
        name = streams.split()[0]
        summary = awslocal('kinesis describe-stream-summary --stream-name %s '
                           '--query "StreamDescriptionSummary.[StreamStatus,OpenShardCount]" --output text' % name)
        parts = summary.split()
        status = parts[0] if parts else "?"
        shards = parts[1] if len(parts) > 1 else "?"
        rec("B5", "the Kinesis stream is ACTIVE", status == "ACTIVE", "%s status=%s shards=%s" % (name, status, shards))
    else:
        skip("B5", "the Kinesis stream is ACTIVE", "no stream -- no leg opened a kinesis publisher")


# --------------------------------------------------------------------------- C. WIRING
def group_c():
    head("C. WIRING -- the parts of an SNS subscription that fail SILENTLY when wrong")

    arns = [a for a in awslocal('sns list-topics --query "Topics[].TopicArn" --output text').split() if a.strip()]
    if not arns:
        skip("C1", "subscription wiring", "no topic")
        return

    bad_raw, bad_filter, checked = [], [], 0
    for arn in arns:
        subs = awslocal('sns list-subscriptions-by-topic --topic-arn %s '
                        '--query "Subscriptions[].SubscriptionArn" --output text' % arn)
        for s in subs.split():
            if not s.startswith("arn:"):
                continue
            checked += 1
            attrs = awslocal('sns get-subscription-attributes --subscription-arn %s --output json' % s)
            try:
                a = json.loads(attrs)["Attributes"]
            except Exception:
                continue
            # RAW delivery: without it SNS wraps the body in its own envelope and the header bag is no longer
            # the message -- every consumer then reads a bag that is not there.
            if a.get("RawMessageDelivery") != "true":
                bad_raw.append(s.rsplit(":", 1)[-1])
            # A filter policy an EARLIER deployment wrote stays on the subscription for good: Subscribe applies
            # attributes only when it CREATES one. That is exactly how enyMan once received 8 of 255 messages.
            if a.get("FilterPolicy"):
                bad_filter.append("%s=%s" % (s.rsplit(":", 1)[-1], a.get("FilterPolicy")))
    rec("C1", "raw message delivery on every subscription", not bad_raw,
        "%d checked; bad: %s" % (checked, bad_raw) if bad_raw else "%d subscriptions" % checked)
    rec("C2", "no stale filter policy", not bad_filter, "; ".join(bad_filter) or "all cleared")

    # C3: SNS is a different service and a queue refuses its writes until a policy says otherwise. LocalStack
    # lets it through; real AWS does not -- so without this the lab passes and the cloud delivers nothing.
    queues = [q for q in awslocal('sqs list-queues --query "QueueUrls[]" --output text').split() if q.strip()]
    unpoliced = []
    for q in queues:
        if "broadcast" not in q:
            continue
        pol = awslocal('sqs get-queue-attributes --queue-url %s --attribute-names Policy --output json' % q)
        if "sns.amazonaws.com" not in pol:
            unpoliced.append(q.rsplit("/", 1)[-1])
    rec("C3", "the topic may write into each broadcast queue", not unpoliced,
        "; ".join(unpoliced) or "policy present on all")


# --------------------------------------------------------------------------- D. BUSES
def group_d():
    head("D. BUSES -- every AWS leg open, reporting and metered")

    down = []
    for svc in SERVICES:
        c = "%s-%s" % (PREFIX, svc)
        if not running(c):
            continue
        body = sh('docker run --rm --network %s curlimages/curl:latest -s -m 10 http://%s:8090/actuator/health'
                  % (NETWORK, svc))
        if not body:
            continue
        try:
            h = json.loads(body)["components"]["messagingBus"]
        except Exception:
            continue
        for bus, state in (h.get("details") or {}).items():
            if state == "DOWN":
                down.append("%s/%s" % (svc, bus))
    rec("D1", "no bus reports DOWN", not down, "; ".join(down) or "all UP or UNKNOWN")

    up = promq("messaging_transport_up")
    zero = ["%s/%s" % (s["metric"].get("application"), s["metric"].get("bus_id"))
            for s in up if s["value"][1] == "0"]
    rec("D2", "messaging_transport_up is never 0", bool(up) and not zero,
        "; ".join(zero) or "%d legs reporting" % len(up))

    sent = promq("sum(messaging_send_total)")
    n = float(sent[0]["value"][1]) if sent else 0
    rec("D3", "the bus has actually carried messages", n > 0, "messaging_send_total=%d" % n)


# --------------------------------------------------------------------------- E. o11y ACROSS THE AWS HOP
def group_e():
    head("E. o11y -- the trace context survives the AWS hop")

    streams = awslocal('kinesis list-streams --query "StreamNames[]" --output text').split()
    if not streams:
        skip("E1", "a record carries the trace context", "no kinesis stream")
        return
    name = streams[0]

    # Reading Kinesis does NOT consume: an iterator is the reader's own place in the log, so this inspects the
    # real wire without taking anything off it. That is why the trace check is done here and not on a queue.
    shards = awslocal('kinesis list-shards --stream-name %s --query "Shards[].ShardId" --output text' % name).split()
    bags, traced = [], []
    for sid in shards:
        it = awslocal('kinesis get-shard-iterator --stream-name %s --shard-id %s '
                      '--shard-iterator-type TRIM_HORIZON --query ShardIterator --output text' % (name, sid))
        if not it or it.startswith("An error"):
            continue
        data = awslocal('kinesis get-records --shard-iterator %s --limit 200 --query "Records[].Data" --output text'
                        % it)
        for b64 in data.split():
            try:
                import base64
                bag = json.loads(base64.b64decode(b64).decode("utf-8"))
            except Exception:
                continue
            bags.append(bag)
            if bag.get("TraceParent") and bag.get("CorrelationID"):
                traced.append(bag)

    rec("E1", "records are on the wire", bool(bags), "%d records across %d shards" % (len(bags), len(shards)))

    if not bags:
        skip("E2", "a record carries TraceParent + CorrelationID", "no records")
        skip("E3", "the record's trace id resolves in Tempo", "no records")
        return

    rec("E2", "a record carries TraceParent + CorrelationID", bool(traced),
        "%d of %d records traced (untraced = written while observability was off)" % (len(traced), len(bags)))

    if not traced:
        skip("E3", "the record's trace id resolves in Tempo", "no traced record")
        return

    # The trace-id half of the traceparent must BE the correlationId -- that identity is what makes one id
    # join a metric, a trace and a log. A traceparent carrying some other trace id would look healthy here
    # and still break the single pane.
    # the NEWEST traced record, not the last one the shard scan happened to yield: shards are read one after
    # another, so scan order is not time order, and an old record's trace may have aged out of Tempo.
    traced.sort(key=lambda b: b.get("SendingTime") or "")
    bag = traced[-1]
    tp, cid = bag["TraceParent"], bag["CorrelationID"]
    rec("E3", "traceparent's trace id IS the correlationId", tp.split("-")[1] == cid,
        "%s vs %s" % (tp, cid))

    # /api/traces/{id} -- NOT /api/v2/. Count the spans rather than string-matching the body: a 200 whose
    # trace carries no span is the shape that reads as healthy and is not.
    spans = 0
    body = http("%s/api/traces/%s" % (TEMPO, urllib.parse.quote(cid)))
    if body:
        try:
            d = json.loads(body)
            for b in d.get("batches", []):
                for ss in b.get("scopeSpans", []):
                    spans += len(ss.get("spans", []))
        except Exception:
            spans = 0
    rec("E4", "the id resolves to a trace in Tempo", spans > 0,
        "trace %s... has %d spans" % (cid[:16], spans))


# --------------------------------------------------------------------------- F. DEPOOL
def probe(svc, path):
    """The actuator HTTP status, from inside the lab network. The service images carry no curl, so a throwaway
    one is run on the network rather than exec'd into the container."""
    out = sh('docker run --rm --network %s curlimages/curl:latest -s -o /dev/null -w "%%{http_code}" '
             '-m 10 http://%s:8090/actuator/health/%s' % (NETWORK, svc, path))
    return out.strip()[-3:] if out else "000"


def wait_for(svc, path, want, seconds):
    """Poll one probe until it reads `want`, or give up. Returns the last code seen."""
    ret = ""
    deadline = time.time() + seconds
    while time.time() < deadline:
        ret = probe(svc, path)
        if (ret == want) if want else False:
            break
        time.sleep(3)
    return ret


def group_f():
    head("F. DEPOOL -- AWS goes away: readiness drops, liveness holds, the legs return")

    if os.environ.get("AWS_SPEC_CHAOS", "1") == "0":
        skip("F1", "readiness sweep", "AWS_SPEC_CHAOS=0")
        skip("F2", "readiness drops while liveness holds", "AWS_SPEC_CHAOS=0")
        skip("F3", "the legs recover on their own", "AWS_SPEC_CHAOS=0")
        return

    # F1: every service is ready BEFORE anything is broken. Without this the rest proves nothing -- a service
    # that was already unready would "flip" without AWS having anything to do with it.
    # Give a settling fleet a moment. A request/response SERVER is the reason: its bus health is the AGE of
    # its last successful send, so kcMaster reads DOWN until something has actually asked it for something.
    # Sampling once, cold, would call a healthy fleet unready and skip the rest of the group.
    unready = []
    for svc in SERVICES:
        if not running("%s-%s" % (PREFIX, svc)):
            continue
        code = wait_for(svc, "readiness", "200", 60)
        if code != "200":
            unready.append("%s=%s" % (svc, code))
    rec("F1", "every service is ready to begin with", not unready,
        "; ".join(unready) + " -- drive some traffic first" if unready else "all 200")
    if unready:
        skip("F2", "readiness drops while liveness holds", "did not start from a ready fleet")
        skip("F3", "the legs recover on their own", "did not start from a ready fleet")
        return

    victim = "enyman" if "enyman" in SERVICES else SERVICES[0]
    try:
        print("  ... stopping %s (this takes about a minute)" % LOCALSTACK)
        sh("docker stop %s" % LOCALSTACK)

        # F2: the WHOLE point of the readiness/liveness split. A transport outage must DEPOOL the pod, never
        # restart it: readiness goes 503 so k8s stops routing, liveness stays 200 so nothing kills a process
        # that is perfectly healthy and simply has nothing to talk to. The SQS receive long-polls up to 20s,
        # so the flip is not instant.
        code = wait_for(victim, "readiness", "503", 120)
        live = probe(victim, "liveness")
        rec("F2", "readiness drops while liveness holds", code == "503" and live == "200",
            "%s readiness=%s liveness=%s" % (victim, code, live))
    finally:
        # ALWAYS put it back, whatever happened above -- a spec run must not leave the lab broken.
        sh("docker start %s" % LOCALSTACK)

    # F3: recovery is the driver's own doing, with nothing restarted. The consumer re-makes a queue that went
    # away, the publisher resolves a topic that is gone, and the Kinesis reader takes a fresh iterator.
    #
    # EVERY service, not just the one that was watched going down. The three transports fail and recover by
    # different mechanisms, and they are not spread evenly across the fleet: the first version of this check
    # watched one producer, passed, and said nothing while auKeep's Kinesis reader stayed dead -- its poll
    # threads had ended on the outage and nothing was left reading when AWS came back. A fleet-wide sweep is
    # what makes the check cover the transports rather than one service's share of them.
    lagging = []
    for svc in SERVICES:
        if not running("%s-%s" % (PREFIX, svc)):
            continue
        code = wait_for(svc, "readiness", "200", 240)
        if code != "200":
            lagging.append("%s=%s" % (svc, code))
    rec("F3", "every leg recovers on its own", not lagging,
        "; ".join(lagging) or "all %d services back to 200, nothing restarted" % len(SERVICES))


def main():
    print("Esquire AWS SPEC  [lab=%s]  Prometheus=%s" % (PREFIX, PROM or "<unset>"))
    if not running(LOCALSTACK):
        print("  %s is not running -- nothing to check." % LOCALSTACK)
        return 1
    for g in (group_a, group_b, group_c, group_d, group_e, group_f):
        g()
    print()
    print("%-5s %-48s %-6s %s" % ("ID", "CHECK", "VERDICT", "DETAIL"))
    print("-" * 118)
    for gid, name, verdict, detail in RESULTS:
        print("%-5s %-48s %-6s %s" % (gid, name[:48], verdict, detail[:60]))
    fails = [r for r in RESULTS if r[2] == "FAIL"]
    skips = [r for r in RESULTS if r[2] == "SKIP"]
    print()
    print("PASS: %d   FAIL: %d   SKIP: %d" % (len(RESULTS) - len(fails) - len(skips), len(fails), len(skips)))
    for r in fails:
        print("  FAIL %s %s -- %s" % (r[0], r[1], r[3]))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
