#!/usr/bin/env bash
# =============================================================================
# Audit smoke matrix driver -- see README.md.
# Per cell: configure audit env -> recreate the audit-path containers -> run the
# hauberk EntitySmoke (service-account auth, office/user/account/deposit/cleanup,
# which also audits usr_par) -> validate the audit landed -> record PASS/FAIL.
#
# Usage:
#   ./run.sh docker-pg            all Postgres-primary docker cells
#   ./run.sh docker-pg c          a single cell (b-ded-pg|b-ded-ora|c|c-ora|ck|ck-ora|d|dk|a)
#   ./run.sh docker-ora           Oracle-primary docker cells   (needs Oracle up: esq2025/q @ //host:1521/MIR0N)
#   ./run.sh k8s                  local-k8s cells
# Prereqs: docker stack up (compose), hauberk.jar built, sqlplus(host)+psql(docker exec).
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SVCS="$(cd "${HERE}/../.." && pwd)"
HBK="$(cd "${SVCS}/../explorer/hauberk" && pwd)"
SEED="$(cd "${SVCS}/../db.seed" && pwd)"
STAMP="$(date +%y%m%d-%H%M)"
RESULTS="${HERE}/results-${STAMP}.md"
ORA="esq2025/q@//localhost:1521/MIR0N"
# docker uses the esq-postgres CONTAINER. It used to use the external host instance (pg18 on
# localhost:5432) and this driver read that -- but compose.yaml now defaults every DB_*_HOST to the
# "postgres" service, and the seed is baked into that image. Reading the host instance here measured a
# database the services never write to, so every docker-pg cell reported whatever that stale database
# happened to hold. All docker-pg DB access now goes through the container; k8s keeps using its
# in-cluster postgres (k8s_pg_* via kubectl exec, further below).
hpg() { docker exec -i esq-postgres psql -U esq2025 -d esq2025 "$@"; }

# ---- audit option (a): DB triggers. The base seed is trigger-FREE; the (a) cell applies the trigger
# overlay, runs, validates, then DROPS it so the next (bus/in-process) cell is trigger-free again
# (a stray trigger would write *_log in-transaction and corrupt the bus delta -- mir0n's rule).
# AUDIT-OFF mechanism: the audit ref points at the EXPLICIT "audit-off" bus (topology slot rod-class
# XRodDisabled) -- a true no-op sink, so the bus audit is OFF (the triggers carry the audit instead).
# This is the #17 "disable only when explicitly defined": a blank/undefined bus-id (correctly) fails fast
# at boot, so audit-off must be a real, XRodDisabled-backed bus. Verified: a no-trigger smoke leaves *_log flat.
AUDIT_OFF="audit-off"
pg_trig_apply()  { cat "${SEED}"/postgres/triggers/esq_*_briud.sql | hpg >/dev/null 2>&1; }
pg_trig_drop()   { hpg <"${SEED}/postgres/triggers/drop.sql" >/dev/null 2>&1; }
ora_trig_apply() { ( cd "${SEED}/oracle/triggers" && /c/ora19/bin/sqlplus -S "${ORA}" @all.sql  >/dev/null 2>&1 ); }
ora_trig_drop()  { ( cd "${SEED}/oracle/triggers" && /c/ora19/bin/sqlplus -S "${ORA}" @drop.sql >/dev/null 2>&1 ); }
# The *_log dedup unique index is an OPTIONAL overlay (db.seed/<db>/dedup/), NOT in the base seed. It is
# mutually exclusive with the triggers: a BRIUD trigger fires per DML op, so an in-request insert+update
# writes two *_log rows with the same dedup key -> the unique index rolls the business write back. So the
# (a) cell ensures the index is ABSENT before applying triggers (a no-op on the dedup-free base seed; it
# only matters on a DB still seeded from the old index-carrying seed).
pg_dedup_drop()  { hpg <"${SEED}/postgres/dedup/drop.sql" >/dev/null 2>&1; }
ora_dedup_drop() { ( cd "${SEED}/oracle/dedup" && /c/ora19/bin/sqlplus -S "${ORA}" @drop.sql >/dev/null 2>&1 ); }
pg_trig_count()  { hpg -t -A -c "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgname ILIKE 'esq\_%';" 2>/dev/null | tr -d '\r'; }
ora_trig_count() { /c/ora19/bin/sqlplus -S "${ORA}" <<'SQL' 2>/dev/null | tr -d ' \r' | grep -E '^[0-9]'
set heading off feedback off pagesize 0
SELECT count(*) FROM user_triggers WHERE trigger_name LIKE 'ESQ\_%' ESCAPE '\';
exit
SQL
}

