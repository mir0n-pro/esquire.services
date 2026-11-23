# Release Report: v2.0.1 → HEAD

**Repo:** `esquire.services/develop2`  
**Top commit:** `8ae938b`

---

## Release Notes

### doc/release_notes.txt


**v2.0.2-2611.2217**  v2.0.2 -- pure OAuth, a lab mode: the claims leave the token  
&nbsp;: Feature:     pure OAuth (lab): the token carries no Esquire claims; the gateway takes them from  
&nbsp;                 keySmith and keeps them while they are asked for  
&nbsp;: Feature:     a change to a user's access, or a move, removes that user from the cache of every  
&nbsp;                 gateway at once, over the message bus  
&nbsp;: Feature:     a lookup and a removal that run at the same time never leave the old claims in the  
&nbsp;                 cache  
&nbsp;: Fix:         login ids are kept in lower case, the way Keycloak keeps its user names, and a  
&nbsp;                 lookup by login id is an exact match  
&nbsp;: Config:      the mode is off by default on every target; on docker and local Kubernetes the two  
&nbsp;                 pure clients are listed at the gateway, and one setting on the services turns it on  
&nbsp;: Config:      Keycloak: two new clients for the mode, esq-angular-pure-oauth and esq-hauberk-P  
&nbsp;: Config:      the database image is built from the 2.0 seed line, db.seed2, as esquire-postgres:17-2.0  
&nbsp;: Config:      the services use Armoury 2.0.1  
&nbsp;: Config:      OKE runs the claims mode only; its e2e run skips the pure OAuth token spec  
&nbsp;: Feature:     hauberk: the perf matrix measures the load as the pure client on docker, classic and  
&nbsp;                 compact  
&nbsp;   Components:   gateway,  
&nbsp;                 gateWard,  
&nbsp;                 keySmith,  
&nbsp;                 enyMan,  
&nbsp;                 kcMaster,  
&nbsp;                 mesnie,  
&nbsp;                 common,  
&nbsp;                 keycloak,  
&nbsp;                 postgres,  
&nbsp;                 compose,  
&nbsp;                 compose-compact,  
&nbsp;                 k8s,  
&nbsp;                 k8s-compact,  
&nbsp;                 k8s-oci,  
&nbsp;                 k8s-oci-compact,  
&nbsp;                 hauberk  

---

## Code Changes

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**11/22/2026** mir0n  v2.0.2 -- pure OAuth (lab): the claims leave the token  
dto.access.EsqUserClaims  (new)  
&nbsp;- created (v2.0.2): the claims of one user -- loginId, uid, rootPath, roles, changeNo; compareTo orders two  
&nbsp;   answers of the same uid by changeNo, and another uid always compares greater  
messaging.ClaimsBusPublisher  (new)  
&nbsp;- created (v2.0.2): pure OAuth (lab) -- the claims-bus publisher: evict(uid, correlationId, requestId) sends  
&nbsp;   a DELETE of the access profile; off, it sends nothing and takes no rod  
**security.JwtClaimsExtractionFilter**  
&nbsp;- pure OAuth (lab): with no esq_uid in the token, uid / rootPath / roles come from the X-Esq-UID /  
&nbsp;   X-Esq-Root-Path / X-Esq-Roles headers the gateway set; claimsComplete() checks the values taken  
**security.SecurityConfiguration**  
&nbsp;- /esq-claims permitted with no token (pure OAuth lab: the gateway's claims lookup; no gateway route exposes  
&nbsp;   it)  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**11/22/2026** mir0n  v2.0.2 -- pure OAuth (lab); login ids in lower case  
EsqConstants  
&nbsp;- pure OAuth (lab): X_ESQ_UID / X_ESQ_ROOT_PATH / X_ESQ_ROLES, BUS_KEY_CLAIMS, PROP_PURE_OAUTH and  
&nbsp;   PROP_PURE_OAUTH_CLIENTS  
EsqUtils  
&nbsp;- loginIdOf(String): the login id in lower case (Locale.ROOT); null stays null  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**11/22/2026** mir0n  v2.0.2 -- pure OAuth (lab): the claims bus; login ids in lower case  
EnyManApplication  
&nbsp;- the ClaimsBusPublisher bean on esq.pure-oauth; the claims bus is built while esq.pure-oauth is on  
**queue.MoveQueueManager**  
&nbsp;- with the claims bus on, a moved user is evicted over it in place of the path request to KeyCloak; a failed  
&nbsp;   eviction is logged  
**service.impl.UsrService**  
&nbsp;- a new user's login id is its email in lower case  
**resources/application.yml**  
&nbsp;- esq.pure-oauth (ESQ_PURE_OAUTH, default false); the claims-bus leg (bus esquire.claims, slot claims,  
&nbsp;   SERVER; feed-await-ms 0)  

### gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt


**11/22/2026** mir0n  v2.0.2 -- pure OAuth (lab): gateWard takes the claims from keySmith in mesnie  
BizTreeCacheController  
&nbsp;- context(): uid / rootPath / roles from the X-Esq-* headers when the token carries no esq_uid (pure OAuth)  
GateWardApplication  
&nbsp;- the claims bus is built beside the entity and biz buses while esq.gateway.pure-oauth.clients is not empty  
**resources/application.yml**  
&nbsp;- esq.gateway.pure-oauth: clients, cache-idle-ms, claims-uri (keySmith in mesnie), claims-timeout-ms; the  
&nbsp;   claims-bus leg (bus esquire.claims, slot claims, CLIENT)  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**11/22/2026** mir0n  v2.0.2 -- pure OAuth (lab): the gateway takes the claims from keySmith  
lab.pureoauth.IUserClaimsClient  (new)  
&nbsp;- created (v2.0.2): pure OAuth (lab) -- fetch(loginId): the claims of a login id  
lab.pureoauth.WebClientUserClaimsClient  (new)  
&nbsp;- created (v2.0.2): pure OAuth (lab) -- GET claims-uri?loginId= (the login id strictly encoded); a 404 is a  
&nbsp;   BadCredentialsException  
lab.pureoauth.PureOAuthClaimsCache  (new)  
&nbsp;- created (v2.0.2): pure OAuth (lab) -- the claims keyed by the lower-cased login id in an Armoury  
&nbsp;   KeyedExpiringCache (AFTER_ACCESS, evicted by uid); a miss asks keySmith through the read-through get, and  
&nbsp;   an answer an eviction overtook is asked again; meter esq.biz.gw.pureoauth.total, result hit / miss / stale  
lab.pureoauth.PureOAuthAuthenticationConverter  (new)  
&nbsp;- created (v2.0.2): pure OAuth (lab) -- for a token whose azp is on the pure list, the authentication is  
&nbsp;   built from the cached claims (ROLE_ authorities, the claims on its details); a failed lookup becomes an  
&nbsp;   AuthenticationServiceException with its cause kept; any other token goes to the claims-mode converter  
lab.pureoauth.PureOAuthHeaderFilter  (new)  
&nbsp;- created (v2.0.2): pure OAuth (lab) -- strips inbound X-Esq-UID / X-Esq-Root-Path / X-Esq-Roles for every  
&nbsp;   caller, and sets them from the claims on the authentication's details  
lab.pureoauth.PureOAuthEvictionListener  (new)  
&nbsp;- created (v2.0.2): pure OAuth (lab) -- the claims-bus worker: a DELETE of an access profile evicts its uid  
&nbsp;   from PureOAuthClaimsCache; off, it takes no rod  
**config.SecurityConfig**  
&nbsp;- pure OAuth (lab): the esq.gateway.pure-oauth.* settings; the PureOAuthClaimsCache bean (a WebClient under  
&nbsp;   the routes' connect timeout and claims-timeout-ms; started only when a client is listed, and claims-uri  
&nbsp;   required then) and the PureOAuthEvictionListener bean; the JWT authentication converter is  
&nbsp;   PureOAuthAuthenticationConverter, PureOAuthHeaderFilter runs LAST, and EsqClaimsValidator gets the pure  
&nbsp;   client list  
**security.EsqClaimsValidator**  
&nbsp;- a token whose azp is on the pure OAuth list needs only sub and preferred_username; any other token is  
&nbsp;   checked for the Esquire claims as before  
**bus.BizBusRoutingFilter**  
&nbsp;- uid / rootPath / roles from the X-Esq-* headers when the gateway set them (pure OAuth), else from the  
&nbsp;   token  
GatewayApplication  
&nbsp;- the claims bus is built beside the biz bus while esq.gateway.pure-oauth.clients is not empty  
**lab.tokenrelay.TokenRelayCache**  
&nbsp;- the Armoury 2.0.1 cache names: get, putIfGreater, remove  
**resources/application.yml**  
&nbsp;- esq.gateway.pure-oauth: clients (ESQ_GW_PURE_OAUTH_CLIENTS, empty = off), cache-idle-ms, claims-uri  
&nbsp;   (keySmith /esq-claims), claims-timeout-ms; the claims-bus leg (bus esquire.claims, slot claims, CLIENT)  

