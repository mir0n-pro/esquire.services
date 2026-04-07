package pro.mir0n.esquire.gateway.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class EntityKindRoutePredicateFactoryTest {

    private final EntityKindRoutePredicateFactory factory = new EntityKindRoutePredicateFactory();

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage.getInstance().init(
            new EsqObjectKind(10, "org", "Org", "orgs", "",
                true, false, false, "", false, false, "", null, null, null, false)
        );
        EsqObjectKindStorage.getInstance().init(
            new EsqObjectKind(50, "cl_acct", "Client Account", "cl_accts", "",
                false, false, true, "", false, false, "", null, null, null, false)
        );
    }

    // ---- helpers ----

    private Predicate<ServerWebExchange> predicate(String check) {
        EntityKindRoutePredicateFactory.Config config = new EntityKindRoutePredicateFactory.Config();
        config.setCheck(check);
        return factory.apply(config);
    }

    private ServerWebExchange exchangeWithKind(String kind) {
        return MockServerWebExchange.from(
            MockServerHttpRequest.get("/esq-cmd-new").queryParam("kind", kind).build()
        );
    }

    private ServerWebExchange exchangeWithoutKind() {
        return MockServerWebExchange.from(
            MockServerHttpRequest.get("/esq-cmd-new").build()
        );
    }

    // ---- isAcct ----

    @Test
    void isAcct_acctKind_returnsTrue() {
        assertThat(predicate("isAcct").test(exchangeWithKind("50"))).isTrue();
    }

    @Test
    void isAcct_orgKind_returnsFalse() {
        assertThat(predicate("isAcct").test(exchangeWithKind("10"))).isFalse();
    }

    @Test
    void isAcct_unknownKind_returnsFalse() {
        assertThat(predicate("isAcct").test(exchangeWithKind("999"))).isFalse();
    }

    @Test
    void isAcct_missingKindParam_returnsFalse() {
        assertThat(predicate("isAcct").test(exchangeWithoutKind())).isFalse();
    }

    @Test
    void isAcct_nonNumericKind_returnsFalse() {
        assertThat(predicate("isAcct").test(exchangeWithKind("abc"))).isFalse();
    }

    // ---- unknown check ----

    @Test
    void unknownCheck_returnsFalse() {
        assertThat(predicate("isAdmin").test(exchangeWithKind("50"))).isFalse();
    }
}
