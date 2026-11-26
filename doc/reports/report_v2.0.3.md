# Release Report: v2.0.2 → HEAD

**Repo:** `esquire.services/develop2`  
**Top commit:** `29b91d2`

---

## Release Notes

### doc/release_notes.txt


**v2.0.3-2611.2602**  v2.0.3 -- finalization: the docs describe one kind of sign-in  
&nbsp;: Doc:         README.md  
&nbsp;                 Releases.md  
&nbsp;                 Esquire.Auth.md  
&nbsp;                 Esquire.Auth.TokenPatterns.md  
&nbsp;                 Esquire.Auth.keySmithRoutine.md  
&nbsp;                 Esquire.ContinuingDev.md  
&nbsp;                 Esquire.GitHubActions.md  
&nbsp;                 Esquire.GrafanaGuide.md  
&nbsp;                 Esquire.Haubergeon.md  
&nbsp;                 Esquire.ObservabilityStack.md  
&nbsp;                 Esquire.Q&A.md  
&nbsp;                 Esquire.TestingStack.md  
&nbsp;                 services.configuring.md  
&nbsp;                 v2.0.x.Goals.md  
&nbsp;                 v2.0.x.Planning.md  
&nbsp;   Components:   doc,  
&nbsp;                 compose,  
&nbsp;                 compose-compact,  
&nbsp;                 k8s,  
&nbsp;                 k8s-compact,  
&nbsp;                 k8s-oci,  
&nbsp;                 k8s-oci-compact  

**v2.0.3-2611.2522**  v2.0.3 -- OKE by hand: the observability check on the cloud  
&nbsp;: Fix:         the cloud observability check looks for paacMan, the service the cloud runs, in place of pacMan  
&nbsp;: Fix:         the latency check reads all its numbers at the same moment, so a check run right after traffic  
&nbsp;                 no longer reports a gap that is not there  
&nbsp;   Components:   o11y  

**v2.0.3-2611.2518**  v2.0.3 -- code review: what the claims mode left behind  
&nbsp;- the gateway no longer carries the token relay filter and a sign-in client setup it never used  
&nbsp;- the KeyCloak load harness client loses its token encryption settings, which KeyCloak never applied  
&nbsp;- KeyCloak users carry no Esquire id; kcMaster writes no extra data to a KeyCloak user  
&nbsp;- the KeyCloak reconcile check pairs the users by login id and also checks the email  
&nbsp;- five demo users in KeyCloak get the email the seed gives them  
&nbsp;   Components:   gateway,  
&nbsp;                 gateWard,  
&nbsp;                 common,  
&nbsp;                 kcMaster,  
&nbsp;                 keycloak,  
&nbsp;                 hauberk,  
&nbsp;                 compose,  
&nbsp;                 compose-compact,  
&nbsp;                 k8s,  
&nbsp;                 k8s-compact,  
&nbsp;                 k8s-oci,  
&nbsp;                 k8s-oci-compact  

**v2.0.3-2611.2501**  v2.0.3 -- OAuth only: observability  
&nbsp;: Config:      the services board shows the gateway's claims cache hit rate and the keySmith claims query  
&nbsp;                 time in place of the token relay panels; the KeyCloak line is out of the request time bands  
&nbsp;: Config:      the topology boards draw the claims bus, right after the biz bus, on every shape  
&nbsp;: Config:      the classic topology board's gateway calls bizTree from its left side, under the bus lines  
&nbsp;: Config:      the classic OKE Grafana loads the OKE boards, as compact OKE already did  
&nbsp;: Config:      the o11y inventory lists the OAuth claims signals; the token relay signals are out  
&nbsp;   Components:   compose,  
&nbsp;                 compose-compact,  
&nbsp;                 k8s,  
&nbsp;                 k8s-compact,  
&nbsp;                 k8s-oci,  
&nbsp;                 k8s-oci-compact  

**v2.0.3-2611.2419**  v2.0.3 -- OAuth only: the test routines and the KeyCloak seed  
&nbsp;: Config:      the KeyCloak realm keeps three Esquire clients: the browser's, the load harness's and  
&nbsp;                 kcMaster's; the lab, token relay and token exchange clients are removed  
&nbsp;: Config:      the KeyCloak users carry no root path and no Esquire roles, and the realm defines no  
&nbsp;                 Esquire roles  
&nbsp;: Config:      KeyCloak runs without the token exchange feature on docker and k8s  
&nbsp;: Config:      the compact Postgres and KeyCloak image pins move to v2.0.3-2611.2418, the release of the  
&nbsp;                 new seed and realm  
&nbsp;: Config:      the OKE e2e run skips no spec  
&nbsp;: Config:      the config parity check no longer accepts the removed token relay and lab client keys  
&nbsp;: Refactoring: the KeyCloak reconcile check reports a missing or an orphan account only; the root path  
&nbsp;                 repair is removed  
&nbsp;: Refactoring: the load harness drops the race test for a path reaching KeyCloak before its user, the  
&nbsp;                 token relay and lab client overlays, and their perf cells  
&nbsp;   Components:   keycloak,  
&nbsp;                 postgres,  
&nbsp;                 hauberk  

**v2.0.3-2611.2401**  v2.0.3 -- dead code out  
&nbsp;: Refactoring: what pure OAuth left unused is removed: the claims publisher, the pure OAuth switch name,  
&nbsp;                 the moved-path hand-over to the identity gateway, and the root path and roles fields of the  
&nbsp;                 identity command  
&nbsp;: Refactoring: three older unused pieces are removed too: a name-value serializer, a gateway timing header  
&nbsp;                 name and an error detail field name  
&nbsp;   Components:   common,  
&nbsp;                 kcMaster,  
&nbsp;                 keySmith  

