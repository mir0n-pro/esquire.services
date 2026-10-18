/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/13/2026 mir0n  v2.0.0 -- created: the Esquire base for every hauberk Simulation. Armoury's
 *                   AHauberkSimulation holds the machinery (the lazy token, the instrumented
 *                   httpProtocol, the perf-matrix flush) and takes the target base URL as a
 *                   constructor argument; this one line supplies the Esquire gateway. Every
 *                   Simulation in this project extends THIS, and nothing else changed in them.
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;
import pro.mir0n.hauberk.simulations.AHauberkSimulation;

/**
 * Every Esquire Simulation runs against the gateway, so the base URL is settled once here rather than
 * in twenty-four subclasses. Everything else -- the bearer / basic header, the X-Request-ID, the
 * Performance Matrix capture, the after() flush -- comes from the harness.
 */
public abstract class AEsqSimulation extends AHauberkSimulation {

    protected AEsqSimulation() {
        super(EsqHauberkConfig.GW_BASE);
    }
}