# ---- audit-DB count helpers (5 log tables: org,usr,acc,orgpar,usrpar) ----
pg_counts() { # docker audit DB = the HOST pg18 (positional arg kept for call-site compatibility, ignored)
  hpg -t -A -F',' -c \
   "SELECT (SELECT count(*) FROM esq_org_log),(SELECT count(*) FROM esq_user_log),(SELECT count(*) FROM esq_account_log),(SELECT count(*) FROM esq_org_par_log),(SELECT count(*) FROM esq_usr_par_log);" 2>/dev/null
}
ora_counts() {
  /c/ora19/bin/sqlplus -S "${ORA}" <<'SQL' 2>/dev/null | tr -d ' \r' | grep -E '^[0-9]'
set heading off feedback off pagesize 0
SELECT (SELECT count(*) FROM ESQ_ORG_LOG)||','||(SELECT count(*) FROM ESQ_USER_LOG)||','||(SELECT count(*) FROM ESQ_ACCOUNT_LOG)||','||(SELECT count(*) FROM ESQ_ORG_PAR_LOG)||','||(SELECT count(*) FROM ESQ_USR_PAR_LOG) FROM dual;
exit
SQL
}
redis_len() { docker exec esq-redis redis-cli XLEN esquire.rod.audit 2>/dev/null | tr -d '\r'; }
kafka_off() { MSYS_NO_PATHCONV=1 docker exec esq-kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic esquire.rod.audit 2>/dev/null | awk -F: '{s+=$3} END{print s+0}'; }

run_smoke() { # runs one EntitySmoke against gw :7070
  ( cd "${HBK}" && java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED \
      --add-opens=java.base/sun.nio.ch=ALL-UNNAMED -jar target/hauberk.jar run entity-smoke >/dev/null 2>&1 )
}

recreate_docker() { # recreate producers (+aukeep unless $1=no-aukeep) with the current exported env
  local extra="aukeep"; [ "${1:-}" = "no-aukeep" ] && extra=""
  ( cd "${SVCS}/compose" && docker compose up -d --force-recreate --no-deps enyman pacman keysmith ${extra} >/dev/null 2>&1 )
  # The gateway caches routes by container IP; a force-recreate gives producers NEW IPs, so the gateway
  # must be restarted or the smoke's calls hit dead IPs (503) and audit nothing.
  docker restart esq-gateway >/dev/null 2>&1
  # poll the gateway until it answers HTTP (any code, even 401) -> routes are live
  local i code
  for i in $(seq 1 40); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:7070/esq-enode?kind=20&name=probe" 2>/dev/null)"
    [ -n "$code" ] && [ "$code" != "000" ] && [ "$code" != "502" ] && [ "$code" != "503" ] && break
    sleep 2
  done
  sleep 10   # producers reconnect to the bus + (c/ck) auKeep re-subscribes
}

