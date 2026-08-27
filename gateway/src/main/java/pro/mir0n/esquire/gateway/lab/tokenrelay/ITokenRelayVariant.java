/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/15/2026 mir0n  created: public SPI for Token Relay variants. Each variant inspects
 *                   the inbound request and returns a VariantAction (Pass / Reject /
 *                   Relay); the shared TokenRelayFilter does cache + KC call + header
 *                   rewrite once. Adding a new variant later (e.g. mTLS at edge, DPoP)
 *                   is one new class implementing this interface.
 */
package pro.mir0n.esquire.gateway.lab.tokenrelay;

import org.springframework.web.server.ServerWebExchange;

/**
 * A Token Relay variant. Examines an inbound request and produces a
 * {@link VariantAction}:
 *
 *   - Pass   -- this variant doesn't claim this request (the filter tries
 *               the next variant in the list).
 *   - Reject -- this variant claims but refuses (e.g. allowlist mismatch,
 *               malformed credential, wrong wire shape). Filter returns 401.
 *   - Relay  -- this variant claims and provides everything the filter
 *               needs to fetch a JWT: a cache key (so HITs skip KC) and
 *               a {@link KcTokenRequest} (the form + auth for KC's
 *               {@code /token} endpoint).
 *
 * Variants only do synchronous examination and decision-making. Cache
 * lookup, KC call execution, response header rewrite, and chain
 * dispatch live in the shared {@link TokenRelayFilter} +
 * {@link TokenRelayCache} + {@link ITokenRelayClient}.
 */
public interface ITokenRelayVariant {

    VariantAction examine(ServerWebExchange exchange);
}
