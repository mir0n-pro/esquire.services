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
 * 06/17/2026 mir0n  usesOutboundTransport() / bindInbound() removed; isEnabled() overrides false (the only
 *                   x-rod that is off)
 * 06/22/2026 mir0n  start(name,devLog,worker) split into setWorker (no-op) + init (no-op) + start (no-op); the
 *                   facade no longer falls back to it (an undeclared bus throws), so a slot picks it ON PURPOSE
 *                   with rod-class = XRodDisabled. import Role/XRodParams from messaging.catalog, IXRod/RodEvent
 *                   from messaging.
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.function.Consumer;

/** The OFF x-rod: a fully inert {@link IXRod}. Both legs are absent -- it transmits nothing and receives nothing,
 *  carries no config, opens no transport. A slot selects it ON PURPOSE with {@code rod-class = XRodDisabled} to
 *  run a service WITHOUT that bus (a declared-but-disabled bus, e.g. audit off) -- the facade never falls back to
 *  it (an undeclared bus throws), so a disabled bus is always explicit. */
public final class XRodDisabled implements IXRod {

    /** No-arg: x-rods are class-name-resolved + reflectively instantiated. */
    public XRodDisabled() {
    }

    @Override
    public void configure(XRodParams params, Role role, ObjectMapper objectMapper) {
        // OFF: no config treated.
    }

    @Override
    public void setWorker(Consumer<RodEvent> worker) {
        // OFF: no receive worker.
    }

    @Override
    public void init(String name, Logger devLog) {
        // OFF: no legs created.
    }

    @Override
    public void start() {
        // OFF: nothing to run.
    }

    @Override
    public void shutdown() {
        // OFF: nothing to stop.
    }

    @Override
    public boolean isEnabled() {
        return false;   // the ONLY x-rod that is not enabled
    }

    @Override
    public void transmit(RodEvent event) {
        // OFF: transmit no-op.
    }

    @Override
    public void receive(RodEvent event) {
        // OFF: receive no-op.
    }
}