# delta-positive check on selected indices of "o,u,a,op,up"
grew() { # $1=pre $2=post $3=csv-of-indices(0..4)  -> echo PASS/FAIL + deltas
  IFS=',' read -ra A <<<"$1"; IFS=',' read -ra B <<<"$2"
  local ok=1 d=""
  for i in 0 1 2 3 4; do d+="$(( ${B[$i]:-0} - ${A[$i]:-0} )),"; done
  for i in ${3//,/ }; do [ "$(( ${B[$i]:-0} - ${A[$i]:-0} ))" -gt 0 ] || ok=0; done
  [ $ok -eq 1 ] && echo "PASS ${d%,}" || echo "FAIL ${d%,}"
}

row() { printf '| %-14s | %-26s | %-6s | %s |\n' "$1" "$2" "$3" "$4" >>"${RESULTS}"; }

# --------------------------------------------------------------------------
# Docker, Postgres primary -- a cell = export audit env, recreate, smoke, validate
# audit DB for b/c/ck on docker-pg = the HOST pg18 (no esq-postgres container; org,usr,acc,usrpar grow)
# --------------------------------------------------------------------------
cell_docker_pg() {
  local cell="$1"
  unset AUDIT_BUS_ID DB_DATAKEEP_URL
  local idx="0,1,2,4" desc="" stream="" streamfn="" trig="" warm=""
  case "$cell" in
    a)          export AUDIT_BUS_ID="$AUDIT_OFF"; trig="pg"; desc="DB triggers in-tx (audit msg off)";;
    b-ded-pg)   export AUDIT_BUS_ID=audit-b; desc="in-process keep, dedicated pool (pg)";;
    c)          export AUDIT_BUS_ID=audit-c;   desc="bus AMQ -> auKeep -> *_log (pg)";;
    ck)         export AUDIT_BUS_ID=audit-ck; warm="1"; desc="bus Kafka -> auKeep -> *_log (pg)";;
    d)          export AUDIT_BUS_ID=audit-d;   desc="Redis stream (producer-only)"; stream="redis"; streamfn="redis_len";;
    dk)         export AUDIT_BUS_ID=audit-dk;  desc="Kafka stream (producer-only)"; stream="kafka"; streamfn="kafka_off";;
    *) echo "unknown docker-pg cell: $cell"; return 1;;
  esac
  echo ">>> docker-pg / $cell : $desc"
  local pre post res
  if [ -n "$trig" ]; then
    pg_dedup_drop                                      # triggers need the dedup index absent
    pg_trig_apply
    echo "    [trig] applied ($(pg_trig_count) ESQ triggers); dedup index dropped; audit msg OFF"
    pre="$(pg_counts esq-postgres)"
    recreate_docker; run_smoke; sleep 8
    post="$(pg_counts esq-postgres)"
    res="$(grew "$pre" "$post" "$idx")"
    pg_trig_drop                                       # isolation: leave the DB trigger-free for the next cell
    echo "    pre=$pre post=$post -> $res (triggers dropped; now $(pg_trig_count))"
    row "docker-pg" "$cell ($desc)" "${res%% *}" "$res ; pg pre=$pre post=$post ; triggers applied+dropped"
    return
  fi
  if [ -n "$stream" ]; then
    docker stop esq-aukeep >/dev/null 2>&1            # producer-only sink: no consumer should drain it into *_log
    local spre; spre="$($streamfn)"; pre="$(pg_counts esq-postgres)"
    recreate_docker no-aukeep; run_smoke; sleep 6
    local spost; spost="$($streamfn)"; post="$(pg_counts esq-postgres)"
    local sd=$(( ${spost:-0} - ${spre:-0} )); local lg ld; lg="$(grew "$pre" "$post" "")"; ld="${lg#* }"
    if   [ "$sd" -gt 0 ] && [ "$ld" = "0,0,0,0,0" ]; then res="PASS ${stream}Δ=${sd}; *_log flat"
    elif [ "$sd" -gt 0 ]; then res="PASS ${stream}Δ=${sd}; *_log Δ=${ld}"
    else res="FAIL ${stream}Δ=${sd}"; fi
    docker start esq-aukeep >/dev/null 2>&1
  else
    if [ -n "$warm" ]; then
      # Kafka consumer-group COLD START (mirrors cell_k8s): warm auKeep's kafka consumer with a throwaway
      # smoke + poll until *_log first grows (group joined + draining), THEN measure -- so the fixed sleep
      # below doesn't snapshot before the first-join rebalance delivers anything (earliest => not lost).
      recreate_docker
      local w0 wi; w0="$(pg_counts esq-postgres)"; run_smoke
      for wi in $(seq 1 20); do sleep 3; [ "$(pg_counts esq-postgres)" != "$w0" ] && break; done
      echo "    [warm] kafka consumer group warmed (drained after ~$((wi*3))s)"
      pre="$(pg_counts esq-postgres)"; run_smoke; sleep 8; post="$(pg_counts esq-postgres)"
    else
      pre="$(pg_counts esq-postgres)"
      recreate_docker; run_smoke; sleep 8
      post="$(pg_counts esq-postgres)"
    fi
    res="$(grew "$pre" "$post" "$idx")"
  fi
  echo "    pre=$pre post=$post -> $res"
  row "docker-pg" "$cell ($desc)" "${res%% *}" "$res ; pg pre=$pre post=$post"
}