### hauberk/changes.txt


**11/22/2026** mir0n  v2.0.2 -- the load as the pure OAuth client  
hauberk-P.properties  (new)  
&nbsp;- the pure OAuth overlay for docker: kc.client.id esq-hauberk-P and its dev secret  
hauberk-k8s-P.properties  (new)  
&nbsp;- the pure OAuth overlay for local k8s classic: hauberk-k8s.properties with kc.client.id esq-hauberk-P  
hauberk-k8s-compact-P.properties  (new)  
&nbsp;- the pure OAuth overlay for local k8s compact: hauberk-k8s-compact.properties with kc.client.id  
&nbsp;   esq-hauberk-P  
src/main/resources/logback.xml  (new)  
&nbsp;- the console at INFO for the whole harness, in place of the DEBUG default  
**perf-matrix.ps1**  
&nbsp;- the docker-OFF-PURE, k8s-x2-OFF-PURE and k8sc-x2-OFF-PURE cells (pure = the -P properties); the docker  
&nbsp;   gate reads the gateway container's health status  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt


**11/22/2026** mir0n  v2.0.2 -- pure OAuth (lab): no root path and no roles in KeyCloak  
**identity.KcIdentityGateway**  
&nbsp;- esq.pure-oauth read and logged; on, a moved path is not parked; storeIfGreater -> putIfGreater  
**messaging.KcRequestHandler**  
&nbsp;- with pure OAuth on, no root path attribute and no realm roles reach KeyCloak (null roles leave the realm  
&nbsp;   roles as they are); the constructor written out in place of Lombok's  
**service.impl.KcIdentityService**  
&nbsp;- consume -> getAndRemove  
**resources/application.yml**  
&nbsp;- esq.pure-oauth (ESQ_PURE_OAUTH, default false)  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**11/22/2026** mir0n  v2.0.2 -- pure OAuth (lab): the claims lookup and the claims bus; login ids in lower case  
**controller.KeySmithController**  
&nbsp;- GET /esq-claims?loginId=: the user claims, for the gateway's lookup  
**service.IKeySmithService**  
&nbsp;- esquireClaims(loginId)  
**service.impl.KeySmithService**  
&nbsp;- esquireClaims(loginId): the access profile and its roles as EsqUserClaims, 404 when none (meter op claims,  
&nbsp;   span esq.svc.key.claims); a saved access profile is evicted over the claims bus; with the claims bus on,  
&nbsp;   the identity request carries no path and no roles; the login id is saved in lower case  
**jpa.EsqAccessProfileRepository**  
&nbsp;- claims(loginId): the access profile by login id  
KeySmithApplication  
&nbsp;- the ClaimsBusPublisher bean on esq.pure-oauth; the claims bus is built while esq.pure-oauth is on  
**resources/META-INF/postgres-access-profile.xml**  
&nbsp;- EsqAccessProfileJpa.claims: the access profile WHERE au_login_id = :loginId  
**resources/META-INF/oracle-access-profile.xml**  
&nbsp;- EsqAccessProfileJpa.claims: the same lookup, matching the postgres dialect  
**resources/application.yml**  
&nbsp;- esq.pure-oauth (ESQ_PURE_OAUTH, default false); the claims-bus leg (bus esquire.claims, slot claims,  
&nbsp;   SERVER; feed-await-ms 0)  

### mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt


**11/22/2026** mir0n  v2.0.2 -- pure OAuth (lab): the claims bus  
MesnieApplication  
&nbsp;- the ClaimsBusPublisher bean on esq.pure-oauth; the claims bus is built while esq.pure-oauth is on  
**resources/application.yml**  
&nbsp;- esq.pure-oauth (ESQ_PURE_OAUTH, default false); the claims-bus leg (bus esquire.claims, slot claims,  
&nbsp;   SERVER; feed-await-ms 0)  

---

## Commits

```

-- 2026-11-22 | commit: 8ae938b | mir0n.the.programmer | Update deploy-oke.yml --
M	.github/workflows/deploy-oke.yml
 1 file changed, 2 insertions(+), 1 deletion(-)


-- 2026-11-22 | commit: b7d7aa6 | mir0n.the.programmer | v2.0.2 -- pure OAuth, a lab mode --
M	.github/scripts/deploy-compose.cmd
M	.github/scripts/deploy-local.cmd
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
M	.github/workflows/deploy-local.yml
M	.github/workflows/deploy-oke.yml
M	README.md
M	Releases.md
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqUserClaims.java
A	common/src/main/java/pro/mir0n/esquire/backend/messaging/ClaimsBusPublisher.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/SecurityConfiguration.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/test/java/pro/mir0n/esquire/backend/dto/access/EsqUserClaimsTest.java
M	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
M	compose-compact/compose.yaml
M	compose-compact/topology/esquire-topology.yml
M	compose/compose.yaml
M	compose/topology/esquire-topology.yml
M	doc/logo/herald.3.svg
D	doc/logo/herald.5.png
M	doc/logo/herald.5.svg
M	doc/media/ComponentModel.Compact.svg
M	doc/media/ComponentModel.svg
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/v2.0.x.Goals.md
M	doc/v2.0.x.Planning.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/IUserClaimsClient.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthAuthenticationConverter.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthClaimsCache.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthEvictionListener.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthHeaderFilter.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/WebClientUserClaimsClient.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidator.java
M	gateway/src/main/resources/application.yml
M	gateway/src/test/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilterTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidatorTest.java
M	hauberk/changes.txt
A	hauberk/hauberk-P.properties
A	hauberk/hauberk-k8s-P.properties
A	hauberk/hauberk-k8s-compact-P.properties
M	hauberk/perf-matrix.ps1
M	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
M	k8s-compact/charts/esquire-gateward/values.yaml
M	k8s-compact/charts/esquire-mesnie/templates/configmap.yaml
M	k8s-compact/charts/esquire-mesnie/values.yaml
M	k8s-compact/charts/esquire-topology/esquire-topology.yml
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/paacman.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-compact/values/postgres.yaml
M	k8s-oci-compact/esquire-topology.yml
M	k8s-oci-compact/ghcr-push.bat
M	k8s-oci/esquire-topology.yml
M	k8s-oci/ghcr-push.bat
A	k8s/1st.bat
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/k8s-rebuild.bat
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
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/ParkedPathTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/jpa/EsqAccessProfileRepository.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keySmith/src/main/resources/META-INF/postgres-access-profile.xml
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/import/esquire.json
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	pom.xml
M	postgres/Dockerfile
M	postgres/compose.yaml
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
M	test/config-parity/config-parity.py
M	test/o11y/o11y-verify.py
 131 files changed, 2657 insertions(+), 446 deletions(-)
```

