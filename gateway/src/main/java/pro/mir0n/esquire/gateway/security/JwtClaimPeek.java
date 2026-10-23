/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: shared utility for peeking JWT azp / jti claims without signature
 *                   validation; used by CredentialBoundAuthenticationFilter and
 *                   PhantomTokenAuthenticationFilter as a routing decision before downstream
 *                   JWS validation runs
 * 10/19/2026 mir0n  v2.0.1 -- peekClaims(String): all the claims of the token, unverified; empty when there is no
 *                   token or it cannot be parsed
 */
package pro.mir0n.esquire.gateway.security;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Map;

/**
 * Unauthenticated peek at a JWT's claims. Used by the gateway-side filters
 * (CredentialBoundAuthenticationFilter, PhantomTokenAuthenticationFilter)
 * to route a request based on claims BEFORE the downstream JWS validator
 * runs. A peeked claim is not signature-verified; a tampered token will
 * fail downstream validation regardless, so the peek is only used as a
 * routing decision, never as the authoritative claim source.
 */
public final class JwtClaimPeek {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + JwtClaimPeek.class.getName());

    private JwtClaimPeek() {}

    public static String peekAzp(String jwtCompact) {
        return peekString(jwtCompact, "azp");
    }

    public static String peekJti(String jwtCompact) {
        return peekString(jwtCompact, "jti");
    }

    /** All the claims of the token, unverified; empty when there is no token or it cannot be parsed. */
    public static Map<String, Object> peekClaims(String jwtCompact) {
        Map<String, Object> ret = Map.of();
        if (jwtCompact != null) {
            try {
                JWT jwt = JWTParser.parse(jwtCompact);
                ret = jwt.getJWTClaimsSet().getClaims();
            } catch (ParseException ex) {
                devLog.debug("peekClaims: failed to parse JWT -- {}", ex.toString());
            }
        }
        return ret;
    }

    private static String peekString(String jwtCompact, String claim) {
        String ret = null;
        try {
            JWT jwt = JWTParser.parse(jwtCompact);
            Object value = jwt.getJWTClaimsSet().getClaim(claim);
            if (value instanceof String) {
                ret = (String) value;
            }
        } catch (ParseException ex) {
            devLog.debug("peekString[{}]: failed to parse JWT -- {}", claim, ex.toString());
        }
        return ret;
    }
}