# Docker, Postgres PRIMARY, ORACLE audit DB (b-dedicated / c / ck) -- validate via host sqlplus.
# Entities are created in Postgres; the audit is written to Oracle MIR0N (esq2025/q @ host.docker.internal:1521).
cell_docker_pg_ora() {
  local cell="$1"
  unset AUDIT_BUS_ID DB_DATAKEEP_URL \
        DB_DATAKEEP_VENDOR DB_DATAKEEP_HOST DB_DATAKEEP_PORT DB_DATAKEEP_NAME
  local desc="" ORAURL="jdbc:oracle:thin:@//host.docker.internal:1521/MIR0N"
  case "$cell" in
    b-ded-ora) export AUDIT_BUS_ID=audit-b \
                      DB_DATAKEEP_URL="$ORAURL"; desc="in-process keep -> ORACLE *_log";;
    c-ora)     export AUDIT_BUS_ID=audit-c  DB_DATAKEEP_VENDOR=dev-oracle DB_DATAKEEP_HOST=host.docker.internal \
                      DB_DATAKEEP_PORT=1521 DB_DATAKEEP_NAME=MIR0N; desc="bus AMQ -> auKeep -> ORACLE *_log";;
    ck-ora)    export AUDIT_BUS_ID=audit-ck DB_DATAKEEP_VENDOR=dev-oracle DB_DATAKEEP_HOST=host.docker.internal \
                      DB_DATAKEEP_PORT=1521 DB_DATAKEEP_NAME=MIR0N; desc="bus Kafka -> auKeep -> ORACLE *_log";;
    *) echo "unknown oracle-audit cell: $cell"; return 1;;
  esac
  echo ">>> docker-pg+ora-audit / $cell : $desc"
  local pre post res
  pre="$(ora_counts)"
  recreate_docker; run_smoke; sleep 10
  post="$(ora_counts)"
  res="$(grew "$pre" "$post" "0,1,2,4")"
  echo "    ORA pre=$pre post=$post -> $res"
  row "docker-pg/ora" "$cell ($desc)" "${res%% *}" "$res ; ora pre=$pre post=$post"
}

# --------------------------------------------------------------------------
# Docker, ORACLE primary (all services -> MIR0N). Audit DB = pg or oracle per cell;
# entities live in Oracle, so the (a) trigger cell lands *_log in Oracle (in-transaction).
# --------------------------------------------------------------------------
set_oracle_primary() { for s in ENYMAN PACMAN KEYSMITH BIZTREE; do
  export DB_${s}_VENDOR=dev-oracle DB_${s}_HOST=host.docker.internal DB_${s}_PORT=1521 DB_${s}_NAME=MIR0N; done; }
unset_oracle_primary() { for s in ENYMAN PACMAN KEYSMITH BIZTREE; do
  unset DB_${s}_VENDOR DB_${s}_HOST DB_${s}_PORT DB_${s}_NAME; done; }

recreate_full() { # biztree + producers (+aukeep) -- oracle-primary needs biztree re-pointed too
  local extra="aukeep"; [ "${1:-}" = "no-aukeep" ] && extra=""
  ( cd "${SVCS}/compose" && docker compose up -d --force-recreate --no-deps biztree enyman pacman keysmith ${extra} >/dev/null 2>&1 )
  docker restart esq-gateway >/dev/null 2>&1
  local i code
  for i in $(seq 1 45); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:7070/esq-enode?kind=20&name=probe" 2>/dev/null)"
    [ -n "$code" ] && [ "$code" != "000" ] && [ "$code" != "502" ] && [ "$code" != "503" ] && break
    sleep 2
  done
  sleep 14   # bizTree rebuilds its cache from the new primary
}

