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
 */
package pro.mir0n.esquire.tp.activemq;

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

    public TransportProvider() {
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        String brokerUrl = withParams(s.endpoint(), s.params());
        boolean pubSub = Boolean.parseBoolean(s.param(PARAM_PUBSUB_DOMAIN, "false"));
        ActiveMQConnectionFactory amq = new ActiveMQConnectionFactory(brokerUrl);
        if (s.poolSize() > 0) {
            amq.setUseAsyncSend(true);
        }
        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UP);
        amq.setTransportListener(stateListener(conn, "publisher " + destination));
        CachingConnectionFactory ccf = new CachingConnectionFactory(amq);
        if (s.poolSize() > 0) {
            ccf.setSessionCacheSize(s.poolSize());
        }
        JmsTemplate jms = new JmsTemplate(ccf);
        jms.setPubSubDomain(pubSub);
        devLog.info("tp-activemq: publisher opened on {} (broker={}, {}, poolSize={})",
                destination, brokerUrl, pubSub ? "topic" : "queue", s.poolSize());

        // close() releases the caching connection factory (the cached connection + sessions); the underlying
        // ActiveMQConnectionFactory holds no connection of its own.
        return TransportPublisher.of(msg -> {
            try {
                Map<String, Object> props = new LinkedHashMap<>(msg.headers());
                String applMsgId = UUID.randomUUID().toString();
                props.put(BusConstants.FIELD_APPL_MSG_ID,  applMsgId);
                props.put(BusConstants.FIELD_SENDING_TIME, Instant.now().toString());
                jms.send(destination, session -> {
                    Message m = session.createMessage();
                    Utils.setProps(m, props);
                    return m;
                });
                conn.set(TransportHealth.UP);           // a successful send -> the connection is up
            } catch (Exception ex) {
                conn.set(TransportHealth.DOWN);          // a failed send -> the connection is down
                devLog.error("tp-activemq: publish failed on {}: {}", destination, ex.getMessage(), ex);
            }
        }, ccf::destroy, conn::get);
    }

    @Override
    public TransportConsumer openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        String brokerUrl = withParams(s.endpoint(), s.params());
        boolean pubSub = Boolean.parseBoolean(s.param(PARAM_PUBSUB_DOMAIN, "false"));
        ActiveMQConnectionFactory amq = new ActiveMQConnectionFactory(brokerUrl);
        AtomicReference<TransportHealth> conn = new AtomicReference<>(TransportHealth.UP);
        amq.setTransportListener(stateListener(conn, "consumer " + destination));
        DefaultMessageListenerContainer c = new DefaultMessageListenerContainer();
        c.setConnectionFactory(amq);
        c.setDestinationName(destination);
        c.setPubSubDomain(pubSub);
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
        devLog.info("tp-activemq: consumer created (paused) on {} (broker={}, {}, concurrency={})",
                destination, brokerUrl, pubSub ? "topic" : "queue", s.concurrency());
        return TransportConsumer.of(c::start, () -> {
            c.stop();
            c.destroy();
        }, conn::get);
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
                if (PARAM_PUBSUB_DOMAIN.equals(e.getKey())) {
                    continue;   // applied via setPubSubDomain(...), not a broker-URI option
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
}
