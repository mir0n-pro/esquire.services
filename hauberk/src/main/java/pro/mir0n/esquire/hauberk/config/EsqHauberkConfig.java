/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/13/2026 mir0n  v2.0.0 -- created: the Esquire half of the old HauberkConfig. The file loading, the
 *                   overlay, the ${key} expansion and the typed accessors are Armoury's HauberkConfig;
 *                   what the settings MEAN is Esquire's, and that is this class: the service endpoints,
 *                   the playground scope, the prepare / move / super shape knobs, and the direct esq2025
 *                   connection the kc-reconcile utility reads. Every setting keeps its property NAME, so
 *                   hauberk.properties and every target overlay read exactly as they did.
 */
package pro.mir0n.esquire.hauberk.config;

import pro.mir0n.hauberk.config.HauberkConfig;

/**
 * What the hauberk's settings mean for an Esquire stack.
 *
 * <p>Armoury's {@link HauberkConfig} owns the mechanism -- one properties file at the module root plus an
 * optional overlay, a system property overriding either, and {@code ${other.key}} expanded once at load.
 * This class is the Esquire vocabulary over it, read on first touch.
 */
public final class EsqHauberkConfig {

    private EsqHauberkConfig() {}

    /** The gateway root -- every Simulation's base URL. */
    public static final String GW_BASE = HauberkConfig.require("gw.base");

    /** The BFF root -- the browser-tier login path the LoginViaBff chain walks. */
    public static final String BFF_BASE = HauberkConfig.require("bff.base");

    /** enyMan reached DIRECTLY (not via the gateway) -- the R6 HA timeout smoke hits the flag-gated /test hook. */
    public static final String ENYMAN_BASE = HauberkConfig.require("enyman.base");

    /**
     * Playground parent ORG id -- where every hauberk-created office / user / account roots itself. The
     * Test Driver service accounts have esq_rootpath="1.14.", so the hauberk can only create entities
     * under Test House (org 14).
     */
    public static final String PLAYGROUND_PARENT_ID = HauberkConfig.require("playground.parent.id");

    // Prepare-shape knobs allow 0 (e.g. 0 accounts per client = users only).
    public static final int PREPARE_OFFICES_DEPTH       = HauberkConfig.requireInt("prepare.offices.depth");
    public static final int PREPARE_CLIENTS_PER_OFFICE  = HauberkConfig.requireIntMin0("prepare.clients.per.office");
    public static final int PREPARE_ACCOUNTS_PER_CLIENT = HauberkConfig.requireIntMin0("prepare.accounts.per.client");

    // Move-shape: depth must be >=3 (need L1/L2/L3 distinct positions).
    public static final int MOVE_OFFICES_DEPTH       = HauberkConfig.requireIntMin("move.offices.depth", 3);
    public static final int MOVE_CLIENTS_PER_OFFICE  = HauberkConfig.requireInt("move.clients.per.office");
    public static final int MOVE_ACCOUNTS_PER_CLIENT = HauberkConfig.requireIntMin0("move.accounts.per.client");

    // SuperLoad: duration >= 1; workers >= 0 (0 disables that scenario).
    // SuperLoadSimulation enforces "at least one workers > 0" at runtime.
    public static final int SUPER_DURATION_SECONDS   = HauberkConfig.requireIntMin("super.duration.seconds", 1);
    public static final int SUPER_READ_WORKERS       = HauberkConfig.requireIntMin0("super.read.workers");
    public static final int SUPER_UPDATE_WORKERS     = HauberkConfig.requireIntMin0("super.update.workers");
    public static final int SUPER_CREATE_WORKERS     = HauberkConfig.requireIntMin0("super.create.workers");
    public static final int SUPER_MOVE_WORKERS       = HauberkConfig.requireIntMin0("super.move.workers");
    public static final int SUPER_TX_WORKERS         = HauberkConfig.requireIntMin0("super.tx.workers");
    public static final int SUPER_TREE_WORKERS       = HauberkConfig.requireIntMin0("super.tree.workers");
    public static final int SUPER_READACCT_WORKERS   = HauberkConfig.requireIntMin0("super.readacct.workers");
    public static final int SUPER_TREESMALL_WORKERS  = HauberkConfig.requireIntMin0("super.treesmall.workers");
    public static final int SUPER_CACHE_WORKERS      = HauberkConfig.requireIntMin0("super.cache.workers");
    public static final int SUPER_CACHESMALL_WORKERS = HauberkConfig.requireIntMin0("super.cachesmall.workers");

    /** Direct esq2025 connection for the OUT-OF-BAND kc-reconcile utility (PG/JDBC). Optional -- only the
     *  kc-reconcile command touches them, so they are NOT required (a Simulation never opens a DB). */
    public static final String PG_URL      = HauberkConfig.optional("pg.url", "");
    public static final String PG_USER     = HauberkConfig.optional("pg.user", "esq2025");
    public static final String PG_PASSWORD = HauberkConfig.optional("pg.password", "");
}
