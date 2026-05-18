/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/15/2026 mir0n  created: Vanilla Token Relay variant. Edge credential = static OAuth
 *                   client credentials presented as HTTP Basic. Examines the inbound
 *                   request, returns Reject for misuse (Bearer with azp in vanilla
 *                   allowlist; Basic with unknown client_id; malformed Basic), Relay
 *                   for valid Basic, Pass otherwise. Cache key = client_id; KC grant =
 *                   client_credentials with the inbound client's own credentials.
 */
package pro.mir0n.esquire.gateway.security.tokenrelay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import pro.mir0n.esquire.gateway.security.JwtClaimPeek;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/**
 * Vanilla Token Relay variant. The client carries no token -- only static
 * credentials presented as HTTP Basic. The gateway runs the OAuth 2.0
 * {@code client_credentials} grant against KC on the client's behalf,
 * caches the resulting JWT per {@code client_id}, and forwards a full
 * JWT downstream.
 *
 * {@code examine()} contract:
 *
 *   - No Authorization header -> Pass.
 *   - Bearer JWT with {@code azp} in this variant's allowlist -> Reject
 *     (the wire shape is wrong -- the client must present Basic, not
 *     Bearer; this closes the architectural-bypass gap where a client
 *     could acquire a JWT directly from KC and skip the no-token-on-
 *     client property).
 *   - HTTP Basic with {@code client_id} in allowlist -> Relay
 *     (cacheKey = client_id, KC request = client_credentials grant with
 *     the client's own Basic creds passed through).
 *   - HTTP Basic with {@code client_id} NOT in allowlist -> Reject.
 *   - Malformed Basic -> Reject.
 *   - Anything else -> Pass.
 */
public class VanillaTokenRelay implements ITokenRelayVariant {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + VanillaTokenRelay.class.getName());

    private static final String BASIC_PREFIX  = "Basic ";
    private static final String BEARER_PREFIX = "Bearer ";

    private final Set<String> allowlist;

    public VanillaTokenRelay(Set<String> allowlist) {
        this.allowlist = allowlist == null ? Set.of() : Set.copyOf(allowlist);
    }

    @Override
    public VariantAction examine(ServerWebExchange exchange) {
        VariantAction ret;
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null) {
            ret = new VariantAction.Pass();
        } else if (authHeader.startsWith(BEARER_PREFIX)) {
            String azp = JwtClaimPeek.peekAzp(authHeader.substring(BEARER_PREFIX.length()).trim());
            if (azp != null && allowlist.contains(azp)) {
                ret = new VariantAction.Reject(
                        "Bearer JWT with azp=[" + azp + "] is in Vanilla Token Relay allowlist -- must use HTTP Basic");
            } else {
                ret = new VariantAction.Pass();
            }
        } else if (authHeader.startsWith(BASIC_PREFIX)) {
            BasicCreds creds = decodeBasic(authHeader);
            if (creds == null) {
                ret = new VariantAction.Reject("malformed Basic header");
            } else if (!allowlist.contains(creds.clientId())) {
                ret = new VariantAction.Reject(
                        "client_id=[" + creds.clientId() + "] not in Vanilla Token Relay allowlist");
            } else {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("grant_type", "client_credentials");
                KcTokenRequest req = new KcTokenRequest(form, creds.clientId(), creds.clientSecret());
                ret = new VariantAction.Relay(creds.clientId(), req);
            }
        } else {
            ret = new VariantAction.Pass();
        }
        return ret;
    }

    private BasicCreds decodeBasic(String authHeader) {
        BasicCreds ret = null;
        try {
            String b64 = authHeader.substring(BASIC_PREFIX.length()).trim();
            String decoded = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon > 0 && colon < decoded.length() - 1) {
                ret = new BasicCreds(decoded.substring(0, colon), decoded.substring(colon + 1));
            }
        } catch (IllegalArgumentException ex) {
            devLog.error("decodeBasic: base64 decode failed -- {}", ex.toString());
        }
        return ret;
    }

    private record BasicCreds(String clientId, String clientSecret) {}
}
