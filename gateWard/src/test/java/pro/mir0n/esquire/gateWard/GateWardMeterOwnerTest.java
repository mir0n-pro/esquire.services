package pro.mir0n.esquire.gateWard;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Whether a meter is the gate's or the cache's, in the one process that runs both. The last case is the one
// worth guarding: the JVM, the connection pool and the Netty server are shared by both halves, so they must
// answer null and stay tagged with the process.
class GateWardMeterOwnerTest {

    private static final MeterRegistry REGISTRY = new SimpleMeterRegistry();
    private static final GateWardMeterOwner OWNER = new GateWardMeterOwner("esquire.entity");

    private static Meter.Id id(String name, String... tags) {
        return REGISTRY.counter(name, tags).getId();
    }

    @Test
    void theGatesOwnWorkIsTheGateways() {
        assertThat(OWNER.ownerOf(id("esq.gw.outer", "route", "biztree-local"))).isEqualTo("gateway");
        assertThat(OWNER.ownerOf(id("esq.gw.inner", "route", "enyman-route"))).isEqualTo("gateway");
        assertThat(OWNER.ownerOf(id("esq.biz.gw.tokenrelay.total"))).isEqualTo("gateway");
        assertThat(OWNER.ownerOf(id("spring.cloud.gateway.requests"))).isEqualTo("gateway");
        assertThat(OWNER.ownerOf(id("resilience4j.circuitbreaker.state"))).isEqualTo("gateway");
    }

    @Test
    void aProxiedRequestIsTheGateways() {
        assertThat(OWNER.ownerOf(id("http.server.requests", "uri", "UNKNOWN"))).isEqualTo("gateway");
    }

    @Test
    void theCachesOwnWorkIsBizTrees() {
        assertThat(OWNER.ownerOf(id("esq.biz.tree.rebuild.total"))).isEqualTo("biztree");
        assertThat(OWNER.ownerOf(id("esq.biz.tree.handler.dispatch.total"))).isEqualTo("biztree");
        assertThat(OWNER.ownerOf(id("esq.srv.outer", "route", "biztree-local"))).isEqualTo("biztree");
        assertThat(OWNER.ownerOf(id("esq.srv.inner", "route", "biztree-local"))).isEqualTo("biztree");
        assertThat(OWNER.ownerOf(id("esq.svc.tree"))).isEqualTo("biztree");
        assertThat(OWNER.ownerOf(id("spring.data.repository.invocations"))).isEqualTo("biztree");
    }

    @Test
    void aLocallyAnsweredRouteIsBizTrees() {
        assertThat(OWNER.ownerOf(id("http.server.requests", "uri", "/esq-tree"))).isEqualTo("biztree");
        assertThat(OWNER.ownerOf(id("http.server.requests", "uri", "/esq-path"))).isEqualTo("biztree");
        assertThat(OWNER.ownerOf(id("http.server.requests", "uri", "/esq"))).isEqualTo("biztree");
    }

    @Test
    void theEntityBusIsTheCachesLeg() {
        assertThat(OWNER.ownerOf(id("messaging.receive.total", "bus-id", "esquire.entity"))).isEqualTo("biztree");
    }

    @Test
    void whatTheTwoHalvesShareIsNotAttributed() {
        assertThat(OWNER.ownerOf(id("jvm.memory.used"))).isNull();
        assertThat(OWNER.ownerOf(id("hikaricp.connections"))).isNull();
        assertThat(OWNER.ownerOf(id("reactor.netty.http.server.data.sent.bytes"))).isNull();
    }

    // The edge server's meters carry a coarse uri, so without the process-owned rule a cache route would
    // credit the whole edge to bizTree. Seen live on docker compact before the rule existed.
    @Test
    void theEdgeServerStaysWithTheProcessEvenOnACacheRoute() {
        assertThat(OWNER.ownerOf(id("reactor.netty.http.server.data.sent.bytes", "uri", "/esq-tree"))).isNull();
        assertThat(OWNER.ownerOf(id("reactor.netty.http.server.response.time", "uri", "/esq"))).isNull();
    }

    // The gate's OUTBOUND leg is tagged with the downstream uri, which must never be read as a local route.
    @Test
    void theOutboundClientLegIsTheGateways() {
        assertThat(OWNER.ownerOf(id("http.client.requests", "uri", "/esq"))).isEqualTo("gateway");
    }
}
