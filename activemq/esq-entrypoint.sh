#!/bin/sh
#
# Esquire frameworks (tm) -- ActiveMQ entrypoint wrapper.
#
# Three things happen here, all of them because ONE image serves docker, local k8s and OKE: whatever must differ
# per target has to arrive as an environment variable rather than be baked into activemq.xml.
#
#   1. THE DATA DIRECTORY. The broker is NON-PERSISTENT (activemq.xml, persistent="false"), so there is no
#      message store and nothing here needs to survive a restart. The data dir still holds the temp spool
#      (PListStore) and the broker's own log, and it is pointed at the volume that compose and the StatefulSet
#      already mount -- so the mount is genuinely written to rather than sitting there empty pretending to be a
#      store. (It WAS empty: the broker defaults its data dir to the container's ephemeral layer, so the volume
#      and the store never met. See the plan, T9-A defect 4.)
#      The launcher honours ACTIVEMQ_DATA; see bin/activemq.
#
#   2. THE TEMP LIMIT. There is deliberately no store limit: with no message store, a store limit would bound
#      nothing. tempUsage is the spool that memoryUsage (70% of heap) overflows into, and with every message now
#      non-persistent it is the only disk the bus can actually touch -- so it is the one that must be bounded.
#
#   3. THE OBSERVABILITY GATE. The JMX exporter agent is baked into the image but not loaded unless
#      observability is on, so OFF costs nothing: the jar sits on disk unread and the JVM starts exactly as stock.
#
set -e

# 1. Temp spool + broker log land on the mounted volume (compose bind mount / StatefulSet PVC).
ACTIVEMQ_DATA="${ESQ_AMQ_DATA_DIR:-/var/opt/activemq/data}"
export ACTIVEMQ_DATA

# 2. Temp limit, read by activemq.xml as a system property. NOTE: no spaces -- ACTIVEMQ_OPTS is word-split when
#    the launcher builds the java command line, so "512 mb" would arrive as two arguments and break the JVM.
ACTIVEMQ_OPTS="${ACTIVEMQ_OPTS} -Desq.amq.temp.limit=${ESQ_AMQ_TEMP_LIMIT:-512mb}"

# 3. The observability gate.
if [ "${ESQ_OBSERVABILITY_ENABLED}" = "true" ]; then
    ACTIVEMQ_OPTS="${ACTIVEMQ_OPTS} -javaagent:/opt/esq-o11y/jmx_prometheus_javaagent.jar=${ESQ_AMQ_METRICS_PORT:-9404}:/opt/esq-o11y/jmx-exporter.yml"
    echo "[esq] observability ON -- JMX exporter on :${ESQ_AMQ_METRICS_PORT:-9404}"
else
    echo "[esq] observability OFF -- JMX exporter not loaded"
fi
export ACTIVEMQ_OPTS

echo "[esq] non-persistent broker | data dir ${ACTIVEMQ_DATA} (temp spool + log) | temp limit ${ESQ_AMQ_TEMP_LIMIT:-512mb}"

exec /usr/local/bin/entrypoint.sh "$@"