cell_docker_ora() {
  local cell="$1"
  unset AUDIT_BUS_ID DB_DATAKEEP_URL \
        DB_DATAKEEP_VENDOR DB_DATAKEEP_HOST DB_DATAKEEP_PORT DB_DATAKEEP_NAME
  set_oracle_primary
  local ORAURL="jdbc:oracle:thin:@//host.docker.internal:1521/MIR0N" db="ora" desc="" stream="" streamfn="" idx="0,1,2,4" trig=""
  case "$cell" in
    a)         export AUDIT_BUS_ID="$AUDIT_OFF"; db="ora"; trig="ora"; desc="DB triggers in-tx (oracle primary, audit msg off)";;
    b-ded-pg)  export AUDIT_BUS_ID=audit-b DB_DATAKEEP_URL="jdbc:postgresql://postgres:5432/esq2025"; db="pg"; desc="in-proc keep -> PG audit";;
    b-ded-ora) export AUDIT_BUS_ID=audit-b DB_DATAKEEP_URL="$ORAURL"; db="ora"; desc="in-proc keep -> ORA audit";;
    c-pg)      export AUDIT_BUS_ID=audit-c;  db="pg";  desc="bus AMQ -> auKeep -> PG audit";;
    c-ora)     export AUDIT_BUS_ID=audit-c  DB_DATAKEEP_VENDOR=dev-oracle DB_DATAKEEP_HOST=host.docker.internal DB_DATAKEEP_PORT=1521 DB_DATAKEEP_NAME=MIR0N; db="ora"; desc="bus AMQ -> auKeep -> ORA audit";;
    ck-pg)     export AUDIT_BUS_ID=audit-ck; db="pg";  desc="bus Kafka -> auKeep -> PG audit";;
    ck-ora)    export AUDIT_BUS_ID=audit-ck DB_DATAKEEP_VENDOR=dev-oracle DB_DATAKEEP_HOST=host.docker.internal DB_DATAKEEP_PORT=1521 DB_DATAKEEP_NAME=MIR0N; db="ora"; desc="bus Kafka -> auKeep -> ORA audit";;
    d)         export AUDIT_BUS_ID=audit-d;  stream="redis"; streamfn="redis_len"; desc="Redis stream";;
    dk)        export AUDIT_BUS_ID=audit-dk; stream="kafka"; streamfn="kafka_off"; desc="Kafka stream";;
    *) echo "unknown oracle-primary cell: $cell"; return 1;;
  esac
  echo ">>> docker-ora / $cell : $desc"
  local pre post res
  cnt() { [ "$db" = "ora" ] && ora_counts || pg_counts esq-postgres; }
  # warm-up smoke after a primary switch: Oracle has no "hauberk-office-smoke" yet, so the first run
  # creates+caches it; the MEASURED run then exercises the full create/param/move/delete lifecycle.
  if [ -n "$trig" ]; then
    ora_dedup_drop                                     # triggers need the dedup index absent
    ora_trig_apply
    echo "    [trig] applied ($(ora_trig_count) ESQ triggers on MIR0N); dedup index dropped; audit msg OFF"
    recreate_full; run_smoke; sleep 4                  # warm-up (caches the office in the new primary)
    pre="$(ora_counts)"
    run_smoke; sleep 10
    post="$(ora_counts)"
    res="$(grew "$pre" "$post" "$idx")"
    ora_trig_drop                                      # isolation: drop before any subsequent bus cell
    echo "    [ora] pre=$pre post=$post -> $res (triggers dropped; now $(ora_trig_count))"
    row "docker-ora" "$cell ($desc)" "${res%% *}" "$res ; ora pre=$pre post=$post ; triggers applied+dropped"
    return
  fi
  if [ -n "$stream" ]; then
    docker stop esq-aukeep >/dev/null 2>&1
    recreate_full no-aukeep; run_smoke; sleep 4
    local spre; spre="$($streamfn)"
    run_smoke; sleep 6
    local spost; spost="$($streamfn)"; local sd=$(( ${spost:-0} - ${spre:-0} ))
    [ "$sd" -gt 0 ] && res="PASS ${stream}Δ=${sd}" || res="FAIL ${stream}Δ=${sd}"
    docker start esq-aukeep >/dev/null 2>&1
  else
    recreate_full; run_smoke; sleep 4
    pre="$(cnt)"
    run_smoke; sleep 10
    post="$(cnt)"
    res="$(grew "$pre" "$post" "$idx")"
  fi
  echo "    [$db] pre=${pre:-stream} post=${post:-stream} -> $res"
  row "docker-ora" "$cell ($desc)" "${res%% *}" "$res ; $db pre=${pre:-_} post=${post:-_}"
}

