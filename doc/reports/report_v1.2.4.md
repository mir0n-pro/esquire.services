# Release Report: v1.2.3 → v1.2.4

**Repo:** `esquire.services/develop`  
**Top commit:** `9dd573b`

---

## Release Notes

### doc/release_notes.txt


**v1.2.4-2605.1720**  v1.2.4 : JWE via Keycloak research completed, support of Haubergeon sims :  
&nbsp;                 Token Relay umbrella (Vanilla + Phantom Token Relay);  
&nbsp;                 landing page;  
&nbsp;                 /esq-cmd-tree + /esq-tree;  
&nbsp;                 four-layer observability;  
&nbsp;                 OKE two-host ingress  
&nbsp;: Feature:     Token Relay umbrella in gateway/security/tokenrelay  
&nbsp;: Feature:     gateway LandingController at GET / -- HTML og-banner page for visitors  
&nbsp;                 hitting api.esquire.mir0n.pro (replaces stock Spring 404 ProblemDetail)  
&nbsp;: Feature:     four-layer observability protocol  
&nbsp;: Feature:     enyMan /esq-cmd-tree (recursive CTE FK walk) + bizTree /esq-tree  
&nbsp;                 (H2 cache subtree); EsqTreeNode.entityPath field for race-repro  
&nbsp;                 CompareTrees diff axis  
&nbsp;: Fix:         OKE full redeploy  
&nbsp;: Refactoring: KC realm slim and import-- access-token claims reduced, Test Drivers added  
&nbsp;: Doc:         new doc/Esquire.TestingStack.md  
&nbsp;                 new doc/Esquire.Haubergeon.md  
&nbsp;                 renew doc/Esquire.ObservabilityStack.md  
&nbsp;                 renew doc/keyCloak-gateway.JWE.md  
&nbsp;   Config:       Unified k8s-local - k8s-oci down-rebuild-up routine  
&nbsp;   Components:   common,  
&nbsp;                 bizTree,  
&nbsp;                 enyMan,  
&nbsp;                 gateway, keycloak (realm import + chart),  
&nbsp;                 k8s,  
&nbsp;                 k8s-oci,  
&nbsp;                 compose,  
&nbsp;                 doc  

---

