/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the OFF x-Rod pod -- every method is a no-op and it takes NO config. It is the
 *                   default pod when a bus key resolves to NO leg (neither the catalog nor a service-level ref
 *                   defines x-rod), and it can be selected explicitly with rod-class = XRodDisabled when a slot
 *                   needs no x-Rod at all (e.g. audit turned off). post / transmit / submit do nothing; the
 *                   transmit gate isEnabled() is false, so a caller's isEnabled() guard skips the work too.
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import pro.mir0n.esquire.backend.jpa.IMappable;
import pro.mir0n.esquire.messaging.Role;
import pro.mir0n.esquire.messaging.XRodParams;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;

import java.util.Map;
import java.util.function.Consumer;

/** The OFF x-rod: a fully inert {@link IXRod}. Both legs are absent -- it transmits nothing and receives nothing,
 *  carries no config, opens no transport. The frontend selects it when a bus key resolves to no leg, or a slot
 *  sets {@code rod-class = XRodDisabled} on purpose. */
public final class XRodDisabled implements IXRod {

    /** No-arg: x-rods are class-name-resolved + reflectively instantiated. */
    public XRodDisabled() {
    }

    @Override
    public void configure(XRodParams params, Role role, ObjectMapper objectMapper) {
        // OFF: no config treated.
    }

    @Override
    public void start(String name, Logger devLog, Consumer<RodEvent> worker) {
        // OFF: no legs run.
    }

    @Override
    public void bindInbound(AutoCloseable inbound) {
        // OFF: no inbound transport to own.
    }

    @Override
    public void shutdown() {
        // OFF: nothing to stop.
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public boolean usesOutboundTransport() {
        return false;
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, IMappable source, String msgType) {
        // OFF: transmit no-op.
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, String msgType) {
        // OFF: transmit no-op.
    }

    @Override
    public void post(RodEvent.Op op, int kind, String entityId, String subId, Map<String, Object> body, String msgType) {
        // OFF: transmit no-op.
    }

    @Override
    public void transmit(RodEvent event) {
        // OFF: transmit no-op.
    }

    @Override
    public void submit(RodEvent event) {
        // OFF: receive no-op.
    }
}
