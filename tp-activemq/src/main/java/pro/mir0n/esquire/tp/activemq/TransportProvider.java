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
 */
package pro.mir0n.esquire.tp.activemq;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.transport.ConsumeSettings;
import pro.mir0n.esquire.messaging.transport.ITransportProvider;
import pro.mir0n.esquire.messaging.transport.PublishSettings;
import pro.mir0n.esquire.messaging.transport.TransportMessage;
import pro.mir0n.esquire.messaging.transport.TransportPublisher;
import pro.mir0n.esquire.messaging.jms.Utils;

import java.time.Instant;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** ActiveMQ implementation of the transport-provider SPI. Owns its own audit broker connection. */
public final class TransportProvider implements ITransportProvider {

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.tp.activemq.TransportProvider");

    public TransportProvider() {
    }

    @Override
    public TransportPublisher openPublisher(String destination, PublishSettings s) {
        String brokerUrl = withParams(s.endpoint(), s.params());
        ActiveMQConnectionFactory amq = new ActiveMQConnectionFactory(brokerUrl);
        if (s.poolSize() > 0) {
            amq.setUseAsyncSend(true);
        }
        CachingConnectionFactory ccf = new CachingConnectionFactory(amq);
        if (s.poolSize() > 0) {
            ccf.setSessionCacheSize(s.poolSize());
        }
        JmsTemplate jms = new JmsTemplate(ccf);
        jms.setPubSubDomain(s.topic());
        devLog.info("tp-activemq: publisher opened on {} (broker={}, {}, poolSize={})",
                destination, brokerUrl, s.topic() ? "topic" : "queue", s.poolSize());

        // close() releases the caching connection factory (the cached connection + sessions); the underlying
        // ActiveMQConnectionFactory holds no connection of its own.
        return TransportPublisher.of(msg -> {
            try {
                Map<String, Object> props = new LinkedHashMap<>(msg.headers());
                String applMsgId = UUID.randomUUID().toString();
                props.put(EsqMsgConstants.FIELD_APPL_MSG_ID,  applMsgId);
                props.put(EsqMsgConstants.FIELD_SENDING_TIME, Instant.now().toString());
                jms.send(destination, session -> {
                    Message m = session.createMessage();
                    Utils.setProps(m, props);
                    return m;
                });
            } catch (Exception ex) {
                devLog.error("tp-activemq: publish failed on {}: {}", destination, ex.getMessage(), ex);
            }
        }, ccf::destroy);
    }

    @Override
    public AutoCloseable openConsumer(String destination, ConsumeSettings s, Consumer<TransportMessage> handler) {
        String brokerUrl = withParams(s.endpoint(), s.params());
        ActiveMQConnectionFactory amq = new ActiveMQConnectionFactory(brokerUrl);
        DefaultMessageListenerContainer c = new DefaultMessageListenerContainer();
        c.setConnectionFactory(amq);
        c.setDestinationName(destination);
        c.setPubSubDomain(s.topic());
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
        c.afterPropertiesSet();
        c.start();
        devLog.info("tp-activemq: consumer started on {} (broker={}, {}, concurrency={})",
                destination, brokerUrl, s.topic() ? "topic" : "queue", s.concurrency());
        return () -> {
            c.stop();
            c.destroy();
        };
    }

    /** Append the leg's vendor params verbatim to the broker URI -- ActiveMQ parses its own URI options
     *  ({@code jms.*} on the factory, {@code transport.*} on the wire, {@code nested.*} on the broker). So ANY
     *  param a leg sets under {@code transport.params} flows to the vendor with no per-key code here. */
    private static String withParams(String brokerUrl, Map<String, String> params) {
        String ret = brokerUrl;
        if (brokerUrl != null && params != null && !params.isEmpty()) {
            StringBuilder sb = new StringBuilder(brokerUrl).append(brokerUrl.indexOf('?') < 0 ? '?' : '&');
            boolean first = true;
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (!first) {
                    sb.append('&');
                }
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            ret = sb.toString();
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
