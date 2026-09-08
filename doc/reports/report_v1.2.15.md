# Release Report: v1.2.14 → v1.2.15

**Repo:** `esquire.services/develop`  
**Top commit:** `a2c5a9f`

---

## Release Notes

### doc/release_notes.txt


**v1.2.15-2609.0618**  v1.2.15 -- the seeded logins match the database  
&nbsp;: Doc:         doc\Esquire.ContinuingDev.md  
&nbsp;   Components:   keycloak,  
&nbsp;                 postgres,  
&nbsp;                 k8s-compact,  
&nbsp;                 k8s  

**v1.2.15-2609.0601**  v1.2.15 -- signing out on every deployment address  
&nbsp;: Fix:         signing out completes on the local Kubernetes and AWS addresses  
&nbsp;: Config:      every deployment address the sign-in server accepts for signing in it now also  
&nbsp;                 accepts for signing out and for browser calls  
&nbsp;: Config:      the KeyCloak image carries the v1.2.15 stamp, and the local k8s stack points at it  
&nbsp;: Doc:         doc\Esquire.ContinuingDev.md  
&nbsp;   Components:   keycloak,  
&nbsp;                 k8s-compact  

**v1.2.15-2609.0523**  v1.2.15 -- a user whose roles are wrong is told so  
&nbsp;: Fix:         a user holding no role is refused with a message naming the account, instead of  
&nbsp;                 being reported as an expired session and sent round the login again  
&nbsp;: Fix:         a user holding roles but not TREE gets the same message, instead of a blank refusal  
&nbsp;: Fix:         the entity-kind list stays readable on the open path when the caller's token is poor  
&nbsp;   Components:   gateway,  
&nbsp;                 common  

---

## Code Changes

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**09/05/2026** mir0n  v1.2.15 -- the open path is open to a poor token too  
**security.JwtClaimsExtractionFilter**  
&nbsp;- shouldNotFilter() adds /esq-kinds, the path both security configs already permitAll  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**09/05/2026** mir0n  v1.2.15 -- a refusal that names the caller  
security.EsqAccessDeniedHandler  (new)  
&nbsp;- created: writes a ProblemDetail on a refused route, naming the caller from preferred_username,  
&nbsp;   esq_uid or sub, in place of Spring's empty 403  
**security.EsqClaimsValidator**  
&nbsp;- validates the identity claims only: the realm-role check and holdsRealmRole() are gone, and the  
&nbsp;   refusal log drops the realmRole field  
**config.SecurityConfig**  
&nbsp;- exceptionHandling() names EsqAccessDeniedHandler as the access-denied handler  

---

## Commits

```

-- 2026-09-08 | commit: a2c5a9f | mir0n.the.programmer | v1.2.15 -- the seeded logins match the database --
M	README.md
M	Releases.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.HighAvailability.md
A	doc/Esquire.Scrum.Process.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/release_notes.txt
M	doc/v1.2.x.Planning.md
M	k8s-aws-compact/values/keycloak.yaml
M	k8s-aws-compact/values/postgres.yaml
M	k8s-aws/values/keycloak.yaml
M	k8s-aws/values/postgres.yaml
M	k8s-compact/values/keycloak.yaml
M	k8s-compact/values/postgres.yaml
M	k8s/values/keycloak.yaml
M	k8s/values/postgres.yaml
M	keycloak/Dockerfile.keycloak
M	keycloak/import/esquire.json
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	postgres/Dockerfile
 23 files changed, 404 insertions(+), 181 deletions(-)


-- 2026-09-06 | commit: 8a5cc01 | mir0n.the.programmer | v1.2.15 -- signing out on every deployment address --
M	doc/Esquire.ContinuingDev.md
M	doc/release_notes.txt
M	k8s-compact/values/keycloak.yaml
M	keycloak/Dockerfile.keycloak
M	keycloak/import/esquire.json
 5 files changed, 61 insertions(+), 5 deletions(-)

-- 2026-09-06 | commit: 9a1d687 | mir0n.the.programmer | v1.2.15 -- a user whose roles are wrong is told so --
M	README.md
M	Releases.md
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.TestingStack.md
M	doc/model/ESQ.2026.ERD.png
M	doc/release_notes.txt
M	doc/v1.2.x.Planning.md
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqAccessDeniedHandler.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidator.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidatorTest.java
M	pom.xml
A	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/CredentialParamsIntegrationTest.java
 17 files changed, 313 insertions(+), 69 deletions(-)

-- 2026-09-02 | commit: 9e7fab1 | mir0n.the.programmer | Create report_v1.2.14.md --
A	doc/reports/report_v1.2.14.md
 1 file changed, 991 insertions(+)
```

---

## Files Modified

```
M	README.md
M	Releases.md
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtClaimsExtractionFilter.java
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.DevSetup.md
M	doc/Esquire.HighAvailability.md
A	doc/Esquire.Scrum.Process.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/model/ESQ.2026.ERD.png
M	doc/release_notes.txt
A	doc/reports/report_v1.2.14.md
M	doc/v1.2.x.Planning.md
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
A	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqAccessDeniedHandler.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidator.java
M	gateway/src/test/java/pro/mir0n/esquire/gateway/security/EsqClaimsValidatorTest.java
M	k8s-aws-compact/values/keycloak.yaml
M	k8s-aws-compact/values/postgres.yaml
M	k8s-aws/values/keycloak.yaml
M	k8s-aws/values/postgres.yaml
M	k8s-compact/values/keycloak.yaml
M	k8s-compact/values/postgres.yaml
M	k8s/values/keycloak.yaml
M	k8s/values/postgres.yaml
M	keycloak/Dockerfile.keycloak
M	keycloak/import/esquire.json
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pom.xml
M	postgres/Dockerfile
A	tp-activemq/src/test/java/pro/mir0n/esquire/tp/activemq/CredentialParamsIntegrationTest.java
 34 files changed, 1766 insertions(+), 252 deletions(-)
```

---

*From `v1.2.14` till `v1.2.15`*
