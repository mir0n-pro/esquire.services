/*
 *  Esquire frameworks (tm)
 *  messaging library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/29/2026 mir0n  created: the subscription filter -- what a broker does with a message selector, applied in
 *                   code for a transport whose vendor has none. A general filter over the header bag: any
 *                   field, = and <> and !=, IN and NOT IN, joined by AND. A selector it cannot read is
 *                   REFUSED when the leg opens, never treated as take-everything.
 */
package pro.mir0n.esquire.messaging.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The leg's subscription, applied on this side: only messages the selector asks for reach the rod.
 *
 * <p>A broker would do this with a message selector. SQS has none, and an SNS subscription is an address
 * rather than a consumer, so there is nowhere to hang one, and
 * the leg reads its own predicate off the header bag. The grammar is the useful part of a message selector, not
 * one caller's shape:
 *
 * <pre>
 *   FIELD = 'v'                 FIELD &lt;&gt; 'v'          FIELD != 'v'
 *   FIELD IN ('a','b')          FIELD NOT IN ('a','b')
 *   any of the above, joined by AND
 * </pre>
 *
 * <p>Anything outside it is REFUSED when the leg opens, never ignored. A selector silently treated as "take
 * everything" turns a narrowing into a no-op that nothing reports, which is the worst way for a filter to fail.
 */
public final class SelectingReceiver implements Consumer<TransportMessage> {

    private static final Logger devLog =
            LoggerFactory.getLogger("develop.pro.mir0n.esquire.messaging.transport.SelectingReceiver");

    private final Consumer<TransportMessage> handler;
    private final List<Condition> conditions;

    private SelectingReceiver(Consumer<TransportMessage> handler, List<Condition> conditions) {
        this.handler    = handler;
        this.conditions = conditions;
    }

    /** {@code handler} as it is when the leg has no subscription, and wrapped when it has one. The selector is
     *  read HERE, at open, so a shape this transport cannot evaluate stops the leg instead of passing traffic
     *  it was told to hold back. */
    public static Consumer<TransportMessage> wrap(Consumer<TransportMessage> handler, String selector) {
        Consumer<TransportMessage> ret = handler;
        if (selector != null && !selector.isBlank()) {
            List<Condition> conditions = parse(selector);
            ret = new SelectingReceiver(handler, conditions);
            devLog.info("tp-sqns: subscription filter on this leg: {}", selector.trim());
        }
        return ret;
    }

    @Override
    public void accept(TransportMessage message) {
        Map<String, Object> headers = message.headers();
        boolean wanted = true;
        for (Condition condition : conditions) {
            if (!condition.matches(headers)) {
                wanted = false;
            }
        }
        if (wanted) {
            handler.accept(message);
        } else {
            devLog.debug("tp-sqns: dropping a message the subscription did not ask for");
        }
    }

    /** The selector as the conditions it is made of. Every condition must hold, which is what AND means. */
    static List<Condition> parse(String selector) {
        List<Condition> ret = new ArrayList<>();
        List<String> parts = splitOn(selector, "AND");
        for (String part : parts) {
            if (splitOn(part, "OR").size() > 1) {
                throw new IllegalArgumentException("tp-sqns: OR is not supported in a subscription selector ["
                        + selector + "]");
            }
            ret.add(condition(part, selector));
        }
        return ret;
    }

    /** One condition out of one part. The operator is looked for longest-first, so NOT IN is not read as IN and
     *  {@code <>} is not read as the start of something else. */
    private static Condition condition(String part, String whole) {
        String upper = part.toUpperCase(Locale.ROOT);
        Condition ret;
        int at = upper.indexOf(" NOT IN");
        if (at >= 0) {
            ret = new Condition(fieldBefore(part, at, whole), values(part, whole), true);
        } else {
            at = indexOfWord(upper, " IN");
            if (at >= 0) {
                ret = new Condition(fieldBefore(part, at, whole), values(part, whole), false);
            } else {
                at = upper.indexOf("<>");
                if (at < 0) {
                    at = upper.indexOf("!=");
                }
                if (at >= 0) {
                    ret = new Condition(fieldBefore(part, at, whole), values(part, whole), true);
                } else {
                    at = upper.indexOf('=');
                    if (at < 0) {
                        throw new IllegalArgumentException("tp-sqns: cannot read [" + part.trim()
                                + "] in the subscription selector [" + whole
                                + "]; this transport understands = <> != IN and NOT IN, joined by AND");
                    }
                    ret = new Condition(fieldBefore(part, at, whole), values(part, whole), false);
                }
            }
        }
        return ret;
    }

    private static String fieldBefore(String part, int at, String whole) {
        String ret = part.substring(0, at).trim();
        if (ret.isEmpty()) {
            throw new IllegalArgumentException("tp-sqns: the subscription selector [" + whole + "] names no field");
        }
        return ret;
    }

    /** Every quoted value in the part: the one value of a comparison, or the list of an IN. */
    private static Set<String> values(String part, String whole) {
        Set<String> ret = new LinkedHashSet<>();
        int i = part.indexOf('\'');
        while (i >= 0) {
            int end = part.indexOf('\'', i + 1);
            if (end < 0) {
                throw new IllegalArgumentException("tp-sqns: unclosed quote in the subscription selector ["
                        + whole + "]");
            }
            ret.add(part.substring(i + 1, end));
            i = part.indexOf('\'', end + 1);
        }
        if (ret.isEmpty()) {
            throw new IllegalArgumentException("tp-sqns: [" + part.trim() + "] in the subscription selector ["
                    + whole + "] carries no quoted value");
        }
        return ret;
    }

    /** Split on {@code keyword} where it stands as a word and OUTSIDE quotes -- a value may contain the letters
     *  of a keyword and must not be cut on them. */
    static List<String> splitOn(String selector, String keyword) {
        List<String> ret = new ArrayList<>();
        String upper = selector.toUpperCase(Locale.ROOT);
        boolean quoted = false;
        int from = 0;
        int i = 0;
        while (i < selector.length()) {
            char c = selector.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
                i++;
            } else if (!quoted && startsWord(upper, i, keyword)) {
                ret.add(selector.substring(from, i));
                i += keyword.length() + 1;
                from = i;
            } else {
                i++;
            }
        }
        ret.add(selector.substring(from));
        return ret;
    }

    /** Whether {@code keyword} stands at {@code i} as a whole word, with a space on each side. */
    private static boolean startsWord(String upper, int i, String keyword) {
        boolean ret = false;
        int end = i + keyword.length() + 1;
        if (i > 0 && end <= upper.length() && upper.charAt(i) == ' '
                && upper.startsWith(keyword, i + 1)) {
            char after = end < upper.length() ? upper.charAt(end) : ' ';
            ret = after == ' ' || after == '(';
        }
        return ret;
    }

    /** Where a keyword stands as a word; -1 when it does not. Used for IN, which must not match INSIDE a name. */
    private static int indexOfWord(String upper, String keyword) {
        int ret = -1;
        int at = upper.indexOf(keyword);
        while (at >= 0 && ret < 0) {
            int end = at + keyword.length();
            char after = end < upper.length() ? upper.charAt(end) : ' ';
            if (after == ' ' || after == '(') {
                ret = at;
            } else {
                at = upper.indexOf(keyword, at + 1);
            }
        }
        return ret;
    }

    /** One predicate on one header field: its value must be among {@code values}, or must not be when negated. */
    record Condition(String field, Set<String> values, boolean negated) {

        boolean matches(Map<String, Object> headers) {
            Object raw = headers.get(field);
            boolean among = raw != null && values.contains(raw.toString());
            return negated != among;
        }
    }
}
