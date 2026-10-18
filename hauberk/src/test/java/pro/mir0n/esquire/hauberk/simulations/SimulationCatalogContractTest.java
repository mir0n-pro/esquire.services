/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.simulations;

import pro.mir0n.hauberk.simulations.SimulationInfo;

import io.gatling.javaapi.core.Simulation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pro.mir0n.hauberk.cli.SimulationCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Build-time contract for the hauberk Simulation catalog. Enforces what
 * the compiler can't, because Gatling's {@code Simulation} constructor
 * refuses direct {@code new} (so an abstract method on the base class
 * can't be invoked from {@code hauberk list}). The catalog metadata
 * therefore lives on the {@link SimulationInfo @SimulationInfo}
 * class-level annotation -- this test guarantees every concrete sim
 * carries it with a non-blank value, and that every sim extends our
 * shared base.
 *
 * If you add a new Simulation under
 * {@code pro.mir0n.esquire.hauberk.simulations} and forget the
 * annotation, this test fails the build with a message naming the
 * sim, so the fix is one line.
 */
class SimulationCatalogContractTest {

    @Test
    @DisplayName("Every Simulation extends AEsqSimulation")
    void extendsAEsqSimulation() {
        List<Class<? extends Simulation>> sims = SimulationCatalog.discover();
        assertThat(sims).isNotEmpty();
        for (Class<? extends Simulation> sim : sims) {
            assertThat(AEsqSimulation.class)
                    .as("%s must extend AEsqSimulation", sim.getSimpleName())
                    .isAssignableFrom(sim);
        }
    }

    @Test
    @DisplayName("Every Simulation carries @SimulationInfo with a non-blank description")
    void carriesSimulationInfoAnnotation() {
        List<Class<? extends Simulation>> sims = SimulationCatalog.discover();
        assertThat(sims).isNotEmpty();
        for (Class<? extends Simulation> sim : sims) {
            SimulationInfo info = sim.getAnnotation(SimulationInfo.class);
            assertThat(info)
                    .as("%s must be annotated with @SimulationInfo(\"...\") "
                            + "for `hauberk list` to show its description",
                            sim.getSimpleName())
                    .isNotNull();
            assertThat(info.value())
                    .as("%s @SimulationInfo value must not be blank",
                            sim.getSimpleName())
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("@SimulationInfo descriptions stay within ~80 columns for the list table")
    void descriptionFitsTerminalWidth() {
        // Longest alias today is 'kc-integration-smoke' = 20 chars; list
        // adds two spaces of indent + two spaces of padding = 24 chars
        // before the description, leaving ~76 chars before a typical
        // 100-column terminal wraps. Cap at 90 to leave a little slack
        // while still rejecting drift.
        int max = 90;
        List<Class<? extends Simulation>> sims = SimulationCatalog.discover();
        for (Class<? extends Simulation> sim : sims) {
            SimulationInfo info = sim.getAnnotation(SimulationInfo.class);
            if (info != null) {
                assertThat(info.value().length())
                        .as("%s @SimulationInfo too long (%d chars); "
                                + "trim to fit the `hauberk list` table",
                                sim.getSimpleName(), info.value().length())
                        .isLessThanOrEqualTo(max);
            }
        }
    }
}
