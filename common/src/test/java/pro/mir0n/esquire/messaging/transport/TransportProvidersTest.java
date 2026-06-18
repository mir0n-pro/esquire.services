/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/13/2026 mir0n  created: the class-name-driven resolver -- a bare name follows the package convention, a
 *                   value with a dot is a full class name (verbatim); paramKey() yields the param-group name
 *                   for either form.
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
