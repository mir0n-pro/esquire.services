/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/17/2026 mir0n  created: JMS/ActiveMQ configuration for entity broadcast producer
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
}
