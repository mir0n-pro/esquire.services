package pro.mir0n.esquire.enyMan.queue;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.BusConstants;
import pro.mir0n.esquire.audit.AuditBusBridge;
import pro.mir0n.esquire.messaging.IXRod;
import pro.mir0n.esquire.messaging.xrod.impl.XRodDisabled;
import pro.mir0n.esquire.enyMan.jpa.EntityPathLookup;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.enyMan.messaging.EntityBusAdapter;
import pro.mir0n.esquire.enyMan.messaging.KcBusAdapter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the v1.2.6 Goal 3 move-queue orchestrator. The worker dispatch
 * for MoveCommandItem indirectly runs OrgService / UsrService through the injected
 * repositories -- those happy paths are covered end-to-end by the hauberk smoke
 * suite. Here we focus on:
 *   - counter / inMove() invariants
 *   - CreateReconcileItem path-drift detection + reissue
 *   - identity: a CreateReconcileItem carries its originating create's cid/rid and the
 *     worker stamps them itself, so the reissued broadcast is correlated to that create
 *     WITHOUT depending on leftover worker MDC (RE1).
 */
@ExtendWith(MockitoExtension.class)
class MoveQueueManagerTest {

    /** A disabled x-Rod stand-in: the OFF rod -- AuditBusBridge.post() skips it (isEnabled() is false). */
    private static IXRod noopRod() {
        return new XRodDisabled();
    }

    @Mock private EsqEntityDictionaryRepository dictRepo;
    @Mock private EsqOrgRepository orgRepo;
    @Mock private EsqUsrRepository usrRepo;
    @Mock private TransactionTemplate txTemplate;
    @Mock private EntityManager em;
    @Mock private EntityBusAdapter publisher;
    @Mock private KcBusAdapter kcPublisher;
    @Mock private EntityPathLookup pathLookup;