## Code Changes

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**05/14/2026** mir0n  v1.2.4 /esq-tree -- recursive subtree from biztree H2 cache  
**controller.BizTreeController**  
&nbsp;- GET /esq-tree added: takes seed tree-node id, returns seed + every descendant cached node  
&nbsp;   (real entities, virtual folders, account shortcuts) under one tree-path prefix;  
&nbsp;   counterpart to enyMan's /esq-cmd-tree (authoritative DB walk); both consumed by the  
&nbsp;   hauberk CompareTrees scenario for biztree-vs-DB diff  
service.IBizTreeService, service.impl.BizTreeService  
&nbsp;- esquireSubtree(id, rootPath, uid) added; delegates to cache.findSubtree  
**cache.IBizTreeCacheRepository**  
&nbsp;- findSubtree(seedId, rootPath) added: returns nodes whose tree_path starts with seed.tree_path  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- findSubtree implementation via SELECT_SUBTREE_SQL (rootPath-scoped LIKE)  
**cache.BizTreeCacheSql**  
&nbsp;- selectSubtree field added to Repo record  
**h2.BizTreeH2Config**  
&nbsp;- select-subtree SQL property wired into BizTreeCacheSql.Repo  
**src/main/resources/META-INF/h2-cache-sql.properties**  
&nbsp;- new property selectSubtree (recursive walk by tree_path prefix)  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**05/14/2026** mir0n  v1.2.4 EsqTreeNode entityPath + observability header rename  
**dto.EsqTreeNode**  
&nbsp;- entityPath String field added (raw ep_path / tree_entity_path) -- the diff axis for the  
&nbsp;   hauberk CompareTrees scenario comparing biztree cache against natural-FK subtree  
**dto.EsqTreeNodeMapper**  
&nbsp;- entityPath populated from biztree tree_path with virtual-folder segments stripped  
&nbsp;   (stripVirtualSegments); produces the biztree-side path string for CompareTrees diff  
**service.MdcFilter**  
&nbsp;- metrics header constants migrated to Esq-Srv-Outer-Time / Esq-Srv-Inner-Time  
&nbsp;   (was Esq-Service-Time / Esq-Backend-Time; observability four-layer protocol)  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**05/14/2026** mir0n  v1.2.4 observability: four-layer timer header constants  
EsqConstants  
&nbsp;- ESQ_GW_INNER_START_TIME, ESQ_GW_INNER_TIME added (gateway downstream-call-only window)  
&nbsp;- ESQ_SERVICE_TIME -> ESQ_SRV_OUTER_TIME (service total)  
&nbsp;- ESQ_BACKEND_TIME -> ESQ_SRV_INNER_TIME (JPA / inner)  
&nbsp;- ESQ_CAPTURE_METRICS added (hauberk trigger header)  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**05/14/2026** mir0n  v1.2.4 /esq-cmd-tree -- natural-tree subtree from FK walk  
**controller.EnyManController**  
&nbsp;- GET /esq-cmd-tree added: seed kind + id; returns List with seed + every descendant;  
&nbsp;   leaves-first ordering (level DESC); same EsqTreeNode shape as /esq for response compatibility  
service.IEnyManService, service.impl.EnyManService  
&nbsp;- esquireCommandTree(kind, id, rootPath, uid) added; delegates to EsqSubtreeRepository,  
&nbsp;   projects each EsqSubtreeRow to EsqTreeNode (entityPath = esq_entity_path.ep_path)  
jpa.EsqSubtreeRepository  (new)  
&nbsp;- native-query repository for the recursive CTE; FK-walk esq_org / esq_user / esq_account;  
&nbsp;   rootPath-scoped via ep_path LIKE; leaves-first ordering  
jpa.EsqSubtreeRow  (new)  
&nbsp;- SQL result-set row: pk, kind, name, parentPk, ep_path, level  
src/main/resources/META-INF/oracle-entity.xml, postgres-entity.xml  
&nbsp;- EsqSubtreeRepository named native query + EsqSubtreeRow @SqlResultSetMapping added (per flavor)  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**05/14/2026** mir0n  v1.2.4 -- Token Relay subsystem (Vanilla + Phantom variants); LandingController; four-layer observability; /esq-tree route  
**config.SecurityConfig**  
&nbsp;- jwtDecoder bean: NimbusReactiveJwtDecoder against KC JWKS, optionally wrapped by  
&nbsp;   JweAwareJwtDecoder when esq.jwe.private-key-path is set; 5-part JWE tokens decrypted to  
&nbsp;   inner JWS, 3-part JWS pass through  
&nbsp;- buildTokenRelayFilter() builds the single TokenRelayFilter inline (NOT @Bean -- would  
&nbsp;   auto-register globally in WebFlux and double-run after rewrite); injects  
&nbsp;   List; added before SecurityWebFiltersOrder.AUTHENTICATION  
&nbsp;- reads esq.gateway.token-relay.{token-uri, vanilla.clients, phantom.clients,  
&nbsp;   phantom.exchange-client-id, phantom.exchange-client-secret} via @Value  
&nbsp;- CORS exposed-headers extended with the four observability headers  
&nbsp;   (X-Response-Time, Esq-Gw-Inner-Time, Esq-Srv-Outer-Time, Esq-Srv-Inner-Time)  
security/tokenrelay/  (new sub-package -- 10 files; replaces 8 prior parallel classes)  
&nbsp;- TokenRelayFilter: single WebFilter; iterates configured ITokenRelayVariant list; first  
&nbsp;   variant returning Relay or Reject wins; cache lookup, KC call on MISS, Authorization  
&nbsp;   header rewrite, chain dispatch  
&nbsp;- TokenRelayCache: ConcurrentHashMap keyed by variant-supplied  
&nbsp;   cacheKey; shared by both variants  
&nbsp;- ITokenRelayClient + WebClientTokenRelayClient: single KC /token caller; takes  
&nbsp;   KcTokenRequest (form params + Basic auth), returns ExpiringJwt  
&nbsp;- ITokenRelayVariant: SPI returning VariantAction sealed type (Pass | Reject | Relay)  
&nbsp;- VanillaTokenRelay: HTTP Basic at edge -> client_credentials grant; cacheKey = client_id;  
&nbsp;   inbound-Bearer-with-azp-in-allowlist rejected with 401 (closes architectural-bypass gap)  
&nbsp;- PhantomTokenRelay: stripped Bearer at edge -> RFC 8693 token-exchange via confidential  
&nbsp;   esq-gw-exchange client; cacheKey = source-token jti  
&nbsp;- KcTokenRequest, ExpiringJwt, VariantAction: shared records / sealed type  
security.JwtClaimPeek  (new)  
&nbsp;- shared utility for peeking JWT azp / jti claims without signature validation;  
&nbsp;   used by both Token Relay variants as a routing decision before the downstream JWS  
&nbsp;   validator runs  
**security.JweAwareJwtDecoder**  
&nbsp;- kept armed but inert -- KC 26 still does not emit JWE on /token; topic parked until  
&nbsp;   v1.3+ or alternative IAS  
**security.JwksController**  
&nbsp;- kept armed alongside JweAwareJwtDecoder; serves the gateway-side RSA public key  
&nbsp;- security.IntrospectionAwareJwtDecoder  (REMOVED -- intermediate JWS+ design, superseded by Vanilla Token Relay)  
&nbsp;- security.IntrospectionClient            (REMOVED)  
&nbsp;- security.WebClientIntrospectionClient   (REMOVED)  
LandingController  (new)  
&nbsp;- GET / serves HTML landing page (og-banner image) for visitors hitting the public REST  
&nbsp;   API host (api.esquire.mir0n.pro); replaces stock Spring 404 ProblemDetail at root;  
&nbsp;   no SecurityConfig change needed (anyExchange().permitAll() covers /)  
**filters.RequestTraceFilter**  
&nbsp;- converted from Spring Cloud Gateway GlobalFilter @Order(1) to a WebFilter  
&nbsp;   @HIGHEST_PRECEDENCE so the START timestamp is captured BEFORE Spring Security runs  
&nbsp;   (auth-layer time -- Token Relay broker/exchange calls -- is now part of the gateway  
&nbsp;   OUTER timer)  
**filters.ResponseTraceFilter**  
&nbsp;- stays a GlobalFilter @Order(0) so .then() fires before NettyWriteResponseFilter (-1)  
&nbsp;   commits; consumes the start attribute set by RequestTraceFilter; service-tier headers  
&nbsp;   renamed to Esq-Srv-Outer-Time / Esq-Srv-Inner-Time (was Esq-Service-Time / Esq-Backend-Time)  
filters.InnerTimerFilter  (new)  
&nbsp;- GlobalFilter @Order(0) measuring the downstream-call-only window; captures  
&nbsp;   Esq-Gw-Inner-Start-Time at the beginning of the proxied call, computes Esq-Gw-Inner-Time  
&nbsp;   at .then(); paired with RequestTraceFilter for the outer-vs-inner breakdown  
**src/main/resources/application.yml**  
&nbsp;- esq.gateway.token-relay umbrella -- token-uri shared, vanilla.clients,  
&nbsp;   phantom.{clients, exchange-client-id, exchange-client-secret}  
&nbsp;- exposed-headers includes the four observability headers  

