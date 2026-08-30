/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/29/2026 mir0n  created: the own-exclusion filter -- what a broker does with noLocal, applied in code for
 *                   a transport whose vendor cannot. Its own filter, never folded into the subscription one:
 *                   a leg carries whatever subscription its consumer asked for, and this sits in front of any
 *                   of them.
 */
package pro.mir0n.esquire.messaging.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.messaging.BusConstants;

import java.util.function.Consumer;

/**
 * Drops a message this very rod published -- {@code noLocal}.
 *
 * <p>It is for a service sitting on both legs of one wire: enyMan publishes entity events and listens for its
 * peers' on the same broadcast, and must reconcile a PEER instance's create during a move, never its own. A
 * broker does this on a shared connection; SNS has no such thing -- every subscribed queue gets the publisher's
 * own messages back -- so it is done here, and the rod-id is what tells its own apart.
 */
public final class OwnExcluding implements Consumer<TransportMessage> {

    private static final Logger devLog = LoggerFactory.getLogger("develop.pro.mir0n.esquire.messaging.transport.OwnExcluding");

    private final Consumer<TransportMessage> handler;
    private final String ownRodId;

    private OwnExcluding(Consumer<TransportMessage> handler, String ownRodId) {
        this.handler  = handler;
        this.ownRodId = ownRodId;
    }

    /** {@code handler} as it is when {@code noLocal} is off, and wrapped when it is on. */
    public static Consumer<TransportMessage> wrap(Consumer<TransportMessage> handler, String ownRodId,
                                                  boolean noLocal) {
        Consumer<TransportMessage> ret = handler;
        if (noLocal && ownRodId != null && !ownRodId.isBlank()) {
            ret = new OwnExcluding(handler, ownRodId);
            devLog.info("tp-sqns: noLocal on -- this leg drops what rod {} published", ownRodId);
        }
        return ret;
    }

    @Override
    public void accept(TransportMessage message) {
        Object rodId = message.headers().get(BusConstants.FIELD_ROD_ID);
        boolean own = rodId != null && ownRodId.equals(rodId.toString());
        if (own) {
            devLog.debug("tp-sqns: dropping own publication (rod-id={})", ownRodId);
        } else {
            handler.accept(message);
        }
    }
}
