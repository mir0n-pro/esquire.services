/*
 *  Esquire frameworks (tm)
 *  tp-activemq -- transport provider (ActiveMQ)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/12/2026 mir0n  created (as ActivemqTransportProvider): the ActiveMQ ITransportProvider -- openPublisher
 *                   builds its OWN JmsTemplate (poolSize>0 -> a dedicated useAsyncSend CachingConnectionFactory),
 *                   openConsumer runs a programmatic DefaultMessageListenerContainer lifting every JMS property
 *                   into the neutral TransportMessage. Generalized from RodEventBusPublisher + xxRod consumer.
 * 06/13/2026 mir0n  class-name-driven SPI: renamed to the conventional pro.mir0n.esquire.tp.activemq.
 *                   TransportProvider (resolved by name, reflectively instantiated -- no Spring bean, no
 *                   transport() id). Broker endpoint + client-id come from the settings.
 * 06/17/2026 mir0n  openPublisher returns a TransportPublisher (close() destroys the CachingConnectionFactory);
 *                   the ccf.setClientID(...) block removed (a client id rides transport.params.jms.clientID)
 * 06/21/2026 mir0n  the JMS pub/sub flag is read from transport.params.pubSubDomain (setPubSubDomain on the
 *                   template / listener container) instead of settings.topic(); pubSubDomain is excluded from
 *                   the broker-URI append in withParams (a setter call, not a URI option)
 * 06/22/2026 mir0n  two-phase consumer: openConsumer returns a TransportConsumer (start + close legs); the
 *                   container is created PAUSED (setAutoStartup(false), afterPropertiesSet subscribes but does
 *                   not start) -- delivery waits for the bus start() that calls the returned start leg
 * 06/22/2026 mir0n  connection health: a TransportListener on the ActiveMQConnectionFactory flips an
 *                   AtomicReference -> DOWN on transportInterupted / onException, -> UP on transportResumed,
 *                   feeding both the publisher and consumer handle health; a send outcome also refreshes it
 *                   (good send -> UP, failed send -> DOWN).
 * 06/23/2026 mir0n  EsqMsgConstants wire constants -> messaging.BusConstants (references repointed)
 * 06/27/2026 mir0n  openConsumerOn() added -- a consumer that SHARES the AmqPublisher's connection and sets
 *                   pubSubNoLocal (the broker drops the shared connection's own publications, the real JMS
 *                   noLocal); openPublisher returns an AmqPublisher carrying the ccf for that reuse; consumer()
 *                   factored out with a noLocal flag (a separate-connection openConsumer passes false)
 * 06/30/2026 mir0n  AmqPublisher implements the send-retry seam: encode() prepares the broker-free property bag
 *                   (a stable ApplMsgID minted ONCE, absent-only), dispatch() materializes the JMS message + sends
 *                   it THROWING on a transport failure (+ SendingTime per physical send), accept() is the
 *                   best-effort (retry-off) encode+dispatch swallowing path; the swallowing sink Consumer removed
 * 07/12/2026 mir0n  v1.2.11 -- PARAM_PERSISTENT ("persistent") added: the JMS delivery mode read from
 *                   transport.params.persistent (absent = false = NON_PERSISTENT), applied on the JmsTemplate via
 *                   setExplicitQosEnabled(true) + setDeliveryPersistent(...) -- without explicit QoS the delivery
 *                   mode is silently ignored. Excluded from withParams (a setter, not a broker-URI option); the
 *                   publisher-opened devLog line now carries persistent=
 * 08/26/2026 mir0n  the connection health seeds UNKNOWN, not UP -- nothing has proved the connection at open
 * 09/01/2026 mir0n  v1.2.14 -- PARAM_USERNAME ("userName") and PARAM_PASSWORD ("password") added: the broker
 *                   credentials read from transport.params, applied with setUserName/setPassword and BOTH
 *                   excluded from withParams -- the broker URI is written to the develop log on every open,
 *                   so a credential carried as a URI option would be written there too
 */
package pro.mir0n.esquire.tp.activemq;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.transport.TransportListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportConsumer;
import pro.mir0n.esquire.messaging.transport.TransportHealth;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;
import pro.mir0n.esquire.messaging.jms.Utils;

import java.io.IOException;
import java.time.Instant;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** ActiveMQ implementation of the transport-provider SPI. Owns its own audit broker connection. */
public final class TransportProvider implements ITransportProvider {

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.activemq.TransportProvider");

    /** The JMS pub/sub-vs-queue flag, carried as a vendor param ({@code transport.params.pubSubDomain}). Unlike the
     *  other params it is NOT a broker-URI option -- it is a {@code setPubSubDomain(...)} call on the template /
     *  listener container -- so it is read here and excluded from {@link #withParams}. Absent = false = queue. */
    private static final String PARAM_PUBSUB_DOMAIN = "pubSubDomain";

