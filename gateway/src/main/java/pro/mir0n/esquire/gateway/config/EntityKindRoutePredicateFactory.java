/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 04/07/2026 mir0n  created: custom route predicate factory — routes by entity kind check (isAcct/isOrg/isUsr)
 */
package pro.mir0n.esquire.gateway.config;

import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Custom route predicate factory that tests the {@code kind} query parameter against
 * a named entity-kind check from {@link EsqObjectKindStorage}.
 *
 * <p>YAML usage: {@code - EntityKind=isAcct}
 *
 * <p>Supported checks: {@code isAcct}, {@code isOrg}, {@code isUsr}.
 * To add a new check, add an entry to {@link #CHECKS}.
 */
@Component
public class EntityKindRoutePredicateFactory
        extends AbstractRoutePredicateFactory<EntityKindRoutePredicateFactory.Config> {

    private static final Map<String, Predicate<EsqObjectKind>> CHECKS = Map.of(
        "isAcct", EsqObjectKind::isAcct,
        "isOrg",  EsqObjectKind::isOrg,
        "isUsr",  EsqObjectKind::isUsr
    );

    public EntityKindRoutePredicateFactory() {
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("check");
    }

    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        Predicate<EsqObjectKind> kindCheck = CHECKS.get(config.getCheck());
        return exchange -> {
            boolean ret = false;
            String kindParam = exchange.getRequest().getQueryParams().getFirst("kind");
            if (kindParam != null && kindCheck != null) {
                try {
                    int kindId = Integer.parseInt(kindParam);
                    ret = kindCheck.test(EsqObjectKindStorage.getInstance().get(kindId));
                } catch (NumberFormatException ignored) {}
            }
            return ret;
        };
    }

    public static class Config {
        private String check;
        public String getCheck() { return check; }
        public void setCheck(String check) { this.check = check; }
    }
}
