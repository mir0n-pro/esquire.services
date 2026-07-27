/*
 *  Esquire frameworks (tm)  --  messaging library
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  Cross-environment topology DRIFT-GUARD. The messaging topology lives as THREE separate, hand-maintained
 *  per-environment files (compose / local-k8s / OKE) with concrete values and no single source of truth, so a
 *  resilience setting has to be copied into each BY HAND -- and the rare/far one (OKE) silently drifts. This turns
 *  ONE such drift into a build failure instead of a production surprise: every ActiveMQ broker endpoint
 *  (tcp://...:61616) in EVERY topology file MUST be wrapped in failover:, so a broker restart/bounce reconnects
 *  under the cached connection rather than leaving a dead one that needs a pod kick.
 *
 *  Origin: F0 audit B1 -- OKE alone carried plain tcp:// while compose and local-k8s used failover:, so a broker
 *  blip on OKE became a depooling outage that never self-healed. The class of bug ("proven on one env's file, not
 *  propagated to another") is what this guard exists to stop from recurring.
 */
package pro.mir0n.esquire.messaging.catalog;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TopologyDriftGuardTest {

    /** The three per-environment topology files, relative to the messaging module dir (the Surefire basedir). */
    private static final List<String> TOPOLOGY_FILES = List.of(
            "../compose/topology/esquire-topology.yml",
            "../k8s/charts/esquire-topology/esquire-topology.yml",
            "../k8s-oci/esquire-topology.yml");

    private static final String BROKER_PORT = ":61616";    // the ActiveMQ broker endpoint marker
    private static final String FAILOVER    = "failover:";  // the required reconnect wrapper

    @Test
    void everyActiveMqBrokerEndpointUsesFailover() throws IOException {
        List<String> violations = new ArrayList<>();
        int brokerEndpointsSeen = 0;
        for (String rel : TOPOLOGY_FILES) {
            Path p = Path.of(rel);
            assertTrue(Files.exists(p),
                    "topology file not found -- the drift-guard cannot run: " + p.toAbsolutePath());
            List<String> lines = Files.readAllLines(p);
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                // an ActiveMQ endpoint line: names the broker port, is not a comment, and must carry failover:
                boolean isBrokerEndpoint = !t.startsWith("#") && t.contains("endpoint:") && t.contains(BROKER_PORT);
                if (isBrokerEndpoint) {
                    brokerEndpointsSeen++;
                    if (!t.contains(FAILOVER)) {
                        violations.add(rel + ":" + (i + 1) + "  ->  " + t);
                    }
                }
            }
        }
        // Guard against a vacuous pass: if the paths broke or the format changed and NO broker endpoint was found,
        // the failover: check would be trivially satisfied. Every one of the three files has at least one live
        // ActiveMQ bus, so we must have seen several.
        assertTrue(brokerEndpointsSeen >= 3,
                "drift-guard saw only " + brokerEndpointsSeen + " ActiveMQ endpoint(s) across the topology files -- "
                        + "expected several; the paths or the file format likely changed, so the guard is not "
                        + "actually checking anything.");
        if (!violations.isEmpty()) {
            fail("Topology drift (F0 B1): ActiveMQ broker endpoint(s) NOT wrapped in failover: -- a broker bounce "
                    + "would not self-heal on that target. Copy the failover:(...)?timeout=... wrapper from the "
                    + "other topology files.\n  " + String.join("\n  ", violations));
        }
    }
}
