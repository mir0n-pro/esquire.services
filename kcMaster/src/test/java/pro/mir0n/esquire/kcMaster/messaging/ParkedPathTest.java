/*
 *  Esquire frameworks (tm)
 *  kcMaster service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.kcMaster.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import pro.mir0n.utils.concurrent.ExpiringCache;

import static org.assertj.core.api.Assertions.assertThat;

class ParkedPathTest {

    private static ExpiringCache<String, ParkedPath> park() {
        return new ExpiringCache<>(LoggerFactory.getLogger(ParkedPathTest.class), 60_000L);
    }

    private static boolean store(ExpiringCache<String, ParkedPath> c, String path, Long changeNo) {
        return c.storeIfGreater("15", new ParkedPath(path, changeNo));
    }

    @Test
    @DisplayName("the newer move wins, whichever order the two arrive in")
    void newerWins_eitherArrivalOrder() {
        ExpiringCache<String, ParkedPath> forward = park();
        store(forward, "1.2.", 4L);
        store(forward, "1.3.", 7L);
        assertThat(forward.consume("15").path()).isEqualTo("1.3.");

        ExpiringCache<String, ParkedPath> reversed = park();
        store(reversed, "1.3.", 7L);            // the newer one arrives FIRST
        store(reversed, "1.2.", 4L);            // the older one arrives LAST -- and must lose
        assertThat(reversed.consume("15").path()).isEqualTo("1.3.");
    }

    @Test
    @DisplayName("an ABSENT number never displaces a numbered path")
    void absentNumber_losesToNumbered() {
        // A producer that sends no number tells us nothing about order. Letting it win would reinstate
        // last-arrival-wins on exactly the path we cannot check.
        ExpiringCache<String, ParkedPath> c = park();
        store(c, "1.3.", 7L);
        assertThat(store(c, "1.9.", null)).isFalse();
        assertThat(c.consume("15").path()).isEqualTo("1.3.");
    }

    @Test
    @DisplayName("an absent number still parks when nothing is parked yet")
    void absentNumber_stillParksIntoAnEmptySlot() {
        // Better a path with no order than no path at all: the race-8c net must still work against a
        // producer that predates the change number.
        ExpiringCache<String, ParkedPath> c = park();
        assertThat(store(c, "1.9.", null)).isTrue();
        assertThat(c.consume("15").path()).isEqualTo("1.9.");
    }

    @Test
    @DisplayName("a numbered path DOES displace a parked one that has no number")
    void numbered_beatsAbsent() {
        ExpiringCache<String, ParkedPath> c = park();
        store(c, "1.9.", null);
        assertThat(store(c, "1.3.", 7L)).isTrue();
        assertThat(c.consume("15").path()).isEqualTo("1.3.");
    }

    @Test
    @DisplayName("the same move delivered twice changes nothing")
    void sameNumberTwice_isANoOp() {
        ExpiringCache<String, ParkedPath> c = park();
        store(c, "1.3.", 7L);
        assertThat(store(c, "1.3.", 7L)).isFalse();
        assertThat(c.consume("15").path()).isEqualTo("1.3.");
    }

    @Test
    @DisplayName("the park is per entity -- one entity's move does not touch another's")
    void perEntity() {
        ExpiringCache<String, ParkedPath> c = park();
        c.storeIfGreater("15", new ParkedPath("1.3.", 7L));
        c.storeIfGreater("16", new ParkedPath("1.4.", 2L));
        assertThat(c.consume("15").path()).isEqualTo("1.3.");
        assertThat(c.consume("16").path()).isEqualTo("1.4.");
    }
}
