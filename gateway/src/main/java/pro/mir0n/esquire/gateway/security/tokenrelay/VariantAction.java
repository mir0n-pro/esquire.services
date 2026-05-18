/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/15/2026 mir0n  created: sealed return type from ITokenRelayVariant.examine() --
 *                   exactly three outcomes (Pass / Reject / Relay) for the filter to
 *                   branch on.
 */
package pro.mir0n.esquire.gateway.security.tokenrelay;

/**
 * The decision a variant returns from {@link ITokenRelayVariant#examine}.
 * Sealed: exactly three outcomes.
 *
 *   - {@code Pass}   -- variant doesn't claim this request; filter tries the next.
 *   - {@code Reject} -- variant claims but refuses with a reason; filter returns 401.
 *   - {@code Relay}  -- variant claims and provides a cache key + KC request shape;
 *                       filter does cache lookup / KC call / header rewrite and continues.
 */
public sealed interface VariantAction
        permits VariantAction.Pass,
                VariantAction.Reject,
                VariantAction.Relay {

    record Pass() implements VariantAction {}

    record Reject(String reason) implements VariantAction {}

    record Relay(String cacheKey, KcTokenRequest kcRequest) implements VariantAction {}
}
