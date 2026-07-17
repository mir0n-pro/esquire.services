package pro.mir0n.esquire.backend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.messaging.RodEvent;

import java.util.Map;

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

    // ---- MDC side of the holder (I10): set() stamps, applyMessage() stamps MDC-only, clear() removes both ----

    @Test
    void set_alsoStampsCorrelationAndRequestIntoMdc() {
        // set() is the PRIORITY entry point -- binding the context must ALSO carry its ids into MDC, so a worker
        // that called set() needs no separate applyMessage() for its log lines to be correlated.
        EsqContextHolder.set(new EsqRequestContext("corr", "req", "uid-9", "1.2."));

        assertThat(MDC.get(EsqConstants.PD_CORRELATION_ID)).isEqualTo("corr");
        assertThat(MDC.get(EsqConstants.PD_REQUEST_ID)).isEqualTo("req");
    }

    @Test
    void setNull_leavesMdcUntouched() {
        // set(null) binds no context and must not stamp MDC (the null-guard) -- a public/unauthenticated path.
        EsqContextHolder.set(null);

        assertThat(MDC.get(EsqConstants.PD_CORRELATION_ID)).isNull();
        assertThat(MDC.get(EsqConstants.PD_REQUEST_ID)).isNull();
        assertThat(EsqContextHolder.get()).isNull();
    }

    @Test
    void applyMessageIds_stampsMdcOnly_withoutBindingContext() {
        // applyMessage() is for a worker with NO full context to set (a bus listener whose RodEvent has no
        // rootPath): it stamps the MDC ids but leaves the context thread-local untouched.
        EsqContextHolder.applyMessage("req-m", "corr-m");

        assertThat(MDC.get(EsqConstants.PD_CORRELATION_ID)).isEqualTo("corr-m");
        assertThat(MDC.get(EsqConstants.PD_REQUEST_ID)).isEqualTo("req-m");
        assertThat(EsqContextHolder.get()).as("applyMessage must NOT bind a context").isNull();
    }

    @Test
    void applyMessageRodEvent_stampsMdcFromTheEvent() {
        RodEvent event = new RodEvent(RodEvent.Op.UPDATE, 2, "e1", null, 0L, "corr-ev", "req-ev", "uid", Map.of());
        EsqContextHolder.applyMessage(event);

        assertThat(MDC.get(EsqConstants.PD_CORRELATION_ID)).isEqualTo("corr-ev");
        assertThat(MDC.get(EsqConstants.PD_REQUEST_ID)).isEqualTo("req-ev");
    }

    @Test
    void applyMessage_nullIds_stampNothing() {
        // A message may carry no ids; the null-guards must leave MDC empty rather than putting a literal "null".
        EsqContextHolder.applyMessage(null, null);

        assertThat(MDC.get(EsqConstants.PD_CORRELATION_ID)).isNull();
        assertThat(MDC.get(EsqConstants.PD_REQUEST_ID)).isNull();
    }

    @Test
    void clear_removesMdcIds_andContext() {
        // clear() must clear the WHOLE thread state -- MDC ids AND the context -- so a pooled broker/servlet
        // thread never leaks one message's identity into the next.
        EsqContextHolder.set(new EsqRequestContext("c", "r", "u", "p"));
        assertThat(MDC.get(EsqConstants.PD_CORRELATION_ID)).isEqualTo("c");

        EsqContextHolder.clear();

        assertThat(MDC.get(EsqConstants.PD_CORRELATION_ID)).isNull();
        assertThat(MDC.get(EsqConstants.PD_REQUEST_ID)).isNull();
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
