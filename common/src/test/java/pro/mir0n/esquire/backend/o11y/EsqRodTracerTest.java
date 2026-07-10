package pro.mir0n.esquire.backend.o11y;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

// Unit coverage for the OTel-backed bus-hop tracer (v1.2.11 O2/T3), driven by a real SDK tracer with a
// capturing span processor (no sdk-testing dependency): send = PRODUCER, receive = CONSUMER, the trace id is
// ALWAYS the correlationId, and the receive span nests under the producer span id carried on the wire.
class EsqRodTracerTest {

    private static final String CORRELATION = "0af7651916cd43dd8448eb211c80319c";
    private static final String WIRE_SPAN = "b7ad6b7169203331";

    private final List<SpanData> exported = new CopyOnWriteArrayList<>();
    private SdkTracerProvider provider;
    private EsqRodTracer rodTracer;

    @BeforeEach
    void setUp() {
        SpanExporter capturing = new SpanExporter() {
            @Override public CompletableResultCode export(Collection<SpanData> spans) {
                exported.addAll(spans);
                return CompletableResultCode.ofSuccess();
            }
            @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        };
        provider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(SimpleSpanProcessor.create(capturing))
                .build();
        Tracer otel = provider.get("test");
        rodTracer = new EsqRodTracer(otel, true);   // alive-trace on: the RR round-trip legs are exercised below
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    private SpanData spanNamed(String name) {
        return exported.stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void outbound_opensProducerSpanAndReturnsTraceparentWithCorrelationIdAsTraceId() {
        // outbound only fires inside a live command span
        Span command = provider.get("test").spanBuilder("command").startSpan();
        String traceparent;
        try (Scope scope = command.makeCurrent()) {
            traceparent = rodTracer.outbound(CORRELATION, "audit-c", "slot-1", "enyman.0");
        }
        command.end();

        SpanData send = spanNamed("send to audit-c");
        assertThat(send.getKind()).isEqualTo(SpanKind.PRODUCER);
        // the wire trace id is the correlationId; the span id is the producer span the consumer nests under
        assertThat(traceparent).isEqualTo("00-" + CORRELATION + "-" + send.getSpanId() + "-01");
        assertThat(send.getAttributes().get(AttributeKey.stringKey("esq.bus.instance"))).isEqualTo("enyman.0");
        assertThat(send.getAttributes().get(AttributeKey.stringKey("esq.bus.slot"))).isEqualTo("slot-1");
    }

    @Test
    void outbound_noCurrentSpan_returnsNullAndOpensNoSpan() {
        String traceparent = rodTracer.outbound(CORRELATION, "audit-c", "slot-1", "enyman.0");

        assertThat(traceparent).isNull();
        assertThat(exported).isEmpty();
    }

    @Test
    void inbound_opensConsumerSpanNestedUnderWireParent_andRunsWorker() {
        String traceparent = "00-" + CORRELATION + "-" + WIRE_SPAN + "-01";
        boolean[] ran = {false};

        rodTracer.inbound(traceparent, CORRELATION, "audit-c", "slot-1", "enyman.0", "aukeep.1", () -> ran[0] = true);

        assertThat(ran[0]).isTrue();
        SpanData receive = spanNamed("receive from audit-c");
        assertThat(receive.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(receive.getTraceId()).isEqualTo(CORRELATION);          // trace id forced to correlationId
        assertThat(receive.getParentSpanId()).isEqualTo(WIRE_SPAN);       // nests under the producer's span id
        assertThat(receive.getAttributes().get(AttributeKey.stringKey("esq.bus.from"))).isEqualTo("enyman.0");
        assertThat(receive.getAttributes().get(AttributeKey.stringKey("esq.bus.instance"))).isEqualTo("aukeep.1");
    }

    @Test
    void inbound_noAnchor_runsWorkerPlainWithoutSpan() {
        boolean[] ran = {false};

        // null traceparent -> nothing to anchor -> worker runs, but no bus span is opened
        rodTracer.inbound(null, CORRELATION, "audit-c", "slot-1", "enyman.0", "aukeep.1", () -> ran[0] = true);

        assertThat(ran[0]).isTrue();
        assertThat(exported).isEmpty();
    }

    @Test
    void spanNames_carryNoInstancePrefix_replicaIsOnTheAttributeInstead() {
        rodTracer.inbound("00-" + CORRELATION + "-" + WIRE_SPAN + "-01",
                CORRELATION, "esquire.entity", "slot-1", "enyman.0", "biztree.1", () -> { });

        // the name is the bus leg only; which replica received is the esq.bus.instance attribute
        SpanData receive = spanNamed("receive from esquire.entity");
        assertThat(receive.getName()).doesNotContain("biztree");
    }

    // --- RR liveness round-trip (msg-bus-alive-trace) ---

    @Test
    void aliveOutbound_asRoot_opensRootProducerNamedByMsgTypeWithCorrelationTraceId() {
        // a client TestRequest minted off the cadence -- NO current span, yet the trace id must be the correlationId
        String traceparent = rodTracer.aliveOutbound(CORRELATION, "esquire.rr", "TestRequest", "client.0", true);

        SpanData tr = spanNamed("TestRequest");
        assertThat(tr.getKind()).isEqualTo(SpanKind.PRODUCER);
        assertThat(tr.getTraceId()).isEqualTo(CORRELATION);                          // forced root trace id
        assertThat(traceparent).isEqualTo("00-" + CORRELATION + "-" + tr.getSpanId() + "-01");
        assertThat(tr.getAttributes().get(AttributeKey.stringKey("esq.bus.id"))).isEqualTo("esquire.rr");
    }

    @Test
    void aliveInbound_opensConsumerNamedByMsgType_nestedUnderWireParent() {
        boolean[] ran = {false};

        rodTracer.aliveInbound("00-" + CORRELATION + "-" + WIRE_SPAN + "-01", CORRELATION, "esquire.rr",
                "receive HeartBeat", "server.0", "client.0", () -> ran[0] = true);

        assertThat(ran[0]).isTrue();
        SpanData recv = spanNamed("receive HeartBeat");
        assertThat(recv.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(recv.getTraceId()).isEqualTo(CORRELATION);
        assertThat(recv.getParentSpanId()).isEqualTo(WIRE_SPAN);
    }

    @Test
    void aliveRoundTrip_isOneTraceOfFourNestedProducerConsumerSpans() {
        // 1) CLIENT sends TestRequest (root)                2) SERVER receives it and, inside that span,
        // 3) sends the HeartBeat reply                      4) CLIENT receives the HeartBeat
        String tpTestReq = rodTracer.aliveOutbound(CORRELATION, "esquire.rr", "TestRequest", "client.0", true);
        String[] tpHeartbeat = new String[1];
        rodTracer.aliveInbound(tpTestReq, CORRELATION, "esquire.rr", "receive TestRequest", "client.0", "server.0",
                () -> tpHeartbeat[0] = rodTracer.aliveOutbound(CORRELATION, "esquire.rr", "HeartBeat", "server.0", false));
        rodTracer.aliveInbound(tpHeartbeat[0], CORRELATION, "esquire.rr", "receive HeartBeat", "server.0", "client.0",
                () -> { });

        SpanData testReq = spanNamed("TestRequest");
        SpanData recvTestReq = spanNamed("receive TestRequest");
        SpanData heartbeat = spanNamed("HeartBeat");
        SpanData recvHeartbeat = spanNamed("receive HeartBeat");

        // all four in ONE trace = correlationId
        assertThat(testReq.getTraceId()).isEqualTo(CORRELATION);
        assertThat(recvTestReq.getTraceId()).isEqualTo(CORRELATION);
        assertThat(heartbeat.getTraceId()).isEqualTo(CORRELATION);
        assertThat(recvHeartbeat.getTraceId()).isEqualTo(CORRELATION);
        // kinds
        assertThat(testReq.getKind()).isEqualTo(SpanKind.PRODUCER);
        assertThat(recvTestReq.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(heartbeat.getKind()).isEqualTo(SpanKind.PRODUCER);
        assertThat(recvHeartbeat.getKind()).isEqualTo(SpanKind.CONSUMER);
        // nesting: TestRequest -> receive TestRequest -> HeartBeat -> receive HeartBeat
        assertThat(recvTestReq.getParentSpanId()).isEqualTo(testReq.getSpanId());
        assertThat(heartbeat.getParentSpanId()).isEqualTo(recvTestReq.getSpanId());
        assertThat(recvHeartbeat.getParentSpanId()).isEqualTo(heartbeat.getSpanId());
    }

    // ---- newTraceId: the tracer owns the trace-id SHAPE, so the bus never mints one ----

    @Test
    void newTraceId_isAFreshW3cShapedId() {
        String a = rodTracer.newTraceId();
        String b = rodTracer.newTraceId();

        assertThat(a).hasSize(32).matches("[0-9a-f]{32}");   // W3C trace id: 32 lowercase hex
        assertThat(a).isNotEqualTo("00000000000000000000000000000000");
        assertThat(a).isNotEqualTo(b);                       // fresh per call -- never a hash of anything
    }

    @Test
    void newTraceId_isUsableAsTheCorrelationIdOfARootAliveSend() {
        String traceId = rodTracer.newTraceId();

        String traceparent = rodTracer.aliveOutbound(traceId, "esquire.kc", "TestRequest", "enyman.0", true);

        assertThat(traceparent).startsWith("00-" + traceId + "-");
        assertThat(spanNamed("TestRequest").getSpanContext().getTraceId()).isEqualTo(traceId);
    }
}
