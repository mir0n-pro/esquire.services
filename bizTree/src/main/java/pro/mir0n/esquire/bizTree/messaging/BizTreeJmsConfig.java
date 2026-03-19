/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: JMS/ActiveMQ config for durable consumer; clientId set explicitly on CachingConnectionFactory
 */
package pro.mir0n.esquire.bizTree.messaging;

import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;

/**
 * JMS configuration for the entity broadcast consumer.
 *
 * Consumer: jmsDurableTopicListenerFactory — factory for durable topic subscribers.
 * Clients must set a stable clientId per subscriber; subscriptionName per @JmsListener.
 *
 * clientId is set explicitly on the CachingConnectionFactory here.
 * spring.jms.client-id is not reliably applied by Spring Boot auto-config when
 * ActiveMQConnectionFactory is already present on the classpath.
 *
 * Broker URL: spring.activemq.broker-url (application.yml).
 * No broker authorization in phase 1.
 */
@Configuration
@EnableJms
public class BizTreeJmsConfig {

    @Value("${biztree.messaging.client-id:biztree}")
    private String clientId;

    /**
     * Listener container factory for durable topic subscribers.
     * Use this factory in @JmsListener(containerFactory="jmsDurableTopicListenerFactory").
     * Assign a stable subscriptionName per listener.
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsDurableTopicListenerFactory(
            ConnectionFactory connectionFactory) {
        if (connectionFactory instanceof CachingConnectionFactory ccf) {
            ccf.setClientId(clientId);
        }
        DefaultJmsListenerContainerFactory ret = new DefaultJmsListenerContainerFactory();
        ret.setConnectionFactory(connectionFactory);
        ret.setPubSubDomain(true);
        ret.setSubscriptionDurable(true);
        return ret;
    }
}