**v2.0.3-2611.2323**  v2.0.3 -- pure OAuth only: Mesnie  
&nbsp;: Refactoring: Mesnie runs pure OAuth only: the claims bus is always built  
&nbsp;: Config:      the pure OAuth switch and the path hold settings leave Mesnie, the compose-compact mesnie  
&nbsp;                 service and the k8s mesnie chart; the mesnie chart names its entity bus role as a client  
&nbsp;: Config:      the KeyCloak image pin moves to v2.0.3-2611.2300, the release of the pure OAuth realm  
&nbsp;   Components:   mesnie,  
&nbsp;                 keycloak  

**v2.0.3-2611.2320v2.0.3** -- pure OAuth only: kcMaster  
&nbsp;: Refactoring: kcMaster runs pure OAuth only: a new Keycloak user gets the Esquire id and nothing else,  
&nbsp;                 and the root path, the roles and the path moves are no longer written  
&nbsp;: Refactoring: kcMaster no longer listens to the entity broadcasts; the held path for a user who did not  
&nbsp;                 exist yet is gone with them  
&nbsp;: Refactoring: enyMan no longer talks to kcMaster  
&nbsp;: Config:      the pure OAuth switch and the path hold settings leave kcMaster, the compose kcmaster  
&nbsp;                 service and the k8s kcmaster chart; the kcMaster entity bus and the enyMan kc bus settings  
&nbsp;                 leave compose, the k8s charts and the local k8s and OKE overlays  
&nbsp;   Components:   kcMaster, enyMan  

**v2.0.3-2611.2317**  v2.0.3 -- pure OAuth only: enyMan  
&nbsp;: Refactoring: enyMan runs pure OAuth only: a moved user always tells the gateways to drop the user's  
&nbsp;                 claims, and the moved path no longer goes to Keycloak  
&nbsp;: Config:      the pure OAuth switch leaves enyMan, the compose enyman service and the k8s enyman chart  
&nbsp;   Components:   enyMan  

**v2.0.3-2611.2314**  v2.0.3 -- pure OAuth only: keySmith  
&nbsp;: Refactoring: keySmith runs pure OAuth only: a saved access profile always tells the gateways to  
&nbsp;                 drop the user's claims, and the root path and the roles no longer go to Keycloak  
&nbsp;: Config:      the pure OAuth switch leaves keySmith, the compose keysmith service and the k8s keysmith  
&nbsp;                 chart  
&nbsp;: Config:      the keySmith test hold for the old create-while-move race is removed, with its compose  
&nbsp;                 settings  
&nbsp;   Components:   keySmith  

**v2.0.3-2611.2312**  v2.0.3 -- pure OAuth only: common  
&nbsp;: Refactoring: the services take the caller's claims from the gateway's headers only; the Esquire  
&nbsp;                 claims in a token are no longer read  
&nbsp;: Refactoring: the gateway and gateWard share the roles header parse and the login id claim name from  
&nbsp;                 common  
&nbsp;   Components:   common,  
&nbsp;                 gateway,  
&nbsp;                 gateWard  

**v2.0.3-2611.2300**  v2.0.3 -- pure OAuth only: the gateway and gateWard; pure OAuth is the default  
&nbsp;: Refactoring: the gateway and gateWard read every token as a pure OAuth token: the login id comes  
&nbsp;                 from the token, the Esquire claims from keySmith  
&nbsp;: Refactoring: the claims mode leaves both; JWE and the token relay (Vanilla, Phantom) are removed  
&nbsp;: Config:      the services run pure OAuth on every target: ESQ_PURE_OAUTH is true in compose,  
&nbsp;                 compose-compact and the k8s charts  
&nbsp;: Config:      Keycloak: esq-angular and esq-hauberk carry the login id only, with no Esquire claims and  
&nbsp;                 no roles  
&nbsp;: Config:      the settings and the claims cache meter are named oauth (esq.gateway.oauth.*,  
&nbsp;                 ESQ_GW_OAUTH_*, esq.biz.gw.oauth.total)  
&nbsp;: Config:      compose, compose-compact, both local k8s shapes, the frozen OKE shapes and the OKE deploy  
&nbsp;                 lose the relay, JWE and client-list settings and the exchange secret  
&nbsp;   Components:   gateway,  
&nbsp;                 gateWard  

---

## Code Changes

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**11/25/2026** mir0n  v2.0.3 -- code review: what the claims mode left behind  

**11/24/2026** mir0n  v2.0.3 -- dead code out  
**identity.IIdentityGateway**  
&nbsp;- v2.0.3 -- postMessage(RodEvent) out; the doc no longer names the moved-path command (op X) or the path  
&nbsp;   change number  
**identity.AuthSyncRequest**  
&nbsp;- v2.0.3 -- the path and roles fields out, and out of toMap()  
messaging.ClaimsBusPublisher  (removed)  
&nbsp;- the claims-bus publisher: evict() on the claims rod, nothing sent with the switch off  
dto.EsqNameValueSerializer  (removed)  
&nbsp;- the List JSON serializer  

**11/23/2026** mir0n  v2.0.3 -- pure OAuth only: common  
**security.JwtClaimsExtractionFilter**  
&nbsp;- v2.0.3 -- uid / rootPath / roles from the X-Esq-UID / X-Esq-Root-Path / X-Esq-Roles headers only; the  
&nbsp;   token's esq_uid / esq_rootpath / realm_access claims are not read; the roles header parse is  
&nbsp;   EsqRequestScope.rolesOf()  
**service.EsqRequestScope**  
&nbsp;- v2.0.3 -- rolesOf(String): the roles out of the X-Esq-Roles header, split on commas and trimmed  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**11/25/2026** mir0n  v2.0.3 -- code review: what the claims mode left behind  
EsqConstants  
&nbsp;- v2.0.3 -- JWT_CLAIM_ENTITY_ID and JWT_CLAIM_ENTITY_ROOTPATH removed  

**11/24/2026** mir0n  v2.0.3 -- dead code out  
EsqConstants  
&nbsp;- v2.0.3 -- PROP_PURE_OAUTH, ESQ_GW_INNER_START_TIME and PD_DETAILS removed  