# ==========================================================================
# Local k8s (Docker Desktop), Postgres only. Audit DB = the infra StatefulSet
# esquire-infra-postgres-0; kafka/redis are Deployments (exec via deployment/).
# Producers cycle via `helm upgrade --reuse-values --set audit.busId=...` (keeps the running image
# tag + every other deployed value, overrides only the audit busId). The pod templates carry no
# checksum/config annotation, so a configmap change needs an explicit rollout
# restart. The gateway routes by Service DNS, so producer restarts need no gw
# bounce. Smoke runs against the ingress (hauberk-k8s.properties).
# ==========================================================================
KPG=esquire-infra-postgres-0
k8s_pg_counts() { local r i; for i in 1 2 3 4 5; do   # retry: kubectl exec can blip during the busy restart phase
  r=$(kubectl exec "$KPG" -- psql -U esq2025 -d esq2025 -t -A -F',' -c \
   "SELECT (SELECT count(*) FROM esq_org_log),(SELECT count(*) FROM esq_user_log),(SELECT count(*) FROM esq_account_log),(SELECT count(*) FROM esq_org_par_log),(SELECT count(*) FROM esq_usr_par_log);" 2>/dev/null | tr -d '\r')
  [ -n "$r" ] && break; sleep 3; done; echo "$r"; }
k8s_redis_len() { kubectl exec deployment/esquire-infra-redis -- redis-cli XLEN esquire.rod.audit 2>/dev/null | tr -d '\r'; }
k8s_kafka_off() { MSYS_NO_PATHCONV=1 kubectl exec deployment/esquire-infra-kafka -- /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic esquire.rod.audit 2>/dev/null | awk -F: '{s+=$3} END{print s+0}'; }
k8s_trig_apply() { cat "${SEED}"/postgres/triggers/esq_*_briud.sql | kubectl exec -i "$KPG" -- psql -U esq2025 -d esq2025 >/dev/null 2>&1; }
k8s_trig_drop()  { kubectl exec -i "$KPG" -- psql -U esq2025 -d esq2025 <"${SEED}/postgres/triggers/drop.sql" >/dev/null 2>&1; }
k8s_trig_count() { kubectl exec "$KPG" -- psql -U esq2025 -d esq2025 -t -A -c "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgname ILIKE 'esq\_%';" 2>/dev/null | tr -d '\r'; }
k8s_dedup_drop() { kubectl exec -i "$KPG" -- psql -U esq2025 -d esq2025 <"${SEED}/postgres/dedup/drop.sql" >/dev/null 2>&1; }

run_smoke_k8s() { ( cd "${HBK}" && java -Dhauberk.config=hauberk-k8s.properties \
    --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.base/sun.nio.ch=ALL-UNNAMED -jar target/hauberk.jar run entity-smoke >/dev/null 2>&1 ); }

# --reuse-values (NOT -f values/<svc>.yaml): the deployed image tag is a k8s-rebuild stamp that differs
# from the tag pinned in values/, so re-applying the file would downgrade the image to a tag the local
# daemon may not have (ImagePullBackOff). --reuse-values keeps the running tag + every other deployed
# value and overrides ONLY the audit knob (busId).
# --set-string: a whitespace busId (option a) survives helm parsing.
helm_set_producers() { local svc; for svc in enyman pacman keysmith; do
  ( cd "${SVCS}/k8s" && helm upgrade --install esquire-$svc charts/esquire-$svc --reuse-values \
      --set-string audit.busId="$1" --wait --timeout 180s >/dev/null 2>&1 ); done; }
helm_set_aukeep() { ( cd "${SVCS}/k8s" && helm upgrade --install esquire-aukeep charts/esquire-aukeep --reuse-values \
      --set-string audit.busId="$1" --wait --timeout 180s >/dev/null 2>&1 ); }
restart_k8s_producers() {
  kubectl rollout restart statefulset/esquire-enyman-enyman deployment/esquire-pacman-pacman deployment/esquire-keysmith-keysmith >/dev/null 2>&1
  kubectl rollout status statefulset/esquire-enyman-enyman --timeout=180s >/dev/null 2>&1
  kubectl rollout status deployment/esquire-pacman-pacman  --timeout=180s >/dev/null 2>&1
  kubectl rollout status deployment/esquire-keysmith-keysmith --timeout=180s >/dev/null 2>&1
  sleep 10; }   # producers reconnect to the bus + (c/ck) re-subscribe
restart_k8s_aukeep() {
  kubectl rollout restart deployment/esquire-aukeep-aukeep >/dev/null 2>&1
  kubectl rollout status deployment/esquire-aukeep-aukeep --timeout=180s >/dev/null 2>&1; sleep 5; }

