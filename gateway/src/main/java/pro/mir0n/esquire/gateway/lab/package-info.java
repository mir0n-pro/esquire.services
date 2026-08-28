/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */

/**
 * LAB code. Nothing in this package is a supported Esquire path, and none of it runs in the cloud.
 *
 * <p>Four token shapes were tried against KeyCloak. Three of them are here; the fourth is what Esquire uses:
 *
 * <ul>
 *   <li><b>JWE</b> ({@code JweAwareJwtDecoder}, {@code JwksController}) -- decrypt a JWE, validate the inner
 *       JWS, and serve the gate's public key at {@code GET /jwe-jwks} so KeyCloak can encrypt to it. Abandoned
 *       for a reason outside Esquire: KeyCloak 26 does not emit JWE on {@code /token}. Armed but inert.</li>
 *   <li><b>Vanilla token relay</b> and <b>Phantom token relay</b> ({@code tokenrelay}) -- JWE-workaround
 *       patterns: broker or exchange a downstream token at the gate. Both work; neither is recommended.</li>
 *   <li><b>BFF + plain JWT</b> -- the recommended production path, and the one in use. It needs none of the
 *       above, which is why the above stayed here.</li>
 * </ul>
 *
 * <p><b>Off where it would matter: the cloud.</b> The relay allowlists are empty in every CHART default, so a
 * bare install is off, and empty in the OKE overlay ({@code k8s-oci-compact/values/gateward.yaml}) -- the
 * internet-facing gate never brokers or exchanges a relay token. They ARE armed on the three lab targets, for
 * hauberk load runs: both docker stacks, and local k8s via {@code k8s-compact/values/gateward.yaml}.
 *
 * <p>The OKE exclusion is deliberate and was tightened after audit F0 finding A1: {@code esq-hauberk-S}
 * resolves to the mainadmin identity, so leaving the lab relay live on the public API put a full-admin token
 * one warm cache away. The e2e suite mirrors the split -- {@code 20-token-relay.spec.ts} runs on the lab
 * targets and skips on OKE via {@code RELAY_DISABLED=true}.
 *
 * <p><b>Known and accepted limits, recorded on {@code TokenRelayCache}:</b> entries are never evicted on expiry,
 * and a cache HIT returns the stored JWT without re-checking the caller's credential. Both are prerequisites to
 * fix before any of this could be taken to production -- they are limits of a lab path, not open defects.
 *
 * <p>Three independent cold reads have reported the relay as a HIGH security defect, each time measuring lab
 * code against a production bar. That is what this package name and this note exist to answer.
 */
package pro.mir0n.esquire.gateway.lab;
