# Release Report: v1.2.2 → v1.2.3

**Repo:** `esquire.services/develop`  
**Top commit:** `5de4b58`

---

## Release Notes

### doc/release_notes.txt


**v1.2.3-2605.0818**  v1.2.3 BFF sprint -- OKE production cutover (BFF goes live at https://esquire.mir0n.pro)  
&nbsp;: Feature: new values/backend.yaml -- BFF prod overrides (ClusterIP, ghcr v1.2.3,  
&nbsp;              removed values/frontend.yaml  
&nbsp;: Feature: cluster/ingress.yaml redesigned -- single Ingress, two rules:  
&nbsp;             /kc-auth -> KC, / -> esquire-backend-backend:3000;  
&nbsp;             esquire-public-api ingress retired (gateway no longer publicly reachable)  
&nbsp;: Config: /auth -> /kc-auth  
&nbsp;: modified components  
&nbsp;      k8s-oci  

**v1.2.3-2605.0814**  v1.2.3 BFF sprint -- compose/k8s/keycloak alignment with BFF tier (no Java source changes)  
&nbsp;: Config: keycloak -- esq-angular client converted public -> confidential in place; PKCE S256 standard flow;  
&nbsp;                         redirectUris include :3000, :4200, host.docker.internal:*, prod;  
&nbsp;                         KC_HTTP_RELATIVE_PATH set to /kc-auth (from /auth) -- shared by browser KC redirects and BFF discovery  
&nbsp;: Config: compose -- new backend service (BFF, multi-stage build of explorer/backend with baked SPA);  
&nbsp;                       new frontend service (ng-serve live-reload :4200, proxy.conf.docker.json -> backend:3000);  
&nbsp;                       KC TCP healthcheck on :8080 (KC 26.4.7 mgmt-endpoint quirk on :9000);  
&nbsp;                       gateway env KEYCLOAK_PATH=/kc-auth;  
&nbsp;                       backend ALLOWED_ORIGINS=http://localhost:3000,http://localhost:4200 (dual-port login UX);  
&nbsp;                       added compose-rebuild.bat (mvn + docker compose build + recreate; per-service or all)  
&nbsp;: Config: k8s -- new chart esquire-backend (BFF; strategy: Recreate; Secret + ConfigMap; LoadBalancer lbPort 4200);  
&nbsp;                   gateway chart configmap pre-resolves SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI directly  
&nbsp;                   (Spring Boot 3.5 ${KEYCLOAK_PATH:} placeholder fails to interpolate under envFrom);  
&nbsp;                   gateway/biztree/enyman/pacman/keysmith/kcmaster values.yaml: springProfilesActive defaults baked in  
&nbsp;                   (sidesteps helm --set comma-escape issue);  
&nbsp;                   infra/keycloak values.yaml: hostname=host.docker.internal, httpRelativePath=/kc-auth,  
&nbsp;                   securityContext.fsGroup=1000, KC_HTTP_ENABLED=true  
&nbsp;: Feature: k8s scripts (local Docker Desktop dev workflow) -- YYMM.DDHH tag (or YYMM.DDHHmm on collision)  
&nbsp;: Doc: doc/keyCloak-gateway.JWE.md SUPERSEDED preamble expanded with v1.2.3 findings  
&nbsp;: modified components  
&nbsp;      compose,  
&nbsp;      k8s,  
&nbsp;      keycloak,  
&nbsp;      doc  

---

## Code Changes

---

## Commits

```

-- 2026-05-08 | commit: 5de4b58 | mir0n.the.programmer | v1.2.3 Finalazing --
M	README.md
M	k8s-oci/ghcr-push.bat
 2 files changed, 119 insertions(+), 31 deletions(-)


-- 2026-05-08 | commit: 0830dbe | mir0n.the.programmer | v1.2.3 BFF sprint -- OKE production cutover (BFF goes live at https://esquire.mir0n.pro) --
M	README.md
A	doc/media/ComponentModel.png
D	doc/media/ComponentModel.svg
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/v1.2.x.Planning.md
M	k8s-oci/cluster/ingress.yaml
M	k8s-oci/ghcr-push.bat
M	k8s-oci/oke-down.bat
M	k8s-oci/oke-up.bat
M	k8s-oci/values/activemq.yaml
A	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
D	k8s-oci/values/frontend.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keycloak.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s-oci/values/postgres.yaml
D	k8s/1-by-1/1.1) postgres install.bat
D	k8s/1-by-1/1.2)show.all.bat
D	k8s/1-by-1/1.3) postgres exec.bat
D	k8s/1-by-1/1.9) postgres uninstall.bat
D	k8s/1-by-1/10.1) frontend install.bat
D	k8s/1-by-1/10.3) frontend exec.bat
D	k8s/1-by-1/10.9) frontend uninstall.bat
D	k8s/1-by-1/2.1) aMQ install.bat
D	k8s/1-by-1/2.3) aMQ exec.bat
D	k8s/1-by-1/2.9) aMQ uninstall.bat
D	k8s/1-by-1/3.1) keycloak install.bat
D	k8s/1-by-1/3.3) keycloak exec.bat
D	k8s/1-by-1/3.9) keycloak uninstall.bat
D	k8s/1-by-1/4.1) biztree install.bat
D	k8s/1-by-1/4.3) biztree exec.bat
D	k8s/1-by-1/4.9) biztree uninstall.bat
D	k8s/1-by-1/5.1) enyman install.bat
D	k8s/1-by-1/5.3) enyman exec.bat
D	k8s/1-by-1/5.9) enyman uninstall.bat
D	k8s/1-by-1/6.1) pacman install.bat
D	k8s/1-by-1/6.3) pacman exec.bat
D	k8s/1-by-1/6.9) pacman uninstall.bat
D	k8s/1-by-1/7.1) keysmith install.bat
D	k8s/1-by-1/7.3) keysmith exec.bat
D	k8s/1-by-1/7.9) keysmith uninstall.bat
D	k8s/1-by-1/8.1) kcmaster install.bat
D	k8s/1-by-1/8.3) kcmaster exec.bat
D	k8s/1-by-1/8.9) kcmaster uninstall.bat
D	k8s/1-by-1/9.1) gateway install.bat
D	k8s/1-by-1/9.3) gateway exec.bat
D	k8s/1-by-1/9.9) gateway uninstall.bat
D	k8s/charts/esquire-frontend/Chart.yaml
D	k8s/charts/esquire-frontend/templates/configmap.yaml
D	k8s/charts/esquire-frontend/templates/deployment.yaml
D	k8s/charts/esquire-frontend/templates/service.yaml
D	k8s/charts/esquire-frontend/values.yaml
 57 files changed, 302 insertions(+), 14191 deletions(-)

-- 2026-05-08 | commit: 729a19e | mir0n.the.programmer | v1.2.3 BFF sprint -- compose/k8s/keycloak alignment with BFF tier (no Java source changes) --
A	activemq/docker-build.bat
A	bizTree/docker-compose-build.bat
A	build.all.bat
A	compose/compose-rebuild.bat
M	compose/compose.yaml
M	doc/keyCloak-gateway.JWE.md
M	doc/release_notes.txt
M	doc/v1.2.x.Planning.md
A	enyMan/docker-compose-build.bat
A	gateway/docker-compose-build.bat
A	k8s/charts/esquire-backend/Chart.yaml
A	k8s/charts/esquire-backend/templates/configmap.yaml
A	k8s/charts/esquire-backend/templates/deployment.yaml
A	k8s/charts/esquire-backend/templates/secret.yaml
A	k8s/charts/esquire-backend/templates/service.yaml
A	k8s/charts/esquire-backend/values.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/infra/keycloak/values.yaml
M	k8s/k8s-down.bat
A	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
A	kcMaster/docker-compose-build.bat
A	keySmith/docker-compose-build.bat
A	keycloak/docker-build.bat
M	keycloak/import/esquire.json
M	keycloak/themes/esquire-explorer/login/resources/img/unknown.ico
A	pacMan/docker-compose-build.bat
M	pom.xml
A	postgres/docker-build.bat
 35 files changed, 4148 insertions(+), 2676 deletions(-)

-- 2026-05-05 | commit: 0c8308c | mir0n.the.programmer | v1.2.3 sprint started --
M	README.md
A	doc/v1.2.x.Planning.md
M	enyMan/src/main/resources/logback-spring.xml
 3 files changed, 239 insertions(+), 8 deletions(-)

-- 2026-05-03 | commit: c5750e3 | mir0n.the.programmer | Update report_v1.2.2.md --
M	doc/reports/report_v1.2.2.md
 1 file changed, 332 insertions(+), 9 deletions(-)
```

---

## Files Modified

```
M	README.md
A	activemq/docker-build.bat
A	bizTree/docker-compose-build.bat
A	build.all.bat
A	compose/compose-rebuild.bat
M	compose/compose.yaml
M	doc/keyCloak-gateway.JWE.md
A	doc/media/ComponentModel.png
D	doc/media/ComponentModel.svg
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/reports/report_v1.2.2.md
A	doc/v1.2.x.Planning.md
A	enyMan/docker-compose-build.bat
M	enyMan/src/main/resources/logback-spring.xml
A	gateway/docker-compose-build.bat
M	k8s-oci/cluster/ingress.yaml
M	k8s-oci/ghcr-push.bat
M	k8s-oci/oke-down.bat
M	k8s-oci/oke-up.bat
M	k8s-oci/values/activemq.yaml
A	k8s-oci/values/backend.yaml
M	k8s-oci/values/biztree.yaml
M	k8s-oci/values/enyman.yaml
D	k8s-oci/values/frontend.yaml
M	k8s-oci/values/gateway.yaml
M	k8s-oci/values/kcmaster.yaml
M	k8s-oci/values/keycloak.yaml
M	k8s-oci/values/keysmith.yaml
M	k8s-oci/values/pacman.yaml
M	k8s-oci/values/postgres.yaml
D	k8s/1-by-1/1.1) postgres install.bat
D	k8s/1-by-1/1.2)show.all.bat
D	k8s/1-by-1/1.3) postgres exec.bat
D	k8s/1-by-1/1.9) postgres uninstall.bat
D	k8s/1-by-1/10.1) frontend install.bat
D	k8s/1-by-1/10.3) frontend exec.bat
D	k8s/1-by-1/10.9) frontend uninstall.bat
D	k8s/1-by-1/2.1) aMQ install.bat
D	k8s/1-by-1/2.3) aMQ exec.bat
D	k8s/1-by-1/2.9) aMQ uninstall.bat
D	k8s/1-by-1/3.1) keycloak install.bat
D	k8s/1-by-1/3.3) keycloak exec.bat
D	k8s/1-by-1/3.9) keycloak uninstall.bat
D	k8s/1-by-1/4.1) biztree install.bat
D	k8s/1-by-1/4.3) biztree exec.bat
D	k8s/1-by-1/4.9) biztree uninstall.bat
D	k8s/1-by-1/5.1) enyman install.bat
D	k8s/1-by-1/5.3) enyman exec.bat
D	k8s/1-by-1/5.9) enyman uninstall.bat
D	k8s/1-by-1/6.1) pacman install.bat
D	k8s/1-by-1/6.3) pacman exec.bat
D	k8s/1-by-1/6.9) pacman uninstall.bat
D	k8s/1-by-1/7.1) keysmith install.bat
D	k8s/1-by-1/7.3) keysmith exec.bat
D	k8s/1-by-1/7.9) keysmith uninstall.bat
D	k8s/1-by-1/8.1) kcmaster install.bat
D	k8s/1-by-1/8.3) kcmaster exec.bat
D	k8s/1-by-1/8.9) kcmaster uninstall.bat
D	k8s/1-by-1/9.1) gateway install.bat
D	k8s/1-by-1/9.3) gateway exec.bat
D	k8s/1-by-1/9.9) gateway uninstall.bat
A	k8s/charts/esquire-backend/Chart.yaml
A	k8s/charts/esquire-backend/templates/configmap.yaml
A	k8s/charts/esquire-backend/templates/deployment.yaml
A	k8s/charts/esquire-backend/templates/secret.yaml
A	k8s/charts/esquire-backend/templates/service.yaml
A	k8s/charts/esquire-backend/values.yaml
M	k8s/charts/esquire-biztree/values.yaml
M	k8s/charts/esquire-enyman/values.yaml
D	k8s/charts/esquire-frontend/Chart.yaml
D	k8s/charts/esquire-frontend/templates/configmap.yaml
D	k8s/charts/esquire-frontend/templates/deployment.yaml
D	k8s/charts/esquire-frontend/templates/service.yaml
D	k8s/charts/esquire-frontend/values.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/values.yaml
M	k8s/charts/esquire-kcmaster/values.yaml
M	k8s/charts/esquire-keysmith/values.yaml
M	k8s/charts/esquire-pacman/values.yaml
M	k8s/charts/infra/keycloak/values.yaml
M	k8s/k8s-down.bat
A	k8s/k8s-rebuild.bat
M	k8s/k8s-up.bat
A	kcMaster/docker-compose-build.bat
A	keySmith/docker-compose-build.bat
A	keycloak/docker-build.bat
M	keycloak/import/esquire.json
M	keycloak/themes/esquire-explorer/login/resources/img/unknown.ico
A	pacMan/docker-compose-build.bat
M	pom.xml
A	postgres/docker-build.bat
 92 files changed, 5121 insertions(+), 16896 deletions(-)
```

---

*From `v1.2.2` till `v1.2.3`*
