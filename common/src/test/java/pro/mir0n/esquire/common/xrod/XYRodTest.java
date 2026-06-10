package pro.mir0n.esquire.common.xrod;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class XYRodTest {

    private final List<RodEvent> received = new CopyOnWriteArrayList<>();

    private XYRod newRig(boolean enabled) {
        XYRod rig = new XYRod(received::add, enabled, 1000);
        rig.start("test-xy", null);
        return rig;
    }

    @AfterEach
    void tearDown() {
        EsqContextHolder.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void fireAfterCommit() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
    }

    private void fireCompletion(int status) {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(status);
        }
    }

    /** Run {@code body} inside a simulated transaction and fire the commit callbacks. */
    private void inCommittedTx(Runnable body) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            body.run();
            fireAfterCommit();
            fireCompletion(TransactionSynchronization.STATUS_COMMITTED);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void awaitSize(int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (received.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
    }

    @Test
    void onePerEntity_inOneTx_allEmittedAfterCommit() throws Exception {
        XYRod rig = newRig(true);
        inCommittedTx(() -> {
            rig.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"));   // user
            rig.post(RodEvent.Op.UPDATE, 992, "100", null, Map.of("first", "Ann"));  // person sub-kind
            rig.post(RodEvent.Op.UPDATE, 988, "100", "55", Map.of("city", "NYC"));    // address, sub_id=55
        });
        awaitSize(3, 3000);
        assertThat(received).hasSize(3);
        assertThat(received).allMatch(e -> e.actionTime() > 0L);
        rig.shutdown();
    }

    @Test
    void delete_hasEmptyBody() throws Exception {
        XYRod rig = newRig(true);
        inCommittedTx(() -> rig.post(RodEvent.Op.DELETE, 34, "100", null, Map.of("ignored", "x")));
        awaitSize(1, 3000);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).op()).isEqualTo(RodEvent.Op.DELETE);
        assertThat(received.get(0).body()).isEmpty();
        rig.shutdown();
    }

    @Test
    void buffered_nothingEmittedBeforeCommit() throws Exception {
        XYRod rig = newRig(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            rig.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"));
            Thread.sleep(120);
            assertThat(received).isEmpty();   // held until commit
            fireAfterCommit();
            fireCompletion(TransactionSynchronization.STATUS_COMMITTED);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        awaitSize(1, 3000);
        assertThat(received).hasSize(1);
        rig.shutdown();
    }

    @Test
    void rolledBack_emitsNothing() throws Exception {
        XYRod rig = newRig(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            rig.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"));
            // no afterCommit -> rollback path
            fireCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        Thread.sleep(150);
        assertThat(received).isEmpty();
        rig.shutdown();
    }

    @Test
    void disabled_post_isNoOp() throws Exception {
        XYRod rig = newRig(false);
        assertThat(rig.isEnabled()).isFalse();
        inCommittedTx(() -> rig.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME")));
        Thread.sleep(150);
        assertThat(received).isEmpty();
        rig.shutdown();
    }

    @Test
    void event_carriesAuditTripleFromContext() throws Exception {
        EsqContextHolder.set(new EsqRequestContext("corr-1", "req-1", "uid-9", "1.2."));
        XYRod rig = newRig(true);
        inCommittedTx(() -> rig.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME")));
        awaitSize(1, 3000);
        RodEvent e = received.get(0);
        assertThat(e.correlationId()).isEqualTo("corr-1");
        assertThat(e.requestId()).isEqualTo("req-1");
        assertThat(e.uid()).isEqualTo("uid-9");
        rig.shutdown();
    }

    @Test
    void noActiveTransaction_feedsImmediately() throws Exception {
        XYRod rig = newRig(true);
        rig.post(RodEvent.Op.UPDATE, 34, "100", null, Map.of("name", "ACME"));   // no tx active
        awaitSize(1, 3000);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).op()).isEqualTo(RodEvent.Op.UPDATE);
        rig.shutdown();
    }

    // ---- post(IMappable) overload: the entity fills its own body ----

    @Test
    void post_withMappable_buildsBodyViaFillMap() throws Exception {
        XYRod rig = newRig(true);
        CountingMappable src = new CountingMappable(Map.of("name", "ACME", "ccy", "USD"));
        inCommittedTx(() -> rig.post(RodEvent.Op.UPDATE, 50, "100", null, src));
        awaitSize(1, 3000);
        assertThat(src.filled).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).body()).containsEntry("name", "ACME").containsEntry("ccy", "USD");
        rig.shutdown();
    }

    @Test
    void post_withMappable_onDelete_skipsFillMap_emptyBody() throws Exception {
        XYRod rig = newRig(true);
        CountingMappable src = new CountingMappable(Map.of("name", "ACME"));
        inCommittedTx(() -> rig.post(RodEvent.Op.DELETE, 50, "100", null, src));
        awaitSize(1, 3000);
        assertThat(src.filled).isFalse();                 // DELETE: id + kind ride the header, body not built
        assertThat(received).hasSize(1);
        assertThat(received.get(0).body()).isEmpty();
        rig.shutdown();
    }

    @Test
    void post_withMappable_whenDisabled_skipsFillMapAndEmitsNothing() throws Exception {
        XYRod rig = newRig(false);
        CountingMappable src = new CountingMappable(Map.of("name", "ACME"));
        inCommittedTx(() -> rig.post(RodEvent.Op.UPDATE, 50, "100", null, src));
        awaitSize(1, 300);
        assertThat(src.filled).isFalse();                 // disabled: no wasted body-building work
        assertThat(received).isEmpty();
        rig.shutdown();
    }

    @Test
    void post_noBodyOverload_delete_emptyBody() throws Exception {
        XYRod rig = newRig(true);
        inCommittedTx(() -> rig.post(RodEvent.Op.DELETE, 50, "100", null));
        awaitSize(1, 3000);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).op()).isEqualTo(RodEvent.Op.DELETE);
        assertThat(received.get(0).body()).isEmpty();
        rig.shutdown();
    }

    /** An IMappable that records whether fillMap was invoked, so the overload's guards can be asserted. */
    private static final class CountingMappable implements pro.mir0n.esquire.backend.jpa.IMappable {
        private final Map<String, Object> data;
        private boolean filled = false;

        CountingMappable(Map<String, Object> data) {
            this.data = data;
        }

        @Override
        public void fillMap(Map<String, Object> body) {
            filled = true;
            body.putAll(data);
        }
    }
}
