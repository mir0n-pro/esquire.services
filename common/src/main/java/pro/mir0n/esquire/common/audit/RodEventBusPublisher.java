/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the x-Rod option (c) producer dispatcher -- a Consumer<RodEvent> that the
 *                   xy-Rod feed worker calls instead of the in-process xx-Rod. It serializes the event via
 *                   RodEventCodec and publishes it to the durable audit QUEUE. Best-effort: a broker failure
 *                   is logged and the event dropped (same loss profile as the in-process path), never thrown
 *                   back into the single feed worker.
 */
package pro.mir0n.esquire.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.common.xrod.RodEvent;
import pro.mir0n.esquire.messaging.jms.Utils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Publishes each committed {@link RodEvent} to the dedicated audit queue (x-Rod option c). Wire-built once
 * per asset service and handed to {@code XYRod} as its dispatcher; the standalone xxRod service consumes
 * the queue and writes the {@code *_log} tables.
 */
public final class RodEventBusPublisher implements Consumer<RodEvent> {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + RodEventBusPublisher.class.getName());
    private static final Logger msgLog = LoggerFactory.getLogger("msg." + RodEventBusPublisher.class.getName());

    private final JmsTemplate jms;
    private final String queue;
    private final ObjectMapper om;

    public RodEventBusPublisher(JmsTemplate jms, String queue, ObjectMapper om) {
        this.jms   = jms;
        this.queue = queue;
        this.om    = om;
    }

    @Override
    public void accept(RodEvent e) {
        try {
            Map<String, Object> props = RodEventCodec.toProps(e, om);
            String applMsgId = UUID.randomUUID().toString();
            props.put(EsqMsgConstants.FIELD_APPL_MSG_ID,  applMsgId);
            props.put(EsqMsgConstants.FIELD_SENDING_TIME, Instant.now().toString());
            jms.send(queue, session -> {
                Message m = session.createMessage();
                Utils.setProps(m, props);
                return m;
            });
            msgLog.info("ROD | RDA | {} | {} | {} | {} | {}",
                    applMsgId, props.get(EsqMsgConstants.FIELD_EVENT_TYPE), e.kind(), e.entityId(), e.subId());
        } catch (Exception ex) {
            devLog.error("rod-bus: publish failed for kind={}, entityId={}, subId={}: {}",
                    e.kind(), e.entityId(), e.subId(), ex.getMessage(), ex);
        }
    }
}
