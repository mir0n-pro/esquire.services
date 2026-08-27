/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 04/16/2026 mir0n  GET /jwe-jwks -- serve RSA public key for KC access token encryption
 * 05/14/2026 mir0n  v1.2.4 hauberk sprint: restored from backup tree for client_credentials
 *                   JWE re-evaluation; kept armed but inert alongside JweAwareJwtDecoder
 */
package pro.mir0n.esquire.gateway.lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serves the gateway RSA public key as JWKS so Keycloak can encrypt access tokens.
 *
 * Keycloak calls GET /jwe-jwks (configured via the client's jwks.url attribute)
 * to fetch the encryption key when issuing access tokens.
 *
 * Returns an empty keys array when JWE is not configured (no private key path set)
 * -- transparent fallback to plain JWS mode.
 *
 * The public key is read from jwe-cert.pem, derived from the private key path by
 * replacing "-private.pem" with "-cert.pem".
 */
@RestController
public class JwksController {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + JwksController.class.getName());

    @Value("${esq.jwe.private-key-path:}")
    private String privateKeyPath;

    private Map<String, Object> jwksResponse;

    @PostConstruct
    public void init() {
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            devLog.debug("JwksController: JWE not configured -- serving empty JWKS");
            jwksResponse = Collections.singletonMap("keys", Collections.emptyList());
            return;
        }
        String certPath = privateKeyPath.replace("-private.pem", "-cert.pem");
        try (FileInputStream fis = new FileInputStream(certPath)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
            RSAPublicKey pub = (RSAPublicKey) cert.getPublicKey();

            // BigInteger.toByteArray() uses signed 2's complement -- strip leading zero byte
            // that BigInteger adds when the high bit is set, to comply with unsigned JWK format
            byte[] n = stripLeadingZero(pub.getModulus().toByteArray());
            byte[] e = stripLeadingZero(pub.getPublicExponent().toByteArray());

            Map<String, Object> jwk = new LinkedHashMap<>();
            jwk.put("kty", "RSA");
            jwk.put("use", "enc");
            jwk.put("alg", "RSA-OAEP");
            jwk.put("kid", "esq-gw-jwe-1");
            jwk.put("n", Base64.getUrlEncoder().withoutPadding().encodeToString(n));
            jwk.put("e", Base64.getUrlEncoder().withoutPadding().encodeToString(e));

            jwksResponse = Collections.singletonMap("keys", Collections.singletonList(jwk));
            devLog.debug("JwksController: RSA public key loaded from [{}]", certPath);
        } catch (Exception ex) {
            devLog.debug("JwksController: cert not found at [{}] -- serving empty JWKS", certPath);
            jwksResponse = Collections.singletonMap("keys", Collections.emptyList());
        }
    }

    /** Strip the leading 0x00 byte that BigInteger.toByteArray() adds for sign (2's complement -> unsigned). */
    private static byte[] stripLeadingZero(byte[] bytes) {
        return (bytes.length > 1 && bytes[0] == 0) ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    @GetMapping("/jwe-jwks")
    public Mono<Map<String, Object>> getJwks() {
        devLog.debug("getJwks: request received -- returning {} key(s)",
                ((java.util.List<?>) jwksResponse.get("keys")).size());
        return Mono.just(jwksResponse);
    }
}
