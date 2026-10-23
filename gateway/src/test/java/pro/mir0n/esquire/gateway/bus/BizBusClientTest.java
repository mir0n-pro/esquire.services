package pro.mir0n.esquire.gateway.bus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.messaging.BusConstants;
import pro.mir0n.messaging.IXRod;
import pro.mir0n.messaging.RodEvent;
import pro.mir0n.messaging.transport.TransportHealth;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.ObjectProvider;

class BizBusClientTest {

    private IXRod rod;

    @BeforeEach
    void setUp() {
        rod = mock(IXRod.class);
        when(rod.health()).thenReturn(TransportHealth.UP);
    }

    private BizBusClient client(long timeoutSeconds, int maxPending) {
        BizBusClient ret = new BizBusClient(timeoutSeconds, maxPending, 500, mock(ObjectProvider.class));
        ret.attach(rod);
        return ret;
    }

    private static RodEvent command(String correlationId) {
        Map<String, Object> body = new HashMap<>();
        body.put(EsqConstants.BIZ_KEY_OPERATION, EsqConstants.BIZ_OP_ACCT);
        return new RodEvent(null, 52, "a1", null, null, 0L, correlationId, "r1", "u1", null,
                BusConstants.MSG_TYPE_REQUEST, body);
    }

    private static RodEvent answer(String correlationId) {
        Map<String, Object> body = new HashMap<>();
        body.put(EsqConstants.BIZ_KEY_STATUS, 200);
        return new RodEvent(null, 52, "a1", null, null, 0L, correlationId, "r1", null, "gateway.0",
                BusConstants.MSG_TYPE_RESPONSE, body);
    }

    @Test
    @DisplayName("the answer with the command's correlation id completes that command, and the id leaves the map")
    void answerCompletesItsCommand() {
        BizBusClient client = client(30, 10);
        Mono<RodEvent> pending = client.send(command("r1"), null);
        verify(rod).transmit(any(RodEvent.class));
        assertEquals(1, client.pendingCount());

        client.onAnswer(answer("r1"));

        RodEvent got = pending.block(Duration.ofSeconds(2));
        assertNotNull(got);
        assertEquals(200, got.body().get(EsqConstants.BIZ_KEY_STATUS));
        assertEquals(0, client.pendingCount());
    }

    @Test
    @DisplayName("a command with no answer by its deadline fails with a timeout when the sweep runs")
    void sweepTimesOut() throws InterruptedException {
        BizBusClient client = client(0, 10);
        Mono<RodEvent> pending = client.send(command("r1"), null);
        Thread.sleep(5);

        client.sweep();

        Exception e = assertThrows(Exception.class, () -> pending.block(Duration.ofSeconds(2)));
        assertInstanceOf(TimeoutException.class, e.getCause());
        assertEquals(0, client.pendingCount());
    }

    @Test
    @DisplayName("an answer after the deadline is dropped")
    void lateAnswerDropped() {
        BizBusClient client = client(30, 10);
        assertDoesNotThrow(() -> client.onAnswer(answer("r9")));
        assertDoesNotThrow(() -> client.onAnswer(answer(null)));
    }

    @Test
    @DisplayName("the leg down is a 503 at once, nothing sent")
    void legDown() {
        when(rod.health()).thenReturn(TransportHealth.DOWN);
        BizBusClient client = client(30, 10);
        clearInvocations(rod);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> client.send(command("r1"), null).block(Duration.ofSeconds(2)));
        assertEquals(503, e.getStatusCode().value());
        verify(rod, never()).transmit(any(RodEvent.class));
    }

    @Test
    @DisplayName("a full map is a 503")
    void mapFull() {
        BizBusClient client = client(30, 1);
        client.send(command("r1"), null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> client.send(command("r2"), null).block(Duration.ofSeconds(2)));
        assertEquals(503, e.getStatusCode().value());
    }

    @Test
    @DisplayName("a correlation id that is already waiting is a 409, and the waiting one keeps its place")
    void duplicateRequestId() {
        BizBusClient client = client(30, 10);
        Mono<RodEvent> first = client.send(command("r1"), null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> client.send(command("r1"), null).block(Duration.ofSeconds(2)));
        assertEquals(409, e.getStatusCode().value());
        assertEquals(1, client.pendingCount());

        client.onAnswer(answer("r1"));
        assertNotNull(first.block(Duration.ofSeconds(2)));
    }

    @Test
    @DisplayName("a send the rod refuses is a 503, and the correlation id leaves the map")
    void sendRefused() {
        BizBusClient client = client(30, 10);
        doThrow(new IllegalStateException("rig shut down")).when(rod).transmit(any(RodEvent.class));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> client.send(command("r1"), null).block(Duration.ofSeconds(2)));
        assertEquals(503, e.getStatusCode().value());
        assertEquals(0, client.pendingCount());
    }
}