**11/23/2026** mir0n  v2.0.3 -- pure OAuth only: common  
EsqConstants  
&nbsp;- v2.0.3 -- JWT_CLAIM_PREFERRED_USERNAME added; PROP_PURE_OAUTH_CLIENTS, JWT_CLAIM_REALM_ACCESS and  
&nbsp;   JWT_CLAIM_REALM_ACCESS_ROLES removed  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**11/23/2026** mir0n  v2.0.3 -- pure OAuth only: enyMan without the kc leg  
**messaging.EntityBusAdapter**  
&nbsp;- v2.0.3 -- IIdentityGateway out of the constructor; publish() and the receive worker no longer hand a PATH  
&nbsp;   broadcast to postMessage; the subscription back to EventType = 'I'  
EnyManApplication  
&nbsp;- v2.0.3 -- the kc leg out: the IIdentityGateway bean (KcBusAdapter) out; the buses are entity, audit and  
&nbsp;   claims  
messaging.KcBusAdapter  (removed)  
&nbsp;- the kc-bus CLIENT leg (identity requests out, answers in)  
**resources/application.yml**  
&nbsp;- the kc-bus leg out  

**11/23/2026** mir0n  v2.0.3 -- pure OAuth only: enyMan  
**queue.MoveQueueManager**  
&nbsp;- v2.0.3 -- evictClaims(): a moved user is evicted on the claims rod itself (the ClaimsBusPublisher out of  
&nbsp;   the constructor); the path request to KeyCloak out, and IIdentityGateway with it (publishKcMoveRequest()  
&nbsp;   replaced)  
EnyManApplication  
&nbsp;- v2.0.3 -- the ClaimsBusPublisher bean out; the claims bus is always built beside the entity, kc and audit  
&nbsp;   buses  
**resources/application.yml**  
&nbsp;- esq.pure-oauth (ESQ_PURE_OAUTH) out; the claims-bus leg always built  

### gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt


**11/25/2026** mir0n  v2.0.3 -- code review: what the claims mode left behind  
&nbsp;- - TokenRelay= out of every route; the spring.security.oauth2.client registration (keycloak,  
&nbsp;   KEYCLOAK_CLIENT_ID) out  
**pom.xml**  

**11/23/2026** mir0n  v2.0.3 -- the roles header parse from common  
BizTreeCacheController  
&nbsp;- v2.0.3 -- the roles header parse is EsqRequestScope.rolesOf(); the private rolesOf(String) out  

**11/23/2026** mir0n  v2.0.3 -- pure OAuth only: the claims mode and the lab leave gateWard  
BizTreeCacheController  
&nbsp;- v2.0.3 -- context(): uid / rootPath / roles from the X-Esq-* headers only; the endpoints take no  
&nbsp;   @AuthenticationPrincipal Jwt  
GateWardApplication  
&nbsp;- v2.0.3 -- the claims bus is always built beside the entity and biz buses  
**resources/application.yml**  
&nbsp;- esq.gateway.oauth: cache-idle-ms (ESQ_GW_OAUTH_CACHE_IDLE_MS), claims-uri (keySmith in mesnie,  
&nbsp;   ESQ_GW_OAUTH_CLAIMS_URI), claims-timeout-ms (ESQ_GW_OAUTH_CLAIMS_TIMEOUT_MS) in place of esq.gateway.pure-  
&nbsp;   oauth; the pure client list, keycloak.exchange, esq.jwe and esq.gateway.token-relay out  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**11/25/2026** mir0n  v2.0.3 -- code review: what the claims mode left behind  
**config.SecurityConfig**  
&nbsp;- v2.0.3 -- .oauth2Client() out;  
&nbsp;- v2.0.3 -- TokenRelay= out of every route; the spring.security.oauth2.client registration (keycloak,  
&nbsp;   KEYCLOAK_CLIENT_ID) out  
**pom.xml**  

**11/23/2026** mir0n  v2.0.3 -- the roles header parse and the preferred_username claim name from common  
**bus.BizBusRoutingFilter**  
&nbsp;- v2.0.3 -- the roles header parse is EsqRequestScope.rolesOf(); the private rolesOf(String) out  
**oauth.AuthenticationConverter**  
&nbsp;- v2.0.3 -- the preferred_username claim name is EsqConstants.JWT_CLAIM_PREFERRED_USERNAME; the own  
&nbsp;   CLAIM_PREFERRED_USERNAME out  
**security.EsqAccessDeniedHandler**  
&nbsp;- v2.0.3 -- the preferred_username claim name is EsqConstants.JWT_CLAIM_PREFERRED_USERNAME; the own  
&nbsp;   CLAIM_PREFERRED_USERNAME out  
**security.EsqClaimsValidator**  
&nbsp;- v2.0.3 -- the preferred_username claim name is EsqConstants.JWT_CLAIM_PREFERRED_USERNAME; the own  
&nbsp;   CLAIM_PREFERRED_USERNAME out  

