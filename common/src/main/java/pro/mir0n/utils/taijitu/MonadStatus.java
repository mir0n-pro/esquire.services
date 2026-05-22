/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: monad lifecycle status (generalized from bizTree.taijitu).
 * 05/22/2026 mir0n  each constant carries a String code; added code() accessor (stable string form).
 */
package pro.mir0n.utils.taijitu;

/**
 * Lifecycle status of a {@link AMonadY}.
 *
 *   IDLE    -- created / cleared; not serving.
 *   LOADING -- a LOAD command is building the cache.
 *   LOADED  -- cache built; reads served, buffered events drained.
 *   FAILED  -- a LOAD failed; not serving.
 */
public enum MonadStatus {
    IDLE   ("IDLE"),
    LOADING("LOADING"),
    LOADED ("LOADED"),
    FAILED ("FAILED");

    private final String code;

    MonadStatus(String code) {
        this.code = code;
    }

    /** Stable dictionary string form, e.g. for a doCommand result. */
    public String code() {
        return code;
    }
}
