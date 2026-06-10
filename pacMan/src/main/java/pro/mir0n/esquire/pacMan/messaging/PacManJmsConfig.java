/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: JMS/ActiveMQ configuration for entity broadcast producer
 * 06/06/2026 mir0n  jmsQueueTemplate (pubSubDomain=false) added for the x-Rod audit bus producer (option c)
 */
package pro.mir0n.esquire.pacMan.messaging;

import jakarta.jms.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;

/**
 * JMS configuration for the entity broadcast producer.
 *
 * Producer: jmsTopicTemplate — topic-mode JmsTemplate for sending to esquire.entity.broadcast.
 *
 * Broker URL: spring.activemq.broker-url (application.yml).
 * No broker authorization in phase 1.
 */
@Configuration
@EnableJms
public class PacManJmsConfig {

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
     * Queue-mode JmsTemplate for the x-Rod audit bus producer (option c) -- the RodEventBusPublisher
     * sends to the durable audit QUEUE. pubSubDomain=false is required for JMS queue destinations.
     */
    @Bean
    public JmsTemplate jmsQueueTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate ret = new JmsTemplate(connectionFactory);
        ret.setPubSubDomain(false);
        return ret;
    }
}
