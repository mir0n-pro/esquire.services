/*
 *  Esquire frameworks (tm)
 *  common library -- test
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.messaging.xrod.impl;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** XRodInfo proves the x-rod.custom seam is generic (a SECOND impl binds its OWN params) + its directive line. */
class XRodInfoTest {

    @Test
    void implOwnedNamedSubBlockBindsTheImplsOwnParams() {
        // the pod owns its block name (XRodInfo.PARAM = "info"); XRodParams stays impl-agnostic.
        XRodParams p = XRodParams.from(Map.of("rod-class", "XRodInfo", "info", Map.of("dir", "Skipped")));
        XRodInfoParams info = p.sub(XRodInfo.PARAM, XRodInfoParams.class);
        assertEquals("Skipped", info.dir());
    }

    @Test
    void absentSubBlockYieldsNull() {
        XRodParams p = XRodParams.from(Map.of("rod-class", "XRodInfo"));
        assertNull(p.sub(XRodInfo.PARAM, XRodInfoParams.class));
    }

    @Test
    void dirOrFallsBackWhenBlank() {
        assertEquals("Skipped", new XRodInfoParams(null).dirOr("Skipped"));
        assertEquals("Skipped", new XRodInfoParams("  ").dirOr("Skipped"));
        assertEquals("Dropped", new XRodInfoParams("Dropped").dirOr("Skipped"));
    }

    @Test
    void describeLeadsWithTheDirectiveAndCarriesTheWholeEvent() {
        RodEvent e = new RodEvent(RodEvent.Op.UPDATE, 30, "8", "3", 1000L,
                "crl-1", "req-1", "uid-1", "rod-1", "UA", Map.of("k", "v"));
        String line = XRodInfo.describe("Skipped", e);
        assertTrue(line.startsWith("Skipped | UA | "), line);   // directive in the dir slot, then msg-type
        assertTrue(line.contains(" | 30 | 8 | 3 | rod-1 | req-1 | uid-1 | crl-1 | 1000 | "), line);
        assertTrue(line.contains("k=v"), line);                  // the body is logged too (whole event)
    }
}
