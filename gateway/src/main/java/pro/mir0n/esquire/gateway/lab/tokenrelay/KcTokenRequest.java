/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/15/2026 mir0n  created: KC /token call payload -- form params + Basic auth creds the
 *                   gateway sends on behalf of an inbound request. Used by both Vanilla
 *                   Token Relay (client_credentials grant, client's own creds passed
 *                   through) and Phantom Token Relay (token-exchange grant, gateway's
 *                   esq-gw-exchange creds).
 */
package pro.mir0n.esquire.gateway.lab.tokenrelay;

import org.springframework.util.MultiValueMap;

/**
 * KC /token call payload. Built by a variant's {@code examine()} as part
 * of a {@code VariantAction.Relay} decision; consumed by
 * {@link WebClientTokenRelayClient} to POST the request to KC.
 *
 *   - {@code formParams}      -- the x-www-form-urlencoded body
 *                                (grant_type plus variant-specific fields)
 *   - {@code basicAuthClientId},
 *     {@code basicAuthSecret} -- HTTP Basic credentials presented to KC
 *                                for the call (NOT the inbound principal)
 */
public record KcTokenRequest(MultiValueMap<String, String> formParams,
                             String basicAuthClientId,
                             String basicAuthSecret) {}
