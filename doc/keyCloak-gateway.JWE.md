# Keycloak / Gateway -- JWE Token Encryption

## Purpose

Keycloak issues JWT access tokens that are signed (JWS) but not encrypted.
Their payload is readable by anyone who intercepts the HTTP traffic.
JWE encryption wraps the signed token in an opaque envelope so the payload
is visible only to the gateway, which holds the decryption key.
Angular holds an unreadable blob -- it cannot inspect claims, roles, or expiry.

---

## Current Status (2026-04-16)

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
