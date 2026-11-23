/*
 *  Esquire frameworks (tm)
 *  Gateway
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/17/2026 mir0n  created (v2.0.2): pure OAuth (lab) -- GET claims-uri?loginId= (the login id strictly
 *                   encoded); a 404 is a BadCredentialsException
 */

package pro.mir0n.esquire.gateway.lab.pureoauth;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import pro.mir0n.esquire.backend.dto.access.EsqUserClaims;
import reactor.core.publisher.Mono;

import java.net.URI;

public class WebClientUserClaimsClient implements IUserClaimsClient {

    private final WebClient httpClient;
    private final String    claimsUri;

    public WebClientUserClaimsClient(WebClient httpClient, String claimsUri) {
        this.httpClient = httpClient;
        this.claimsUri  = claimsUri;
    }

    @Override
    public Mono<EsqUserClaims> fetch(String loginId) {
        URI uri = uriOf(loginId);
        //xxx: keySmith's 404 means no access profile for the login id -- a refused login, not a fault
        return httpClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                        response -> Mono.error(new BadCredentialsException("no access profile for the login id")))
                .bodyToMono(EsqUserClaims.class);
    }

    //xxx: the login id goes in as a template variable, so it is encoded strictly -- a '+' in an email
    // would otherwise reach keySmith as a space
    private URI uriOf(String loginId) {
        return UriComponentsBuilder.fromUriString(claimsUri)
                .queryParam("loginId", "{loginId}")
                .encode()
                .buildAndExpand(loginId)
                .toUri();
    }
}
