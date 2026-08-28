/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 04/16/2026 mir0n  JWE-aware JWT decoder: decrypt JWE -> validate inner JWS
 * 05/14/2026 mir0n  v1.2.4 hauberk sprint: restored from backup tree for client_credentials
 *                   JWE re-evaluation; kept in tree as latent capability (KC 26 still does not
 *                   emit JWE on /token -- armed but inert; topic parked until v1.3+ or alt-IAS)
 * 07/17/2026 mir0n  note at the switch: the JWE-path twin of the SecurityConfig decoder leaves the JWKS fetch
 *                   un-instrumented for the same reason (I42/L3 accepted); the full note and the instrument-it
 *                   seam live at SecurityConfig.jwtDecoder().
 */
package pro.mir0n.esquire.gateway.lab;

import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jwt.EncryptedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPrivateKey;

/**
 * ReactiveJwtDecoder that handles both JWE and plain JWS tokens.
 *
 * If the incoming token has 5 parts (JWE compact serialization), it is decrypted
 * using the gateway RSA private key and the inner JWS is extracted.  The inner JWS
 * is then passed to a standard NimbusReactiveJwtDecoder for signature validation
 * against Keycloak's JWK endpoint.
 *
 * Jwt.tokenValue == inner JWS string, so any downstream forwarding works with the
 * plain JWS -- no service changes required.
 *
 * Plain JWS tokens (3 parts) are passed directly to the delegate decoder.
 */
public class JweAwareJwtDecoder implements ReactiveJwtDecoder {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + JweAwareJwtDecoder.class.getName());

    private final RSAPrivateKey privateKey;
    private final ReactiveJwtDecoder delegate;

    public JweAwareJwtDecoder(RSAPrivateKey privateKey, String jwkSetUri, OAuth2TokenValidator<Jwt> tokenValidator) {
        this.privateKey = privateKey;
        // I42/L3 (ACCEPTED, 2026-07-16): the JWKS fetch is left UN-instrumented here too -- this is the JWE-path
        // twin of the decoder built in SecurityConfig.jwtDecoder(), and only ONE of the two is ever active. Same
        // reasoning: ReactiveRemoteJWKSource caches the JWK set and re-fetches only on a missing kid (first use +
        // key rotation), so the unmeasured cost lands on one request per rotation, not the hot path. The full
        // note -- including the withJwkSetUri(uri).webClient(wc) seam to instrument it if that ever changes --
        // lives at SecurityConfig.jwtDecoder(); keep the two decisions together.
        NimbusReactiveJwtDecoder jwsDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        jwsDecoder.setJwtValidator(tokenValidator);
        this.delegate   = jwsDecoder;
    }

    @Override
    public Mono<Jwt> decode(String token) throws JwtException {
        Mono<Jwt> ret;
        String[] parts = token.split("\\.");
        if (parts.length == 5) {
            devLog.debug("decode: JWE token detected ({} parts), decrypting", parts.length);
            ret = Mono.fromCallable(() -> decryptJwe(token))
                       .flatMap(jws -> {
                           devLog.debug("decode: JWE decryption complete, inner JWS forwarded to signature validator");
                           return delegate.decode(jws);
                       });
        } else {
            devLog.debug("decode: plain JWS token ({} parts), forwarding to validator", parts.length);
            ret = delegate.decode(token);
        }
        return ret;
    }

    private String decryptJwe(String token) {
        String ret;
        try {
            EncryptedJWT encryptedJWT = EncryptedJWT.parse(token);
            encryptedJWT.decrypt(new RSADecrypter(privateKey));
            ret = encryptedJWT.getPayload().toString();
        } catch (Exception ex) {
            throw new JwtException("JWE decryption failed: " + ex.getMessage(), ex);
        }
        return ret;
    }
}
