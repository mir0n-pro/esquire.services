# Cognito instead of KeyCloak -- what it would actually take

**Portability is one of Esquire's pillars.** The database, the messaging bus, observability and identity
are all promised pluggable -- named by configuration, reached through a seam, with no vendor written into a
service. AWS offers its own native service for each of them, and that is where such a promise is actually
tested: carrying an open component onto another cloud is deployment, not portability. The claim only bites
against the managed service with no equivalent elsewhere, which is where lock-in lives.

This one is the **identity** pillar. Esquire reaches its identity provider through one port,
`IIdentityGateway`, so the question is not whether the seam exists -- it is what putting Amazon's service
behind it would actually cost.

*Every claim below about Cognito was tested against a live user pool in us-east-1. Prices are
us-east-1, September 2026, from the AWS Price List API.*

**The finding: it can be done, and it is POSTPONED.** The hard part is solved -- a Cognito token can carry `esq_uid`, `esq_rootpath` and nested roles, so every Esquire
service except kcMaster and the BFF would not notice the change. It is postponed for three reasons, and
none of them is the sign-in:

1. **It costs 17-26 delivered days** for parity with what KeyCloak already gives -- 12-19 if the hosted
   form is accepted instead of Esquire's own. That buys no capability the system does not have today.
2. **Three things are given up and stay given up.** A user cannot be searched by `esq_uid` (the lookup
   moves into Esquire's own data), TOTP enrolment cannot be forced per user (it is pool-wide), and the
   handshake form cannot be themed -- only tinted with CSS against fixed class names, or rebuilt.
3. **It cannot be developed locally.** Cognito is not in community LocalStack, so docker and local
   Kubernetes could not run it. KeyCloak would have to stay for those targets anyway -- which makes this
   an ADDITION, not a replacement, and the two-master shape below is a consequence of that, not a choice.

---

## 1. What is being compared

Esquire signs people in with KeyCloak. KeyCloak is a container we run, we patch and we back up.
Amazon Cognito is a service AWS runs instead. The question is not "is Cognito good". It is **what it
would cost us -- in development, in deployment and in money -- to put Cognito where KeyCloak stands
today**, and what we would gain or give up by doing it.

To answer that honestly, the first half of the work is not about Cognito at all. It is being precise
about what Esquire asks of an identity provider. Guessing that is how an estimate ends up wrong by a
factor of three.

---

## 2. What Esquire actually asks for

### 2.1 There is already a port

`common/.../identity/IIdentityGateway.java` is the way in, and its own javadoc says the important
part: *"the way into the identity provider, for a caller that is told nothing about what is on the
other side"*. Four methods -- `start`, `stop`, `postRequest`, `postMessage` -- and a result handler.
The implementation is named in each process's wiring, and nothing else knows which one it is.

This matters more than any single feature question. **The seam needed to swap identity providers does
not have to be built. It is already there** -- and it was built for a different reason: so the same
call works whether the request travels the messaging bus to kcMaster or is served inside one process.
A second implementation beside `KcIdentityGateway` is the shape the code already expects.

### 2.2 The token contract, and it is enforced

`common/.../security/JwtClaimsExtractionFilter.java` reads three things out of every access token and
**refuses the request if any is missing**:

| claim | what it is |
|---|---|
| `esq_uid` | the Esquire entity id of the signed-in user |
| `esq_rootpath` | the subtree that user may see -- the authorisation boundary |
| `realm_access.roles` | the roles, as a nested object |

That is the whole contract. It is small, and it is absolute: a token without `esq_rootpath` does not
get a narrower view, it gets rejected.

### 2.3 The admin contract

kcMaster drives KeyCloak's admin API. Reading `KcIdentityService` and `KcIdentityGateway`, the list is:

| operation | what it does |
|---|---|
| create user | username, email, enabled, **custom attributes**, **required actions**, **realm roles** |
| update auth state | find by exact username, toggle required actions, merge attributes, read the credential list to learn whether a password exists |
| delete user | find by username, remove |
| **update entity path** | find users **by the `esq_uid` attribute**, merge a new `esq_rootpath` |
| set password | temporary or permanent |
| assign roles | realm-level |
| rename | `AuthSyncRequest.newLoginId` -- a login can change |

---

## 3. What Cognito does

Tested against a pool carrying `custom:esq_uid` and `custom:esq_rootpath` in its schema, a group
`esq-admin`, and one user with both attributes.

### 3.1 The token carries Esquire's claims -- from the ID token, not the access token

Signing in and decoding both tokens:

- **ID token** -- carries `custom:esq_uid`, `custom:esq_rootpath` and `cognito:groups`.
- **Access token** -- carries `cognito:groups`, and **neither custom attribute**.

That is the first thing anyone hits, and it looks fatal: the access token is the one that reaches the
gateway and the one the filter reads. Out of the box, **a Cognito access token cannot pass Esquire's
filter.**

There are two ways round it. The first, tested here, is a
**Pre-Token-Generation Lambda trigger, version V2_0**, which can write into the access
token. With a nine-line Python function attached, the same sign-in produced:

```json
{ "sub": "c468b418-...", "cognito:groups": ["esq-admin"],
  "esq_uid": "15",
  "esq_rootpath": "1.14.",
  "realm_access": { "roles": ["esq-admin"] },
  "token_use": "access", "username": "mainadmin" }
```

**All three claims, under the right names, with `realm_access` as a nested object.** Esquire's filter
accepts that token unchanged. The nesting was worth testing on its own: a trigger can emit a nested
claim, not only a flat string -- which is what lets Cognito produce a KeyCloak-*shaped* token instead
of a Cognito-shaped one that Esquire would have to be taught to read.

**The tier matters.** V2_0 is the trigger version that can write to the access token at all; V1_0
reaches only the ID token. V2_0 needs the **Essentials** or **Plus** user-pool tier. The probe pool
defaulted to Essentials without being asked.

**This is NOT the route taken.** The second way needs no trigger at all -- the ID token carries the same
values natively -- and it is the one chosen: see 3.4e. The Lambda findings are kept because they were
measured, and because they are what makes the comparison in 3.4e a real one rather than a preference.

### 3.2 Finding a user by `esq_uid`: no

`ListUsers` with `custom:esq_uid = "15"` is refused:

```
InvalidParameterException: Input fails to satisfy the constraints.
```

while `username = "..."`, `email = "..."` and their prefix forms all return the user. **Cognito cannot
search on a custom attribute.**

kcMaster does exactly that on every entity move, and the reason is deliberate: the uid is the
*persistent* value, while a login name can change. The workaround is small -- Esquire's own database
already holds the entity id and the login id together, so the lookup moves from the provider to us:
read the login id locally, then find the user by username. What is given up is what the uid lookup was
protecting by construction -- a rename racing a move.

### 3.3 Renaming a login: yes -- if the pool is built for it

`AuthSyncRequest.newLoginId` exists because a login can change. Cognito:

```
username           -> InvalidParameterException: "Attribute does not exist in the schema"
preferred_username -> accepted, but usable for sign-in only if the pool was CREATED
                      with it as an alias attribute; that cannot be added later
```

**There is no rename verb in the Cognito API**, and `Username` is fixed for the life of the user.

**CORRECTED, later the same day: a login CAN be renamed, and the same change answers "can we sign in
with an email".** Create the pool with **`UsernameAttributes: ["email"]`** and the sign-in identifier
becomes the *email attribute*, which is mutable. Tested on a second pool:

```
sign in as alpha@mir0n.pro   -> OK (token)
admin-update-user-attributes email = beta@mir0n.pro
sign in as beta@mir0n.pro    -> OK (token)
sign in as alpha@mir0n.pro   -> UserNotFoundException: User does not exist
admin-get-user               -> Username: 74785488-4041-70a4-dcfe-cddfca936be5
                                email:    beta@mir0n.pro
```

The immutable `Username` becomes an opaque UUID that nobody types, and the email carries the login. So
`AuthSyncRequest.newLoginId` maps cleanly after all -- a rename is an attribute update.

**Two conditions come with it, and both are real:**

- **It must be decided when the pool is created.** `UsernameAttributes` cannot be added to an existing
  pool, so this is a first-day decision, not something to discover later.
- **Every `loginId` must then BE an email address.** Esquire's seed uses logins like `mainadmin`, which
  is not one. On a Cognito deployment the login id and the email address become the same field, and the
  seed would have to say so.

### 3.4 Required actions: partly

KeyCloak's `requiredActions` is a list Esquire pushes and pulls: `UPDATE_PASSWORD` follows the
force-password-change flag, `CONFIGURE_TOTP` follows the two-factor flag.

Cognito has **`FORCE_CHANGE_PASSWORD`** as a user *status*, which covers the first case. It has no
general required-actions list, and **no "must configure TOTP before continuing"** state -- MFA is a
preference set on the user (`admin-set-user-mfa-preference`), not something a sign-in can be made to
demand once. The probe user, given a permanent password, reported `Status: CONFIRMED` and
`UserMFASettingList: null`.

So the force-password-change half maps; the enrol-your-authenticator half would move into Esquire's
own UI flow instead of being delegated.

### 3.4a The three handshake workflows, tested one by one

KeyCloak drives these as *required actions*: Esquire sets a flag, KeyCloak shows the form at the next
sign-in. Cognito drives them as **auth challenges** -- the sign-in call returns a challenge name and the
caller must answer it. Same outcomes, a different mechanic.

| workflow | Cognito | tested |
|---|---|---|
| **force password change** | `admin-create-user --temporary-password` sets status `FORCE_CHANGE_PASSWORD`; the next sign-in returns challenge **`NEW_PASSWORD_REQUIRED`** | **yes -- works.** The challenge even carries `custom:esq_uid` and `custom:esq_rootpath` back in its parameters |
| **self-service change password** | `ChangePassword` (`--access-token`, `--previous-password`, `--proposed-password`) and `ForgotPassword` / `ConfirmForgotPassword` for the reset path | verb present |
| **force TOTP enrolment, per user** | **not possible** | **yes -- refused.** `admin-set-user-mfa-preference` on a user with no authenticator: `InvalidParameterException: User does not have delivery config set to turn on SOFTWARE_TOKEN_MFA` |

**The TOTP row is the real difference.** A user must first enrol an authenticator
(`AssociateSoftwareToken` then `VerifySoftwareToken`) before the preference can be set at all. So there
is no "this user must set up TOTP at the next sign-in" -- the only lever is pool-wide
`MfaConfiguration: REQUIRED`, which demands it of everyone, or nobody.

Esquire's `tfaMethod` is **per user**. On Cognito that becomes either a policy for the whole pool, or a
flow the Explorer drives itself -- which is the same conclusion the GUI section reaches from the other
direction.

### 3.4b The SDK, and whether it can be developed locally

**The client library exists and is ordinary.** `software.amazon.awssdk:cognitoidentityprovider` resolves
from Maven Central (2.29.0 fetched to check). cgMaster would carry it exactly as `tp-sqns` carries the
SQS and SNS clients -- one module, one dependency, and no other deployment sees it.

**Local development is where it gets more awkward than the bus was.** The three AWS transport
drivers against LocalStack on docker, and never touched a real AWS endpoint until T2. That does not
extend here:

```
LocalStack community, image localstack/localstack:4 -- 35 services reported
  sqs        available
  sns        available
  kinesis    available
  cognito-idp        NOT PRESENT
  cognito-identity   NOT PRESENT
```

Cognito is a LocalStack **Pro** service. So a cgMaster developed the way `tp-sqns` was developed would
need either a LocalStack subscription, or a third-party emulator (`cognito-local` and `moto` both exist;
**neither was tested here**), or simply a real development user pool.

**The last of those is the answer.** Everything in this
document was done against a real pool, and one pool with a handful of users on the Essentials tier costs
**about a cent and a half a month**. The reason to emulate a cloud service is usually cost or speed;
here it is neither.

### 3.4c Where the claim values come from -- and why nothing should be clever about it

On Route B nothing projects the claims at all: Cognito puts the user's own attributes and groups into
the ID token natively. The question is still worth answering, because it is the same question either
way -- **where does the value come from?** The probe Lambda read two places, and both are inside
Cognito:

```python
attrs = event["request"]["userAttributes"]                    # custom:esq_uid, custom:esq_rootpath
event["request"]["groupConfiguration"]["groupsToOverride"]    # -> realm_access.roles
```

**A pure projection: attribute to claim, group to role.** No database call, no API call, no network at
all -- which is exactly why it measured 1.73 ms.

**The tempting alternative is wrong, and Route B removes the temptation.** A Lambda could call
Esquire's database and fetch the root path at sign-in, which would guarantee freshness. It would also
buy a VPC round trip on every token, a coupling from an AWS Lambda into Esquire's data, and a failure
mode in which **Esquire being down means nobody can sign in**. An identity provider that cannot issue a
token without the application it protects is worse than the one being replaced. With no Lambda in the
path, that road is closed by construction.

**The right shape is the one KeyCloak already uses.** The provider holds the values on the user record,
and the master writes them there when they change. kcMaster does this today -- `esq_uid` and
`esq_rootpath` are KeyCloak user attributes. cgMaster writes Cognito custom attributes, and Cognito puts
them in the ID token by itself.

**Which puts the weight on one operation.** `esq_rootpath` is the authorisation boundary, and it changes
when an entity moves -- the op `X` broadcast, served by `updateEntityPath`. On Cognito that write is
the ONLY thing keeping the token's root path true. **If it fails, tokens keep being issued with a stale root
path** and the user sees the wrong subtree until somebody notices. The same is true on KeyCloak today,
so this is not a new risk -- but it becomes the single most important write in the system, and it is
precisely the one whose by-`esq_uid` lookup Cognito cannot do.

Two consequences follow, and they point the same way:

- the entity-path update should carry the login id on the broadcast rather than look it up, and
- a failed path write must be **visible** rather than swallowed -- which is the same argument backlog
  item 15 makes about a send the bus cannot see fail.

### 3.4d Is the Lambda-filled token a JWE? No -- and JWE is now out of the claim

The question is a fair suspicion: a claim added by a Lambda might be smuggled in some private envelope.
It is not. The token is an ordinary signed JWT:

```
token parts : 3          (3 = JWS signed;  5 = JWE encrypted)
JOSE header : {"kid":"4TFsqg0Yn52a2PturPJf20yVZ+pH+JS/unaOk/znMYs=","alg":"RS256"}
```

**And Cognito cannot issue a JWE at all.** The discovery document advertises no encryption capability
whatsoever -- `id_token_encryption_alg_values_supported`, `..._enc_values_supported` and
`userinfo_encryption_alg_values_supported` are all absent, where an encryption-capable provider would
publish them. The only KMS settings on a user pool cover *"codes and temporary passwords sent to custom
sender Lambda triggers"* and the at-rest key type; neither touches a token.

**So this is NOT a workaround.** It is precisely the shape Esquire runs on today: a signed token whose
claims are readable by anyone holding it, trusted because the signature verifies. `esq_uid` and
`esq_rootpath` are as visible on Cognito as they are on KeyCloak, and no more. Nothing is lost by
switching, and nothing is gained.

**What it does tell us is uncomfortable, and belongs in the record.** IAM portability rests on two seams
-- the token at the gateway, and the `IIdentityGateway` / `*Master` SPI behind it. The gateway's JWE
support is real code (`JweAwareJwtDecoder`, `JwksController`, `/jwe-jwks`), it works, and it is inert
because **KeyCloak 26 will not emit JWE on `/token`**. Cognito will not emit one either.

**Two providers examined, zero that can supply it.** The JWE half of the gateway is a seam with no
supplier, and a capability nobody can feed is a capability only on paper.

**Settled the same day: JWE is out of the claim.** The reason to want it was to keep `esq_uid` and
`esq_rootpath` out of the holder's hands; no provider can deliver that, so encryption at the gateway was
never going to be the answer, and looking for one that might emit a JWE would be time spent to unlock a
capability that leaves the underlying question open anyway. The decoder stays where it is -- in a package
whose own javadoc says *"nothing in this package is a supported Esquire path"* -- and **the portability
claim is JWT**, which is what both tested providers issue and what every deployment actually runs.

### 3.4e The Lambda may not be needed at all -- and dropping it changes the tier

**With no Lambda involved, the result changes.**

The Lambda exists to make Cognito's ACCESS token look like KeyCloak's. But the values are already in the
**ID token**, natively, with no trigger: `custom:esq_uid`, `custom:esq_rootpath`, `cognito:groups`. And
the BFF already holds it -- `sessionStore` keeps `access_token` AND `id_token`, and the id token is
already used for `id_token_hint` at logout. The proxy injects the access token
(`apiProxy.ts`: `Bearer ${ext._esqAccessToken}`); injecting the id token instead is **one line**.

| | **Route A** -- with the Lambda | **Route B** -- no Lambda |
|---|---|---|
| what carries the claims | the access token, shaped by a `V2_0` trigger | the **ID token**, natively |
| BFF | unchanged | one line |
| Esquire | unchanged for humans | `JwtClaimsExtractionFilter` learns Cognito's claim names |
| user-pool tier | **Essentials, $0.0150 / MAU** | **Lite, $0.0055 / MAU** |
| moving parts in the sign-in path | a Lambda, with a cold start | none |

**Route B is 2.7x cheaper to run** -- at a thousand monthly active users, $5.50 a month against $15 --
and it removes a service from the authentication path whose failure means nobody signs in.

**And the filter work was already counted.** Item 4 of the work list -- machine-to-machine -- already
forces `JwtClaimsExtractionFilter` to understand a second token shape, because a client-credentials
token has no user behind it. Route B reuses that same change rather than adding a new one.

**Route B is also the more honest seam.** IAM portability says the gateway validates whatever the
provider issues. A Lambda that makes Cognito imitate KeyCloak is the provider pretending; a filter that
reads Cognito's own claim names is the seam doing its job. The Lambda route makes every provider look
like the first one, which is the shape of a workaround rather than a port.

**One decision here is deliberate, and it is recorded rather than slipped in.** An ID token is not
conventionally an API credential -- its audience is the client, not a resource server. Esquire's gateway
uses `JwtValidators.createDefault()`, which checks the issuer and the expiry and **not** the audience, so
it works. That is a deliberate choice with a reviewer's question attached, and the answer is that the
token never leaves the BFF-to-gateway hop: the browser holds a session cookie, not a token.

### 3.5 Two configuration traps

- **Optional MFA is refused at pool creation without SMS configuration.** TOTP-only MFA has to be
  enabled afterwards, by a separate `set-user-pool-mfa-config` call.
- **`update-user-pool` is a full replace, not a patch.** Attaching the Lambda trigger -- the only field
  passed -- silently reset `AutoVerifiedAttributes` to null and flipped `AllowAdminCreateUserOnly` from
  true to **false**. That last one quietly opens self-registration. Every future change to a pool has
  to restate the pool's entire configuration.

---

## 3.6 The tier Esquire would need

There are three, and the difference is not marketing -- each refuses the features of the one above with
a named exception. All of this was tested by trying it:

| tier | per MAU | what it refuses |
|---|---|---|
| **Lite** | $0.0055 | `FeatureUnavailableInTierException: ... Token Customization with Pre-Token Generation Lambda V2_0`. It accepts **V1_0**, which reaches the ID token only |
| **Essentials** | **$0.0150** | `FeatureUnavailableInTierException: ... Threat Protection` |
| **Plus** | $0.0200 | -- accepts threat protection |

**Lite is the floor -- $0.0055 per monthly active user.** Essentials would be the floor only on Route A,
where the claims must reach the ACCESS token and only `V2_0` can put them there. Route B takes the ID
token, which needs no trigger and therefore no tier above Lite. That single choice is the difference
between $0.0055 and $0.0150 per user.

**Plus buys threat protection** -- compromised-credential detection and risk-based sign-in -- for
$0.0050 more per user, a third again on top of the floor. Nothing in Esquire needs it; it is the kind
of thing bought for a public-facing sign-up, not for a system whose users are created by an
administrator inside an entity tree.

**The tier can be changed on a live pool, in both directions.** `LITE -> ESSENTIALS -> PLUS -> LITE`
were all accepted on the same pool, with no recreation. That is genuinely useful: a deployment can
start on the floor and move up if threat protection is ever wanted, without a migration.

**One caution, and it is the same trap as before.** After the downgrade call -- which passed only
`UserPoolTier` -- the pool came back with `UserPoolAddOns: null` and no pre-token trigger. That is
`update-user-pool` replacing rather than patching, showing itself again, not something special about
downgrading. It is the same lesson twice in one afternoon: **every call to `update-user-pool` must
carry the pool's entire configuration.**

**Two more cost lines exist and are worth knowing before anyone models this at scale:** advanced
security is priced separately above 100,000 MAU ($0.02), and Cognito sells guaranteed request rates by
the RPS-month (from $20) for the sign-in, user-creation, user-read, federation and account-recovery
APIs. Neither matters at demo volume. Both would matter to anyone reading this as a production plan.

---

## 3.7 What the Lambda costs

The trigger sits in the sign-in path of every token issued. Prices from the Price List API; timings
from the function's own CloudWatch `REPORT` lines over 14 invocations.

### Performance

| | |
|---|---|
| **warm duration** | **mean 1.73 ms** (min 1.46, max 3.02) |
| billed, warm | **2 ms** |
| **cold start** | **init 71 / 78 / 88 ms**, billed 73 / 81 / 90 ms |
| memory used | **37 MB** of the 128 MB minimum |

**Warm, it is 1.7 ms on a sign-in -- nothing.** A KeyCloak sign-in does not notice a millisecond and
neither will a person.

**Cold, it is about 90 ms**, and this is the part worth thinking about, because it points the opposite
way to the usual intuition. Three of fourteen invocations were cold on an idle probe. **A busy system
almost never pays a cold start; a DEMO pays it almost every time**, because sign-ins are rare and the
execution environment has gone away between them. So the place this Lambda is least free is exactly
the place Esquire runs today -- and it is still only 90 ms on an operation a person already waits a
second for.

### Budget

| | |
|---|---|
| requests | **$0.20 per million** |
| duration | $0.000015 per GB-second (x86); $0.0000107 on ARM |
| this function, warm | 128 MB x 2 ms = 0.00025 GB-s = **$0.00000000375 per sign-in** |

**Per million sign-ins: $0.20 for the requests and under $0.01 for the compute.** The compute is under
two percent of a bill that is itself negligible. At a thousand monthly active users signing in twenty
times each -- twenty thousand invocations -- the Lambda costs **less than half a cent a month**.

**So the Lambda is free and fast, and cost was never the reason to avoid it.** The reason is the one
that does not appear on a bill: it is a second service in the authentication path, with its own
permissions and its own deployment, and **if it fails, nobody signs in**. Nine lines reading two
attributes has very little to go wrong -- but it is one more thing that has to be right, in a path that
today has none.

**Route B was chosen and this Lambda is not deployed.** The measurement is kept because it is what makes
that choice informed: the Lambda was rejected for having a failure mode and a tier cost, not for being
slow or expensive, and those are different reasons.

---

## 4. The effort

### 4.1 Development

The seam exists, so this is an implementation beside `KcIdentityGateway`, not a rework.

| piece | what it is |
|---|---|
| `CognitoIdentityGateway` | the second `IIdentityGateway`: AWS SDK instead of the KeyCloak admin client. Create, delete, enable/disable, set password, group membership, attribute merge -- all one-for-one |
| the entity-path update | replace the by-attribute search with a local id-to-login lookup, then find by username |
| rename | **supported**, once the pool is created with `UsernameAttributes: ["email"]` -- a rename is an `admin-update-user-attributes` on the email. No special case in the caller |
| TOTP enrolment | a flow in the explorer, because there is no required-action to delegate |
| ~~the pre-token Lambda~~ | **not built.** Route B takes the ID token instead, so there is no trigger, no IAM role and nothing in the sign-in path |
| the BFF | endpoints and client id change; the authorisation-code flow itself does not |
| **the pool shape, decided ONCE** | `UsernameAttributes: ["email"]` cannot be added to an existing pool. Get it wrong on day one and the fix is a new pool and a migration |
| **the login id becomes an email** | with email as the sign-in attribute, every `loginId` must BE an email address. The seed uses `mainadmin`, which is not one -- so db.seed and the e2e credentials move too |

**Nothing outside kcMaster and the BFF changes -- for a human signing in.** Machine callers are the exception, and section 4.1a is where that is settled. The gateway, enyMan, keySmith, pacMan, bizTree and
auKeep read a token carrying the same three claims. On Route A a Lambda makes it byte-identical to
today's; on Route B the filter reads Cognito's own names instead -- the same work item as the machine
token, so it is counted once.

The honest shape: the gateway implementation is a **few days**. The TOTP enrolment flow is where it
stops being a few days, because it is a product decision rather than a translation.

### 4.1a The work list, by file

**New code**

| what | where |
|---|---|
| `CognitoIdentityGateway` -- the third `IIdentityGateway` | `kcMaster/.../identity/`, beside `KcIdentityGateway` |
| name the bean | `kcMaster/KcMasterConfig.java` -- one line; `identityGateway(Environment)` already picks the implementation, and enyMan already picks a different one (`KcBusAdapter`), so the pattern is in use, not invented |
| the entity-path update | the by-attribute search has no Cognito equivalent. Either the `X` broadcast starts carrying the login id, or kcMaster looks it up. `updateEntityPath(entityId, newPath, ...)` takes no login id today, so this is a real change, not a rename |
| **`JwtClaimsExtractionFilter`** | see below -- the one file this was NOT supposed to touch |
| ~~the pre-token Lambda~~ | **dropped with Route B.** Kept below as a measured alternative, not as work |

**The machine-to-machine problem, which is the largest single item**

The realm defines **six service-account clients**: `esq-kcMaster`, `esq-rest`, `esq-hauberk`,
`esq-hauberk-S`, `esq-hauberk-M` and `esq-gw-exchange`. In KeyCloak a service account IS a user, so it
carries `esq_uid` and `esq_rootpath` as attributes and the mappers put them in the token.

**A Cognito client-credentials token has no user at all.** Tested, with the V2 trigger attached to the
pool:

```json
{ "sub": "<the client id>", "token_use": "access", "scope": "esq-api/invoke",
  "client_id": "<the client id>", "version": 2 }
```

No `esq_uid`, no `esq_rootpath`, no roles -- and **the trigger did not fire**, because there is no user
to build a token about. Every server-to-server caller would therefore present a token Esquire's filter
rejects. Three ways out, and none is free:

- **Make every service identity a real pool user** and authenticate it as one. The trigger then fires
  and nothing in Esquire changes. It also means machine callers hold passwords and count as monthly
  active users.
- **Teach `JwtClaimsExtractionFilter` a second token shape** -- recognise a client-credentials token,
  map `client_id` to a service identity in Esquire's own data, and derive the uid and root path there.
  This is the honest design, and on Route B it is the change that is happening anyway -- so the machine
  token stops being a separate problem and becomes the second case of one.
- **Drop machine callers on the Cognito deployment** -- which drops hauberk, and with it the ability to
  load-test the thing.

**Config and deployment updates**

| what | where |
|---|---|
| the OIDC endpoint URIs | `gateway/application.yml` and `gateWard/application.yml` compose them from `KEYCLOAK_HOST` / `PORT` / `PATH` / `REALM` in KeyCloak's path shape (`/realms/{r}/protocol/openid-connect/auth`). Cognito's are `/oauth2/authorize`, `/oauth2/token`, `/oauth2/userInfo`, `/.well-known/jwks.json`. Each URI has to become a whole-URL variable |
| the BFF | `KC_ISSUER` / `KC_ISSUER_INTERNAL`, client id and secret. It discovers the issuer at startup, and Cognito publishes a discovery document -- so this is very likely configuration only |
| the realm file | `keycloak/import/esquire.json` becomes pool configuration: 8 realm roles (`MANAGER`, `CLIENT`, `SYSADMIN`, `MERCHANT`, `OPERATOR`, `SUPERVIZOR`, `SUPPORT`, `TREE`) as groups, the clients as app clients, and a resource server for any machine caller |
| hauberk | `hauberk.properties` -- client id, secret and the token endpoint |
| the deployment | on the AWS target only: no KeyCloak StatefulSet, no PVC, no realm import. **Every other target keeps KeyCloak**, so both paths exist from then on |

**Decisions, not code**

- **The pool shape** -- email as the username attribute, decided at creation and not afterwards. It is what
  buys the rename and sign-in by email, and it forces every login id to be an email address.
- **TOTP enrolment** -- build the flow in the explorer, or do without it there.
- **Service identities** -- pool users, or a filter that understands a machine token.

**What this does to the estimate.** The gateway implementation remains a few days. The
machine-to-machine question is the one that turns this from a translation into a design change, because
it reaches `JwtClaimsExtractionFilter` -- a file every service depends on, on every target, not just on
AWS. **Route B leans on that same change**, which is why dropping the Lambda costs nothing: the filter
was going to learn a second token shape regardless.

### 4.1b The GUI side

**With KeyCloak, the GUI work was one HTML template for the logon-handshake forms.**

That is the true measure of the GUI work today: **one theme**. KeyCloak hosts the login, the password
change, the TOTP enrolment and the error pages, and Esquire supplies an HTML template for them. The
Angular application never sees a credential; the BFF never renders a form.

**Cognito has no template.** `set-ui-customization` takes exactly two things:

```
--css         against a FIXED set of class names --
              .logo-customizable  .banner-customizable  .label-customizable
              .submitButton-customizable  .inputField-customizable
              .errorMessage-customizable  .idpButton-customizable
              .socialButton-customizable  .background-customizable  ...
--image-file  one logo
```

**That is styling, not templating.** The structure of the form, its wording, the order of the fields and
anything extra on it are not reachable. Managed login branding -- the newer designer -- is available on
this pool (the API answered *"ManagedLoginBranding ... does not exist"*, not *"unavailable in tier"*),
and it is a richer branding tool, but it is still not arbitrary HTML.

So there are three GUI routes, and they are very different sizes:

| route | what it costs | what it gives |
|---|---|---|
| **Classic hosted UI + CSS** | an afternoon | a page that is *tinted* like Esquire, not one that *is* Esquire. Fixed layout and wording |
| **Managed login branding** | a day or two | better, still inside AWS's design, still not a template |
| **Own the forms in the Explorer** | the real work | exact parity with the KeyCloak theme, and full control |

**The third route is not "write a login page".** Owning the forms means owning the **challenge state
machine** that KeyCloak hides today: `NEW_PASSWORD_REQUIRED` on a first sign-in, `MFA_SETUP` and the
authenticator secret it returns, `SOFTWARE_TOKEN_MFA` on every later sign-in, password reset with its
confirmation code, and each of their error paths. The BFF would drive it -- so credentials stay
server-side and the SPA keeps the shape it has -- and the Explorer would grow one form per challenge.

**This is where the estimate moves.** Everything else is a translation: the same
operation, a different API. The GUI is the one place where **something that does not exist has to be
built**, and in a tier nothing else needs. It is also the piece a demo
feels immediately, because it is the first screen anybody sees.

**And it is the strongest argument for the two-master design.** With cgMaster beside kcMaster, this cost
is paid only by a deployment that chooses Cognito. The Explorer keeps the KeyCloak theme it has, and the
Cognito forms are an addition rather than a replacement -- exactly the property that keeps a
non-AWS deployment free of AWS.

### 4.2 Deployment

Simpler than what it replaces, and that is a real gain. No StatefulSet, no PVC, no image to rebuild,
no realm import to keep in step, no `--import-realm` that silently skips when the realm already exists.
A user pool is created once and configured by API.

On Route A a Lambda would replace it in the sign-in path -- another moving part, in another service,
with its own permissions, and if it fails nobody signs in. **Route B has no such piece**, so what is
given up in deployment is genuinely given up, not traded.

The realm we ship today also carries clients, mappers and roles as a versioned file. In Cognito that
becomes API calls; and because `update-user-pool` replaces rather than patches, those calls must carry
the pool's whole configuration every time.

### 4.3 Budget

Read from the Price List API, us-east-1:

| tier | per monthly active user |
|---|---|
| Lite | $0.0055 |
| **Essentials** (needed for the V2 trigger) | **$0.0150** |
| Plus | $0.0200 |

KeyCloak today costs a share of a node and a 5 GiB volume -- a few dollars a month, and it does not
grow with the number of users.

Cognito on Route B, at the Lite tier, is **$0.0055 per monthly active user** and nothing else. For a
demo with a handful of users that is cents. The crossover is arithmetic: **a thousand monthly active
users is $5.50 a month**, ten thousand is $55 -- against a KeyCloak whose bill does not move. (On Route
A it would be $15 and $150, plus the Lambda.)

The money argument does not point one way. **Cognito is cheaper until it is not**, and where the line
falls depends entirely on how many people sign in.

---

## 5. What it can and cannot do

**It can be done, and the part that looked impossible is solved.** A Cognito access token can be made
to carry `esq_uid`, `esq_rootpath` and a nested `realm_access.roles` -- which means every Esquire
service except kcMaster and the BFF would not notice the change at all.

What stands in the way is not the sign-in. It is the **administration and the forms**, and a day of
testing moved several of those from "no" to "yes, with a condition":

| | on the API alone | with the pool built for it |
|---|---|---|
| the claims Esquire demands, in the token | no, not in the ACCESS token | **yes** -- natively in the ID token, no trigger and no tier above Lite. A `V2_0` Lambda is the alternative, measured and not taken |
| rename a login | no | **yes** -- if the pool is created with email as the username attribute |
| sign in with an email | -- | **yes**, same condition |
| force a password change | -- | **yes** -- `NEW_PASSWORD_REQUIRED` |
| search a user by `esq_uid` | no | **still no** -- the lookup moves to Esquire's own data |
| force TOTP enrolment per user | no | **still no** -- pool-wide, or the Explorer drives it |
| a themed handshake form | -- | **no** -- CSS against fixed class names, or build the forms |
| develop it locally | -- | **not on community LocalStack** -- Cognito is a Pro service |

Esquire does not merely authenticate against its identity provider -- it *manages* it, as an extension
of the entity tree, and that is the half Cognito answers least well. The two that remain are the user
search and the login forms, and the forms are the larger by far.

**And the reason to stay is the one the framework is built on.** No vendor stands between you and your
data. KeyCloak runs on docker, on local Kubernetes, on OKE and on EKS identically, from one image and
one realm file. Cognito runs in one place. Adopting it would mean either keeping two identity
implementations forever, or making AWS the only place Esquire runs.

The seam is there, so the door stays open.

**DECIDED 2026-08-31, after this analysis was written: Cognito WILL be employed** -- and not by
replacing KeyCloak. **kcMaster stays KeyCloak's; a second service, cgMaster, is Cognito's.** Two
services, the way enyMan and keySmith are two services -- nothing intercepts and no process holds both.
`IIdentityGateway` keeps its name; what generalises is the messaging contract a master speaks, which is
already provider-neutral in `AuthSyncRequest` and in the RESPONSE / REJECT answer.

On that route every finding above stops being an objection and becomes something a master **declares**.
Cognito cannot search by `esq_uid`: cgMaster answers that from Esquire's own data, and the caller is
unchanged. Cognito has no user behind a machine token: that is cgMaster's answer to give, not a second
token shape wired into `JwtClaimsExtractionFilter`. And the AWS SDK never enters kcMaster while the KeyCloak client never
enters cgMaster -- the property `tp-sqns` already gives the bus, at the level of a service.

So the conclusion of the analysis stands as analysis -- Cognito costs more in administration than in
sign-in, and adopting it alone would make AWS the only place Esquire runs -- and the answer to it is the
generalisation, not the adoption. Two providers, named in configuration, is a different proposition from
replacing one with the other.

---

> **On the Lambda material below.** The decision is **Route B** -- no Lambda. The
> pre-token trigger was built, attached, measured (1.73 ms warm, 71-88 ms cold, $0.20 per million
> sign-ins) and then **not taken**. All of it is kept on purpose. It is what turns "we chose the ID
> token" from a preference into a decision with a measured alternative behind it, and it is the only
> reason the tier comparison -- Lite $0.0055 against Essentials $0.0150 -- means anything. Work that was
> done and rejected is still evidence; deleting it would leave the conclusion resting on an assertion.

## 6. How long, and what it costs

Everything above is measured. **This section is not** -- it is an estimate built on it, and it is worth
saying so plainly. The sizing assumes one developer who knows this code, and it assumes the two-master
design: cgMaster beside kcMaster, nothing replaced.

### 6.1 The work, decomposed

| # | piece | days | why that size |
|---|---|---|---|
| 1 | **cgMaster** -- module, chart, Dockerfile, `IIdentityGateway` implementation, the operations against the AWS SDK | **3-5** | the bulk of the new code, but it is a translation of `KcIdentityService`, which already exists and is the same shape |
| 2 | **the SPI rename** -- `kc` means *identity* now: the `esquire.kc` bus, the `kc` slot, `KC_BUS_ID`, `KcBusAdapter` | **1-2** | little thought, wide reach: seven deployment trees carry those names, and each one has to be re-verified |
| 3 | **the entity-path change** -- carry the login id on the `X` broadcast instead of looking it up by `esq_uid` | **1** | small change, but it touches the publisher, the event and both masters |
| 4 | **machine-to-machine** -- service identities as pool users, or a second token shape in the filter | **1-3** | 1 day as pool users; 3 if it lands in `JwtClaimsExtractionFilter`, because that file is in every service on every target |
| 5 | **the GUI** -- BFF challenge state machine plus one Explorer form per challenge, with e2e | **5-8** | the only piece where something that does not exist has to be built. See below |
| 5b | **Route B instead of the Lambda** -- inject the id token, and teach the filter Cognito's claim names | **0** | the BFF change is one line, and the filter change is already counted in item 4. It REMOVES the Lambda, its IAM role and its deployment from item 6 |
| 6 | **deployment** -- the k8s-aws chart for cgMaster, and a pool-provisioning script that carries the WHOLE configuration on every call. **No Lambda, no IAM role** on Route B | **2** | the full-replace behaviour of `update-user-pool` makes the script bigger than it looks |
| 7 | **verification** -- the suites on the Cognito shape, AND proof that every other target is untouched | **2** | item 2 reaches all seven trees, so this is not optional |
| 8 | **documentation** -- source history headers, `changes.txt` and `release_notes` on every notable commit; the design docs settled at sprint end | **2-3** | not optional here: this project documents at the commit, and a new service plus a rename across seven trees is a lot of surface to describe |
| | **total, develop AND deliver, full parity** | **17-26 days** | |

### 6.2 The number moves on one decision

**These are DELIVERED days, not "it works on my machine" days.** Esquire's own cycle runs on every
intermediate phase, not only at the end: develop, unit-test, run it on docker and verify it live,
document the change, commit, let the pipeline deploy to docker and local Kubernetes, then run the e2e
and the smokes on BOTH. The numbers above carry that; a bare coding estimate would be roughly a third
smaller and would be a different thing than what was asked for.

**Item 5 is the swing.** Take the hosted UI with CSS instead of building the forms and it drops from
5-8 days to about half a day -- **total 12-19 days** -- and what is given up is that the first screen
anybody sees stops looking like Esquire. It is tinted, not themed.

That is the honest trade, and it is a product decision, not an engineering one. Everything else in the
list is translation work whose size is fairly predictable.

**Two items could overrun, and it is better to say which:**

- **item 2**, because a rename that crosses seven deployment trees is where silent breakage lives, and
  this project has already been bitten by a rename sweep once;
- **item 4**, if it lands in `JwtClaimsExtractionFilter` -- that file is read by every service on every
  target, so a change there is not a Cognito change, it is a framework change.

### 6.3 What it costs to build

**Almost nothing.**

| | |
|---|---|
| development pools | three exist today and bill about **$0.015 a month** together |
| the Lambda, IAM role, resource server, domain | free at this volume |
| local emulation | community LocalStack does **not** carry Cognito. Either a LocalStack Pro subscription, or -- cheaper and what this did -- a real development pool for cents |

There is no capital cost, no new tooling, and no licence. The whole study above was run for less than the
price of a coffee, and building it would not be different in kind.

### 6.4 What it costs to run

| | per month |
|---|---|
| **KeyCloak today** | a share of a node it already shares, plus a 5 GiB volume -- call it **under a dollar**, and it does not grow |
| **Cognito, Lite** -- Route B, no Lambda | **$0.0055 per monthly active user** |
| Cognito, Essentials -- Route A, with the Lambda | $0.0150 per MAU, plus $0.20 per million sign-ins |
| Plus tier, if threat protection is ever wanted | $0.0200 per MAU |

**So the crossover is arithmetic**, and Route B moves it a long way out: at a thousand monthly active
users **$5.50 a month instead of $15**, at ten thousand $55 instead of $150 -- against a KeyCloak whose
bill does not move. Cognito is cheaper than nothing only while nobody is using it, but Route B nearly
triples how long that lasts.

---

## 7. Conclusion -- POSTPONED

**It can be done.** A Cognito token carries `esq_uid`, `esq_rootpath` and a nested
`realm_access.roles`, so every Esquire service except kcMaster and the BFF would not notice the change.
The two identity seams -- the token at the gateway, and `IIdentityGateway` behind it -- hold.

**It is postponed, for three reasons, and none of them is the sign-in.**

| | weight |
|---|---|
| **1. It costs 17-26 delivered days** -- 12-19 if Amazon's hosted form is accepted instead of Esquire's own | a sprint in its own right, and it buys no capability the system does not already have |
| **2. Three capabilities are given up permanently** -- no user search by `esq_uid` (the lookup moves into Esquire's own data), no per-user TOTP enrolment (it is pool-wide), no themed handshake form (tinted with CSS against fixed class names, or rebuilt) | each is a working KeyCloak feature traded for nothing in return |
| **3. It cannot be developed locally** -- Cognito is not in community LocalStack | KeyCloak stays for docker and local Kubernetes regardless, so Cognito is an **addition, not a replacement**: two identity providers to keep working instead of one |

**The money is not the argument in either direction.** Cognito starts at pennies and rises per user;
KeyCloak is a container already being run. Neither number decides it.

**What the work would buy is IAM portability** -- the proof that the two seams are real seams and not a
description of KeyCloak, the same proof the messaging bus and the database already carry. That is the
reason to do it eventually, and it is not urgent while KeyCloak runs identically on docker, local
Kubernetes, OKE and EKS, and Cognito runs in one place.

## Appendix -- what was tested, and how

Three pools in us-east-1, and everything above came from them:

| pool | what it proved |
|---|---|
| `esquire-cognito-probe` (Essentials) | the token contract, the pre-token Lambda at `V2_0`, the user-search limit, the required-action limits, machine-to-machine, the discovery document, the hosted-UI customisation surface |
| `esquire-cognito-lite` (Lite, then moved up and down) | the tier gates, and that a tier can be changed on a live pool in both directions |
| `esquire-cognito-email` (`UsernameAttributes: ["email"]`) | that a login CAN be renamed, and that sign-in by email works |

plus a Python Lambda on the `PreTokenGeneration` trigger, an IAM role, a resource server with a custom
scope, a hosted domain, and a machine-to-machine app client.

### Not settled

The **logout round trip** is only partly tested. Both the standard `post_logout_redirect_uri` and
Cognito's own `client_id` + `logout_uri` returned 302 to Cognito's `/login`, with the parameters carried
through rather than interpreted. `curl` holds no authenticated session, so what a signed-in browser sees
is not established.
