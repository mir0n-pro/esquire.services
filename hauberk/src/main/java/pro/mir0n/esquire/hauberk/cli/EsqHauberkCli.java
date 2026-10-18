/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 10/13/2026 mir0n  v2.0.0 -- created: the Esquire entry point. Same five subcommands as the v1.2.16
 *                   HauberkCli, three of them Armoury's unchanged (list / summary / diff), run replaced by
 *                   the Esquire EsqRunCommand that adds the shape knobs, and kc-reconcile as it was. This
 *                   is the Main-Class of the shaded jar, so `hauberk.cmd <subcommand>` is unchanged.
 */
package pro.mir0n.esquire.hauberk.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import pro.mir0n.hauberk.cli.DiffCommand;
import pro.mir0n.hauberk.cli.ListCommand;
import pro.mir0n.hauberk.cli.SummaryCommand;

/**
 * Top-level entrypoint for the Esquire Haubergeon harness.
 *
 * Run:
 *   java -jar target/hauberk.jar &lt;subcommand&gt;
 *
 * Subcommands:
 *   run           -- launch a Gatling Simulation
 *   list          -- list discovered Simulation classes
 *   summary       -- per-URL percentile summary of one perf-matrix CSV
 *   diff          -- side-by-side compare of two perf-matrix CSV files
 *   kc-reconcile  -- out-of-band KeyCloak / esq2025 recovery
 *
 * Default with no args: print help.
 */
@Command(name = "hauberk",
        mixinStandardHelpOptions = true,
        version = "hauberk 2.0.0",
        description = "Esquire Haubergeon -- Gatling stress / load test harness.",
        subcommands = {
                EsqRunCommand.class,
                ListCommand.class,
                SummaryCommand.class,
                DiffCommand.class,
                KcReconcileCommand.class,
                CommandLine.HelpCommand.class
        })
public final class EsqHauberkCli implements Runnable {

    @Override
    public void run() {
        // No subcommand given -> show help (picocli mixin).
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new EsqHauberkCli()).execute(args);
        System.exit(exitCode);
    }
}
