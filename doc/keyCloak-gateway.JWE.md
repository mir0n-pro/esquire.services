# Keycloak / Gateway -- JWE Token Encryption

## SUPERSEDED -- 2026-05-07

This design was retired during the v1.2.3 BFF sprint. The original goal -- "the browser must not be able to read or modify the access token" -- is now met by a different mechanism (BFF cookie session). The historical content below is preserved for reference; the rest of this section captures **what we actually learned** and the **compromise we settled on**.

### What was attempted

The "Keycloak encrypts access tokens at source" architecture below was implemented end-to-end against a stock KC 26.4.7:

- Gateway side built: `JweAwareJwtDecoder` (5-part JWE branching), `JwksController` serving `/jwe-jwks`, RSA-2048 keypair generation, Spring config to wire the decoder, `esq.jwe.private-key-path` env var.
- KC side patched: `esq-angular` client received `access.token.encrypted.response.alg=RSA-OAEP`, `access.token.encrypted.response.enc=A256GCM`, `use.jwks.url=true`, `jwks.url=http://gateway:7070/jwe-jwks` -- via both Admin UI temp-toggle and `patch-jwe-activate.py`.
- Realm reset, KC rebuilt, full-stack relaunch.

### What was observed

KC continued to deliver **plain 3-part JWS** at the auth-code token endpoint regardless of attribute, client-policy, or realm-level configuration. Verified with:

- `tcpdump`-style trace of the `/token` response body -- 3 dot-separated parts, never 5.
- KC debug log at `DEBUG org.keycloak.protocol.oidc` -- no encryption code path entered for the auth-code flow.
- Cross-check via Postman direct token request -- same plain JWS.
- Repeated the test with `id.token.encrypted.response.alg` set in addition (just to confirm KC *can* encrypt) -- KC encrypted the **ID token** as JWE but emitted the **access token** still as plain JWS.

