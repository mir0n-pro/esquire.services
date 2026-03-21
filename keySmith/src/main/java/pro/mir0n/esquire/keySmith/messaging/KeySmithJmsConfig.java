/*
 *  Esquire frameworks (tm)
 *  keySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — JMS configuration for KC sync request/response queues
 */

package pro.mir0n.esquire.keySmith.messaging;

import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;

/**
 * JMS configuration for KC sync queues.
 *
 * Producer: sends URQ to esquire.kc.request — queue.
 * Consumer: listens for URS on esquire.kc.response — queue.
 *
 * Broker URL: spring.activemq.broker-url (application.yml).
 */
@Configuration
@EnableJms
public class KeySmithJmsConfig {

    @Bean
    public JmsTemplate jmsQueueTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate ret = new JmsTemplate(connectionFactory);
        ret.setPubSubDomain(false);
        return ret;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsQueueListenerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory ret = new DefaultJmsListenerContainerFactory();
        ret.setConnectionFactory(connectionFactory);
        ret.setPubSubDomain(false);
        return ret;
    }
}
