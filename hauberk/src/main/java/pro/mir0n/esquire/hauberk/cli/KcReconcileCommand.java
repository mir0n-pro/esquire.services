/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 07/02/2026 mir0n  created: picocli "kc-reconcile" subcommand -- detect (and optionally --repair) drift between
 *                   esq2025 and KeyCloak, talking PG(JDBC) + KC(REST) directly, out of the Esquire services.
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import pro.mir0n.esquire.hauberk.reconcile.KcRecover;

import java.util.concurrent.Callable;

/**
 * {@code hauberk kc-reconcile [--repair] [--config <file>]}
 *
 * <p>A standalone data-recover utility, OUT of the Esquire services: it reads the authoritative connected-user
 * state from esq2025 over PG/JDBC and the mirror from KeyCloak over the REST admin API, and reports the drift
 * (stale {@code esq_rootpath}, missing-in-KC, orphan-in-KC). With {@code --repair} it fixes a stale
 * {@code esq_rootpath} in place. Because it uses the native APIs directly (not the services or the bus), it
 * recovers even when they are down.
 *
 * <p>Config: PG connection from {@code pg.url} / {@code pg.user} / {@code pg.password}, KC from {@code kc.base} /
 * {@code kc.realm} / {@code kc.admin.user} / {@code kc.admin.password} -- in {@code hauberk.properties} (or the
 * {@code --config} overlay). Exit code: 0 = in sync, 1 = drift found.
 */
@Command(name = "kc-reconcile",
        description = "Reconcile KeyCloak against Esquire (esq2025): detect (and optionally --repair) a stale "
                + "esq_rootpath. Talks PG(JDBC) + KC(REST) directly, out of the services.")
public class KcReconcileCommand implements Callable<Integer> {

    @Option(names = "--repair",
            description = "Fix a stale KC esq_rootpath in place. Default: check only (report, change nothing).")
    boolean repair;

    @Option(names = "--config",
            paramLabel = "FILE",
            description = "Config overlay (e.g. hauberk-k8s.properties). Default: hauberk.properties.")
    String configFile;

    @Override
    public Integer call() throws Exception {
        // Must be set BEFORE HauberkConfig's static init (first touched by KcRecover.run), mirrors RunCommand.
        if (configFile != null && !configFile.isBlank()) {
            System.setProperty("hauberk.config", configFile);
        }
        int drifts = KcRecover.run(repair);
        return drifts == 0 ? 0 : 1;
    }
}