**11/23/2026** mir0n  v2.0.3 -- pure OAuth only: the claims mode and the lab leave the gateway  
**oauth.IUserClaimsClient**  
&nbsp;- v2.0.3 -- moved from lab.pureoauth to oauth  
**oauth.WebClientUserClaimsClient**  
&nbsp;- v2.0.3 -- moved from lab.pureoauth to oauth  
**oauth.ClaimsCache**  
&nbsp;- v2.0.3 -- lab.pureoauth.PureOAuthClaimsCache -> oauth.ClaimsCache; meter esq.biz.gw.oauth.total  
**oauth.AuthenticationConverter**  
&nbsp;- v2.0.3 -- lab.pureoauth.PureOAuthAuthenticationConverter -> oauth.AuthenticationConverter; every token's  
&nbsp;   authentication is built from the cached claims -- the client list, isPure() and the claims-mode converter  
&nbsp;   out  
**oauth.HeaderFilter**  
&nbsp;- v2.0.3 -- lab.pureoauth.PureOAuthHeaderFilter -> oauth.HeaderFilter  
**oauth.EvictionListener**  
&nbsp;- v2.0.3 -- lab.pureoauth.PureOAuthEvictionListener -> oauth.EvictionListener; always takes the claims rod  
&nbsp;   (the on / off flag out); log prefix OAUTH  
**config.SecurityConfig**  
&nbsp;- v2.0.3 -- pure OAuth only: the JWE-aware decoder, the token relay (TokenRelayFilter, the keycloak.exchange  
&nbsp;   connection, assertRelayWiring) and the claims-mode converter (KeycloakRoleConverter) out; the  
&nbsp;   esq.gateway.oauth.* settings (cache-idle-ms, claims-uri, claims-timeout-ms) in place of esq.gateway.pure-  
&nbsp;   oauth.*, with no client list; the claimsCache bean always started, claims-uri required  
&nbsp;   (assertOAuthWiring); the evictionListener bean; AuthenticationConverter for every token;  
&nbsp;   EsqClaimsValidator with no client list; log prefix OAUTH  
**config.KeycloakRoleConverter**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**security.EsqClaimsValidator**  
&nbsp;- v2.0.3 -- every token needs sub and preferred_username only; the claims-mode check and the client list out  
**security.EsqAccessDeniedHandler**  
&nbsp;- v2.0.3 -- the caller is named from preferred_username or sub (esq_uid out)  
**security.JwtClaimPeek**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**bus.BizBusRoutingFilter**  
&nbsp;- v2.0.3 -- uid / rootPath / roles from the X-Esq-* headers only; the token is not read (tokenOf,  
&nbsp;   rolesOf(Map) and the JwtClaimPeek use out)  
GatewayApplication  
&nbsp;- v2.0.3 -- the claims bus is always built beside the biz bus  
**lab.JweAwareJwtDecoder**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.JwksController**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.package-info**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.tokenrelay.ExpiringJwt**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.tokenrelay.ITokenRelayClient**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.tokenrelay.ITokenRelayVariant**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.tokenrelay.KcTokenRequest**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.tokenrelay.PhantomTokenRelay**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.tokenrelay.TokenRelayCache**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.tokenrelay.TokenRelayFilter**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.tokenrelay.VanillaTokenRelay**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.tokenrelay.VariantAction**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**lab.tokenrelay.WebClientTokenRelayClient**  
&nbsp;- removed (v2.0.3): pure OAuth only  
**resources/application.yml**  
&nbsp;- esq.gateway.oauth: cache-idle-ms (ESQ_GW_OAUTH_CACHE_IDLE_MS), claims-uri (ESQ_GW_OAUTH_CLAIMS_URI),  
&nbsp;   claims-timeout-ms (ESQ_GW_OAUTH_CLAIMS_TIMEOUT_MS) in place of esq.gateway.pure-oauth; the pure client  
&nbsp;   list, keycloak.exchange, esq.jwe and esq.gateway.token-relay out  

### hauberk/changes.txt


**11/25/2026** mir0n  v2.0.3 -- code review: what the claims mode left behind  
**reconcile.KcRecover**  
&nbsp;- the two stores are paired by login id (au_login_id = the KC username, in lower case), not by esq_uid;  
&nbsp;   EMAIL_MISMATCH: the KC email against esq_auth.au_email; EsqUser / KcUser carry the email  

**11/24/2026** mir0n  v2.0.3 -- one kind of sign-in: the relay and lab clients and race-8c out  
**chain.ConnectUser**  
&nbsp;- javadoc: kcMaster assigns no realm roles; the roles stay on the access profile  
**chain.KcAdminAuth**  
&nbsp;- javadoc: the master-realm admin token serves the orphan cleanup (the race-8c verify is removed)  
**chain.ReadTreeRoot**  
&nbsp;- the root-path note names the access profile, not the token  
**cli.KcReconcileCommand**  
&nbsp;- --repair removed; the check reports missing-in-KC and orphan-in-KC only  
**config.EsqHauberkConfig**  
&nbsp;- javadoc: the root path is the access profile's, not a token claim  
**reconcile.KcRecover**  
&nbsp;- the stale-path check and its repair removed (KeyCloak holds no esq_rootpath): run() reports MISSING_IN_KC  
&nbsp;   and ORPHAN_IN_KC only; EsqUser / KcUser carry no path; the connected-user query reads esq_auth alone; the  
&nbsp;   report counts in a loop  
**simulations.KcCleanupSimulation**  
&nbsp;- javadoc: an orphan comes from a lost sync request, not from race-8c  
**simulations.RaceMoveCreateSimulation**  
&nbsp;- javadoc: the wrong path reaches biztree only; KeyCloak holds no path  
simulations.RaceMoveCreateKcSimulation  (removed)  
&nbsp;- the race-8c verify: KeyCloak holds no esq_rootpath to diff against  
simulations.RaceMoveCreateKcSingleShotSimulation  (removed)  
&nbsp;- the single-shot race-8c repro: it needed the removed keySmith hold  
hauberk-P.properties  (removed)  
&nbsp;- the pure OAuth overlay for docker (esq-hauberk-P)  
hauberk-k8s-P.properties  (removed)  
&nbsp;- the pure OAuth overlay for local k8s classic (esq-hauberk-P)  
hauberk-k8s-compact-P.properties  (removed)  
&nbsp;- the pure OAuth overlay for local k8s compact (esq-hauberk-P)  
hauberk-S.properties  (removed)  
&nbsp;- the Vanilla Token Relay overlay (esq-hauberk-S)  
hauberk-M.properties  (removed)  
&nbsp;- the Phantom Token Relay overlay (esq-hauberk-M)  
**hauberk.properties**  
&nbsp;- the tokenRelay.type key and its plain / vanilla / phantom notes out; the comments name no race-8c, no  
&nbsp;   hauberk-S and no esq_rootpath  
**hauberk-compact.properties**  
&nbsp;- the tokenRelay.type key and its plain / vanilla / phantom notes out; the comments name no race-8c, no  
&nbsp;   hauberk-S and no esq_rootpath  
**hauberk-k8s.properties**  
&nbsp;- the --S usage line out  
**hauberk-k8s-compact.properties**  
&nbsp;- the --S usage line out  
**hauberk-oke.properties**  
&nbsp;- the --S usage line out  
**perf-matrix.ps1**  
&nbsp;- the docker-OFF-PURE, k8s-x2-OFF-PURE and k8sc-x2-OFF-PURE cells out, with the -P overlay choice  
**README.md**  
&nbsp;- one certified client (esq-hauberk); kc-reconcile reports missing / orphan accounts; the --S line out  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**11/25/2026** mir0n  v2.0.3 -- code review: what the claims mode left behind  
**messaging.KcRequestHandler**  
&nbsp;- v2.0.3 -- a create writes no attribute (esq_uid out); the attributes arguments out  
**service.IKcIdentityService**  
&nbsp;- v2.0.3 -- createUser() and updateUserAuthState() lose attributes  
**service.impl.KcIdentityService**  
&nbsp;- v2.0.3 -- createUser() and updateUserAuthState() lose attributes: the attribute set on a create and the  
&nbsp;   merge on an update out  

