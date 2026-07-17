/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  ported from keySmith
 * 07/17/2026 mir0n  note at the switch: the KC-admin client is un-instrumented at the wire on purpose (I39
 *                   covered) -- the KC-sync duration IS measured at the operation grain
 *                   (esq.biz.kc.sync.duration), only a per-call span is absent; copyright URL mir0n.me ->
 *                   mir0n.pro.
 */

package pro.mir0n.esquire.kcMaster.config;

import lombok.Getter;
import lombok.Setter;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakConfig {

    private String baseUrl;
    private String realm;
    private String clientId;
    private String clientSecret;
    private int connectTimeoutMs;
    private int readTimeoutMs;

    @Bean
    public AsyncTaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("kc-async-");
        executor.setVirtualThreads(true);
        return executor;
    }

    // I39 (COVERED, 2026-07-16): the KC-admin client is left UN-instrumented at the wire ON PURPOSE.
    //
    // MEASURED is not the same as SPANNED -- and I39 was filed as "KC calls are not measured", which is FALSE.
    // The KC-sync duration IS measured, at the OPERATION grain: KcRequestHandler times esq.biz.kc.sync.duration
    // (tagged by op, in a finally so a FAILED sync counts too) and KcIdentityService carries @EsqTraced
    // ("esq.kc.*"). The KC round-trip DOMINATES that number, so it is the KC cost in all but name. Same shape as
    // dataKeep's RodEventDbWriter (esq.keep.apply span + esq.biz.keep.write.* meters): KC is a TRACE LEAF here
    // exactly as Postgres is under the keep writer -- no per-call CLIENT span, no traceparent propagated in.
    //
    // What a wire-level span would ADD is only sub-operation granularity: createUser is create + setPassword +
    // applyBufferedPath (3+ round-trips) collapsed into one number, so today you learn "the createUser sync was
    // slow", not "the setPassword call was slow". A want, not a gap. And the other half -- injecting a traceparent
    // -- buys NOTHING: stock KeyCloak is not OTel-traced, so there is nobody on the far side to continue the
    // trace. If per-call spans are ever wanted, they belong to the async queue worker's own unified
    // instrumentation (the event-driven push), NOT a one-off request filter bolted onto KeycloakBuilder here.
    //
    // Do NOT re-file this as "the KC calls are untraced / unmeasured". The duration is accounted for.
    @Bean
    public Keycloak keycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(baseUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }
}
