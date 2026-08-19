package pro.mir0n.esquire.mesnie;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Which of the three a meter belongs to, in the one process that runs all three. The third case is the one
// worth guarding: a meter there is only ONE of -- the JVM, the pool, the audit keep, the shared permission
// check -- must answer null, so it stays tagged with the process instead of being credited to a service that
// does not own it.
class MesnieMeterOwnerTest {

    private static final MeterRegistry REGISTRY = new SimpleMeterRegistry();
    private static final MesnieMeterOwner OWNER = new MesnieMeterOwner("esquire.entity");

    private static Meter.Id id(String name, String... tags) {
        return REGISTRY.counter(name, tags).getId();
    }

    @Test
    void aBusinessMeterIsOwnedByTheServiceThatEmitsIt() {
        assertThat(OWNER.ownerOf(id("esq.biz.entity.ops.total"))).isEqualTo("enyman");
        assertThat(OWNER.ownerOf(id("esq.biz.dict.lookup.total"))).isEqualTo("enyman");
        assertThat(OWNER.ownerOf(id("esq.biz.move.processed.total"))).isEqualTo("enyman");
        assertThat(OWNER.ownerOf(id("esq.biz.key.ops.total"))).isEqualTo("keysmith");
        assertThat(OWNER.ownerOf(id("esq.biz.key.identity.total"))).isEqualTo("keysmith");
        assertThat(OWNER.ownerOf(id("esq.biz.kc.sync.total"))).isEqualTo("kcmaster");
    }

    @Test
    void aServiceMarkIsOwnedByTheServiceItMarks() {
        assertThat(OWNER.ownerOf(id("esq.svc.create"))).isEqualTo("enyman");
        assertThat(OWNER.ownerOf(id("esq.svc.tree"))).isEqualTo("enyman");
        assertThat(OWNER.ownerOf(id("esq.svc.key.read"))).isEqualTo("keysmith");
        assertThat(OWNER.ownerOf(id("esq.svc.key.save"))).isEqualTo("keysmith");
    }

    @Test
    void aRequestIsOwnedByTheServiceThatServesTheRoute() {
        assertThat(OWNER.ownerOf(id("http.server.requests", "uri", "/esq-cmd-save"))).isEqualTo("enyman");
        assertThat(OWNER.ownerOf(id("http.server.requests", "uri", "/esq-key"))).isEqualTo("keysmith");
        assertThat(OWNER.ownerOf(id("esq.srv.outer", "route", "/esq-move"))).isEqualTo("enyman");
        assertThat(OWNER.ownerOf(id("esq.srv.inner", "route", "/esq-key-save"))).isEqualTo("keysmith");
    }

    @Test
    void theEntityBusIsEnyMansLeg() {
        assertThat(OWNER.ownerOf(id("messaging.send.total", "bus-id", "esquire.entity"))).isEqualTo("enyman");
    }

    @Test
    void whatTheHouseholdSharesIsNotAttributed() {
        assertThat(OWNER.ownerOf(id("jvm.memory.used"))).isNull();
        assertThat(OWNER.ownerOf(id("hikaricp.connections"))).isNull();
        assertThat(OWNER.ownerOf(id("esq.biz.perm.check.total"))).isNull();
        assertThat(OWNER.ownerOf(id("esq.biz.keep.write.total"))).isNull();
        assertThat(OWNER.ownerOf(id("messaging.send.total", "bus-id", "audit-b"))).isNull();
        assertThat(OWNER.ownerOf(id("http.server.requests", "uri", "/actuator/health"))).isNull();
    }
}
