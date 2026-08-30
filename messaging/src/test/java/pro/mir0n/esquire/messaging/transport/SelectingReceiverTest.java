package pro.mir0n.esquire.messaging.transport;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests for the subscription filter -- what a broker would do with a message selector. Written against
 *  the grammar, not against one caller's selector, since every consumer brings its own. */
class SelectingReceiverTest {

    private final List<TransportMessage> taken = new ArrayList<>();
    private final Consumer<TransportMessage> sink = taken::add;

    private static TransportMessage message(String... fieldsAndValues) {
        Map<String, Object> headers = new LinkedHashMap<>();
        for (int i = 0; i + 1 < fieldsAndValues.length; i += 2) {
            headers.put(fieldsAndValues[i], fieldsAndValues[i + 1]);
        }
        return new TransportMessage(headers, null);
    }

    private int keptOf(String selector, TransportMessage... messages) {
        taken.clear();
        Consumer<TransportMessage> receiver = SelectingReceiver.wrap(sink, selector);
        for (TransportMessage message : messages) {
            receiver.accept(message);
        }
        return taken.size();
    }

    @Test
    void aLegWithoutASubscriptionKeepsTheHandlerItWasGiven() {
        assertSame(sink, SelectingReceiver.wrap(sink, null));
        assertSame(sink, SelectingReceiver.wrap(sink, "   "));
    }

    @Test
    void equalityKeepsOnlyTheNamedValue() {
        assertEquals(1, keptOf("EventType = 'I'",
                message("EventType", "I"), message("EventType", "D")));
    }

    @Test
    void inKeepsAnyOfTheListedValues() {
        assertEquals(2, keptOf("EventType IN ('I','X')",
                message("EventType", "I"), message("EventType", "X"), message("EventType", "D")));
    }

    @Test
    void notInKeepsEverythingElse() {
        assertEquals(1, keptOf("EventType NOT IN ('I','X')",
                message("EventType", "I"), message("EventType", "X"), message("EventType", "D")));
    }

    @Test
    void bothSpellingsOfNotEqualAreUnderstood() {
        assertEquals(1, keptOf("EventType <> 'I'", message("EventType", "I"), message("EventType", "D")));
        assertEquals(1, keptOf("EventType != 'I'", message("EventType", "I"), message("EventType", "D")));
    }

    @Test
    void everyConditionOfAnAndMustHold() {
        String selector = "EventType = 'I' AND EntityKind IN ('20','34')";
        assertEquals(1, keptOf(selector,
                message("EventType", "I", "EntityKind", "20"),     // both hold -- kept
                message("EventType", "I", "EntityKind", "99"),     // wrong kind
                message("EventType", "D", "EntityKind", "20")));   // wrong op
    }

    @Test
    void anAbsentFieldIsNotAMatchAndIsAMatchWhenNegated() {
        assertEquals(0, keptOf("EventType = 'I'", message("Other", "I")));
        assertEquals(1, keptOf("EventType <> 'I'", message("Other", "I")));
    }

    /** A value may contain the letters of a keyword. Cutting the selector on them would quietly change what the
     *  leg asks for. */
    @Test
    void aKeywordInsideAValueDoesNotSplitTheSelector() {
        assertEquals(1, keptOf("Text = 'salt AND pepper'",
                message("Text", "salt AND pepper"), message("Text", "salt")));
        assertEquals(1, keptOf("Text = 'gin OR tonic'",
                message("Text", "gin OR tonic"), message("Text", "gin")));
    }

    @Test
    void aFieldWhoseNameContainsTheKeywordIsNotMistakenForIt() {
        assertEquals(1, keptOf("Instance = 'a'", message("Instance", "a"), message("Instance", "b")));
    }

    /** A selector this transport cannot evaluate must stop the leg from opening. Quietly taking everything
     *  would turn a narrowing into a no-op that nothing reports. */
    @Test
    void whatItCannotReadIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SelectingReceiver.wrap(sink, "EntityKind > 20"));
        assertThrows(IllegalArgumentException.class, () -> SelectingReceiver.wrap(sink, "EventType LIKE 'I%'"));
        assertThrows(IllegalArgumentException.class, () -> SelectingReceiver.wrap(sink, " = 'I'"));
        assertThrows(IllegalArgumentException.class, () -> SelectingReceiver.wrap(sink, "EventType = I"));
        assertThrows(IllegalArgumentException.class,
                () -> SelectingReceiver.wrap(sink, "EventType = 'I' OR EventType = 'D'"));
    }
}
