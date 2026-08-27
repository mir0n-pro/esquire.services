/*
 *  Esquire frameworks (tm)
 *  messaging framework
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/26/2026 mir0n  created: encode no longer swallows -- the throw reaches the feed rig and onError records it
 */
package pro.mir0n.esquire.messaging.xrod;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.messaging.RodEvent;
import pro.mir0n.esquire.messaging.catalog.Role;
import pro.mir0n.esquire.messaging.catalog.XRodParams;
import pro.mir0n.esquire.messaging.o11y.IRodMeters;
import pro.mir0n.esquire.messaging.o11y.IRodObserver;
import pro.mir0n.esquire.messaging.o11y.IRodTracer;
import pro.mir0n.esquire.messaging.o11y.RodObserverHolder;
import pro.mir0n.esquire.messaging.xrod.impl.XRod;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An encode failure was the one send failure that left no trace: encode caught, returned null, wrote a warn,
 * and sendOut skipped on the null. It now throws, the feed rig catches it, and AXRod.onError records it the
 * same way a dispatch failure is recorded -- once.
 */
class XRodSendFailureTest {

    private static final String ENCODE_FAILS = "pro.mir0n.esquire.messaging.EncodeFailingTransportProvider";

    /** Records the error leg the rod reports; everything else is a no-op. */
    private static final class CapturingMeters implements IRodMeters {
        final List<String> errors = new CopyOnWriteArrayList<>();
        final List<String> sent   = new CopyOnWriteArrayList<>();

        @Override public void sent(String busId, String slotId, String msgType) {
            sent.add(msgType);
        }
        @Override public void sendDuration(String busId, String slotId, String msgType, long nanos) { }
        @Override public void received(String busId, String slotId, String msgType) { }
        @Override public void error(String busId, String slotId, String msgType, String leg) {
            errors.add(leg);
        }
        @Override public void retryBackoff(String busId, long backoffMs) { }
        @Override public void retryDropped(String busId, String msgType) { }
        @Override public void registerFeedDepth(String busId, String slotId, IntSupplier depth) { }
        @Override public void registerRetryHeld(String busId, String slotId, IntSupplier held) { }
        @Override public void registerTransportUp(String busId, IntSupplier up) { }
    }

    private static XRodParams encodeFailingLeg() {
        return XRodParams.from(Map.of("transport", Map.of(
                "provider", ENCODE_FAILS, "endpoint", "tcp://localhost:61616", "destination", "test.q")))
                .withBus("test-bus", "test-slot", "test.0");
    }

    private static RodEvent event() {
        return new RodEvent(RodEvent.Op.CREATE, 20, "42", null, null, System.currentTimeMillis(),
                "cid-1", "rid-1", null, "test.0", "EVENT", Map.of("name", "jdoe"));
    }

    @Test
    @DisplayName("encode throws -> the feed's onError records ONE send error; the event is not silently dropped")
    void encodeFailure_isRecordedOnce() throws Exception {
        CapturingMeters meters = new CapturingMeters();
        XRod rod = new XRod();
        XRodParams p = encodeFailingLeg();
        rod.validate(p);
        rod.configure(p, Role.SERVER, new ObjectMapper());

        try {
            RodObserverHolder.setObserver(IRodObserver.of(IRodTracer.NOOP, meters));
            rod.init("test-tx", null);
            rod.start();

            rod.transmit(event());

            long deadline = System.currentTimeMillis() + 2000;
            while (meters.errors.isEmpty() && System.currentTimeMillis() < deadline) {
                TimeUnit.MILLISECONDS.sleep(5);
            }

            assertThat(meters.errors)
                    .as("the encode failure reached the rig's error listener")
                    .containsExactly("send");
            assertThat(meters.sent)
                    .as("nothing landed -- a failed encode must not count as sent")
                    .isEmpty();

            rod.shutdown();
        } finally {
            RodObserverHolder.setObserver(null);
        }
    }

    @Test
    @DisplayName("the failure is recorded ONCE -- sendInProcess stopped rethrowing after onSendError")
    void sendFailure_isNotCountedTwice() throws Exception {
        CapturingMeters meters = new CapturingMeters();
        XRod rod = new XRod();
        XRodParams p = encodeFailingLeg();
        rod.validate(p);
        rod.configure(p, Role.SERVER, new ObjectMapper());

        try {
            RodObserverHolder.setObserver(IRodObserver.of(IRodTracer.NOOP, meters));
            rod.init("test-tx", null);
            rod.start();

            rod.transmit(event());
            TimeUnit.MILLISECONDS.sleep(400);

            assertThat(meters.errors)
                    .as("one failure, one record -- the double count was onSendError plus a rethrow to the rig")
                    .hasSize(1);

            rod.shutdown();
        } finally {
            RodObserverHolder.setObserver(null);
        }
    }
}
