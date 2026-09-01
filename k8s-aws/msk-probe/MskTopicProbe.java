/*
 *  Esquire frameworks (tm)
 *  k8s-aws -- T3.2 measurement tool (NOT part of any deployment)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.probe;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Looks at the Kafka the pod is wired to, and can create the audit log topic.
 *
 *  <p>It exists because {@code tp-kafka} creates only its {@code .admin} liveness topic and leaves the LOG topic
 *  to the broker's {@code auto.create.topics.enable}. Docker's Kafka has that on; MSK's default has it off. This
 *  says which of the two is true on a given cluster, and closes the gap when it has to.
 *
 *  <p>Run it inside a service pod, which already carries the Kafka client:
 *  <pre>
 *  java -cp /app/app.jar -Dloader.path=/tmp/msk \
 *       -Dloader.main=pro.mir0n.esquire.probe.MskTopicProbe \
 *       org.springframework.boot.loader.launch.PropertiesLauncher [create]
 *  </pre>
 */
public final class MskTopicProbe {

    private static final String TOPIC = "esquire.rod.audit";
    private static final int TIMEOUT_S = 30;

    /** One hour. Long enough that auKeep can never miss a record, short enough that nothing accumulates. */
    private static final String RETENTION_MS = "3600000";

    private MskTopicProbe() {
    }

    public static void main(String[] args) throws Exception {
        String bootstrap = System.getenv("ESQ_MSK_BOOTSTRAP");
        if (bootstrap == null || bootstrap.isBlank()) {
            System.out.println("[FAIL] ESQ_MSK_BOOTSTRAP is not set -- run this inside a pod wired to the cluster.");
        } else {
            Map<String, Object> cfg = new HashMap<>();
            cfg.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
            cfg.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_S * 1000);
            try (Admin admin = Admin.create(cfg)) {
                System.out.println("bootstrap : " + bootstrap);
                System.out.println("cluster   : " + admin.describeCluster().clusterId().get(TIMEOUT_S, TimeUnit.SECONDS)
                        + "  nodes=" + admin.describeCluster().nodes().get(TIMEOUT_S, TimeUnit.SECONDS).size());

                Set<String> topics = admin.listTopics().names().get(TIMEOUT_S, TimeUnit.SECONDS);
                System.out.println("topics    : " + (topics.isEmpty() ? "(none)" : topics));
                System.out.println(TOPIC + " present: " + topics.contains(TOPIC));

                if (args.length > 0 && "create".equals(args[0])) {
                    create(admin);
                }
                describe(admin);
            }
        }
    }

    /** One partition, ONE replica, one hour of retention.
     *
     *  <p>One partition because the audit log is FIFO by design -- a second would let the order it is read in
     *  drift from the order it was written. One replica and a short retention because this leg is NOT durable:
     *  Kafka has no non-persistent mode, every record goes to disk, so the only things that can be turned down
     *  are how many copies are kept and for how long. It is the same call the ActiveMQ legs made with
     *  {@code persistent: false}, spelled in the terms Kafka has. */
    private static void create(Admin admin) {
        try {
            NewTopic topic = new NewTopic(TOPIC, 1, (short) 1).configs(Map.of(
                    "retention.ms", RETENTION_MS,
                    "segment.ms",   RETENTION_MS));   // roll segments, or retention has nothing to delete
            admin.createTopics(List.of(topic)).all().get(TIMEOUT_S, TimeUnit.SECONDS);
            System.out.println("created   : " + TOPIC + " (1 partition, 1 replica, retention.ms=" + RETENTION_MS + ")");
        } catch (Exception ex) {
            System.out.println("create    : refused (" + ex.getMessage() + ")");
        }
    }

    private static void describe(Admin admin) {
        try {
            TopicDescription d = admin.describeTopics(List.of(TOPIC)).allTopicNames()
                    .get(TIMEOUT_S, TimeUnit.SECONDS).get(TOPIC);
            System.out.println("describe  : partitions=" + d.partitions().size()
                    + " replicas=" + d.partitions().get(0).replicas().size());
        } catch (Exception ex) {
            System.out.println("describe  : not there (" + ex.getMessage() + ")");
        }
    }
}
