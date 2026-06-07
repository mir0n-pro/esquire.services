/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: AuditRod bus-mode wiring -- the xy feed dispatches committed events to the
 *                   supplied publisher (no in-process xx-Rod), and shutdown is clean with no XXRod.
 */
package pro.mir0n.esquire.common.audit;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.common.xrod.RodEvent;
import pro.mir0n.esquire.common.xrod.XYRod;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRodBusTest {

    private static AuditSettings settings(boolean enabled) {
        return new AuditSettings(enabled, 4, false, 4096, "shared",
                "dev-postgres", "", "", "", 8, "dev-postgres");
    }

    @Test
    void busModeDispatchesCommittedEventAndShutsDownCleanly() throws Exception {
        CopyOnWriteArrayList<RodEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        Consumer<RodEvent> dispatcher = e -> {
            received.add(e);
            latch.countDown();
        };

        AuditRod.Handle handle = AuditRod.buildBus("test", settings(true), dispatcher,
                LoggerFactory.getLogger("develop.AuditRodBusTest"));
        XYRod xy = handle.xyRod();

        assertThat(xy.isEnabled()).isTrue();
        xy.post(RodEvent.Op.CREATE, 20, "1", null, Map.of("name", "office"));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).entityId()).isEqualTo("1");
        assertThat(received.get(0).kind()).isEqualTo(20);

        handle.shutdown(); // no in-process xx-Rod -> must not throw
    }

    @Test
    void busModeDisabledIsNoOp() {
        AuditRod.Handle handle = AuditRod.buildBus("test2", settings(false),
                e -> { throw new IllegalStateException("disabled feed must not dispatch"); },
                LoggerFactory.getLogger("develop.AuditRodBusTest"));
        handle.xyRod().post(RodEvent.Op.CREATE, 20, "1", null, Map.of());
        handle.shutdown();
    }
}
