package pro.mir0n.esquire.common.xrod;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RodRepositoryRegistryTest {

    private static IRodRepository noop() {
        return e -> { };
    }

    @Test
    void register_thenLookupByKind() {
        RodRepositoryRegistry reg = new RodRepositoryRegistry();
        IRodRepository repo = noop();
        reg.register(34, repo);

        assertThat(reg.repositoryFor(34)).isSameAs(repo);
        assertThat(reg.handles(34)).isTrue();
    }

    @Test
    void unregisteredKind_returnsNull_andNotHandled() {
        RodRepositoryRegistry reg = new RodRepositoryRegistry();
        assertThat(reg.repositoryFor(99)).isNull();
        assertThat(reg.handles(99)).isFalse();
    }

    @Test
    void severalKindsCanShareTheSameRepository() {
        RodRepositoryRegistry reg = new RodRepositoryRegistry();
        IRodRepository personLog = noop();
        reg.register(992, personLog);
        reg.register(994, personLog);
        reg.register(996, personLog);

        assertThat(reg.repositoryFor(992)).isSameAs(personLog);
        assertThat(reg.repositoryFor(994)).isSameAs(personLog);
        assertThat(reg.repositoryFor(996)).isSameAs(personLog);
    }

    @Test
    void register_overwritesPreviousForSameKind() {
        RodRepositoryRegistry reg = new RodRepositoryRegistry();
        IRodRepository first = noop();
        IRodRepository second = noop();
        reg.register(34, first);
        reg.register(34, second);

        assertThat(reg.repositoryFor(34)).isSameAs(second);
    }
}