---

## Commits

```

-- 2026-05-18 | commit: 9dd573b | mir0n.the.programmer | v1.2.4 : JWE via Keycloak research completed, support of Haubergeon sims --
M	README.md
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/IBizTreeService.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqTreeNode.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqTreeNodeMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqTreeNodeMapperTest.java
M	compose/compose.yaml
A	doc/Esquire.Haubergeon.md
D	doc/Esquire.ObservabilityStack.docx
M	doc/Esquire.ObservabilityStack.md
A	doc/Esquire.TestingStack.md
M	doc/Testing.md
M	doc/keyCloak-gateway.JWE.md
M	doc/media/ComponentModel.png
A	doc/media/block-1.svg
A	doc/media/block-2.svg
A	doc/media/block-3.svg
A	doc/media/block-4.svg
A	doc/media/gatling.svg
A	doc/media/hauberk.svg
A	doc/media/jasmine.svg
A	doc/media/junit.svg
A	doc/media/karma.svg
A	doc/media/playwrite.svg
A	doc/media/seq-1.svg
A	doc/media/seq-2.svg
A	doc/media/seq-3.svg
A	doc/media/seq-4.svg
A	doc/media/timing.svg
A	doc/media/vitest.svg
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/v1.2.x.Planning.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqSubtreeRepository.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqSubtreeRow.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/LandingController.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/InnerTimerFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/ResponseTraceFilter.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JweAwareJwtDecoder.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JwksController.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JwtClaimPeek.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ExpiringJwt.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ITokenRelayClient.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ITokenRelayVariant.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/KcTokenRequest.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/PhantomTokenRelay.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayCache.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayFilter.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/VanillaTokenRelay.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/VariantAction.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/WebClientTokenRelayClient.java
M	gateway/src/main/resources/application.yml
M	k8s-oci/cluster/ingress.yaml
M	k8s-oci/ghcr-push.bat
A	k8s-oci/oke-rebuild.bat
M	k8s-oci/oke-up.bat
M	k8s-oci/values/activemq.yaml
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keycloak.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s-oci/values/postgres.yaml
M	k8s/charts/esquire-backend/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
A	k8s/charts/esquire-gateway/templates/secret.yaml
M	k8s/charts/esquire-gateway/templates/service.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/infra/keycloak/templates/service.yaml
M	k8s/charts/infra/keycloak/templates/statefulset.yaml
M	k8s/charts/infra/keycloak/values.yaml
A	k8s/cluster/ingress.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
A	k8s/values/activemq.yaml
A	k8s/values/backend.yaml
A	k8s/values/biztree.yaml
A	k8s/values/enyman.yaml
A	k8s/values/gateway.yaml
A	k8s/values/kcmaster.yaml
A	k8s/values/keycloak.yaml
A	k8s/values/keysmith.yaml
A	k8s/values/pacman.yaml
A	k8s/values/postgres.yaml
M	keycloak/import/esquire.json
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/postgres-acct-transaction.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
 120 files changed, 8165 insertions(+), 716 deletions(-)

-- 2026-05-09 | commit: 85c12c6 | mir0n.the.programmer | v1.2.4 Planning --
M	README.md
A	doc/Testing.md
M	doc/v1.2.x.Planning.md
 3 files changed, 198 insertions(+), 9 deletions(-)

-- 2026-05-08 | commit: 8508fc9 | mir0n.the.programmer | Create report_v1.2.3.md --
A	doc/reports/report_v1.2.3.md
 1 file changed, 275 insertions(+)
```