---

## Files Modified

```
M	.github/scripts/deploy-compose.cmd
M	.github/scripts/deploy-local.cmd
M	.github/scripts/deploy-oke.sh
M	.github/scripts/oke-build-push.sh
M	.github/workflows/deploy-local.yml
M	.github/workflows/deploy-oke.yml
M	README.md
M	Releases.md
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqUserClaims.java
A	common/src/main/java/pro/mir0n/esquire/backend/messaging/ClaimsBusPublisher.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/SecurityConfiguration.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/test/java/pro/mir0n/esquire/backend/dto/access/EsqUserClaimsTest.java
M	common/src/test/java/pro/mir0n/esquire/common/EsqUtilsTest.java
M	compose-compact/compose.yaml
M	compose-compact/topology/esquire-topology.yml
M	compose/compose.yaml
M	compose/topology/esquire-topology.yml
M	doc/logo/herald.3.svg
D	doc/logo/herald.5.png
M	doc/logo/herald.5.svg
M	doc/media/ComponentModel.Compact.svg
M	doc/media/ComponentModel.svg
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/v2.0.x.Goals.md
M	doc/v2.0.x.Planning.md
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManager.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/queue/MoveQueueManagerTest.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/BizTreeCacheController.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/GateWardApplication.java
M	gateWard/src/main/java/pro/mir0n/esquire/gateWard/changes.txt
M	gateWard/src/main/resources/application.yml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/IUserClaimsClient.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthAuthenticationConverter.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthClaimsCache.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthEvictionListener.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthHeaderFilter.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/pureoauth/WebClientUserClaimsClient.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidator.java
M	gateway/src/main/resources/application.yml
M	gateway/src/test/java/pro/mir0n/esquire/gateway/bus/BizBusRoutingFilterTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/lab/pureoauth/PureOAuthTest.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidatorTest.java
M	hauberk/changes.txt
A	hauberk/hauberk-P.properties
A	hauberk/hauberk-k8s-P.properties
A	hauberk/hauberk-k8s-compact-P.properties
M	hauberk/perf-matrix.ps1
M	k8s-compact/charts/esquire-gateward/templates/configmap.yaml
M	k8s-compact/charts/esquire-gateward/values.yaml
M	k8s-compact/charts/esquire-mesnie/templates/configmap.yaml
M	k8s-compact/charts/esquire-mesnie/values.yaml
M	k8s-compact/charts/esquire-topology/esquire-topology.yml
M	k8s-compact/k8s-rebuild.bat
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/paacman.yaml
M	k8s-compact/values/pacman.yaml
M	k8s-compact/values/postgres.yaml
M	k8s-oci-compact/esquire-topology.yml
M	k8s-oci-compact/ghcr-push.bat
M	k8s-oci/esquire-topology.yml
M	k8s-oci/ghcr-push.bat
A	k8s/1st.bat
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-topology/esquire-topology.yml
M	k8s/k8s-rebuild.bat
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
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/identity/KcIdentityGateway.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/ParkedPathTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/jpa/EsqAccessProfileRepository.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keySmith/src/main/resources/META-INF/postgres-access-profile.xml
M	keySmith/src/main/resources/application.yml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	keycloak/import/esquire.json
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/MesnieApplication.java
M	mesnie/src/main/java/pro/mir0n/esquire/mesnie/changes.txt
M	mesnie/src/main/resources/application.yml
M	pom.xml
M	postgres/Dockerfile
M	postgres/compose.yaml
M	test/audit-smoke/README.md
M	test/audit-smoke/run.sh
M	test/config-parity/config-parity.py
M	test/o11y/o11y-verify.py
 131 files changed, 2659 insertions(+), 447 deletions(-)
```

---

*From `v2.0.1` till `HEAD`*
