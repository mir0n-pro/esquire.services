/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/17/2026 mir0n  created: AuditBusBridge transmit-path tests (lifted from XRodTest's transmit leg) -- buffer /
 *                   after-commit / rollback / immediate / audit-triple / IMappable body build, wrapping a
 *                   capturing x-rod that records each transmit().
 */
package pro.mir0n.esquire.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.backend.jpa.IMappable;
import pro.mir0n.esquire.backend.service.EsqContextHolder;
import pro.mir0n.esquire.backend.service.EsqRequestContext;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.xrod.impl.XRodDisabled;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class AuditBusBridgeTest {

    private final List<RodEvent> transmitted = new CopyOnWriteArrayList<>();

    /** A capturing x-rod: records each {@code transmit()} so the bridge's transmit path (buffer / after-commit /
     *  audit-triple / body build) can be asserted. The audit-OFF case uses the real OFF rod ({@link XRodDisabled}),
     *  which the bridge detects to make {@code post()} a no-op. */
    private final class CapturingXRod implements IXRod {
        @Override public void configure(XRodParams params, Role role, ObjectMapper objectMapper) { }
        @Override public void setWorker(Consumer<RodEvent> worker) { }
        @Override public void init(String name, Logger devLog) { }
        @Override public void start() { }
        @Override public void shutdown() { }
        @Override public void transmit(RodEvent event) { transmitted.add(event); }
        @Override public void receive(RodEvent event) { }
    }

    private AuditBusBridge bridge(boolean enabled) {
        return new AuditBusBridge(enabled ? new CapturingXRod() : new XRodDisabled());
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

    @Test
    void onePerEntity_inOneTx_allTransmittedAfterCommit() {
        AuditBusBridge bridge = bridge(true);
        inCommittedTx(() -> {
            bridge.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"));
            bridge.post(RodEvent.Op.UPDATE, 992, "100", null, Map.of("first", "Ann"));
            bridge.post(RodEvent.Op.UPDATE, 988, "100", "55", Map.of("city", "NYC"));
        });
        assertThat(transmitted).hasSize(3);
        assertThat(transmitted).allMatch(e -> e.actionTime() > 0L);
        assertThat(transmitted).allMatch(e -> EsqMsgConstants.MSG_TYPE_AUDIT.equals(e.msgType()));
    }

    @Test
    void delete_hasEmptyBody() {
        AuditBusBridge bridge = bridge(true);
        inCommittedTx(() -> bridge.post(RodEvent.Op.DELETE, 34, "100", null, Map.of("ignored", "x")));
        assertThat(transmitted).hasSize(1);
        assertThat(transmitted.get(0).op()).isEqualTo(RodEvent.Op.DELETE);
        assertThat(transmitted.get(0).body()).isEmpty();
    }

    @Test
    void buffered_nothingTransmittedBeforeCommit() {
        AuditBusBridge bridge = bridge(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            bridge.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"));
            assertThat(transmitted).isEmpty();
            fireAfterCommit();
            fireCompletion(TransactionSynchronization.STATUS_COMMITTED);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(transmitted).hasSize(1);
    }

    @Test
    void rolledBack_transmitsNothing() {
        AuditBusBridge bridge = bridge(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            bridge.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME"));
            fireCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(transmitted).isEmpty();
    }

    @Test
    void disabled_post_isNoOp() {
        AuditBusBridge bridge = bridge(false);
        inCommittedTx(() -> bridge.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME")));
        assertThat(transmitted).isEmpty();
    }

    @Test
    void event_carriesAuditTripleFromContext() {
        EsqContextHolder.set(new EsqRequestContext("corr-1", "req-1", "uid-9", "1.2."));
        AuditBusBridge bridge = bridge(true);
        inCommittedTx(() -> bridge.post(RodEvent.Op.CREATE, 34, "100", null, Map.of("name", "ACME")));
        RodEvent e = transmitted.get(0);
        assertThat(e.correlationId()).isEqualTo("corr-1");
        assertThat(e.requestId()).isEqualTo("req-1");
        assertThat(e.uid()).isEqualTo("uid-9");
    }

    @Test
    void noActiveTransaction_transmitsImmediately() {
        AuditBusBridge bridge = bridge(true);
        bridge.post(RodEvent.Op.UPDATE, 34, "100", null, Map.of("name", "ACME"));
        assertThat(transmitted).hasSize(1);
        assertThat(transmitted.get(0).op()).isEqualTo(RodEvent.Op.UPDATE);
    }

    @Test
    void post_withMappable_buildsBodyViaFillMap() {
        AuditBusBridge bridge = bridge(true);
        CountingMappable src = new CountingMappable(Map.of("name", "ACME", "ccy", "USD"));
        inCommittedTx(() -> bridge.post(RodEvent.Op.UPDATE, 50, "100", null, src));
        assertThat(src.filled).isTrue();
        assertThat(transmitted).hasSize(1);
        assertThat(transmitted.get(0).body()).containsEntry("name", "ACME").containsEntry("ccy", "USD");
    }

    @Test
    void post_withMappable_onDelete_skipsFillMap_emptyBody() {
        AuditBusBridge bridge = bridge(true);
        CountingMappable src = new CountingMappable(Map.of("name", "ACME"));
        inCommittedTx(() -> bridge.post(RodEvent.Op.DELETE, 50, "100", null, src));
        assertThat(src.filled).isFalse();
        assertThat(transmitted).hasSize(1);
        assertThat(transmitted.get(0).body()).isEmpty();
    }

    @Test
    void post_withMappable_whenDisabled_skipsFillMapAndTransmitsNothing() {
        AuditBusBridge bridge = bridge(false);
        CountingMappable src = new CountingMappable(Map.of("name", "ACME"));
        inCommittedTx(() -> bridge.post(RodEvent.Op.UPDATE, 50, "100", null, src));
        assertThat(src.filled).isFalse();
        assertThat(transmitted).isEmpty();
    }

    @Test
    void post_noBodyOverload_delete_emptyBody() {
        AuditBusBridge bridge = bridge(true);
        inCommittedTx(() -> bridge.post(RodEvent.Op.DELETE, 50, "100", null));
        assertThat(transmitted).hasSize(1);
        assertThat(transmitted.get(0).op()).isEqualTo(RodEvent.Op.DELETE);
        assertThat(transmitted.get(0).body()).isEmpty();
    }

    /** An IMappable that records whether fillMap was invoked, so the overload's guards can be asserted. */
    private static final class CountingMappable implements IMappable {
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