k8s_infra_ensure() {   # install the audit sinks + all-sinks topology the running cluster predates
  ( cd "${SVCS}/k8s"
    helm upgrade --install esquire-topology    charts/esquire-topology >/dev/null 2>&1
    helm upgrade --install esquire-infra-kafka  charts/infra/kafka >/dev/null 2>&1
    helm upgrade --install esquire-infra-redis  charts/infra/redis >/dev/null 2>&1
    kubectl rollout status deployment/esquire-infra-kafka --timeout=180s >/dev/null 2>&1
    kubectl rollout status deployment/esquire-infra-redis --timeout=120s >/dev/null 2>&1 )
  # PROPAGATION GUARD: the topology ConfigMap was just upgraded with the ck/d/dk legs. A producer that
  # restarts onto one of those legs BEFORE the new ConfigMap has reached its mount gets a topology
  # missing the leg, and the producer's strict AuditConfig.resolve() crashes the pod (CrashLoopBackOff).
  # Poll a long-running pod's MOUNTED topology until the last-added leg (audit-dk) appears -- that both
  # confirms propagation and warms this (single) node's kubelet ConfigMap cache, so every subsequent
  # fresh restart also mounts the full topology. Without this the c/ck/d/dk cells race and fail.
  local i n=0
  for i in $(seq 1 36); do
    n=$(kubectl exec deployment/esquire-biztree-biztree -- sh -c "grep -c 'audit-dk' /etc/esquire/topology.yml" 2>/dev/null | tr -d '\r')
    [ "${n:-0}" -gt 0 ] && break
    sleep 5
  done
  echo "    [infra] topology(all-sinks)+kafka+redis ensured (audit-dk in mount: ${n:-0})"; }

cell_k8s() {
  local cell="$1"
  local idx="0,1,2,4" desc="" stream="" streamfn="" trig="" busid="" akbus="" warm=""
  case "$cell" in
    a)         busid="$AUDIT_OFF"; trig="1"; desc="DB triggers in-tx (audit msg off)";;
    b-ded-pg)  busid="audit-b"; desc="in-process keep, dedicated pool (pg)";;
    c)         busid="audit-c";  akbus="audit-c";  desc="bus AMQ -> auKeep -> *_log (pg)";;
    ck)        busid="audit-ck"; akbus="audit-ck"; warm="1"; desc="bus Kafka -> auKeep -> *_log (pg)";;
    d)         busid="audit-d";  stream="redis"; streamfn="k8s_redis_len"; desc="Redis stream (producer-only)";;
    dk)        busid="audit-dk"; stream="kafka"; streamfn="k8s_kafka_off"; desc="Kafka stream (producer-only)";;
    *) echo "unknown k8s cell: $cell"; return 1;;
  esac
  echo ">>> k8s / $cell : $desc"
  local pre post res
  if [ -n "$trig" ]; then
    k8s_dedup_drop                                     # triggers need the dedup index absent
    k8s_trig_apply; echo "    [trig] applied ($(k8s_trig_count) ESQ triggers); dedup index dropped; audit msg OFF"
    helm_set_producers "$busid"; restart_k8s_producers
    pre="$(k8s_pg_counts)"; run_smoke_k8s; sleep 8; post="$(k8s_pg_counts)"
    res="$(grew "$pre" "$post" "$idx")"
    k8s_trig_drop
    echo "    pre=$pre post=$post -> $res (triggers dropped; now $(k8s_trig_count))"
    row "k8s" "$cell ($desc)" "${res%% *}" "$res ; pg pre=$pre post=$post ; triggers applied+dropped"
    return
  fi
  if [ -n "$stream" ]; then
    kubectl scale deployment/esquire-aukeep-aukeep --replicas=0 >/dev/null 2>&1; sleep 4
    helm_set_producers "$busid"; restart_k8s_producers
    local spre; spre="$($streamfn)"; pre="$(k8s_pg_counts)"
    run_smoke_k8s; sleep 6
    local spost; spost="$($streamfn)"; post="$(k8s_pg_counts)"
    local sd=$(( ${spost:-0} - ${spre:-0} )); local lg ld; lg="$(grew "$pre" "$post" "")"; ld="${lg#* }"
    if   [ "$sd" -gt 0 ] && [ "$ld" = "0,0,0,0,0" ]; then res="PASS ${stream}Δ=${sd}; *_log flat"
    elif [ "$sd" -gt 0 ]; then res="PASS ${stream}Δ=${sd}; *_log Δ=${ld}"
    else res="FAIL ${stream}Δ=${sd}"; fi
    kubectl scale deployment/esquire-aukeep-aukeep --replicas=1 >/dev/null 2>&1; restart_k8s_aukeep
  else
    helm_set_producers "$busid"; [ -n "$akbus" ] && helm_set_aukeep "$akbus"
    restart_k8s_producers; [ -n "$akbus" ] && restart_k8s_aukeep
    if [ -n "$warm" ]; then
      # Kafka consumer-group COLD START: auKeep's first join of the esquire-audit group (find-coordinator +
      # JoinGroup/SyncGroup rebalance + initial fetch) outlasts the fixed post-wait below, so a freshly
      # restarted consumer can deliver nothing inside the measured window -> false 0,0,0,0,0. Warm the group
      # with a throwaway smoke, then poll until auKeep actually drains a row (group joined + polling) before
      # measuring. auto.offset.reset=earliest -> the warm-up rows are not lost while the group forms; once it
      # is warm the measured run drains well within sleep 8 (same idiom as the oracle-primary warm-up).
      local w0 wi; w0="$(k8s_pg_counts)"; run_smoke_k8s
      for wi in $(seq 1 20); do sleep 3; [ "$(k8s_pg_counts)" != "$w0" ] && break; done
      echo "    [warm] kafka consumer group warmed (drained after ~$((wi*3))s)"
    fi
    pre="$(k8s_pg_counts)"; run_smoke_k8s; sleep 8; post="$(k8s_pg_counts)"
    res="$(grew "$pre" "$post" "$idx")"
  fi
  echo "    pre=${pre:-_} post=${post:-_} -> $res"
  row "k8s" "$cell ($desc)" "${res%% *}" "$res ; pg pre=${pre:-_} post=${post:-_}"
}

