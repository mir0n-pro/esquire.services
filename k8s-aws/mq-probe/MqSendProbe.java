/*
 *  Esquire frameworks (tm)
 *  k8s-aws -- T3.1 measurement tool (NOT part of any deployment)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.probe;

import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;

/** Times PERSISTENT against NON_PERSISTENT sends on the broker the pod is wired to.
 *
 *  <p>It exists because the load test cannot answer this. Changing the flag needs a pod roll, a rolled pod is
 *  cold, and the warm-up transient measured on EKS is 60-140 ms of mean response time -- roughly ten times the
 *  effect being looked for. This sends nothing but JMS, from inside the VPC, with both modes in ONE process on
 *  ONE connection, so the only difference left between the two numbers is the delivery mode.
 *
 *  <p>Same shape as the measurement quoted in tp-activemq's PARAM_PERSISTENT javadoc for our own broker, so the
 *  two numbers can be read side by side.
 *
 *  <p>Run it inside a service pod, which already has the client and the credentials:
 *  <pre>
 *  java -cp /app/app.jar -Dloader.path=/tmp/probe \
 *       -Dloader.main=pro.mir0n.esquire.probe.MqSendProbe \
 *       org.springframework.boot.loader.launch.PropertiesLauncher
 *  </pre>
 */
public final class MqSendProbe {

    private static final String QUEUE = "esquire.probe.persistence";
    private static final int WARMUP = 500;
    private static final int SENDS = 2000;

    private MqSendProbe() {
    }

    public static void main(String[] args) throws Exception {
        String url = System.getenv("ESQ_MQ_URL");
        String user = System.getenv("ESQ_MQ_USER");
        String password = System.getenv("ESQ_MQ_PASSWORD");
        if (url == null || url.isBlank()) {
            System.out.println("[FAIL] ESQ_MQ_URL is not set -- run this inside a service pod wired to the broker.");
        } else {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
            factory.setUserName(user);
            factory.setPassword(password);
            Connection connection = factory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(QUEUE);
            MessageProducer producer = session.createProducer(queue);

            System.out.println("broker : " + url);
            System.out.println("queue  : " + QUEUE);

            // The first sends pay for JIT, the TLS handshake and the broker's first look at the queue. They are
            // timed and thrown away so neither measured batch carries that cost.
            run(session, producer, DeliveryMode.NON_PERSISTENT, WARMUP, "warm-up (discarded)");
            run(session, producer, DeliveryMode.PERSISTENT, WARMUP, "warm-up (discarded)");

            long nonPersistent = run(session, producer, DeliveryMode.NON_PERSISTENT, SENDS, "NON_PERSISTENT");
            long persistent = run(session, producer, DeliveryMode.PERSISTENT, SENDS, "PERSISTENT   ");

            double each = (persistent - nonPersistent) / (double) SENDS;
            System.out.printf("%nPERSISTENT costs %.3f ms more per send, over %d sends each.%n", each, SENDS);

            drain(session, queue);
            producer.close();
            session.close();
            ((ActiveMQConnection) connection).destroyDestination(new ActiveMQQueue(QUEUE));
            connection.close();
        }
    }

    /** Send {@code count} messages in {@code mode} and return the milliseconds it took. */
    private static long run(Session session, MessageProducer producer, int mode, int count, String label)
            throws Exception {
        producer.setDeliveryMode(mode);
        Message message = session.createTextMessage("esquire mq send probe");
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            producer.send(message);
        }
        long ms = (System.nanoTime() - start) / 1_000_000L;
        System.out.printf("%-20s %5d sends : %6d ms  (%.3f ms each)%n", label, count, ms, ms / (double) count);
        return ms;
    }

    /** Take everything this probe put on the queue back off it, so nothing is left behind. */
    private static void drain(Session session, Queue queue) throws Exception {
        MessageConsumer consumer = session.createConsumer(queue);
        int drained = 0;
        Message m = consumer.receive(2000);
        while (m != null) {
            drained++;
            m = consumer.receive(500);
        }
        consumer.close();
        System.out.println("drained " + drained + " messages off " + QUEUE);
    }
}
