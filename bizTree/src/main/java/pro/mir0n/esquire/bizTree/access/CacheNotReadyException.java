/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/19/2026 mir0n  created: explicit top-level exception (v1.2.5 Taijitu refactor Step 2).
 *                   Thrown by a director when a read arrives before the cache is ready
 *                   to serve (e.g. monad not yet LOADED). Shared across director impls.
 */
package pro.mir0n.esquire.bizTree.access;

/**
 * Thrown by an {@link IBizTreeDirector} when a read arrives before the cache
 * is ready to serve -- i.e. bootstrap has not finished loading, or a load
 * failed. Lets the caller distinguish "not ready yet" from "no such data".
 */
public class CacheNotReadyException extends RuntimeException {

    public CacheNotReadyException(String detail) {
        super("bizTree cache not ready (" + detail + ")");
    }
}
