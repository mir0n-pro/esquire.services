/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import pro.mir0n.esquire.messaging.catalog.BusRef;
import pro.mir0n.esquire.messaging.catalog.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The BusRef bind the facade relies on: a mistyped {@code role} fails fast; a valid one binds to the enum. */
class BusRefBindTest {

    private static MockEnvironment refEnv(String roleValue) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("esquire.kc-bus.messaging-bus.bus-id", "esquire.kc");
        env.setProperty("esquire.kc-bus.messaging-bus.slot-id", "kc");
        env.setProperty("esquire.kc-bus.messaging-bus.role", roleValue);
        return env;
    }

    private static BusRef bind(MockEnvironment env) {
        return Binder.get(env).bind("esquire.kc-bus.messaging-bus", Bindable.of(BusRef.class)).orElse(null);
    }

    @Test
    void mistypedRole_failsFast() {
        // a role value that is not a Role enum constant -> the bind throws (NOT a silent fall-through to no-role).
        assertThatThrownBy(() -> bind(refEnv("CLEINT")))   // typo of CLIENT
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("role");
    }

    @Test
    void validRole_binds() {
        BusRef ref = bind(refEnv("CLIENT"));
        assertThat(ref).isNotNull();
        assertThat(ref.role()).isEqualTo(Role.CLIENT);
        assertThat(ref.busId()).isEqualTo("esquire.kc");
        assertThat(ref.slotId()).isEqualTo("kc");
    }

    @Test
    void lowercaseRole_binds() {
        // relaxed binding: a lowercase role value in yaml (role: server) binds to the enum.
        assertThat(bind(refEnv("server")).role()).isEqualTo(Role.SERVER);
        assertThat(bind(refEnv("client")).role()).isEqualTo(Role.CLIENT);
    }
}
