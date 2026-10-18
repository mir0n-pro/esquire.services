/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: kind-code constants (ORG=20, USR_CLIENT=34, ACCT_CLIENT=50, ...) used by all Chains
 * 08/26/2026 mir0n  TEST_EMAIL_DOMAIN removed
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.config;

/**
 * Entity kind codes used by the hauberk's Chains. Sourced from
 * services/common/src/main/resources/esq-entity-dictionaries.xml -- the kind
 * code is the integer the gateway routes on (kind query param to /esq-cmd-*).
 */
public final class EntityKinds {

    private EntityKinds() {}

    public static final int ORG          = 20;   // Organization (Office)
    public static final int USR_CLIENT   = 34;   // Client user (carries accounts)
    public static final int ACCT_CLIENT  = 50;   // Client account
    public static final int TX_DEPOSIT   = 1000; // Deposit transaction
    public static final int TX_WITHDRAW  = 1002; // Withdrawal transaction
    public static final int TX_TRANSFER  = 1004; // Transfer transaction (debit/credit pair)

    /** Root entity id -- top of the tree, parent of all top-level offices. */
    public static final String ROOT_ID = "1";

}
