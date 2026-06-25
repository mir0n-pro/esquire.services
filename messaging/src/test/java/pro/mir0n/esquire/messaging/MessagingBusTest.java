package pro.mir0n.esquire.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Facade lifecycle + the #17 fail-fast contract. Driven on a FRESH facade ({@code new MessagingBus()}, the
 * package-private test hook) rather than the {@link MessagingBus#getInstance() singleton}, so each test is
 * isolated. The buses here resolve to {@code rod-class = XRodDisabled} (a no-op rod) -- the facade builds, inits,
 * and starts them with NO broker, which is exactly the disabled-bus path under test.
 */
class MessagingBusTest {

    /** An env declaring one bus the service uses ({@code esquire.<key>.messaging-bus}) whose catalog slot is an
     *  explicit XRodDisabled. roleAndSlot pick whether the ref carries a role / the catalog defines the leg. */
    private static MockEnvironment env(String key, String busId, String slotId, String role, boolean defineLeg) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.application.name", "test-svc");
        // the service-level ref: which bus this service uses, and as what role.
        env.setProperty("esquire." + key + ".messaging-bus.bus-id", busId);
        env.setProperty("esquire." + key + ".messaging-bus.slot-id", slotId);
        if (role != null) {
            env.setProperty("esquire." + key + ".messaging-bus.role", role);
        }
        // the catalog leg (the topology) -- an explicit disabled slot, so build/init/start need no broker.
        if (defineLeg) {
            env.setProperty("esquire.messaging-bus[0].bus-id", busId);
            env.setProperty("esquire.messaging-bus[0].slots[0].slot-id", slotId);
            env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.rod-class", "XRodDisabled");
        }
        return env;
    }

    @Test
    void getXRod_throwsForABusTheServiceNeverDeclared() {
        // #17: asking for a bus that was never built is a wiring bug -> hard fail, NOT a silent OFF no-op.
        MessagingBus bus = new MessagingBus();
        bus.init(env("audit", "audit-x", "audit", "SERVER", true));

        assertThatThrownBy(() -> bus.getXRod("never-declared"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT built");
    }

    @Test
    void anExplicitlyDisabledBusBuildsAndReturns_isNotEnabled() {
        // #17: to run WITHOUT a bus you declare it disabled (rod-class XRodDisabled). It is then BUILT (in the map)
        // and returns normally -- disabled, but never absent.
        MessagingBus bus = new MessagingBus();
        bus.init(env("audit", "audit-x", "audit", "SERVER", true));

        IXRod rod = bus.getXRod("audit");
        assertThat(rod).isNotNull();
        assertThat(rod.isEnabled()).isFalse();
    }

    @Test
    void init_explicitForm_throwsWhenANamedBusSlotIsUndefined() {
        // a named bus whose topology slot is missing is a boot failure -- the service declared it uses a bus the
        // topology does not define.
        MessagingBus bus = new MessagingBus();
        MockEnvironment env = env("audit", "audit-x", "audit", "SERVER", false);   // no catalog leg

        assertThatThrownBy(() -> bus.init(env, new String[]{"audit"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no leg defines it");
    }

    @Test
    void init_explicitForm_throwsWhenANamedBusDeclaresNoRole() {
        // a bus a service uses MUST declare its role.
        MessagingBus bus = new MessagingBus();
        MockEnvironment env = env("audit", "audit-x", "audit", null, true);   // ref has bus-id/slot-id but no role

        assertThatThrownBy(() -> bus.init(env, new String[]{"audit"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no role declared");
    }

    @Test
    void init_scanForm_buildsEveryRoleDeclaredBus_thenStartCloseAreClean() {
        // the scan form discovers the buses from config (esquire.<key>.messaging-bus.role); the full lifecycle
        // (init -> start -> close) runs without a broker on a disabled bus.
        MessagingBus bus = new MessagingBus();
        bus.init(env("audit", "audit-x", "audit", "SERVER", true));

        assertThat(bus.getXRod("audit")).isNotNull();
        assertThatCode(() -> {
            bus.start();
            bus.close();
        }).doesNotThrowAnyException();
    }

    @Test
    void init_throwsForAnUnknownRodClass() {
        // a leg whose rod-class is not on the classpath fails fast at build -- the FQCN is reflectively resolved,
        // so a typo / missing class is a boot failure, not a silent skip.
        MessagingBus bus = new MessagingBus();
        MockEnvironment env = env("audit", "audit-x", "audit", "SERVER", true);
        env.setProperty("esquire.messaging-bus[0].slots[0].x-rod.rod-class", "NoSuchRodClass");

        assertThatThrownBy(() -> bus.init(env, new String[]{"audit"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("classpath");
    }
}
