/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/15/2026 mir0n  created: JWT plus its cache expiry instant; lets TokenRelayCache do
 *                   HIT-vs-MISS decisions on isBefore() alone without reparsing exp.
 */
package pro.mir0n.esquire.gateway.lab.tokenrelay;

import java.time.Instant;

/**
 * Pair of (JWT compact string, cache expiry instant).
 * {@code expiresAt} is computed at acquisition time as
 * {@code now + KC expires_in - safety_buffer}, so the cache only needs
 * to compare against {@code Instant.now()}.
 */
public record ExpiringJwt(String jwt, Instant expiresAt) {}
