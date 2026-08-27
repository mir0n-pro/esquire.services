/*
 *  Esquire frameworks (tm)
 *  Gateway service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/23/2026 mir0n  created: the tokens and headers the token-relay tests are built from
 */
package pro.mir0n.esquire.gateway.lab.tokenrelay;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;

import java.util.Base64;

/**
 * The material a relay test needs: a JWT the gate can peek at, and a Basic header.
 * <p>
 * The tokens are PLAIN JWTs -- unsigned, and that is enough here. The relay decides on {@code azp} and
 * {@code jti} alone, read with {@code JwtClaimPeek} before any signature is looked at; validating the
 * signature is the resource server's job, further down the chain and not what these tests are about.
 */
final class RelayTestTokens {

    private RelayTestTokens() {}

    /** A token carrying both claims the two variants read. Either may be left out by passing null. */
    static String jwt(String azp, String jti) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
        if (azp != null) {
            claims.claim("azp", azp);
        }
        if (jti != null) {
            claims.jwtID(jti);
        }
        return new PlainJWT(claims.build()).serialize();
    }

    static String basic(String clientId, String secret) {
        String raw = clientId + ":" + secret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes());
    }

    static String bearer(String azp, String jti) {
        return "Bearer " + jwt(azp, jti);
    }
}