    /** JMS delivery mode, carried as a vendor param ({@code transport.params.persistent}). Absent = false =
     *  NON_PERSISTENT. Like {@link #PARAM_PUBSUB_DOMAIN} it is applied via a setter, not a broker-URI option, so
     *  it is excluded from {@link #withParams}.
     *
     *  <p>It matters far more than "does the broker write to disk". JMS requires a SYNCHRONOUS send for a
     *  message marked PERSISTENT -- the producer blocks for a broker ack on every message -- whether or not the
     *  broker persists anything. So against a non-persistent broker the PERSISTENT flag buys nothing at all and
     *  still costs a round-trip per send. Measured on the local broker, 2000 sends: 233 ms persistent vs 27 ms
     *  non-persistent.
     *
     *  <p>This MUST agree with the broker's own {@code persistent} setting. That is exactly why it is a declared
     *  bus param rather than a constant here: the coupling is visible in the topology, where whoever changes the
     *  broker will see it, instead of hidden in a file they will never open. */
    private static final String PARAM_PERSISTENT = "persistent";

    /** The broker user, carried as a vendor param ({@code transport.params.userName}). Absent = an anonymous
     *  connection, which is what a broker of our own is set up for; a managed broker (Amazon MQ) always demands
     *  one. Applied with a setter and excluded from {@link #withParams} -- and here the exclusion is the whole
     *  point, not a detail: the broker URI is written to the develop log on every open, so a credential appended
     *  to it would be written there too. */
    private static final String PARAM_USERNAME = "userName";

    /** The broker password, beside {@link #PARAM_USERNAME} and read only when that one is set. */
    private static final String PARAM_PASSWORD = "password";

    public TransportProvider() {
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        String brokerUrl = withParams(s.endpoint(), s.params());
        boolean pubSub = Boolean.parseBoolean(s.param(PARAM_PUBSUB_DOMAIN, "false"));
        ActiveMQConnectionFactory amq = new ActiveMQConnectionFactory(brokerUrl);
        credentials(amq, s.param(PARAM_USERNAME, null), s.param(PARAM_PASSWORD, null));
        if (s.poolSize() > 0) {
            amq.setUseAsyncSend(true);
        }

        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UNKNOWN);
        amq.setTransportListener(stateListener(conn, "publisher " + destination));
        CachingConnectionFactory ccf = new CachingConnectionFactory(amq);
        if (s.poolSize() > 0) {
            ccf.setSessionCacheSize(s.poolSize());
        }
        boolean persistent = Boolean.parseBoolean(s.param(PARAM_PERSISTENT, "false"));
        JmsTemplate jms = new JmsTemplate(ccf);
        jms.setPubSubDomain(pubSub);
        // JmsTemplate ignores the delivery mode unless explicit QoS is switched on -- without this line
        // setDeliveryPersistent is silently a no-op and every send stays PERSISTENT.
        jms.setExplicitQosEnabled(true);
        jms.setDeliveryPersistent(persistent);
        devLog.info("tp-activemq: publisher opened on {} (broker={}, {}, poolSize={}, persistent={})",
                destination, brokerUrl, pubSub ? "topic" : "queue", s.poolSize(), persistent);

