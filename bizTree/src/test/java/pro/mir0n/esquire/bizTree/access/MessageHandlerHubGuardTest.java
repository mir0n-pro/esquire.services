/*
 *  Esquire frameworks (tm)
 *  BizTree service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.bizTree.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.messaging.BusConstants;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Race cases for the v1.2.12 freshness guard in {@link MessageHandlerHub#dispatch}.
 *
 * <p>Each test names the race it stands for. "Applied" is asserted by the handler's own cache call
 * ({@code updateNode} for an entity event, {@code moveOrgNode} for a path event) -- the guard sits in
 * front of the handler, so the handler running IS the guard letting it through.
 */
class MessageHandlerHubGuardTest {

    private static final int  ORG_KIND  = 20;
    private static final long ENTITY_PK = 777L;
    private static final String ENTITY_ID = "777";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IBizTreeCacheRepository repo;
    private MessageHandlerHub       hub;

    @BeforeAll
    static void seedKindStorage() {
        EsqObjectKind org = new EsqObjectKind();
        org.setId(ORG_KIND);
        org.setOrg(true);
        EsqObjectKindStorage.getInstance().init(org);
    }

    @BeforeEach
    void setUp() {
        repo = mock(IBizTreeCacheRepository.class);
        hub  = new MessageHandlerHub(repo);
    }

    /** The cache currently holds these two numbers for ENTITY_PK. Null = the node is not cached at all. */
    private void cached(Long entityChangeNo, Long pathChangeNo) {
        when(repo.findChangeNumbers(ENTITY_PK)).thenReturn(new Long[]{entityChangeNo, pathChangeNo});
    }

    private void notCached() {
        when(repo.findChangeNumbers(ENTITY_PK)).thenReturn(null);
    }

    private JsonNode updateBody() {
        return MAPPER.valueToTree(Map.of("name", "ACME", "desc", "d"));
    }

    private JsonNode pathBody(Long pathChangeNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("path", "1.9.777.");
        if (pathChangeNo != null) {
            body.put("pathChangeNo", pathChangeNo);
        }
        return MAPPER.valueToTree(body);
    }

    private void update(Long changeNo) {
        hub.dispatch(BusConstants.EVENT_UPDATE, ENTITY_ID, ORG_KIND, updateBody(), changeNo);
    }

    /** An X as a move publishes it: the ENTITY number in the header, the PATH number in the body. */
    private void path(Long entityChangeNo, Long pathChangeNo) {
        hub.dispatch(BusConstants.EVENT_UPDATE_PATH, ENTITY_ID, ORG_KIND, pathBody(pathChangeNo), entityChangeNo);
    }

    private void verifyEntityApplied() {
        verify(repo).updateNode(eq(ENTITY_PK), any(), any(), any());
    }

    private void verifyEntitySkipped() {
        verify(repo, never()).updateNode(anyLong(), any(), any(), any());
        verify(repo, never()).stampEntityChangeNo(anyLong(), any());
    }

    private void verifyPathApplied() {
        verify(repo).moveOrgNode(eq(ENTITY_PK), anyString());
    }

    private void verifyPathSkipped() {
        verify(repo, never()).moveOrgNode(anyLong(), anyString());
        verify(repo, never()).stampPathChangeNo(anyLong(), any());
        verify(repo, never()).stampEntityChangeNo(anyLong(), any());
    }

    // =====================================================================
    // R1..R3 -- the entity stream
    // =====================================================================

    @Test
    @DisplayName("R1 redelivery: the SAME number arriving twice is applied once")
    void redelivery_isSkipped() {
        // The broker redelivered an event the cache already applied. Same number, nothing new to do.
        cached(5L, null);
        update(5L);
        verifyEntitySkipped();
    }

    @Test
    @DisplayName("R2 out-of-order: a LOWER number arriving after a higher one is skipped")
    void outOfOrder_isSkipped() {
        // THE case this sprint exists for. The entity broadcast is applied on a flat worker pool with no
        // per-entity affinity, so update 6 can be applied before update 5 lands. Without the guard, 5
        // overwrites 6 and the cache silently holds the older state until the night-watch heals it.
        cached(6L, null);
        update(5L);
        verifyEntitySkipped();
    }

    @Test
    @DisplayName("R3 forward gap: a number several ahead still applies -- gaps are by design")
    void forwardGap_isApplied() {
        // The async paths are allowed to gap (a raised number whose message was never sent, or a lost
        // message). A gap must not stall the cache: anything HIGHER is fresher and applies.
        cached(2L, null);
        update(9L);
        verifyEntityApplied();
        verify(repo).stampEntityChangeNo(ENTITY_PK, 9L);
    }

    // =====================================================================
    // R4..R6 -- the path stream, and the exception that keeps them apart
    // =====================================================================

    @Test
    @DisplayName("R4 THE EXCEPTION: a path event is NOT blocked by a higher entity number")
    void pathEvent_isNotBlockedByEntityNumber() {
        // The node has been edited a lot (entity number 7) but moved rarely (path number 2). A move now
        // sends path number 3. Comparing 3 against the ENTITY number 7 would skip it and leave this node
        // on its old path -- a subtree half-repathed, healed only by the night-watch.
        cached(7L, 2L);
        path(7L, 3L);
        verifyPathApplied();
        verify(repo).stampPathChangeNo(ENTITY_PK, 3L);
        verify(repo).stampEntityChangeNo(ENTITY_PK, 7L);
    }

    @Test
    @DisplayName("R5 mirror: an entity event is NOT blocked by a higher path number")
    void entityEvent_isNotBlockedByPathNumber() {
        // The mirror of R4: a much-moved node (path 9) getting its first rename (entity 1).
        cached(null, 9L);
        update(1L);
        verifyEntityApplied();
        verify(repo).stampEntityChangeNo(ENTITY_PK, 1L);
        verify(repo, never()).stampPathChangeNo(anyLong(), any());
    }

