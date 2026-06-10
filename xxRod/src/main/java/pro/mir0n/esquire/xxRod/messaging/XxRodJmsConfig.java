/*
 *  Esquire frameworks (tm)
 *  xxRod service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: JMS configuration for the audit queue consumer. Point-to-point (pubSubDomain
 *                   false) -> competing consumers across replicas, no durable-sub clientId (dodges the
 *                   rolling-update clientId trap). Listener concurrency is configurable.
 * 06/08/2026 mir0n  gated by xxrod.transport=activemq (default); not wired when transport=kafka.
 */
package pro.mir0n.esquire.xxRod.messaging;

import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import pro.mir0n.esquire.common.audit.AuditRod;

@Configuration
@EnableJms
@ConditionalOnProperty(prefix = "xxrod", name = "transport",
        havingValue = AuditRod.TRANSPORT_ACTIVEMQ, matchIfMissing = true)
public class XxRodJmsConfig {

    @Value("${xxrod.messaging.concurrency:1-1}")
    private String concurrency;

    @Bean
    public DefaultJmsListenerContainerFactory jmsQueueListenerFactory(
            @Qualifier("jmsConnectionFactory") ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory ret = new DefaultJmsListenerContainerFactory();
        ret.setConnectionFactory(connectionFactory);
        ret.setPubSubDomain(false);
        ret.setConcurrency(concurrency);
        return ret;
    }
}
