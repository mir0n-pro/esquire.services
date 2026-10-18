/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/13/2026 mir0n  v2.0.0 -- created: the Esquire shape knobs of `hauberk run`, back on the subcommand by
 *                   extension. Armoury's RunCommand carries what belongs to any run (--metrics, --output,
 *                   --config, --times, --no-health-check, --set); these seventeen name Esquire scenarios
 *                   and so live here. picocli reads inherited @Option fields, so the command line is
 *                   unchanged from v1.2.16 -- every existing hauberk.cmd invocation still works.
 */
package pro.mir0n.esquire.hauberk.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import pro.mir0n.hauberk.cli.RunCommand;

/**
 * `hauberk run &lt;sim&gt; [options]` for an Esquire stack: the harness command plus the shape knobs of the
 * Esquire Simulations. Each option pushes the property its Simulation reads through
 * {@code EsqHauberkConfig}, which is why they have to be set BEFORE the config layer is first touched --
 * {@link #applyProperties()} is called from the harness for exactly that reason.
 */
@Command(name = "run",
        description = "Run a Gatling Simulation against an Esquire stack (programmatic launch).")
public class EsqRunCommand extends RunCommand {

    // --- SuperLoad shape knobs (system properties consumed by EsqHauberkConfig) ---

    @Option(names = "--duration", paramLabel = "N",
            description = "super.duration.seconds")
    Integer duration;

    @Option(names = "--read",   paramLabel = "N", description = "super.read.workers")   Integer read;
    @Option(names = "--update", paramLabel = "N", description = "super.update.workers") Integer update;
    @Option(names = "--create", paramLabel = "N", description = "super.create.workers") Integer create;
    @Option(names = "--move",   paramLabel = "N", description = "super.move.workers")   Integer move;
    @Option(names = "--tx",     paramLabel = "N", description = "super.tx.workers")     Integer tx;
    @Option(names = "--tree",     paramLabel = "N", description = "super.tree.workers")     Integer tree;
    @Option(names = "--readacct", paramLabel = "N", description = "super.readacct.workers") Integer readAcct;
    @Option(names = "--treesmall", paramLabel = "N", description = "super.treesmall.workers") Integer treeSmall;
    @Option(names = "--cache", paramLabel = "N", description = "super.cache.workers") Integer cache;
    @Option(names = "--cachesmall", paramLabel = "N", description = "super.cachesmall.workers") Integer cacheSmall;

    // --- Prepare shape knobs ---

    @Option(names = "--prep-depth",    paramLabel = "N", description = "prepare.offices.depth")        Integer prepDepth;
    @Option(names = "--prep-clients",  paramLabel = "N", description = "prepare.clients.per.office")   Integer prepClients;
    @Option(names = "--prep-accounts", paramLabel = "N", description = "prepare.accounts.per.client")  Integer prepAccounts;

    // --- Move sim shape knobs ---

    @Option(names = "--move-depth",    paramLabel = "N", description = "move.offices.depth")        Integer moveDepth;
    @Option(names = "--move-clients",  paramLabel = "N", description = "move.clients.per.office")   Integer moveClients;
    @Option(names = "--move-accounts", paramLabel = "N", description = "move.accounts.per.client")  Integer moveAccounts;

    @Override
    protected void applyProperties() {
        super.applyProperties();
        // shape knobs -- only set if the user supplied one; EsqHauberkConfig still reads
        // the file for the defaults.
        setIfPresent("super.duration.seconds",         duration);
        setIfPresent("super.read.workers",             read);
        setIfPresent("super.update.workers",           update);
        setIfPresent("super.create.workers",           create);
        setIfPresent("super.move.workers",             move);
        setIfPresent("super.tx.workers",               tx);
        setIfPresent("super.tree.workers",             tree);
        setIfPresent("super.readacct.workers",         readAcct);
        setIfPresent("super.treesmall.workers",        treeSmall);
        setIfPresent("super.cache.workers",            cache);
        setIfPresent("super.cachesmall.workers",       cacheSmall);
        setIfPresent("prepare.offices.depth",          prepDepth);
        setIfPresent("prepare.clients.per.office",     prepClients);
        setIfPresent("prepare.accounts.per.client",    prepAccounts);
        setIfPresent("move.offices.depth",             moveDepth);
        setIfPresent("move.clients.per.office",        moveClients);
        setIfPresent("move.accounts.per.client",       moveAccounts);
    }
}