    @Test
    @DisplayName("R6 path redelivery: the same path number twice is applied once")
    void pathRedelivery_isSkipped() {
        cached(7L, 3L);
        path(7L, 3L);
        verifyPathSkipped();
    }

    @Test
    @DisplayName("R7 path out-of-order: an older path number is skipped")
    void pathOutOfOrder_isSkipped() {
        cached(7L, 4L);
        path(7L, 3L);
        verifyPathSkipped();
    }

    // =====================================================================
    // N3 -- an X carries BOTH numbers: the path number guards, the entity number rides
    // =====================================================================

    @Test
    @DisplayName("N3a the MOVED entity: its X stamps the raised entity number and the new path number")
    void movedEntity_stampsBothNumbers() {
        cached(7L, 2L);
        path(8L, 3L);
        verifyPathApplied();
        verify(repo).stampEntityChangeNo(ENTITY_PK, 8L);
        verify(repo).stampPathChangeNo(ENTITY_PK, 3L);
    }

    @Test
    @DisplayName("N3b a DESCENDANT: its entity number is unchanged, and that must not read as stale")
    void descendant_isNotJudgedByItsUnchangedEntityNumber() {
        cached(7L, 2L);
        path(7L, 3L);
        verifyPathApplied();
    }

    @Test
    @DisplayName("N3c mixed freshness: a stale path number skips the message, entity number and all")
    void staleePath_stampsNeitherNumber() {
        // the entity number looks fresher, but one message is one decision: skipped stamps nothing
        cached(7L, 3L);
        path(9L, 3L);
        verifyPathSkipped();
    }

    @Test
    @DisplayName("N3d an X with no path number in the body: applied unguarded, entity number stamped")
    void pathEventWithoutPathNumber_appliesUnguarded() {
        cached(7L, 4L);
        path(8L, null);
        verifyPathApplied();
        verify(repo).stampEntityChangeNo(ENTITY_PK, 8L);
        verify(repo, never()).stampPathChangeNo(anyLong(), any());
    }

    // =====================================================================
    // R8..R11 -- "unknown" must never read as "old"
    // =====================================================================

    @Test
    @DisplayName("R8 no number on the message: applied unguarded")
    void nullChangeNo_appliesUnguarded() {
        // A producer that sent no number (or an older build during a rolling upgrade). Unknown is not old.
        cached(6L, null);
        update(null);
        verifyEntityApplied();
        verify(repo, never()).stampEntityChangeNo(anyLong(), any());
    }

    @Test
    @DisplayName("R9 node not in the cache yet: applied unguarded")
    void unknownNode_appliesUnguarded() {
        // A CREATE that has not landed, or an event for a node outside this cache. Nothing to compare.
        notCached();
        update(3L);
        verifyEntityApplied();
    }

    @Test
    @DisplayName("R10 node cached but never numbered: applied unguarded")
    void cachedButUnnumbered_appliesUnguarded() {
        // A row loaded before the columns existed, or a folder row. Null stored number = never stale.
        cached(null, null);
        update(3L);
        verifyEntityApplied();
        verify(repo).stampEntityChangeNo(ENTITY_PK, 3L);
    }

    @Test
    @DisplayName("R11 non-numeric entity id: applied unguarded, no crash")
    void nonNumericEntityId_appliesUnguarded() {
        hub.dispatch(BusConstants.EVENT_UPDATE, "14~4", ORG_KIND, updateBody(), 3L);
        // it reached the handler (which then fails to parse the id itself -- swallowed by dispatch),
        // and crucially the guard neither threw nor skipped on a well-formed message.
        verify(repo, never()).findChangeNumbers(anyLong());
    }

    // =====================================================================
    // R12 -- the stamp is the guard's memory; it must not run when skipped
    // =====================================================================

    @Test
    @DisplayName("R12 a skipped event does not move the stored number")
    void skippedEvent_doesNotStamp() {
        // If a skipped event still stamped, a stale message would push the number backwards or forwards
        // and corrupt every later comparison.
        cached(6L, 6L);
        update(5L);
        path(6L, 5L);
        verify(repo, never()).stampEntityChangeNo(anyLong(), any());
        verify(repo, never()).stampPathChangeNo(anyLong(), any());
    }

    // =====================================================================
    // R13 -- the meter must not report success for a dispatch that threw
    // =====================================================================

    @Test
    @DisplayName("R13 the cache read throws -> the dispatch meter says error, not handled")
    void cacheReadThrows_meterSaysError() {
        // isStale() reads the cache DB from OUTSIDE the inner try, so an H2 failure there escapes dispatch.
        // The outcome starts at "error" for exactly this: the meter is the only thing that says the tree did
        // not change.
        MeterRegistry registry = new SimpleMeterRegistry();
        EsqBizMeters.setRegistry(registry);
        when(repo.findChangeNumbers(ENTITY_PK)).thenThrow(new IllegalStateException("cache is closed"));

        try {
            update(7L);
        } catch (RuntimeException expected) {
            // the throw is the point -- dispatch does not swallow it
        }

        assertThat(registry.counter("esq.biz.tree.handler.dispatch.total",
                "event", BusConstants.EVENT_UPDATE, "kind", String.valueOf(ORG_KIND), "outcome", "error")
                .count()).isEqualTo(1.0);
        assertThat(registry.counter("esq.biz.tree.handler.dispatch.total",
                "event", BusConstants.EVENT_UPDATE, "kind", String.valueOf(ORG_KIND), "outcome", "handled")
                .count()).isZero();
    }
}
