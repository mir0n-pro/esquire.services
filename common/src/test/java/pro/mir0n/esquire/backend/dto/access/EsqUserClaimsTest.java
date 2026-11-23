package pro.mir0n.esquire.backend.dto.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import pro.mir0n.utils.concurrent.ExpiringCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EsqUserClaimsTest {

    private static EsqUserClaims claims(String uid, String rootPath, long changeNo) {
        return new EsqUserClaims("x", uid, rootPath, List.of("TREE"), changeNo);
    }

    @Test
    @DisplayName("same user: the higher change number is the greater one")
    void sameUser_ordersByChangeNo() {
        assertThat(claims("5", "1.", 3L).compareTo(claims("5", "1.", 2L))).isPositive();
        assertThat(claims("5", "1.", 2L).compareTo(claims("5", "1.", 3L))).isNegative();
        assertThat(claims("5", "1.", 2L).compareTo(claims("5", "1.2.", 2L))).isZero();
    }

    @Test
    @DisplayName("another user: always greater, whatever the change numbers")
    void otherUser_alwaysGreater() {
        assertThat(claims("7", "1.", 1L).compareTo(claims("5", "1.", 57L))).isPositive();
        assertThat(claims("5", "1.", 57L).compareTo(claims("7", "1.", 1L))).isPositive();
    }

    @Test
    @DisplayName("cache: another user's answer under the same login id replaces the parked one")
    void cache_otherUserReplaces() {
        ExpiringCache<String, EsqUserClaims> cache =
                new ExpiringCache<>(LoggerFactory.getLogger(EsqUserClaimsTest.class), 60_000L);
        EsqUserClaims renamedAway = claims("5", "1.", 57L);
        EsqUserClaims newOwner = claims("7", "1.3.", 1L);

        cache.putIfGreater("x", renamedAway);
        assertThat(cache.putIfGreater("x", newOwner)).isTrue();
        assertThat(cache.get("x")).isEqualTo(newOwner);
    }

    @Test
    @DisplayName("cache: an older answer of the same user does not replace the newer one")
    void cache_sameUserOlderKept() {
        ExpiringCache<String, EsqUserClaims> cache =
                new ExpiringCache<>(LoggerFactory.getLogger(EsqUserClaimsTest.class), 60_000L);
        EsqUserClaims newer = claims("5", "1.", 3L);

        cache.putIfGreater("x", newer);
        assertThat(cache.putIfGreater("x", claims("5", "1.", 2L))).isFalse();
        assertThat(cache.get("x")).isEqualTo(newer);
    }
}
