# Release Report: v1.2.15 → v1.2.16

**Repo:** `esquire.services/develop`  
**Top commit:** `6f96549`

---

## Release Notes

### doc/release_notes.txt


**v1.2.16-2610.1117**  v1.2.16 -- expiring cache: shared code  
&nbsp;: Refactoring: expiring cache: shared code  
&nbsp;: Fix:         a cached sign-in token is dropped once it is too old to be used  
&nbsp;: Doc:         doc\Esquire.ContinuingDev.md  
&nbsp;                 doc\Esquire.Auth.md  
&nbsp;                 doc\Esquire.Vision.md  
&nbsp;                 doc\Esquire.DevProcess.md  
&nbsp;   Components:   gateway,  
&nbsp;                 mir0n-utils,  
&nbsp;                 k8s,  
&nbsp;                 k8s-compact  

---

## Code Changes

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**10/11/2026** mir0n  v1.2.16 -- the token relay cache on the shared ExpiringCache  
**lab.tokenrelay.TokenRelayCache**  
&nbsp;- use ExpiringCache  
**lab.tokenrelay.ExpiringJwt**  
&nbsp;- implements Comparable, ordered by expiresAt  

### mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt


**10/11/2026** mir0n  v1.2.16 -- a cache read that does not take the value away  
**concurrent.ExpiringCache**  
&nbsp;- peek(K): reads the value WITHOUT taking it away, with the same age check consume() applies.  
&nbsp;   consume() is hand-off semantics; a cache reads one entry many times over its life, and through  
&nbsp;   consume() every read after the first would be a miss  

---

## Commits

```

-- 2026-10-12 | commit: 6f96549 | mir0n.the.programmer | Finalization --
M	README.md
 1 file changed, 4 insertions(+), 5 deletions(-)


-- 2026-10-11 | commit: 75e839a | mir0n.the.programmer |  v1.2.16 -- expiring cache: shared code, doc updates --
M	README.md
M	Releases.md
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	doc/v1.2.x.Planning.md
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ExpiringJwt.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/pacman.yaml
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keycloak.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
M	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
M	pom.xml
 32 files changed, 216 insertions(+), 44 deletions(-)

-- 2026-09-08 | commit: fec575d | mir0n.the.programmer | Create report_v1.2.15.md --
A	doc/reports/report_v1.2.15.md
 1 file changed, 171 insertions(+)
```

---

## Files Modified

```
M	README.md
M	Releases.md
M	doc/Esquire.Auth.md
M	doc/Esquire.ContinuingDev.md
M	doc/Esquire.DevProcess.md
M	doc/Esquire.TestingStack.md
M	doc/Esquire.Vision.md
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
A	doc/reports/report_v1.2.15.md
M	doc/v1.2.x.Planning.md
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/ExpiringJwt.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/lab/tokenrelay/TokenRelayCache.java
M	k8s-compact/values/aukeep.yaml
M	k8s-compact/values/backend.yaml
M	k8s-compact/values/gateward.yaml
M	k8s-compact/values/mesnie.yaml
M	k8s-compact/values/pacman.yaml
M	k8s/values/activemq.yaml
M	k8s/values/aukeep.yaml
M	k8s/values/backend.yaml
M	k8s/values/biztree.yaml
M	k8s/values/enyman.yaml
M	k8s/values/gateway.yaml
M	k8s/values/kcmaster.yaml
M	k8s/values/keycloak.yaml
M	k8s/values/keysmith.yaml
M	k8s/values/pacman.yaml
M	k8s/values/postgres.yaml
M	mir0n-utils/src/main/java/pro/mir0n/utils/changes.txt
M	mir0n-utils/src/main/java/pro/mir0n/utils/concurrent/ExpiringCache.java
M	pom.xml
 33 files changed, 391 insertions(+), 49 deletions(-)
```

---

*From `v1.2.15` till `v1.2.16`*
