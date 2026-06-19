package pro.mir0n.esquire.messaging.xrod;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RodEventRepoRegistryTest {

    private static IRodEventRepo noop() {
        return e -> { };
    }

    @Test
    void register_thenLookupByKind() {
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        IRodEventRepo repo = noop();
        reg.register(34, repo);

        assertThat(reg.repositoryFor(34)).isSameAs(repo);
        assertThat(reg.handles(34)).isTrue();
    }

    @Test
    void unregisteredKind_returnsNull_andNotHandled() {
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        assertThat(reg.repositoryFor(99)).isNull();
        assertThat(reg.handles(99)).isFalse();
    }

    @Test
    void severalKindsCanShareTheSameRepository() {
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        IRodEventRepo personLog = noop();
        reg.register(992, personLog);
        reg.register(994, personLog);
        reg.register(996, personLog);

        assertThat(reg.repositoryFor(992)).isSameAs(personLog);
        assertThat(reg.repositoryFor(994)).isSameAs(personLog);
        assertThat(reg.repositoryFor(996)).isSameAs(personLog);
    }

    @Test
    void register_overwritesPreviousForSameKind() {
        RodEventRepoRegistry reg = new RodEventRepoRegistry();
        IRodEventRepo first = noop();
        IRodEventRepo second = noop();
        reg.register(34, first);
        reg.register(34, second);

        assertThat(reg.repositoryFor(34)).isSameAs(second);
    }
}
