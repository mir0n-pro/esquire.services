package pro.mir0n.esquire.enyMan.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.common.EsqMsgConstants;
import pro.mir0n.esquire.messaging.xrod.IXRod;
import pro.mir0n.esquire.messaging.xrod.RodEvent;
import pro.mir0n.esquire.messaging.xrod.XRodManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-tests the x-Rod-fronted KcRequestPublisher: it builds an EVENT_UPDATE_PATH {@link RodEvent} (msg-type
 * URQ) and hands it to the transmit leg ({@code rod.transmit}). The {@link XRodManager} hands back a capturing
 * {@link IXRod}, so only the event-building contract is exercised here.
 */
class KcRequestPublisherTest {

    private KcRequestPublisher publisher;
    private IXRod rod;
    private final List<RodEvent> sent = new ArrayList<>();

    @BeforeEach
    void setUp() {
        XRodManager rods = mock(XRodManager.class);
        rod = mock(IXRod.class);
        when(rods.producer(any(), any())).thenReturn(rod);
        doAnswer(inv -> { sent.add(inv.getArgument(0)); return null; }).when(rod).transmit(any());
        publisher = new KcRequestPublisher(rods);
    }

    private RodEvent only() {
        assertThat(sent).hasSize(1);
        return sent.get(0);
    }

    @Test
    @DisplayName("publishPathUpdate: emits one RodEvent")
    void publishPathUpdate_emitsOneEvent() {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "rid1", "cid1");
        assertThat(sent).hasSize(1);
    }

    @Test
    @DisplayName("publishPathUpdate: op=UPDATE_PATH, msg-type URQ, kind and entityId set")
    void publishPathUpdate_headerCorrect() {
        publisher.publishPathUpdate("uid-99", 20, "1.10.uid-99.", "rid1", "cid1");
        RodEvent e = only();
        assertThat(e.op()).isEqualTo(RodEvent.Op.UPDATE_PATH);
        assertThat(e.opCode()).isEqualTo(EsqMsgConstants.EVENT_UPDATE_PATH);
        assertThat(e.msgType()).isEqualTo(EsqMsgConstants.MSG_TYPE_REQUEST);
        assertThat(e.kind()).isEqualTo(20);
        assertThat(e.entityId()).isEqualTo("uid-99");
    }

    @Test
    @DisplayName("publishPathUpdate: requestId and correlationId propagated")
    void publishPathUpdate_traceContextPropagated() {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "my-rid", "my-cid");
        RodEvent e = only();
        assertThat(e.requestId()).isEqualTo("my-rid");
        assertThat(e.correlationId()).isEqualTo("my-cid");
    }

    @Test
    @DisplayName("publishPathUpdate: requestId generated when null (the former testReqId)")
    void publishPathUpdate_requestIdGeneratedWhenNull() {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", null, "cid1");
        RodEvent e = only();
        assertThat(e.requestId()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("publishPathUpdate: rod-id is null on the event (the leg's rod-id is used on the wire)")
    void publishPathUpdate_rodIdNullOnEvent() {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "rid1", "cid1");
        assertThat(only().rodId()).isNull();
    }

    @Test
    @DisplayName("publishPathUpdate: body carries id, kind and path")
    void publishPathUpdate_bodyHasIdKindPath() {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "rid1", "cid1");
        RodEvent e = only();
        assertThat(e.body()).containsKeys("id", "kind", "path");
        assertThat(e.body().get("id")).isEqualTo("uid-1");
        assertThat(e.body().get("kind")).isEqualTo(20);
        assertThat(e.body().get("path")).isEqualTo("1.10.uid-1.");
    }

    @Test
    @DisplayName("publishPathUpdate: a transmit failure does not propagate")
    void publishPathUpdate_sinkExceptionAbsorbed() {
        doThrow(new RuntimeException("broker down")).when(rod).transmit(any());
        assertThatNoException().isThrownBy(() ->
                publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "rid1", "cid1"));
    }
}
