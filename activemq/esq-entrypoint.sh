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
#   4. THE DECLARED DESTINATIONS. activemq.xml declares the destinations up front so their depth and
#      consumer-count meters exist at broker start rather than springing into existence on the first message.
#      WHICH destinations exist is a property of the COMPOSITION, not of the broker: classic drains the identity
#      request/reply pair and the audit queue, compact runs kcMaster inside Mesnie so the identity pair is never
#      consumed, and super-compact audits in the database so the audit queue is never consumed either. A declared
#      destination with no consumer reads as TROUBLE on the board -- correctly, on a composition that uses it. So
#      the list arrives per target, and the baked-in four stay the default.
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

# 4. The declared destinations, when the target names them. Unset leaves activemq.xml exactly as baked -- the
#    classic set -- so docker and the classic stacks are untouched. Format: space-separated
#    "<topic|queue>:<physicalName>".
if [ -n "${ESQ_AMQ_DESTINATIONS}" ]; then
    esq_conf="${ACTIVEMQ_CONF:-/opt/apache-activemq/conf}/activemq.xml"
    esq_block=/tmp/esq-destinations.xml
    : > "${esq_block}"
    for esq_d in ${ESQ_AMQ_DESTINATIONS}; do
        esq_kind="${esq_d%%:*}"
        esq_name="${esq_d#*:}"
        if [ "${esq_kind}" != "topic" ] && [ "${esq_kind}" != "queue" ]; then
            echo "[esq] ESQ_AMQ_DESTINATIONS: ${esq_d} is not topic:<name> or queue:<name> -- refusing" >&2
            exit 1
        fi
        printf '            <%s physicalName="%s"/>
' "${esq_kind}" "${esq_name}" >> "${esq_block}"
    done
    awk -v blk="${esq_block}" '
        /<destinations>/   { print; while ((getline line < blk) > 0) print line; close(blk); drop = 1; next }
        /<\/destinations>/ { drop = 0 }
        !drop              { print }
    ' "${esq_conf}" > "${esq_conf}.esq" && mv "${esq_conf}.esq" "${esq_conf}"
    echo "[esq] declared destinations: ${ESQ_AMQ_DESTINATIONS}"
else
    echo "[esq] declared destinations: the activemq.xml default (classic set)"
fi

echo "[esq] non-persistent broker | data dir ${ACTIVEMQ_DATA} (temp spool + log) | temp limit ${ESQ_AMQ_TEMP_LIMIT:-512mb}"

exec /usr/local/bin/entrypoint.sh "$@"