**11/24/2026** mir0n  v2.0.3 -- dead code out  
**identity.KcIdentityGateway**  
&nbsp;- v2.0.3 -- the empty postMessage() out  

**11/23/2026** mir0n  v2.0.3 -- pure OAuth only: kcMaster  
**identity.KcIdentityGateway**  
&nbsp;- v2.0.3 -- pure OAuth only: the path park (ExpiringCache, kcmaster.path-buffer.*), serveBroadcast() and  
&nbsp;   kcUserExists() out; serve() handles a request only; postMessage() empty; esq.pure-oauth no longer read;  
&nbsp;   KcRequestHandler and KcIdentityService built without the flag and the park  
**messaging.KcRequestHandler**  
&nbsp;- v2.0.3 -- pure OAuth only: the pureOAuth constructor argument out; a create writes esq_uid only;  
&nbsp;   realmRoles() and the UPDATE_PATH command (handleUpdatePath()) out  
**service.IKcIdentityService**  
&nbsp;- v2.0.3 -- createUser() and updateUserAuthState() lose realmRoles; updateEntityPath() out  
**service.impl.KcIdentityService**  
&nbsp;- v2.0.3 -- pure OAuth only: the path park field and the race-8c flush (applyBufferedPath()) out of  
&nbsp;   createUser(); the realm role sync (updateRealmRoles(), assignRealmRoles()) and updateEntityPath() out  
KcMasterApplication  
&nbsp;- v2.0.3 -- the kc bus only: the entity bus is no longer built  
KcMasterConfig  
&nbsp;- v2.0.3 -- the identityGateway() doc: one bus adapter, no path park, no kcmaster.path-buffer.*  
messaging.EntityBusAdapter  (removed)  
&nbsp;- the entity-bus receive leg into the gateway's safety net  
messaging.ParkedPath  (removed)  
&nbsp;- the parked path and its change number  
**resources/application.yml**  
&nbsp;- esq.pure-oauth (ESQ_PURE_OAUTH), kcmaster.path-buffer.* and the entity-bus leg out  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**11/24/2026** mir0n  v2.0.3 -- dead code out  
**messaging.KcBusAdapter**  
&nbsp;- v2.0.3 -- the empty postMessage() out  

**11/23/2026** mir0n  v2.0.3 -- pure OAuth only: keySmith  
**service.impl.KeySmithService**  
&nbsp;- v2.0.3 -- evictClaims(): a save sends the DELETE of the access profile on the claims rod itself (the  
&nbsp;   ClaimsBusPublisher out of the constructor); the identity request never carries the root path or the roles  
&nbsp;   (identityEvent() loses its roles argument); the race-8c test hook (KEYSMITH_TEST_CONNECT_HOLD_MS) out  
KeySmithApplication  
&nbsp;- v2.0.3 -- the ClaimsBusPublisher bean out; the claims bus is always built beside the kc and audit buses  
**resources/application.yml**  
&nbsp;- esq.pure-oauth (ESQ_PURE_OAUTH) out; the claims-bus leg always built  

### mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt


**11/23/2026** mir0n  v2.0.3 -- pure OAuth only: Mesnie  
MesnieApplication  
&nbsp;- v2.0.3 -- the ClaimsBusPublisher bean out; the claims bus is always built beside the entity and audit  
&nbsp;   buses  
**resources/application.yml**  
&nbsp;- esq.pure-oauth (ESQ_PURE_OAUTH) and kcmaster.path-buffer.* out; the claims-bus leg always built  

---

## Commits