main() {
  local env="${1:-docker-pg}"; shift || true
  echo "# Audit smoke matrix -- ${STAMP}" >"${RESULTS}"
  echo "" >>"${RESULTS}"; echo "| env | cell | result | detail |" >>"${RESULTS}"
  echo "|---|---|---|---|" >>"${RESULTS}"
  case "$env" in
    docker-pg)
      local cells=("$@"); [ ${#cells[@]} -eq 0 ] && cells=(a b-ded-pg c ck d dk)
      for c in "${cells[@]}"; do cell_docker_pg "$c"; done
      # restore default audit-c
      unset AUDIT_BUS_ID; export AUDIT_BUS_ID=audit-c
      recreate_docker ;;
    docker-pg-ora)
      local cells=("$@"); [ ${#cells[@]} -eq 0 ] && cells=(b-ded-ora c-ora ck-ora)
      for c in "${cells[@]}"; do cell_docker_pg_ora "$c"; done
      unset DB_DATAKEEP_VENDOR DB_DATAKEEP_HOST DB_DATAKEEP_PORT DB_DATAKEEP_NAME DB_DATAKEEP_URL
      export AUDIT_BUS_ID=audit-c; recreate_docker ;;
    docker-ora)
      local cells=("$@"); [ ${#cells[@]} -eq 0 ] && cells=(a b-ded-pg b-ded-ora c-pg c-ora ck-pg ck-ora d dk)
      for c in "${cells[@]}"; do cell_docker_ora "$c"; done
      unset_oracle_primary
      unset DB_DATAKEEP_VENDOR DB_DATAKEEP_HOST DB_DATAKEEP_PORT DB_DATAKEEP_NAME DB_DATAKEEP_URL
      export AUDIT_BUS_ID=audit-c; recreate_full ;;
    k8s)
      k8s_infra_ensure
      local cells=("$@"); [ ${#cells[@]} -eq 0 ] && cells=(a b-ded-pg c ck d dk)
      for c in "${cells[@]}"; do cell_k8s "$c"; done
      # restore default: bus (c) producers + auKeep
      helm_set_producers audit-c; helm_set_aukeep audit-c; restart_k8s_producers; restart_k8s_aukeep ;;
    *) echo "env '$env' not wired in this build yet"; exit 2;;
  esac
  echo "=== results -> ${RESULTS} ==="; cat "${RESULTS}"
}
main "$@"