The investigation notes (discovery doc, client-policy enumeration, full debug log) are filed under `services/doc/BFF.md` *On Keycloak token encryption (and why we don't use it)*.

### Why

In KC 26 the `access.token.encrypted.response.alg` attribute is honored only on certain response paths -- notably JWT-mode authorization responses (`response_mode=jwt`) and a couple of niche endpoints -- not on the standard auth-code `/token` endpoint that every OIDC client uses. The encryption code path is wired for the ID-token side and for response-mode JWT, not for the access-token side of the auth-code token response. Achieving "JWE access tokens out of the standard token endpoint" requires a custom Java SPI (`OIDCAccessTokenResponseMapper`-style) and a KC image rebuild -- out of scope for v1.2.3.

### Compromise -- BFF cookie session in place of JWE

The original design goal was *browser cannot see or modify the token*. JWE achieved that by encrypting the bytes the browser holds. The BFF achieves it by **never giving the browser the bytes at all**:

- The browser holds an opaque server-side session id (`esq.sid` cookie, HttpOnly + SameSite=Lax) -- not the access token.
- The access + refresh tokens live in the BFF's session store, server-side.
- The BFF injects `Authorization: Bearer <jws>` on the cluster-internal `/api/*` proxy hop. The token never crosses the browser boundary.
- The browser cannot inspect claims, roles, or expiry; cannot tamper with the token; cannot replay it.

Same threat model, different mechanism. See `services/doc/BFF.md` *Token security model* for the full rationale.

### What it cost

- **Added:** the BFF tier (`explorer/backend/`) -- Express, openid-client v5, session store, OIDC code+PKCE. Cookie domain + CSRF semantics now part of the deployment story.
- **Removed:** all gateway-side JWE artifacts -- `JweAwareJwtDecoder`, `JwksController`, `/jwe-jwks` endpoint, `esq.jwe.*` config keys, `gateway/JWE.scripts/`, `gateway/conf/jwe-*.pem`. Gateway is back to stock `NimbusReactiveJwtDecoder` against KC JWKS.
- **Net deployment surface:** one extra service (BFF) but one less custom decoder + key-management story -- about even, with the BFF carrying its own clearer operational cost (session store sizing, cookie lifetime, replica policy `strategy: Recreate`).

### When JWE could come back

The `JweAwareJwtDecoder` design was sound; only the KC side failed to emit JWE. The decoder branches on part count (3 = JWS, 5 = JWE), so a future scenario could revive it without disturbing existing JWS clients:

- An IdP that genuinely emits JWE access tokens (e.g. ForgeRock, PingFederate, or a custom KC SPI).
- A non-browser client (mobile, service account) that wraps its own token in JWE before forwarding -- BFF-to-gateway leg confidentiality, which is currently relying on cluster-internal HTTPS or in-cluster plain HTTP.

The gateway-side implementation is preserved in the local backup tree at `C:\MyProjects\garbage\esquire.JWE\JWE.services.attempt2\`. Reintroduction is a copy-back of the four files (`JweAwareJwtDecoder.java`, `JwksController.java`, `SecurityConfig` JWE branch, `application.yml` keys) plus an env var to point at the private key.

### Don't repeat the dead-end

If a future sprint asks "should we encrypt the access token end-to-end?" -- the answer is **not via stock Keycloak attributes**. Either accept the BFF model (browser holds an opaque cookie, the token is server-side only), or budget an SPI implementation up front. Don't reattempt the attribute-flag approach; it was conclusively eliminated in this sprint.

### External references

- **GitHub keycloak/keycloak#32919** -- reported panic in JWT encryption code paths in recent KC versions; corroborates that access-token encryption support is partial / not stabilized in stock KC.
- **RFC 8693 -- OAuth 2.0 Token Exchange**, supported in KC 26.2+ via the "Standard Token Exchange" client switch. *Not* the same thing as encrypting access tokens -- it's the supported path for service-to-service token rotation / downstream scoping. Worth keeping in mind for a future BFF -> gateway hop, but it doesn't replace JWE.
- **Keycloak Upgrading Guide** -- check between release bumps for token-related config drift; `TOKEN_EXCHANGE_ERROR` is the server-log signal for client-side token-exchange misconfiguration.

### Stance: wait it out

Don't reattempt access-token JWE on stock KC until Keycloak themselves clean up the mess (stabilize the encryption code paths, finalize attribute semantics, close the open issues like #32919). Until then, accept the BFF cookie model as the answer. Reopen this design only when paired with an IdP -- KC or otherwise -- that ships proper, stable access-token JWE on the standard `/token` endpoint.

### v1.2.3 scope: Java pure-REST integration test playground

Within v1.2.3 scope, an integration-test playground will be built using a **Java pure-REST client** (not the Node.js BFF tier). It connects via OAuth client-credentials grant -- no interactive user authentication flow -- and exercises the gateway / services hops directly without a BFF in the loop. This is expected to raise its own set of KC-implementation questions on the client-credentials side (token shape, scopes, response differences vs. auth-code) which are tracked separately from the JWE story.

### v1.x.x backlog: alternative OAuth IAS

Future v1.x.x sprints will add support for an alternative OAuth Identity Authorization Server where the JWS-vs-JWE distinction may not be a constraint (i.e. an IdP that emits JWE on the standard token endpoint out of the box, without custom SPI work). When that lands, the gateway-side `JweAwareJwtDecoder` design (preserved in the backup tree) becomes reusable as-is.

---

## Original purpose (historical)

Keycloak issues JWT access tokens that are signed (JWS) but not encrypted.
Their payload is readable by anyone who intercepts the HTTP traffic.
JWE encryption wraps the signed token in an opaque envelope so the payload
is visible only to the gateway, which holds the decryption key.
Angular holds an unreadable blob -- it cannot inspect claims, roles, or expiry.

---

## Status as of 2026-04-16 (now superseded)

Infrastructure complete.  Approach switched to **Keycloak-encrypts-at-source**
(see Architecture below).  Gateway side committed; KC patch applied via
`gateway/JWE.scripts/activate-jwe.bat`.  Awaiting rebuild and full-stack test.

**Previous approach (abandoned)**: Angular `jweInterceptor` wrapped JWS -> JWE.
Caused login failure ("Invalid or expired token") from `esquireKey()` profile
load in `app-shell.ts`.  Root cause not identified.  Angular interceptor files
are retained but are a no-op in the current approach (KC tokens are already
5-part JWE; the interceptor's 5-part check causes it to skip encryption).

---

## Architecture

### Chosen approach: Keycloak encrypts access tokens at source

```
                    fetch /jwe-jwks (KC fetches once, caches)
KC  <-----------------------------------------  Gateway

KC: encrypts access token as JWE using gateway RSA public key

Angular  --login-->  KC
Angular  <-- JWE access token --  KC
Angular forwards JWE unchanged (no interceptor involvement)

Angular  --GET /esq  Bearer JWE-->  Gateway
                                       |  JweAwareJwtDecoder: decrypt JWE
                                       |  delegate.decode(innerJWS) -> KC JWKS
                                       +-->  Services  Bearer JWS (TokenRelay, unchanged)
```

Key points:

- JWE has **5** dot-separated parts; JWS has **3**.
- `JweAwareJwtDecoder` branches on part count -- accepts both JWE and plain JWS.
- KC is configured with `access.token.encrypted.response.alg=RSA-OAEP` and
  `jwks.url=http://gateway:7070/jwe-jwks`.  KC fetches the public key from
  the gateway and encrypts access tokens before delivering them to Angular.
- **ID tokens are NOT encrypted** -- `id.token.encrypted.response.alg` must
  NOT be set.  If set, KC encrypts the ID token; keycloak-js stores it and
  sends it as `id_token_hint`; KC rejects it with "Invalid parameter: id_token_hint".
- `TokenRelay` forwards the inner JWS to services because `Jwt.tokenValue`
  is the decrypted string.  Services are unchanged.
- `JwksController` serves GET /jwe-jwks; returns empty keys array when JWE
  is not configured -- transparent fallback to plain JWS mode.

### KC "temporary toggle trick" (Admin UI -- if reconfiguring manually)

KC UI hides the Keys tab for public clients.  Workaround:
1. Enable Client Authentication temporarily (Settings tab)
2. Keys tab appears -- set JWKS URL to `http://gateway:7070/jwe-jwks`
3. Advanced tab: set `access.token.encrypted.response.alg = RSA-OAEP`
4. Disable Client Authentication -- KC retains key + encryption config

For automated import, `patch-jwe-activate.py` sets these directly in
`esquire.json` (KC accepts them on import regardless of publicClient flag).

---

## Keycloak configuration

`patch-jwe-activate.py` sets two attributes on the `esq-angular` client:

```
access.token.encrypted.response.alg = RSA-OAEP
access.token.encrypted.response.enc = A256GCM
use.jwks.url                        = true
jwks.url                            = http://gateway:7070/jwe-jwks
```

**ID token must NOT be encrypted.**  Setting `id.token.encrypted.response.alg`
causes KC to encrypt the ID token as JWE.  `keycloak-js` stores it and sends
it as `id_token_hint` on subsequent requests -- KC rejects it.  Only access
token encryption is configured.

`patch-jwe-deactivate.py` removes these attributes.  After deactivation KC
issues plain JWS access tokens again; rebuild KC image to apply.

---

## BFF evolution path

When a Backend-for-Frontend layer is added for `esq-angular` (planned):

```
Angular --cookie--> BFF --JWS--> Gateway --JWS--> Services
```

- BFF is a confidential Keycloak client -- tokens are held server-side.
- The Angular `jweInterceptor` is no longer needed (tokens never reach
  the browser in a BFF setup).
- `JweAwareJwtDecoder` remains useful for the BFF -> gateway path if
  payload confidentiality on that leg is required.
- Other clients (mobile, service accounts) sending plain JWS continue
  to work -- the decoder branches on part count.

---

## Protocol -- without JWE (baseline)

```
Angular        Keycloak                  Gateway          Services
   |               |                        |                 |
   |-- login ----► |                        |                 |
   |◄- JWS ------- |                        |                 |
   |                                        |                 |
   |-- GET /esq  Bearer JWS -------------->|                 |
   |                         validate JWS   |                 |
   |                                        |-- GET /esq ---->|
   |◄- 200 ------------------------------------------|◄- 200 -|
```

## Protocol -- with JWE (KC-encrypts-at-source, activated)

> **Kept for future reference, not active scope.** End-to-end access-token
> encryption is standard functionality for the OIDC + Keycloak stack we picked
> -- it should just work, but doesn't (see SUPERSEDED preamble for findings).
> This protocol is preserved verbatim for the day Keycloak finalizes its
> access-token-encryption story (or an alternative IdP enters scope). It is
> highly unlikely to land in any v1.2.x sprint -- the v1.2.x line is closing
> down its remaining backlog, not chasing IdP regressions. Treat as parking lot.

```
Angular        Keycloak                  Gateway          Services
   |               |  fetch /jwe-jwks (once, cached)  |
   |               |--------------------------------->|
   |               |<-- RSA public key (JWKS) --------|
   |               |                                  |
   |-- login ----► |                                  |
   |               | KC: encrypt access token as JWE  |
   |◄- JWE ------- |                                  |
   |                                                  |
   |-- GET /esq  Bearer JWE ------------------------>|
   |                                  decrypt JWE     |
   |                                  validate JWS    |
   |                                                  |-- GET /esq ---->|
   |                                                  |   Bearer JWS    |
   |◄- 200 ---------------------------------------------------|◄- 200 -|
```

---

## Activation

Run from `services/gateway/JWE.scripts/`:

```
activate-jwe.bat
```

Generates RSA-2048 key pair into `services/gateway/conf/` if not present.
Patches `keycloak/import/esquire.json` with JWE attributes.

Then rebuild and restart:

```
keycloak\docker-build.bat
rmdir /s /q compose\data\keycloak
docker compose up -d
```

Gateway starts with `JweAwareJwtDecoder` configured.  KC imports the patched
`esquire.json` and issues JWE access tokens for the `esq-angular` client.

---

## Deactivation

Run from `services/gateway/JWE.scripts/`:

```
deactivate-jwe.bat
```

Removes JWE attributes from `keycloak/import/esquire.json`.

Then rebuild KC and restart:

```
keycloak\docker-build.bat
rmdir /s /q compose\data\keycloak
docker compose up -d
```

Gateway falls back to plain JWS mode (`FileNotFoundException` on absent key file,
logged at debug level, standard Nimbus decoder used instead).

---

## Key files

### Gateway (services repo)

| File | Purpose |
|------|---------|
| `gateway/JWE.scripts/activate-jwe.bat` | Generate keys if absent + patch esquire.json |
| `gateway/JWE.scripts/deactivate-jwe.bat` | Remove KC JWE attrs from esquire.json |
| `gateway/JWE.scripts/generate-jwe-keys.bat` | Key pair generation only |
| `gateway/JWE.scripts/patch-jwe-activate.py` | Patch esquire.json -- access token enc only |
| `gateway/JWE.scripts/patch-jwe-deactivate.py` | Remove KC JWE attrs from esquire.json |
| `gateway/conf/jwe-private.pem` | RSA private key (generated, NOT committed) |
| `gateway/conf/jwe-cert.pem` | Public certificate (generated) |
| `gateway/src/.../security/JweAwareJwtDecoder.java` | Decrypt JWE -> validate inner JWS |
| `gateway/src/.../security/JwksController.java` | GET /jwe-jwks -- serve RSA public key for KC |
| `gateway/src/.../config/SecurityConfig.java` | jwtDecoder bean; permit /jwe-jwks |
| `gateway/src/.../resources/application.yml` | esq.jwe.private-key-path property |
| `compose/compose.yaml` | ESQ_JWE_PRIVATE_KEY_PATH env var + conf/ volume mount |

### Angular (explorer repo) -- no changes required

Angular receives JWE access tokens directly from Keycloak and forwards them
unchanged.  No interceptor, no service.  `app.config.ts` is at baseline.