```

-- 2026-11-26 | commit: 29b91d2 | mir0n.the.programmer | Attempt to fix old known issue with CI --
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
 1 file changed, 19 insertions(+), 2 deletions(-)


-- 2026-11-26 | commit: f0d7b3c | mir0n.the.programmer | v2.0.3 -- finalization --
M	README.md
M	Releases.md
M	compose-compact/o11y/grafana/gen-topology.py
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose/o11y/grafana/gen-topology.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	doc/Esquire.Auth.TokenPatterns.md
M	doc/Esquire.Auth.keySmithRoutine.md
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.GrafanaGuide.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.TestingStack.md
M	doc/img/auth-collaboration.svg
M	doc/img/auth-connect-down.svg
M	doc/img/auth-connect-up.svg
M	doc/img/auth-move.svg
D	doc/img/auth-person-props.svg
A	doc/img/auth-profile-change.svg
M	doc/img/auth-tfa-disable.svg
M	doc/img/auth-tfa-enable.svg
M	doc/media/ComponentModel.Compact.png
M	doc/media/ComponentModel.Compact.svg
M	doc/media/ComponentModel.png
M	doc/media/ComponentModel.svg
M	doc/media/token-detour.svg
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/services.configuring.md
M	doc/v2.0.x.Goals.md
M	doc/v2.0.x.Planning.md
M	k8s-compact/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/paacman.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-oci-compact/grafana/esquire-topology.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keycloak.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/paacman.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
 56 files changed, 5628 insertions(+), 5719 deletions(-)

-- 2026-11-25 | commit: c6d86c8 | mir0n.the.programmer | v2.0.3 -- OKE by hand: the observability check on the cloud --
M	doc/release_notes.txt
M	test/o11y/fleet-supercompact-k8s.bat
M	test/o11y/o11y-verify.py
 3 files changed, 24 insertions(+), 9 deletions(-)

-- 2026-11-25 | commit: c3e6499 | mir0n.the.programmer | v2.0.3 -- code review: --
M	.github/workflows/deploy-local.yml
M	build-with-JaCoCo.bat
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/identity/KcConnectionSettings.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/W3CTraceContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/SecurityConfiguration.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/identity/KcConnectionSettingsTest.java
M	compose-compact/compose.yaml
M	compose/compose.yaml
M	doc/release_notes.txt
M	gateWard/pom.xml
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardMeterOwner.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateway/compose.yaml
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/LandingController.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
M	gateway/src/main/resources/application.yml
M	hauberk/changes.txt
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/KcReconcileCommand.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/reconcile/KcRecover.java
M	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
M	k8s-compact/charts/esquire-gateward/values.yaml
M	k8s-compact/values/keycloak.yaml
M	k8s-oci-compact/values/gateward.yaml
M	k8s-oci/values/gateway.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/IKcIdentityService.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keycloak/Dockerfile.keycloak
M	keycloak/import/esquire.json
M	test/JaCoCo/framed.html
 43 files changed, 182 insertions(+), 314 deletions(-)

-- 2026-11-25 | commit: e1a1afe | mir0n.the.programmer | v2.0.3 -- OAuth only: observability --
M	compose-compact/o11y/grafana/gen-dashboard.py
M	compose-compact/o11y/grafana/gen-topology.py
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose-compact/topology/esquire-topology.yml
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/gen-topology.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose/topology/esquire-topology.yml
M	doc/release_notes.txt
M	k8s-compact/charts/esquire-topology/esquire-topology.yml
M	k8s-compact/charts/infra/grafana/dashboards/esquire-services.json
M	k8s-compact/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s-oci-compact/esquire-topology.yml
M	k8s-oci-compact/grafana/esquire-services.json
M	k8s-oci-compact/grafana/esquire-topology.json
M	k8s-oci/esquire-topology.yml
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s-oci/oke-o11y-on.bat
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s/charts/infra/grafana/templates/configmap-dashboards.yaml
M	k8s/charts/infra/grafana/values.yaml
M	test/o11y/o11y-inventory.py
 27 files changed, 1575 insertions(+), 861 deletions(-)

-- 2026-11-24 | commit: ed6a0cb | mir0n.the.programmer | v2.0.3 -- OAuth only: the test routines and the KeyCloak seed --
M	.github/scripts/deploy-oke.sh
M	.github/workflows/deploy-oke.yml
M	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
M	compose-compact/compose.yaml
M	compose/compose.yaml
M	doc/release_notes.txt
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	gateWard/src/test/java/pro/mir0n/esquire/gateWard/GateWardMeterOwnerTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/oauth/OAuthTest.java
M	hauberk/README.md
M	hauberk/changes.txt
D	hauberk/hauberk-M.properties
D	hauberk/hauberk-P.properties
D	hauberk/hauberk-S.properties
M	hauberk/hauberk-compact.properties
D	hauberk/hauberk-k8s-P.properties
D	hauberk/hauberk-k8s-compact-P.properties
M	hauberk/hauberk-k8s-compact.properties
M	hauberk/hauberk-k8s.properties
M	hauberk/hauberk-oke.properties
M	hauberk/hauberk.properties
M	hauberk/perf-matrix.ps1
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ConnectUser.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/KcAdminAuth.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeRoot.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/KcReconcileCommand.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EsqHauberkConfig.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/reconcile/KcRecover.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcCleanupSimulation.java
D	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSimulation.java
D	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSingleShotSimulation.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateSimulation.java
M	k8s-compact/charts/infra/keycloak/templates/statefulset.yaml
M	k8s-compact/k8s-up.bat
M	k8s-compact/values/keycloak.yaml
M	k8s-compact/values/postgres.yaml
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci-compact/oke-up.bat
M	k8s-oci/oke-rebuild.bat
M	k8s-oci/oke-up.bat
M	k8s/charts/infra/keycloak/templates/statefulset.yaml
M	k8s/k8s-up.bat
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/Dockerfile.keycloak
M	keycloak/import/esquire.json
M	postgres/Dockerfile
M	test/config-parity/config-parity.py
 47 files changed, 198 insertions(+), 1761 deletions(-)

-- 2026-11-24 | commit: f51d34d | mir0n.the.programmer | v2.0.3 -- dead code out --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
D	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqNameValueSerializer.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/AuthSyncRequest.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/IIdentityGateway.java
D	common/src/main/java/pro/mir0n/esquire/backend/messaging/ClaimsBusPublisher.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	doc/release_notes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
 13 files changed, 45 insertions(+), 136 deletions(-)

-- 2026-11-24 | commit: 9039829 | mir0n.the.programmer | v2.0.3 -- pure OAuth only: kcMaster --
M	compose-compact/compose.yaml
M	doc/release_notes.txt
M	k8s-compact/charts/esquire-mesnie/templates/configmap.yaml
M	k8s-compact/charts/esquire-mesnie/values.yaml
M	k8s-compact/values/keycloak.yaml
M	keycloak/Dockerfile.keycloak
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
 9 files changed, 92 insertions(+), 114 deletions(-)

-- 2026-11-23 | commit: d56b29f | mir0n.the.programmer | v2.0.3 -- pure OAuth only: kcMaster, enyMan (once again) --
M	compose-compact/topology/esquire-topology.yml
M	compose/compose.yaml
M	compose/topology/esquire-topology.yml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/resources/application.yml
M	k8s-compact/charts/esquire-topology/esquire-topology.yml
M	k8s-oci/esquire-topology.yml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/values/enyman.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/ParkedPath.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/IKcIdentityService.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
D	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/ParkedPathTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
 32 files changed, 139 insertions(+), 1028 deletions(-)

-- 2026-11-23 | commit: 1f12beb | mir0n.the.programmer | v2.0.3 -- pure OAuth only: enyMan --
M	compose/compose.yaml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
 9 files changed, 39 insertions(+), 67 deletions(-)

-- 2026-11-23 | commit: 474aeaf | mir0n.the.programmer | v2.0.3 -- pure OAuth only: keySmith --
M	compose-compact/compose.yaml
M	compose/compose.yaml
M	doc/release_notes.txt
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
 10 files changed, 76 insertions(+), 70 deletions(-)

-- 2026-11-23 | commit: 5f8ebd8 | mir0n.the.programmer | v2.0.3 -- pure OAuth only: common --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestScope.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilterTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqRequestScopeTest.java
M	doc/release_notes.txt
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/AuthenticationConverter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqAccessDeniedHandler.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidator.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilterTest.java
 16 files changed, 139 insertions(+), 128 deletions(-)

-- 2026-11-23 | commit: fea62c4 | mir0n.the.programmer | v2.0.3 -- pure OAuth only: gateway, gateWard --
M	.github/scripts/deploy-oke.sh
M	.github/workflows/deploy-oke.yml
M	compose-compact/compose.yaml
M	compose/compose.yaml
M	doc/Esquire.Auth.TokenPatterns.md
M	doc/Esquire.Auth.md
M	doc/release_notes.txt
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
D	gateway/src/main/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/JweAwareJwtDecoder.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/JwksController.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/package-info.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ExpiringJwt.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ITokenRelayClient.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ITokenRelayVariant.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/KcTokenRequest.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/PhantomTokenRelay.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayFilter.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VanillaTokenRelay.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VariantAction.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/WebClientTokenRelayClient.java
R063	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthAuthenticationConverter.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/AuthenticationConverter.java
R084	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthClaimsCache.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/ClaimsCache.java
R053	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthEvictionListener.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/EvictionListener.java
R090	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthHeaderFilter.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/HeaderFilter.java
R080	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/IUserClaimsClient.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/IUserClaimsClient.java
R095	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/WebClientUserClaimsClient.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/WebClientUserClaimsClient.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqAccessDeniedHandler.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidator.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JwtClaimPeek.java
M	gateway/src/main/resources/application.yml
M	gateway/src/test/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilterTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverterTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/config/TokenRelayWiringGuardTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/PhantomTokenRelayTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/RelayTestTokens.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCacheTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayFilterTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayVariantContractTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VanillaTokenRelayTest.java
R076	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthTest.java	gateway/src/test/java/pro/mir0n/esquire/gateway/oauth/OAuthTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidatorTest.java
M	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
M	k8s-compact/charts/esquire-gateward/templates/deployment.yaml
M	k8s-compact/charts/esquire-gateward/templates/secret.yaml
M	k8s-compact/charts/esquire-gateward/values.yaml
M	k8s-compact/charts/esquire-mesnie/values.yaml
M	k8s-compact/k8s-up.bat
M	k8s-compact/values/gateward.yaml
M	k8s-oci-compact/oke-up.bat
M	k8s-oci-compact/values/gateward.yaml
M	k8s-oci/oke-up.bat
M	k8s-oci/values/gateway.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
D	k8s/charts/esquire-gateway/templates/secret.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/k8s-up.bat
M	k8s/values/gateway.yaml
M	keycloak/import/esquire.json
M	pom.xml
M	test/o11y/o11y-verify.py
 73 files changed, 458 insertions(+), 2905 deletions(-)
```

