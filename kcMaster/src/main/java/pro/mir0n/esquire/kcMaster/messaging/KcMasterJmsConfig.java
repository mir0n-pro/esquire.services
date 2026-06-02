/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — JMS configuration for KC sync request/response queues and entity broadcast topic
 * 04/06/2026 mir0n  clientId set directly on CachingConnectionFactory (kcmaster.messaging.client-id);
 *                   setting on listener factory caused "setClientID not supported on shared connection proxy"
 * 06/02/2026 mir0n  race-8c (v1.2.6 Goal 3): entity-broadcast topic subscription DURABLE -> NON-DURABLE
 *                   (the safety-net buffer is in-memory / ephemeral); clientId + CachingConnectionFactory
 *                   wiring removed; jmsDurableTopicListenerFactory renamed jmsTopicListenerFactory
 */

package pro.mir0n.esquire.kcMaster.messaging;

import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;

/**
 * JMS configuration for kcMaster.
 *
 * Producer: sends URS to esquire.kc.response — queue.
 * Consumer: listens on esquire.kc.request (URQ) — queue.
 * Consumer: listens on esquire.entity.broadcast — non-durable topic
 *           (race-8c safety-net buffer; ephemeral by design).
 *
 * Broker URL: spring.activemq.broker-url (application.yml).
 */
@Configuration
@EnableJms
public class KcMasterJmsConfig {

    @Bean
    public JmsTemplate jmsQueueTemplate(@Qualifier("jmsConnectionFactory") ConnectionFactory connectionFactory) {
        JmsTemplate ret = new JmsTemplate(connectionFactory);
        ret.setPubSubDomain(false);
        return ret;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsQueueListenerFactory(@Qualifier("jmsConnectionFactory") ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory ret = new DefaultJmsListenerContainerFactory();
        ret.setConnectionFactory(connectionFactory);
        ret.setPubSubDomain(false);
        return ret;
    }

    /**
     * Non-durable topic subscription for the race-8c safety-net consumer
     * ({@link KcEntityBroadcastConsumer}). The race-8c buffer is in-memory and
     * ephemeral -- durable persistence would only park already-stale paths past
     * kcMaster restarts. Mirrors bizTree's v1.2.5 non-durable subscription shape,
     * which also avoids the clientId-on-CachingConnectionFactory headache that
     * blocks k8s RollingUpdate.
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsTopicListenerFactory(@Qualifier("jmsConnectionFactory") ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory ret = new DefaultJmsListenerContainerFactory();
        ret.setConnectionFactory(connectionFactory);
        ret.setPubSubDomain(true);
        ret.setSubscriptionDurable(false);
        return ret;
    }
}
