/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/15/2026 mir0n  created: SPI for the KC /token call. Single shared interface used by
 *                   both Vanilla Token Relay and Phantom Token Relay variants (each
 *                   passes a different KcTokenRequest shape). Production impl is
 *                   WebClientTokenRelayClient; tests can substitute a mock.
 */
package pro.mir0n.esquire.gateway.lab.tokenrelay;

import reactor.core.publisher.Mono;

/**
 * Reactive seam for calling Keycloak's {@code /token} endpoint. Given a
 * {@link KcTokenRequest} (form params + Basic auth credentials), returns
 * an {@link ExpiringJwt} or an error.
 *
 * Both Token Relay variants funnel through one instance of this client;
 * the per-variant differences (grant type, subject token vs client creds,
 * etc.) are encoded into the {@link KcTokenRequest} payload by the
 * variant itself.
 */
public interface ITokenRelayClient {

    Mono<ExpiringJwt> acquire(KcTokenRequest request);
}