---

## Files Modified

```
M	README.md
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/IBizTreeService.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqTreeNode.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqTreeNodeMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqTreeNodeMapperTest.java
M	compose/compose.yaml
A	doc/Esquire.Haubergeon.md
D	doc/Esquire.ObservabilityStack.docx
M	doc/Esquire.ObservabilityStack.md
A	doc/Esquire.TestingStack.md
A	doc/Testing.md
M	doc/keyCloak-gateway.JWE.md
M	doc/media/ComponentModel.png
A	doc/media/block-1.svg
A	doc/media/block-2.svg
A	doc/media/block-3.svg
A	doc/media/block-4.svg
A	doc/media/gatling.svg
A	doc/media/hauberk.svg
A	doc/media/jasmine.svg
A	doc/media/junit.svg
A	doc/media/karma.svg
A	doc/media/playwrite.svg
A	doc/media/seq-1.svg
A	doc/media/seq-2.svg
A	doc/media/seq-3.svg
A	doc/media/seq-4.svg
A	doc/media/timing.svg
A	doc/media/vitest.svg
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
A	doc/reports/report_v1.2.3.md
M	doc/v1.2.x.Planning.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqSubtreeRepository.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqSubtreeRow.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/LandingController.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/InnerTimerFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/ResponseTraceFilter.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JweAwareJwtDecoder.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JwksController.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/JwtClaimPeek.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ExpiringJwt.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ITokenRelayClient.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/ITokenRelayVariant.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/KcTokenRequest.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/PhantomTokenRelay.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayCache.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/TokenRelayFilter.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/VanillaTokenRelay.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/VariantAction.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/tokenrelay/WebClientTokenRelayClient.java
M	gateway/src/main/resources/application.yml
M	k8s-oci/cluster/ingress.yaml
M	k8s-oci/ghcr-push.bat
A	k8s-oci/oke-rebuild.bat
M	k8s-oci/oke-up.bat
M	k8s-oci/values/activemq.yaml
M	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keycloak.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s-oci/values/postgres.yaml
M	k8s/charts/esquire-backend/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/deployment.yaml
A	k8s/charts/esquire-gateway/templates/secret.yaml
M	k8s/charts/esquire-gateway/templates/service.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/infra/keycloak/templates/service.yaml
M	k8s/charts/infra/keycloak/templates/statefulset.yaml
M	k8s/charts/infra/keycloak/values.yaml
A	k8s/cluster/ingress.yaml
M	k8s/k8s-down.bat
M	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
A	k8s/values/activemq.yaml
A	k8s/values/backend.yaml
A	k8s/values/biztree.yaml
A	k8s/values/enyman.yaml
A	k8s/values/gateway.yaml
A	k8s/values/kcmaster.yaml
A	k8s/values/keycloak.yaml
A	k8s/values/keysmith.yaml
A	k8s/values/pacman.yaml
A	k8s/values/postgres.yaml
M	keycloak/import/esquire.json
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/postgres-acct-transaction.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
M	pom.xml
 121 files changed, 8575 insertions(+), 662 deletions(-)
```

---

*From `v1.2.3` till `v1.2.4`*
