/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — entity broadcast topic consumer (skeleton)
 */

package pro.mir0n.esquire.kcMaster.messaging;

import jakarta.jms.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Durable subscriber on esquire.entity.broadcast.
 * Listens for entity update events that may require Keycloak synchronization.
 *
 * Subscription name is stable — never change it once deployed.
 */
@Slf4j
@Component
public class KcEntityBroadcastConsumer {

    private static final String TOPIC         = "esquire.entity.broadcast";
    private static final String SUBSCRIPTION  = "esquire.entity.broadcast.kcmaster.primary";

    @JmsListener(
            destination      = TOPIC,
            containerFactory = "jmsDurableTopicListenerFactory",
            subscription     = SUBSCRIPTION
    )
    public void onMessage(Message message) {
        try {
            String msgType   = message.getStringProperty("MsgType");
            String entityId  = message.getStringProperty("EntityID");
            int    entityKind = message.getIntProperty("EntityKind");
            String text      = message.getStringProperty("Text");

            log.debug("kcMaster: entity broadcast received: msgType={} entityId={} entityKind={} text={}", msgType, entityId, entityKind, text);

            // todo: determine what KC action (if any) this event requires
        } catch (Exception e) {
            log.error("kcMaster: entity broadcast processing failed: {}", e.getMessage(), e);
        }
    }
}
