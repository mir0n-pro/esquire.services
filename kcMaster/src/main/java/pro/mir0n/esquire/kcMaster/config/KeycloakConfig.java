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
 * 08/11/2026 mir0n  v1.2.12 -- the race-8c path buffer is declared here as an
 *                   ExpiringCache<String,ParkedPath> bean, shared by the topic adapter and createUser, and
 *                   logs its effective ttl and prune interval at startup
 * 08/12/2026 mir0n  v1.2.13 -- the keycloak() and kcPathBuffer() beans removed: KcIdentityGateway builds the admin client
 *                   and the path park itself; the I39 note moved with the client
 */

package pro.mir0n.esquire.kcMaster.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.Setter;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import pro.mir0n.esquire.kcMaster.messaging.ParkedPath;
import pro.mir0n.utils.concurrent.ExpiringCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Slf4j
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

}
