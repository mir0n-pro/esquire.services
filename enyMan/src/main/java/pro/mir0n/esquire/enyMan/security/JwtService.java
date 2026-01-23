/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.enyMan.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    //@Value("${jwt.secret}")
    //private String secretKey;

    //private SecretKey getSignInKey() {
    //    byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
    //    return Keys.hmacShaKeyFor(keyBytes);
    //}

    // have to keep it non-static for a case of token got signed
    public Claims extractAllClaims(String token) {

        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) throw new IllegalArgumentException("Invalid JWT format");

            // Decode payload part directly
            byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(decodedBytes);

            // Parse JSON into Map
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.readValue(payload, Map.class);

            // Convert numeric 'exp' and 'iat' to Date if they exist
            // JJWT's build() expects these to be correct types
            if (map.containsKey("exp") && map.get("exp") instanceof Number) {
                map.put("exp", new Date(((Number) map.get("exp")).longValue() * 1000));
            }
            if (map.containsKey("iat") && map.get("iat") instanceof Number) {
                map.put("iat", new Date(((Number) map.get("iat")).longValue() * 1000));
            }
            return Jwts.claims().add(map).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read token payload", e);
        }

        // This will throw an exception if the token is malformed,
        // which the Filter's try-catch will handle.
        //return (Claims) Jwts.parser()
         //       .build()
         //       .parse(token)
         //       .getPayload();
    }

    public <T> T extractClaim(Claims claims, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(claims);
    }

    public boolean isClaimsValid(Claims claims) {
        return !isClaimsExpired(claims);
    }

    private boolean isClaimsExpired(Claims claims) {
        return extractExpiration(claims).before(new Date());
    }

    private Date extractExpiration(Claims claims) {
        return extractClaim(claims, Claims::getExpiration);
    }

}