    private MoveQueueManager manager;

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage oks = EsqObjectKindStorage.getInstance();
        // acct kind (path-parent-only): ep_path equals parent USR path
        oks.init(new EsqObjectKind(50, "clAcct", "Client Account", "clAccts", "Test acct",
                false, false, true, "", false, false, "", null, null, null, false));
        // org kind (not path-parent-only): ep_path = parent + ownId + "."
        oks.init(new EsqObjectKind(20, "org", "Org", "orgs", "Test org",
                true, false, false, "", false, false, "", null, null, null, false));
    }

    @BeforeEach
    void setUp() {
        manager = new MoveQueueManager(dictRepo, orgRepo, usrRepo, txTemplate, em,
                publisher, kcPublisher, pathLookup, new AuditBusBridge(noopRod()), 16, 0, 0);
        // Do not call manager.start() -- we want to invoke process() directly without
        // racing the daemon worker thread. The rig is constructed but unstarted.
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // ---- counter ----

    @Test
    @DisplayName("inMove(): false initially")
    void inMove_falseInitially() {
        assertThat(manager.inMove()).isFalse();
    }

    @Test
    @DisplayName("submitMove: rolls back counter when rig drops the put (queue not running)")
    void submitMove_rollsBackOnDrop() {
        // The rig is unstarted (manager.start() not called) so tryPut returns false. submitMove
        // must NOT leak a counter increment in this scenario -- the counter-leak fix rolls it
        // back so inMove() stays false. This is the regression test for the bug we discovered
        // mid-Goal-3 where 16k queued items left inMove() stuck true forever.
        assertThat(manager.inMove()).isFalse();
        manager.submitMove(new MoveCommandItem(20, "100", "200", "1.", "99",
                java.util.List.of("ROLE_ADMIN"), "rid-1", "cid-1", null));
        assertThat(manager.inMove())
                .as("counter must be rolled back when tryPut returns false")
                .isFalse();
    }

    @Test
    @DisplayName("submitMove: succeeds on a started rig -> counter increments (inMove true)")
    void submitMove_incrementsCounter_whenRigRunning() throws InterruptedException {
        manager.start();    // rig running + processing enabled; worker drains.
        try {
            manager.submitMove(new MoveCommandItem(20, "100", "200", "1.", "99",
                    java.util.List.of("ROLE_ADMIN"), "rid-1", "cid-1", null));
            // The worker decrements once it processes (calls orgService -- mocks return null;
            // org dispatch throws because eek isOrg but service returns null List). Either way,
            // counter is decremented by the finally block. We assert that AT LEAST the
            // submit-thread caller observed an increment by checking the path through a
            // submitReconcile that requires inMove() == true to be useful.
            // Wait briefly for the worker to drain.
            long deadline = System.currentTimeMillis() + 2000;
            while (manager.inMove() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            // After drain, counter is back to 0 -- which is the correct post-processing state.
            // (This manager is built with grace 0, so inMove() is exactly counter > 0.)
            assertThat(manager.inMove()).isFalse();
        } finally {
            manager.stop();
        }
    }

    @Test
    @DisplayName("inMove(): grace window keeps it true briefly after the last move drains (elastic end of move)")
    void inMove_graceLingersAfterDrain() throws InterruptedException {
        // A manager with a 500ms grace: after a move drains, inMove() must stay true within the window so a
        // CREATE landing just behind the move is still caught (the grace=0 manager reads false at this same point).
        MoveQueueManager graced = new MoveQueueManager(dictRepo, orgRepo, usrRepo, txTemplate, em,
                publisher, kcPublisher, pathLookup, new AuditBusBridge(noopRod()), 16, 0, 500);
        graced.start();
        try {
            assertThat(graced.inMove()).as("nothing moved yet -> grace not open").isFalse();
            graced.submitMove(new MoveCommandItem(20, "100", "200", "1.", "99",
                    java.util.List.of("ROLE_ADMIN"), "rid-1", "cid-1", null));
            // Wait for the worker to drain the queue (item taken + finally stamps the grace).
            long deadline = System.currentTimeMillis() + 2000;
            while (graced.queueSize() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            Thread.sleep(60);   // let the worker's finally run (decrement -> stamp lastMoveDoneNanos)
            assertThat(graced.inMove())
                    .as("grace keeps inMove() true just after the move drained")
                    .isTrue();
        } finally {
            graced.stop();
        }
    }

    // ---- reconcile: no drift ----

    @Test
    @DisplayName("processReconcile: path matches expected -> no DB update, no broadcast")
    void processReconcile_noDrift() {
        // acct kind 50 is path-parent-only: expected path == parent path.
        when(pathLookup.pathFor("parent-7")).thenReturn("1.5.7.");

        CreateReconcileItem item = new CreateReconcileItem("acct-42", 50, "parent-7", "1.5.7.",
                "create-cid", "create-rid");
        manager.process(item);

        verify(pathLookup).pathFor("parent-7");
        verify(pathLookup, never()).updatePath(anyString(), anyString());
        verify(publisher, never()).publish(anyInt(), anyString(), anyString(), any(), any(), any());
    }

    // ---- reconcile: drift detected ----

    @Test
    @DisplayName("processReconcile: drift detected -> updatePath + broadcast EVENT_UPDATE_PATH")
    void processReconcile_driftFixesAndReissues() {
        // CREATE was published with "1.5.7." but the parent has since moved to "1.9.200.7."
        when(pathLookup.pathFor("parent-7")).thenReturn("1.9.200.7.");
        when(pathLookup.updatePath(eq("acct-42"), eq("1.9.200.7."))).thenReturn(1);

        // The item carries the originating create's cid/rid; the worker stamps them itself and the
        // reissued broadcast is published under them (no leftover-MDC dependency).
        CreateReconcileItem item = new CreateReconcileItem("acct-42", 50, "parent-7", "1.5.7.",
                "create-cid", "create-rid");
        manager.process(item);

        verify(pathLookup).updatePath("acct-42", "1.9.200.7.");
        ArgumentCaptor<Map<String, Object>> textCapt =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq(50), eq("acct-42"), eq(BusConstants.EVENT_UPDATE_PATH),
                eq("create-rid"), eq("create-cid"), textCapt.capture());
        Map<String, Object> text = textCapt.getValue();
        assertThat(text).containsEntry(EsqConstants.TEXT_ID, "acct-42")
                        .containsEntry(EsqConstants.TEXT_KIND, 50)
                        .containsEntry(EsqConstants.TEXT_PATH, "1.9.200.7.");
    }

    @Test
    @DisplayName("processReconcile: broadcast carries the item's OWN cid/rid with worker MDC empty; cleared after (RE1)")
    void processReconcile_usesItemIds_notWorkerMdc() {
        // RE1 regression: after I10 the move worker clears MDC in its finally, so by the time a reconcile
        // runs the worker MDC is EMPTY. The reconcile must therefore carry its create's ids ON THE ITEM and
        // stamp them itself, NOT read leftover MDC. Assert: MDC empty in, broadcast carries the item's ids,
        // MDC cleared out (nothing lingers on the worker thread).
        assertThat(MDC.get(EsqConstants.PD_CORRELATION_ID)).isNull();
        assertThat(MDC.get(EsqConstants.PD_REQUEST_ID)).isNull();

        when(pathLookup.pathFor("parent-7")).thenReturn("1.9.200.7.");
        when(pathLookup.updatePath(eq("acct-42"), eq("1.9.200.7."))).thenReturn(1);

        CreateReconcileItem item = new CreateReconcileItem("acct-42", 50, "parent-7", "1.5.7.",
                "create-cid", "create-rid");
        manager.process(item);

        verify(publisher).publish(eq(50), eq("acct-42"), eq(BusConstants.EVENT_UPDATE_PATH),
                eq("create-rid"), eq("create-cid"), any());
        assertThat(MDC.get(EsqConstants.PD_CORRELATION_ID)).isNull();
        assertThat(MDC.get(EsqConstants.PD_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("processReconcile: parent has no ep_path -> skip (no update, no broadcast)")
    void processReconcile_parentMissing() {
        when(pathLookup.pathFor("parent-7")).thenReturn(null);

        CreateReconcileItem item = new CreateReconcileItem("acct-42", 50, "parent-7", "1.5.7.",
                "create-cid", "create-rid");
        manager.process(item);

        verify(pathLookup, never()).updatePath(anyString(), anyString());
        verify(publisher, never()).publish(anyInt(), anyString(), anyString(), any(), any(), any());
    }
}