---

## Files Modified

```
M	.github/scripts/deploy-oke.sh
M	.github/workflows/deploy-local.yml
M	.github/workflows/deploy-oke.yml
M	README.md
M	Releases.md
M	build-with-JaCoCo.bat
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
D	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqNameValueSerializer.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/AuthSyncRequest.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/IIdentityGateway.java
M	common/src/main/java/pro/mir0n/esquire/backend/identity/KcConnectionSettings.java
D	common/src/main/java/pro/mir0n/esquire/backend/messaging/ClaimsBusPublisher.java
M	common/src/main/java/pro/mir0n/esquire/backend/o11y/W3CTraceContext.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/SecurityConfiguration.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/EsqRequestScope.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/test/java/pro/mir0n/esquire/backend/identity/KcConnectionSettingsTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilterTest.java
M	common/src/test/java/pro/mir0n/esquire/backend/service/EsqRequestScopeTest.java
M	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
M	compose-compact/compose.yaml
M	compose-compact/o11y/grafana/gen-dashboard.py
M	compose-compact/o11y/grafana/gen-topology.py
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose-compact/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose-compact/topology/esquire-topology.yml
M	compose/compose.yaml
M	compose/o11y/grafana/gen-dashboard.py
M	compose/o11y/grafana/gen-topology.py
M	compose/o11y/grafana/provisioning/dashboards/esquire-services.json
M	compose/o11y/grafana/provisioning/dashboards/esquire-topology.json
M	compose/topology/esquire-topology.yml
M	doc/Esquire.Auth.TokenPatterns.md
M	doc/Esquire.Auth.keySmithRoutine.md
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.GitHubActions.md
M	doc/Esquire.GrafanaGuide.md
M	doc/Esquire.Haubergeon.md
M	doc/Esquire.ObservabilityStack.md
M	doc/Esquire.Q&A.md
M	doc/Esquire.TestingStack.md
M	doc/img/auth-collaboration.svg
M	doc/img/auth-connect-down.svg
M	doc/img/auth-connect-up.svg
M	doc/img/auth-move.svg
D	doc/img/auth-person-props.svg
A	doc/img/auth-profile-change.svg
M	doc/img/auth-tfa-disable.svg
M	doc/img/auth-tfa-enable.svg
M	doc/media/ComponentModel.Compact.png
M	doc/media/ComponentModel.Compact.svg
M	doc/media/ComponentModel.png
M	doc/media/ComponentModel.svg
M	doc/media/token-detour.svg
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/services.configuring.md
M	doc/v2.0.x.Goals.md
M	doc/v2.0.x.Planning.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EntityBusAdapter.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcBusAdapter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateWard/pom.xml
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardMeterOwner.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateWard/src/test/java/pro/mir0n/esquire/gateWard/GateWardMeterOwnerTest.java
M	gateway/compose.yaml
M	gateway/pom.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/LandingController.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
D	gateway/src/main/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/JweAwareJwtDecoder.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/JwksController.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/package-info.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ExpiringJwt.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ITokenRelayClient.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ITokenRelayVariant.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/KcTokenRequest.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/PhantomTokenRelay.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayFilter.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VanillaTokenRelay.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VariantAction.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/WebClientTokenRelayClient.java
R061	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthAuthenticationConverter.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/AuthenticationConverter.java
R084	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthClaimsCache.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/ClaimsCache.java
R053	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthEvictionListener.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/EvictionListener.java
R090	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthHeaderFilter.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/HeaderFilter.java
R080	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/IUserClaimsClient.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/IUserClaimsClient.java
R095	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/WebClientUserClaimsClient.java	gateway/src/main/java/pro/mir0n/esquire/gateway/oauth/WebClientUserClaimsClient.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqAccessDeniedHandler.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidator.java
D	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JwtClaimPeek.java
M	gateway/src/main/resources/application.yml
M	gateway/src/test/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilterTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverterTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/config/TokenRelayWiringGuardTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/PhantomTokenRelayTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/RelayTestTokens.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCacheTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayFilterTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayVariantContractTest.java
D	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/tokenrelay/VanillaTokenRelayTest.java
R075	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthTest.java	gateway/src/test/java/pro/mir0n/esquire/gateway/oauth/OAuthTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidatorTest.java
M	hauberk/README.md
M	hauberk/changes.txt
D	hauberk/hauberk-M.properties
D	hauberk/hauberk-P.properties
D	hauberk/hauberk-S.properties
M	hauberk/hauberk-compact.properties
D	hauberk/hauberk-k8s-P.properties
D	hauberk/hauberk-k8s-compact-P.properties
M	hauberk/hauberk-k8s-compact.properties
M	hauberk/hauberk-k8s.properties
M	hauberk/hauberk-oke.properties
M	hauberk/hauberk.properties
M	hauberk/perf-matrix.ps1
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ConnectUser.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/KcAdminAuth.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/chain/ReadTreeRoot.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/cli/KcReconcileCommand.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/config/EsqHauberkConfig.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/reconcile/KcRecover.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/KcCleanupSimulation.java
D	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSimulation.java
D	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateKcSingleShotSimulation.java
M	hauberk/src/main/java/pro/mir0n/esquire/hauberk/simulations/RaceMoveCreateSimulation.java
M	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
M	k8s-compact/charts/esquire-gateward/templates/deployment.yaml
M	k8s-compact/charts/esquire-gateward/templates/secret.yaml
M	k8s-compact/charts/esquire-gateward/values.yaml
M	k8s-compact/charts/esquire-mesnie/templates/configmap.yaml
M	k8s-compact/charts/esquire-mesnie/values.yaml
M	k8s-compact/charts/esquire-topology/esquire-topology.yml
M	k8s-compact/charts/infra/grafana/dashboards/esquire-services.json
M	k8s-compact/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s-compact/charts/infra/keycloak/templates/statefulset.yaml
M	k8s-compact/k8s-up.bat
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/keycloak.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/paacman.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-compact/values/postgres.yaml
M	k8s-oci-compact/esquire-topology.yml
M	k8s-oci-compact/grafana/esquire-services.json
M	k8s-oci-compact/grafana/esquire-topology.json
M	k8s-oci-compact/oke-rebuild.bat
M	k8s-oci-compact/oke-up.bat
M	k8s-oci-compact/values/gateward.yaml
M	k8s-oci/esquire-topology.yml
M	k8s-oci/grafana/esquire-services.json
M	k8s-oci/grafana/esquire-topology.json
M	k8s-oci/oke-o11y-on.bat
M	k8s-oci/oke-rebuild.bat
M	k8s-oci/oke-up.bat
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
D	k8s/charts/esquire-gateway/templates/secret.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/charts/infra/grafana/dashboards/esquire-services.json
M	k8s/charts/infra/grafana/dashboards/esquire-topology.json
M	k8s/charts/infra/grafana/templates/configmap-dashboards.yaml
M	k8s/charts/infra/grafana/values.yaml
M	k8s/charts/infra/keycloak/templates/statefulset.yaml
M	k8s/k8s-up.bat
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keycloak.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/paacman.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/EntityBusAdapter.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/ParkedPath.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/IKcIdentityService.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
D	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/ParkedPathTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcBusAdapter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/Dockerfile.keycloak
M	keycloak/import/esquire.json
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	pom.xml
M	postgres/Dockerfile
M	test/JaCoCo/framed.html
M	test/config-parity/config-parity.py
M	test/o11y/fleet-supercompact-k8s.bat
M	test/o11y/o11y-inventory.py
M	test/o11y/o11y-verify.py
 238 files changed, 8472 insertions(+), 12972 deletions(-)
```

---

*From `v2.0.2` till `HEAD`*
