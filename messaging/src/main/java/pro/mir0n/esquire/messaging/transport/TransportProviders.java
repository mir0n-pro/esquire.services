/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/15/2026 mir0n  created: the class-name-driven resolver for ITransportProvider -- a config provider value
 *                   (a bare name -> pro.mir0n.esquire.tp.<name>.TransportProvider by convention, or a full class
 *                   name verbatim) is reflectively instantiated (no-arg) and cached; paramKey() yields its
 *                   param-group name. A new transport plugs in by jar-on-classpath + config, zero framework change.
 * 06/17/2026 mir0n  paramKey() removed (a vestige of the old per-provider param-group design)
 */
package pro.mir0n.esquire.messaging.transport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves an {@link ITransportProvider} from its config value -- a convention name OR a full class name. */
public final class TransportProviders {

    /** The convention: provider-name {@code x} -> class {@code pro.mir0n.esquire.tp.x.TransportProvider}. */
    public static final String PACKAGE_PREFIX = "pro.mir0n.esquire.tp.";
    public static final String CLASS_SUFFIX   = ".TransportProvider";

    private static final Map<String, ITransportProvider> CACHE = new ConcurrentHashMap<>();

    private TransportProviders() {
    }

    /** The class name the config value resolves to: a value WITH a dot is a full class name (used verbatim);
     *  a bare name follows the convention pro.mir0n.esquire.tp.&lt;name&gt;.TransportProvider. */
    public static String classNameFor(String provider) {
        String p = provider.trim();
        return p.indexOf('.') >= 0 ? p : PACKAGE_PREFIX + p.toLowerCase() + CLASS_SUFFIX;
    }

    /** Resolve (and cache) the provider for {@code provider} (convention name or full class name). Throws with
     *  a clear message if the class is absent from the classpath or is not an {@link ITransportProvider}. */
    public static ITransportProvider resolve(String provider) {
        return CACHE.computeIfAbsent(classNameFor(provider), TransportProviders::instantiate);
    }

    private static ITransportProvider instantiate(String fqcn) {
        ITransportProvider ret;
        try {
            Object o = Class.forName(fqcn).getDeclaredConstructor().newInstance();
            if (!(o instanceof ITransportProvider p)) {
                throw new IllegalStateException(fqcn + " does not implement ITransportProvider");
            }
            ret = p;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("no transport provider class " + fqcn + " on the classpath", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate transport provider " + fqcn, e);
        }
        return ret;
    }
}
