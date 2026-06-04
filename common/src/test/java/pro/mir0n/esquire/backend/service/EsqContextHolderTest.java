package pro.mir0n.esquire.backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EsqContextHolderTest {

    @AfterEach
    void tearDown() {
        EsqContextHolder.clear();
    }

    @Test
    void get_returnsNull_whenNothingSet() {
        assertThat(EsqContextHolder.get()).isNull();
    }

    @Test
    void setThenGet_returnsSameContext_withFieldsIntact() {
        EsqRequestContext ctx = new EsqRequestContext("corr", "req", "uid-9", "1.2.");
        EsqContextHolder.set(ctx);

        assertThat(EsqContextHolder.get()).isSameAs(ctx);
        assertThat(EsqContextHolder.get().correlationId()).isEqualTo("corr");
        assertThat(EsqContextHolder.get().requestId()).isEqualTo("req");
        assertThat(EsqContextHolder.get().uid()).isEqualTo("uid-9");
        assertThat(EsqContextHolder.get().rootPath()).isEqualTo("1.2.");
    }

    @Test
    void clear_removesContext() {
        EsqContextHolder.set(new EsqRequestContext("c", "r", "u", "p"));
        EsqContextHolder.clear();
        assertThat(EsqContextHolder.get()).isNull();
    }

    @Test
    void context_isThreadConfined() throws InterruptedException {
        EsqContextHolder.set(new EsqRequestContext("c", "r", "u", "p"));

        // A different thread must not see this thread's context (ThreadLocal isolation).
        EsqRequestContext[] seenInOtherThread = { new EsqRequestContext("x", "x", "x", "x") };
        Thread t = new Thread(() -> seenInOtherThread[0] = EsqContextHolder.get());
        t.start();
        t.join();

        assertThat(seenInOtherThread[0]).isNull();
        assertThat(EsqContextHolder.get()).isNotNull();
    }
}