        // close() (on the returned handle) releases the caching connection factory (the cached connection +
        // sessions); the underlying ActiveMQConnectionFactory holds no connection of its own. The handle carries
        // the ccf so a dual-leg rod's consumer can REUSE this connection (openConsumerOn).
        return new AmqPublisher(ccf, jms, destination, conn);
    }

    @Override
    public TransportConsumer openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        String brokerUrl = withParams(s.endpoint(), s.params());
        ActiveMQConnectionFactory amq = new ActiveMQConnectionFactory(brokerUrl);
        credentials(amq, s.param(PARAM_USERNAME, null), s.param(PARAM_PASSWORD, null));

        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UNKNOWN);
        amq.setTransportListener(stateListener(conn, "consumer " + destination));
        // a SEPARATE-connection consumer: the broker's noLocal cannot see another connection's publications, so it
        // is NOT applied here -- the x-rod does own-exclusion in code for the two-connection case.
        return consumer(destination, s, handler, amq, conn, false, brokerUrl);
    }

    @Override
    public TransportConsumer openConsumerOn(TransportPublisher publisher, String destination,
                                            ConsumeSettings s, Consumer<TransportMessage> handler) {
        TransportConsumer ret;
        if (publisher instanceof AmqPublisher ap) {
            // SHARE the publisher's connection (one connection, two legs); honor noLocal so the BROKER drops this
            // connection's OWN publications -- the real JMS noLocal, which works only because both legs share it.
            boolean noLocal = Boolean.parseBoolean(s.param(BusConstants.PARAM_NO_LOCAL, "false"));
            ret = consumer(destination, s, handler, ap.ccf, ap.conn, noLocal, withParams(s.endpoint(), s.params()));
        } else {
            ret = openConsumer(destination, s, handler);   // unknown publisher handle -> separate-connection fallback
        }
        return ret;
    }

    /** Build the PAUSED listener container on {@code cf} -- a fresh factory for a separate leg, or the publisher's
     *  shared {@link CachingConnectionFactory} for the dual leg. {@code noLocal} sets {@code pubSubNoLocal} so the
     *  broker drops THIS connection's own publications (meaningful only on the shared connection). close() stops the
     *  listener; the shared factory's connection is owned + closed by the publisher, not here. */
    private TransportConsumer consumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler,
                                       ConnectionFactory cf, AtomicReference<TransportHealth> conn, boolean noLocal,
                                       String brokerUrl) {
        boolean pubSub = Boolean.parseBoolean(s.param(PARAM_PUBSUB_DOMAIN, "false"));
        DefaultMessageListenerContainer c = new DefaultMessageListenerContainer();
        c.setConnectionFactory(cf);
        c.setDestinationName(destination);
        c.setPubSubDomain(pubSub);
        if (noLocal) {
            c.setPubSubNoLocal(true);   // the broker drops the shared connection's own publications (real JMS noLocal)
        }
        c.setAutoStartup(false);   // created PAUSED -- the x-rod's start() begins delivery once the bus is wired
        if (s.selector() != null && !s.selector().isBlank()) {
            c.setMessageSelector(s.selector());
        }
        if (s.concurrency() > 0) {
            c.setConcurrentConsumers(s.concurrency());
        }
        c.setMessageListener((MessageListener) message -> {
            try {
                handler.accept(new TransportMessage(readProps(message), null));
            } catch (Exception ex) {
                devLog.error("tp-activemq: consume failed on {}: {}", destination, ex.getMessage(), ex);
            }
        });
        c.afterPropertiesSet();   // subscribe, but do NOT start (autoStartup=false) -- delivery waits for start()
        devLog.info("tp-activemq: consumer created (paused) on {} (broker={}, {}, concurrency={}, noLocal={})",
                destination, brokerUrl, pubSub ? "topic" : "queue", s.concurrency(), noLocal);
        return TransportConsumer.of(c::start, () -> {
            c.stop();
            c.destroy();
        }, conn::get);
    }

    /** Put the broker user on the factory when the leg declares one. The two are read together: a password with
     *  no user names nobody, so the pair is applied only when the user is there. */
    private static void credentials(ActiveMQConnectionFactory amq, String user, String password) {
        if (user != null && !user.isBlank()) {
            amq.setUserName(user);
            amq.setPassword(password);
        }
    }

    /** A connection-state listener that flips {@code conn} to DOWN on a transport interrupt / exception and back
     *  to UP on a resume -- the source for a leg's {@link TransportHealth}. (ActiveMQ's API spells the interrupt
     *  callback {@code transportInterupted}, with one 'r'.) Set on the {@code ActiveMQConnectionFactory}, it
     *  propagates to the connection the factory creates. */
    private static TransportListener stateListener(AtomicReference<TransportHealth> conn, String tag) {
        return new TransportListener() {
            @Override
            public void onCommand(Object command) {
            }

            @Override
            public void onException(IOException error) {
                conn.set(TransportHealth.DOWN);
                devLog.warn("tp-activemq: {} transport exception -> DOWN: {}", tag, error.getMessage());
            }

            @Override
            public void transportInterupted() {
                conn.set(TransportHealth.DOWN);
                devLog.warn("tp-activemq: {} transport interrupted -> DOWN", tag);
            }

            @Override
            public void transportResumed() {
                conn.set(TransportHealth.UP);
                devLog.info("tp-activemq: {} transport resumed -> UP", tag);
            }
        };
    }

    /** Append the leg's vendor params verbatim to the broker URI -- ActiveMQ parses its own URI options
     *  ({@code jms.*} on the factory, {@code transport.*} on the wire, {@code nested.*} on the broker). So ANY
     *  param a leg sets under {@code transport.params} flows to the vendor with no per-key code here -- EXCEPT
     *  {@code pubSubDomain}, which is a {@code setPubSubDomain(...)} call (read separately) and not a URI option. */
    private static String withParams(String brokerUrl, Map<String, String> params) {
        String ret = brokerUrl;
        if (brokerUrl != null && params != null && !params.isEmpty()) {
            StringBuilder q = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (PARAM_PUBSUB_DOMAIN.equals(e.getKey()) || BusConstants.PARAM_NO_LOCAL.equals(e.getKey())
                        || PARAM_PERSISTENT.equals(e.getKey()) || PARAM_USERNAME.equals(e.getKey())
                        || PARAM_PASSWORD.equals(e.getKey())) {
                    continue;   // applied via a setter (setPubSubDomain / setPubSubNoLocal /
                                // setDeliveryPersistent / setUserName / setPassword), not a broker-URI option
                }
                q.append(q.length() == 0 ? "" : "&").append(e.getKey()).append('=').append(e.getValue());
            }
            if (q.length() > 0) {
                ret = brokerUrl + (brokerUrl.indexOf('?') < 0 ? '?' : '&') + q;
            }
        }
        return ret;
    }

    /** Lift every JMS property into a neutral header map (the audit adapter decodes it via RodEventCodec). */
    private static Map<String, Object> readProps(Message message) throws Exception {
        Map<String, Object> headers = new LinkedHashMap<>();
        Enumeration<?> names = message.getPropertyNames();
        while (names.hasMoreElements()) {
            String n = (String) names.nextElement();
            headers.put(n, message.getObjectProperty(n));
        }
        return headers;
    }

    /** The ActiveMQ publisher handle: it carries the {@link CachingConnectionFactory} (ONE shared connection) so a
     *  dual-leg rod's consumer can REUSE the same connection via {@link #openConsumerOn} -- which is what lets the
     *  broker's real JMS {@code noLocal} drop this connection's own publications. {@code close()} destroys the
     *  factory (and the shared connection).
     *
     *  <p>The send-retry seam: {@link #encode} prepares the BROKER-FREE unit -- the stamped property bag (a stable
     *  ApplMsgID minted ONCE) -- since a {@code jakarta.jms.Message} cannot be built without a live session, which
     *  is exactly what a DOWN broker lacks. {@link #dispatch} materializes the JMS message from that bag and sends
     *  it, THROWING on a transport failure (the retry signal) and flipping the health indicator. A held event's
     *  resend relays the SAME bag (no re-encode); the per-physical-send {@code SendingTime} is stamped in dispatch.
     *  {@link #accept} is the best-effort (retry-off) path: encode + dispatch, swallowing. */
    private static final class AmqPublisher implements TransportPublisher {
        private final CachingConnectionFactory ccf;
        private final JmsTemplate jms;
        private final String destination;
        private final AtomicReference<TransportHealth> conn;

        AmqPublisher(CachingConnectionFactory ccf, JmsTemplate jms, String destination,
                     AtomicReference<TransportHealth> conn) {
            this.ccf         = ccf;
            this.jms         = jms;
            this.destination = destination;
            this.conn        = conn;
        }

        @Override
        public Object encode(TransportMessage message) {
            // the broker-free prepared unit: copy the neutral headers, keep a STABLE ApplMsgID (a held event's
            // resend reuses it = dedup-able), mint one only when absent. SendingTime is per physical send -> dispatch.
            Map<String, Object> props = new LinkedHashMap<>(message.headers());
            props.computeIfAbsent(BusConstants.FIELD_APPL_MSG_ID, k -> UUID.randomUUID().toString());
            return props;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void dispatch(Object encoded) throws Exception {
            // materialize + send the JMS message from the prepared bag; THROW on a transport failure (the retry
            // signal). createMessage needs a live session, so a DOWN broker throws HERE (not in encode).
            Map<String, Object> props = (Map<String, Object>) encoded;
            try {
                jms.send(destination, session -> {
                    Message m = session.createMessage();
                    Utils.setProps(m, props);
                    m.setStringProperty(BusConstants.FIELD_SENDING_TIME, Instant.now().toString());
                    return m;
                });
                conn.set(TransportHealth.UP);           // a successful send -> the connection is up
            } catch (Exception ex) {
                conn.set(TransportHealth.DOWN);          // a failed send -> the connection is down
                throw ex;
            }
        }

        @Override
        public void accept(TransportMessage message) {
            // best-effort (retry off): encode + send, swallowing a failure (the health indicator still flips).
            try {
                dispatch(encode(message));
            } catch (Exception ex) {
                devLog.error("tp-activemq: publish failed on {}: {}", destination, ex.getMessage(), ex);
            }
        }

        @Override
        public TransportHealth health() {
            return conn.get();
        }

        @Override
        public void close() {
            ccf.destroy();
        }
    }
}
