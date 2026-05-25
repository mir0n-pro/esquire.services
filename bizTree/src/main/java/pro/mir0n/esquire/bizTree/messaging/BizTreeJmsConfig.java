/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: JMS/ActiveMQ config for durable consumer; clientId set explicitly on CachingConnectionFactory
 * 03/26/2026 mir0n  @Qualifier("jmsConnectionFactory") added to ConnectionFactory parameter
 * 05/23/2026 mir0n  non-durable subscriber: bean renamed jmsDurableTopicListenerFactory ->
 *                   jmsTopicListenerFactory; removed the clientId (@Value + setClientId) and
 *                   setSubscriptionDurable(true). The night-watch recovers messages missed while down,
 *                   so durable is unneeded -- and dropping the clientId unblocks k8s rolling updates.
 */
package pro.mir0n.esquire.bizTree.messaging;

import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

/**
 * JMS configuration for the entity broadcast consumer.
 *
 * Consumer: jmsTopicListenerFactory — NON-DURABLE topic subscriber. No clientId, no subscriptionName:
 * a message missed while bizTree is down is recovered by the night-watch (anti-entropy sweep reloads
 * the cache from the source of truth), so the durable subscription is no longer needed. Dropping it
 * also drops the per-subscriber clientId, which deadlocked k8s rolling updates (two pods could not
 * both hold "biztree" on the broker).
 *
 * Broker URL: spring.activemq.broker-url (application.yml).
 * No broker authorization in phase 1.
 */
@Configuration
@EnableJms
public class BizTreeJmsConfig {

    /**
     * Listener container factory for non-durable topic subscribers.
     * Use this factory in @JmsListener(containerFactory="jmsTopicListenerFactory").
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsTopicListenerFactory(
            @Qualifier("jmsConnectionFactory") ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory ret = new DefaultJmsListenerContainerFactory();
        ret.setConnectionFactory(connectionFactory);
        ret.setPubSubDomain(true);
        return ret;
    }
}
