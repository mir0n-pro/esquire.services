/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: JMS/ActiveMQ configuration for entity broadcast producer
 */
package pro.mir0n.esquire.enyMan.messaging;

import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;

/**
 * JMS configuration for the entity broadcast producer and consumer template.
 *
 * Producer: jmsTopicTemplate — topic-mode JmsTemplate for sending to esquire.entity.broadcast.
 * Consumer template: jmsDurableTopicListenerFactory — factory for durable topic subscribers.
 *   Clients must set a stable clientId per subscriber; subscriptionName per @JmsListener.
 *
 * clientId is set on the CachingConnectionFactory via spring.jms.client-id (application.yml).
 * Setting it here on the listener factory causes "setClientID not supported on shared
 * connection proxy" — Spring Boot's CachingConnectionFactory must own the clientId.
 *
 * Broker URL: spring.activemq.broker-url (application.yml).
 * No broker authorization in phase 1.
 */
@Configuration
@EnableJms
public class EnyManJmsConfig {

    /**
     * Topic-mode JmsTemplate for the entity broadcast producer.
     * pubSubDomain=true is required for JMS topic destinations.
     */
    @Bean
    public JmsTemplate jmsTopicTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate ret = new JmsTemplate(connectionFactory);
        ret.setPubSubDomain(true);
        return ret;
    }

    /**
     * Listener container factory for durable topic subscribers.
     * Use this factory in @JmsListener(containerFactory="jmsDurableTopicListenerFactory").
     * Assign a stable subscriptionName per listener.
     *
     * Selector examples (JMS property selectors, FIX-JSON notation):
     *   EntityKind = 34
     *   BusID = 'esquire.entity' AND EntityKind = 34
     *   BusID = 'esquire.entity' AND MsgType = 'UE' AND EntityKind = 34
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsDurableTopicListenerFactory(
            ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory ret = new DefaultJmsListenerContainerFactory();
        ret.setConnectionFactory(connectionFactory);
        ret.setPubSubDomain(true);
        ret.setSubscriptionDurable(true);
        return ret;
    }
}
