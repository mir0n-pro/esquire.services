/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/13/2026 mir0n  created: the class-name-driven resolver for x-Rod pods (mirrors transport.TransportProviders).
 *                   The x-rod.rod-class config value -- a bare name -> pro.mir0n.esquire.messaging.xrod.<name>, or a
 *                   full class name -- selects the IXRod implementation; default "XRod". A fresh instance is
 *                   created per resolve (pods are stateful: each is configured + started by its owner).
 */
package pro.mir0n.esquire.messaging.xrod;

/** Resolves an {@link IXRod} x-rod from its config value -- a convention name OR a full class name. */
public final class
XRods {

    /** The convention: name {@code x} -> class {@code pro.mir0n.esquire.messaging.xrod.x}. */
    public static final String PACKAGE_PREFIX = "pro.mir0n.esquire.messaging.xrod.impl.";
    /** The default x-rod when a leg EXISTS but names no rod-class: the full transceiver {@link XRod}. */
    public static final String DEFAULT = "XRod";
    /** The OFF x-rod -- selected when a bus key resolves to NO leg, or set explicitly to disable a slot. */
    public static final String DISABLED = "XRodDisabled";

    private XRods() {
    }

    /** The class name a config value resolves to: a value WITH a dot is a full class name (verbatim); a bare
     *  name follows the convention; blank -> the default {@link #DEFAULT}. */
    public static String classNameFor(String rodClass) {
        String p = (rodClass == null || rodClass.isBlank()) ? DEFAULT : rodClass.trim();
        return p.indexOf('.') >= 0 ? p : PACKAGE_PREFIX + p;
    }

    /** Create a fresh x-rod for {@code rodClass}. Throws with a clear message if the class is absent or is not
     *  an {@link IXRod}. */
    public static IXRod resolve(String rodClass) {
        String fqcn = classNameFor(rodClass);
        IXRod ret;
        try {
            Object o = Class.forName(fqcn).getDeclaredConstructor().newInstance();
            if (!(o instanceof IXRod p)) {
                throw new IllegalStateException(fqcn + " does not implement IXRod");
            }
            ret = p;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("no x-rod class " + fqcn + " on the classpath", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate x-rod " + fqcn, e);
        }
        return ret;
    }
}
