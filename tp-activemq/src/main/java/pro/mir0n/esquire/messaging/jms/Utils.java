/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/21/2026 mir0n  created: JMS message formatting utilities (setProps + formatProps overloads); formatProps
 *                   sorts keys alphabetically for consistent log output.
 */
package pro.mir0n.esquire.messaging.jms;

import jakarta.jms.JMSException;
import jakarta.jms.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JMS message formatting utilities shared across all messaging components.
 */
public class Utils {
    private Utils() {}

    /**
     * Sets all entries from a props map as JMS message properties.
     * Integer values are set via {@code setIntProperty}; all others via {@code setStringProperty}.
     */
    public static void setProps(Message msg, Map<String, Object> props) throws JMSException {
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (entry.getValue() instanceof Integer) {
                msg.setIntProperty(entry.getKey(), (Integer) entry.getValue());
            } else {
                msg.setStringProperty(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
            }
        }
    }

    /**
     * Formats a props map (used by publishers) as {@code key=value | key=value}.
     * Keys are sorted alphabetically — same order as {@link #formatProps(Message)}.
     */
    public static String formatProps(Map<String, Object> props) {
        List<String> keys = new ArrayList<>(props.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(key).append('=').append(props.get(key));
        }
        return sb.toString();
    }

    /**
     * Formats all JMS message properties (used by listeners) as {@code key=value | key=value}.
     * Properties are sorted alphabetically.
     */
    public static String formatProps(Message msg) throws JMSException {
        List<String> names = new ArrayList<>();
        java.util.Enumeration<?> e = msg.getPropertyNames();
        while (e.hasMoreElements()) names.add(e.nextElement().toString());
        Collections.sort(names);
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(name).append('=').append(msg.getObjectProperty(name));
        }
        return sb.toString();
    }
}
