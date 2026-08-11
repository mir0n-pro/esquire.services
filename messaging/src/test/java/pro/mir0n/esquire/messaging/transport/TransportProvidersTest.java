/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.messaging.transport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransportProvidersTest {

    @Test
    void bareNameFollowsTheConvention() {
        assertThat(TransportProviders.classNameFor("redis"))
                .isEqualTo("pro.mir0n.esquire.tp.redis.TransportProvider");
        assertThat(TransportProviders.classNameFor("  ACTIVEMQ "))   // trimmed + lower-cased
                .isEqualTo("pro.mir0n.esquire.tp.activemq.TransportProvider");
    }

    @Test
    void aValueWithADotIsAFullClassNameVerbatim() {
        assertThat(TransportProviders.classNameFor("com.acme.MyTransportProvider"))
                .isEqualTo("com.acme.MyTransportProvider");
    }
}
