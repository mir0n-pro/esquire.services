package pro.mir0n.esquire.kcMaster.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-tests the x-Rod-fronted KcResponsePublisher: it builds a reply {@link RodEvent} (msg-type URS / URR on
 * the event) and hands it to the transmit leg ({@code rod.transmit}). The {@link XRodManager} hands back a
 * capturing {@link IXRod}, so only the reply-building contract is exercised.
 */
class KcResponsePublisherTest {

    private KcResponsePublisher publisher;
    private RodEvent lastEvent;

    @BeforeEach
    void setUp() {
        XRodManager rods = mock(XRodManager.class);
        IXRod rod = mock(IXRod.class);
        when(rods.producer(any(), any())).thenReturn(rod);
        doAnswer(inv -> { lastEvent = inv.getArgument(0); return null; }).when(rod).transmit(any());
        publisher = new KcResponsePublisher(rods);
    }

    // --- publishSuccess ---

    @Test
    @DisplayName("publishSuccess: msg-type is URS")
    void publishSuccess_msgTypeIsUrs() {
        publisher.publishSuccess("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE,
                "ctrl1", "rid1", "cid1");
        assertThat(lastEvent.msgType()).isEqualTo(EsqMsgConstants.MSG_TYPE_RESPONSE);
    }

    @Test
    @DisplayName("publishSuccess: op echoed from command, kind and entityId forwarded")
    void publishSuccess_headerEchoed() {
        publisher.publishSuccess("entity-42", 34, EsqMsgConstants.EVENT_UPDATE, "ctrl1", "rid1", "cid1");
        assertThat(lastEvent.opCode()).isEqualTo(EsqMsgConstants.EVENT_UPDATE);
        assertThat(lastEvent.kind()).isEqualTo(34);
        assertThat(lastEvent.entityId()).isEqualTo("entity-42");
    }

    @Test
    @DisplayName("publishSuccess: requester rod-id echoed onto the event for reply routing")
    void publishSuccess_rodIdEchoed() {
        publisher.publishSuccess("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE,
                "my-ctrl", "rid1", "cid1");
        assertThat(lastEvent.rodId()).isEqualTo("my-ctrl");
    }

    @Test
    @DisplayName("publishSuccess: requestId and correlationId forwarded; body empty")
    void publishSuccess_traceForwardedBodyEmpty() {
        publisher.publishSuccess("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE,
                "ctrl1", "my-rid", "my-cid");
        assertThat(lastEvent.requestId()).isEqualTo("my-rid");
        assertThat(lastEvent.correlationId()).isEqualTo("my-cid");
        assertThat(lastEvent.body()).isEmpty();
    }

    // --- publishFailure ---

    @Test
    @DisplayName("publishFailure: msg-type is URR")
    void publishFailure_msgTypeIsUrr() {
        publisher.publishFailure("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE,
                "KC_SYNC_ERROR", "boom", "ctrl1", "rid1", "cid1", null);
        assertThat(lastEvent.msgType()).isEqualTo(EsqMsgConstants.MSG_TYPE_REJECT);
    }

    @Test
    @DisplayName("publishFailure: body.error is an RFC-9457 problem with title, detail, status, type")
    @SuppressWarnings("unchecked")
    void publishFailure_errorIsRfc9457() {
        publisher.publishFailure("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE,
                "KC_SYNC_ERROR", "user not found", "ctrl1", "rid1", "cid1", null);
        Map<String, Object> error = (Map<String, Object>) lastEvent.body().get("error");
        assertThat(error.get("title")).isEqualTo("KC_SYNC_ERROR");
        assertThat(error.get("detail")).isEqualTo("user not found");
        assertThat(error.get("status")).isEqualTo(500);
        assertThat(error.get("type")).isEqualTo("about:blank");
    }

    @Test
    @DisplayName("publishFailure: requester rod-id echoed onto the event")
    void publishFailure_rodIdEchoed() {
        publisher.publishFailure("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE,
                "KC_SYNC_ERROR", "boom", "my-ctrl", "rid1", "cid1", null);
        assertThat(lastEvent.rodId()).isEqualTo("my-ctrl");
    }

    @Test
    @DisplayName("publishFailure: the original request is echoed under body.request when provided")
    void publishFailure_requestEchoedWhenProvided() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("id", "uid-001");
        req.put("kind", 34);
        publisher.publishFailure("eid1", 34, EsqMsgConstants.EVENT_CREATE,
                "KC_SYNC_ERROR", "boom", "ctrl1", "rid1", "cid1", req);
        assertThat(lastEvent.body().get("request")).isEqualTo(req);
    }

    @Test
    @DisplayName("publishFailure: body.request absent when request is null")
    void publishFailure_requestAbsentWhenNull() {
        publisher.publishFailure("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE,
                "KC_SYNC_ERROR", "boom", "ctrl1", "rid1", "cid1", null);
        assertThat(lastEvent.body()).doesNotContainKey("request");
    }
}
