/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — JMS configuration for KC sync request/response queues and entity broadcast topic
 * 04/06/2026 mir0n  clientId set directly on CachingConnectionFactory (kcmaster.messaging.client-id);
 *                   setting on listener factory caused "setClientID not supported on shared connection proxy"
 */

package pro.mir0n.esquire.kcMaster.messaging;

import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

/**
 * JMS configuration for kcMaster.
 *
 * Producer: sends URS to esquire.kc.response — queue.
 * Consumer: listens on esquire.kc.request (URQ) — queue.
 * Consumer: listens on esquire.entity.broadcast — durable topic.
 *
 * Broker URL: spring.activemq.broker-url (application.yml).
 * clientId for durable topic subscription: spring.jms.client-id (application.yml).
 */
@Configuration
@EnableJms
public class KcMasterJmsConfig {

    @Value("${kcmaster.messaging.client-id:kcmaster}")
    private String clientId;

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

    @Bean
    public DefaultJmsListenerContainerFactory jmsDurableTopicListenerFactory(@Qualifier("jmsConnectionFactory") ConnectionFactory connectionFactory) {
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
