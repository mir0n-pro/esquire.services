# Release Report: v1.2.1 → v1.2.2

**Repo:** `esquire.services/develop`  
**Top commit:** `e798f7e`

---

## Release Notes

### doc/release_notes.txt


**v1.2.2-2604.2217**  local k8s deployment  
&nbsp;: doc added doc/OCI.Pricing.md  
&nbsp;      added doc/WhereToGo.md  

**v1.2.2-2604.2017**  acct transfer: conversion rate; KC realm theme fix  
&nbsp;: Feature: pacMan -- AcctTransactionProcessorTransfer: FIELD_RATE required, validated > 0; credit = abs(debit)*rate; shared pkTx links both legs; refCode4 auto-note on both legs  
&nbsp;: Refactoring: pacMan -- transaction PK: Long->String (generateTransId); nextId()/ESQ_ATR_SEQ removed  
&nbsp;: Fix: common -- MdcFilter: String.valueOf() on getTotalJpaTime() log call  
&nbsp;: Config: keycloak -- loginTheme moved to esq_angular client only; realm-level theme reset to keycloak.v2  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      pacMan,  
&nbsp;      keycloak  

**v1.2.2-2604.1620**  procedural style sweep: ret pattern, null-guard across services  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      enyMan,  
&nbsp;      bizTree,  
&nbsp;      kcMaster,  
&nbsp;      keySmith  

**v1.2.2-2604.1617** keycloak esquire-explorer schema : more templates added  

**v1.2.2-2604.1517** postgres container added; local docker compose cleanup  

**v1.2.2-2604.1514**  acct transaction PK : classic : from ESQ_ATR_SEQ  
&nbsp;: Fix: pacMan — EsqAcctTransactionRepository.nextId(): DB sequence ESQ_ATR_SEQ replaces generated ID  
&nbsp;: modified components  
&nbsp;      pacMan  

**v1.2.2-2604.1421**  Acct transaction Phase IV, acct transfer fixes; Transfer dictionary;  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      pacMan  

**v1.2.2-2604.1319**  account transaction Phase III — single op complete; transfer draft  
&nbsp;: Feature: pacMan — AcctTransactionProcessorSingle: full single-leg processing (amount/status/balance/field validation, insert + balance update)  
&nbsp;: Feature: pacMan — AcctTransactionProcessorTransfer: two-leg transfer draft (debit source, credit target with -amount)  
&nbsp;: Refactoring: pacMan — AcctTransactionService: thin router; AcctOperation, IAcctTransactionProcessor, AcctTransactionSingle extracted to dedicated classes  
&nbsp;: modified components  
&nbsp;      pacMan  

**v1.2.2-2604.1219**  account transaction Phase II — kind=1000 dictionary; EntityFieldUtils validation; GenericValidator fix  
&nbsp;: Feature: common — EntityFieldUtils.applyFields(kind, fields): kind-based field validation with listvalues whitespace)  
&nbsp;: Fix: pacMan — AcctTransactionService: KIND_ACCTTR=1000; field validation via EntityFieldUtils; skipValidation as explicit param  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      pacMan  

**v1.2.2-2604.1016** Object Kind enumeration specified  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;: added doc/Object.Kind.enum.md  

**v1.2.2-2604.0921**  account transaction command Phase I;  
&nbsp;: Feature: pacMan — AcctTransactionService: POST /esq-acct  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      pacMan  

**v1.2.2-2604.0914**  funded account fields; generalization of biz validation rules; EntityFieldUtils utility  
&nbsp;: Feature: common — EsqAcctJpa/EsqAcct: fundedDate + negativeAllowed fields added  
&nbsp;: Refactoring: enyMan/pacMan — validateDelete() called generically on all entity delete paths  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      enyMan,  
&nbsp;      pacMan  

**v1.2.2-2604.0711**  unified facade REST API: esq-cmd-* namespace; kind-aware gateway predicate  
&nbsp;: Refactoring: pacMan — PacManController: a-prefix mappings dropped (/esq-cmd-asave→/esq-cmd-save, /esq-anew→/esq-new, /esq-adel→/esq-del); /esq-new→/esq-cmd-new, /esq-del→/esq-cmd-del  
&nbsp;: Feature/Config: gateway — EntityKindRoutePredicateFactory: kind query param tested via EsqObjectKindStorage (isAcct/isOrg/isUsr); replaces regex Query predicate in gateway config  
&nbsp;: modified components  
&nbsp;      enyMan,  
&nbsp;      pacMan,  
&nbsp;      gateway  

**v1.2.2-2604.0700**  entity kind validation: Map storage, Integer→int, upfront checks; KC theme fixes; gateway config  
&nbsp;: Refactoring: common — EsqObjectKindStorage/EsqEntityDictionaryStorage: List → Map for O(1) lookup; normalization removed  
&nbsp;: Refactoring: enyMan — all service kind params Integer → int; upfront applicability check (!isOrg && !isUsr) before permission gate  
&nbsp;: Refactoring: pacMan — all service kind params Integer → int; upfront applicability check (!isAcct) before permission gate  
&nbsp;: Refactoring: bizTree — kind normalization removed from consumer dispatch and MoveUsrHandler  
&nbsp;: Fix: compose — Keycloak theme: added error.ftl, login-update-password.ftl, login-config-totp.ftl, login-otp.ftl  
&nbsp;: Config: compose — KC_HOSTNAME=localhost for stable auth session cookies  
&nbsp;: Config: gateway — added redirect-uri to client registration  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      enyMan,  
&nbsp;      pacMan,  
&nbsp;      bizTree,  
&nbsp;      compose,  
&nbsp;      keyCloak,  
&nbsp;      gateway  

**v1.2.2-2604.0611**  esq-move command Phase III: KC path sync; entity path semantics fix  
&nbsp;: Feature: enyMan — KC path sync on USR move: EVENT_UPDATE_PATH URQ published to kcMaster; KcRequestPublisher + KcResponseListener added  
&nbsp;: Feature: kcMaster — updateEntityPath(): updates esq_rootpath KC attribute on USR entity move  
&nbsp;: Fix: — USR entity path semantics: admin (kind 30/32) ep_path = parent org path; regular ep_path = org path + user pk  
&nbsp;: modified components  
&nbsp;      enyMan,  
&nbsp;      kcMaster,  
&nbsp;      bizTree,  
&nbsp;      common,  
&nbsp;      pacMan  
&nbsp;: added doc\entity.path.semantics.md  
&nbsp;: added doc\model\ComonentModel.vsdx  

**v1.2.2-2604.0219**  esq-move command: move ORG/USR to a different parent org, Phase II - bizTree cache  
&nbsp;: Improvement: synch bizTree cache via X entity messages  
&nbsp;: modified components  
&nbsp;      enyMan,  
&nbsp;      bizTree  

**v1.2.2-2603.3120**  esq-move command: move ORG/USR to a different parent org, Phase I - basic functionality  
&nbsp;: Feature: move basics  
&nbsp;: Improvement : added kind to ESQ_ENTITY_PATH  
&nbsp;: Fix: bizTree : correct load of admins  
&nbsp;: modified components  
&nbsp;      enyMan,  
&nbsp;      pacMan,  
&nbsp;      common,  
&nbsp;      bizTree,  
&nbsp;      gateway  

**v1.2.2-2603.3013** keyCloak esquire theme  
&nbsp;: modified components  
&nbsp;      kecloak  
&nbsp;      compose  

**v1.2.2-2603.2823**  Preparing for "move" command  
&nbsp;: Refactoring:  ESQ_ENTITY_PATH — path column extracted to satellite table  
&nbsp;: modified components  
&nbsp;      enyMan,  
&nbsp;      pacMan,  
&nbsp;      keySmith,  
&nbsp;      bizTree  

**v1.2.2-2603.2817** default field — dictionary-driven entity creation defaults  
&nbsp;: Feature: common — EsqEntityField/EsqCustomEntityFieldJpa: defaultValue field; @JsonProperty("default") for JSON serialization  
&nbsp;: Feature: common — EsqEntityLayer.injectDefaults(): populates absent non-nullable fields from dictionary defaults before applyFields  
&nbsp;: Feature: common — esq-entity-dictionaries.xml:  for non-nullable fields (deleted, ccy, balance, status, connectFlg, tfaMethod)  
&nbsp;: Feature: enyMan — createOrg/createUsr: injectDefaults per layer before applyFields; insertCustomOrg/Usr uses par_default as initial *_PAR value  
&nbsp;: Feature: pacMan — createAcct(): dict-driven ccy/status defaults via injectDefaults; replaces hardcoded constants  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      enyMan,  
&nbsp;      pacMan  
&nbsp;: added doc\DefaultRule.md  

**v1.2.2-2603.2813** DELETE workflow — pre-checks and cascade-safe delete sequence  
&nbsp;: Feature: enyMan — USR delete pre-check: connectFlg="Y" → 409 (active auth connection); deletePersonAddresses+deletePersonBankInfo before deleteUsr  
&nbsp;: Feature: pacMan — ACCT delete pre-check: status != "C" → 409 (account must be closed before delete)  
&nbsp;: Fix: common — DeleteRestrictedException wired into 409 handler in GenericExceptionHandler  
&nbsp;: Refactoring: bizTree — deleteNodes(String entityId): WHERE ? IN (tree_pk, tree_tree_pk_link, tree_tree_pk_parent); covers ORG folder nodes  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      enyMan,  
&nbsp;      pacMan,  
&nbsp;      bizTree  

**v1.2.2-2603.2617** Entity CREATE workflow complete; DELETE- drafted; bizTree handler dispatch; cleanup  
&nbsp;: Feature: enyMan — POST /esq-new (ORG/USR create); POST /esq-del drafted  
&nbsp;: Feature: pacMan — POST /esq-anew (ACCT create); POST /esq-adel drafted  
&nbsp;: Feature: bizTree — CREATE cache handlers: CreateOrgHandler, CreateUsrHandler, CreateAcctHandler  
&nbsp;: Feature: bizTree — IBizTreeCacheRepository: insertOrgNodes/insertUsrNode/insertAcctNode  
&nbsp;: Feature: common — EsqUtils.generateEntityId(); TEXT_* protocol constants; FLAG_OPEN, CCY_DEFAULT  
&nbsp;: Feature: common — EmailExistsException (409 CONFLICT) on duplicate email at user creation  
&nbsp;: Refactoring: bizTree — handler dispatch map (HandlerKey → IBizTreeEventHandler) replaces inline if/else  
&nbsp;: Refactoring: bizTree — BizTreeConstants extracted; folderKindForUsr() data-driven via EsqObjectKindStorage childKinds (no hardcoded kind IDs)  
&nbsp;: Refactoring: common — parentId consolidated to EsqEntityJpa (removed from Acct/Org/Usr subclasses)  
&nbsp;: Refactoring: common — MSG_ENCODING_JSON (renamed from MESSAGE_ENCODING) in EsqMsgConstants  
&nbsp;: Draft: DELETE workflow — enyMan/pacMan endpoints + bizTree DeleteEntityHandler drafted; no cascade (Phase 4)  
&nbsp;: Cleanup: keySmith — KeySmithServiceJpa removed (dead code, superseded by KeySmithService @Primary)  
&nbsp;: Tests: kcMaster — KcRequestHandlerTest (12 tests), KcResponsePublisherTest (13 tests)  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      enyMan,  
&nbsp;      pacMan,  
&nbsp;      bizTree,  
&nbsp;      keySmith,  
&nbsp;      kcMaster  

**v1.2.2-2603.2121** Three-tier logging normalization across all services; JMS msg audit dual-mode pattern  
&nbsp;: Refactoring: established three-tier logging strategy (doc/Logging.md): console (log/@Slf4j),  
&nbsp;: Refactoring: all log.debug→devLog.debug; all log.warn eliminated (→devLog.debug or dual error)  
&nbsp;: Refactoring: dual error pattern: log.error (no stacktrace) + devLog.error (with stacktrace)  
&nbsp;: Refactoring: JMS publishers — props map migrated to LinkedHashMap+Utils.setProps;  
&nbsp;: Refactoring: JMS consumers — MDC set from message properties; cleared in finally; devLog added  
&nbsp;: Refactoring: MdcFilter — /actuator/** short-circuit added (eliminates healthcheck log noise)  
&nbsp;: Feature: common messaging.jms.Utils — setProps, formatProps(Map), formatProps(Message)  
&nbsp;: Feature: doc/Logging.md — full logging strategy reference  
&nbsp;: Config: logback-test.xml added to all services with tests (WARN, plain console, no rolling files)  
&nbsp;: Config: compose.yaml and application.yml — log env vars normalized across all services  
&nbsp;: Fix KC sync CtrlID — stable instance identifier; response listener routing by CtrlID  
&nbsp;: Config: keysmith.messaging.ctrl-id property added (env: KEYSMITH_CTRL_ID, default: keysmith.default)  
&nbsp;: Config: compose.yaml — KEYSMITH_CTRL_ID added to keysmith service; gateway service volumes mount added  
&nbsp;: modified components  
&nbsp;      common,  
&nbsp;      bizTree,  
&nbsp;      enyMan,  
&nbsp;      pacMan,  
&nbsp;      keySmith,  
&nbsp;      kcMaster,  
&nbsp;      gateway,  
&nbsp;      compose  
&nbsp;: added doc\Logging.md  

**v1.2.2-2603.2114**  Messaging Phase 3 — kcMaster service; KC sync decoupled from keySmith via JMS URQ/URS/URR  
&nbsp;: Feature: kcMaster — new messaging-only service owning all Keycloak identity sync  
&nbsp;- consumes URQ from esquire.kc.request (queue)  
&nbsp;- dispatches C/D/U commands to KcIdentityService (ported from keySmith)  
&nbsp;- publishes URS (success, silent ACK) or URR (reject, RFC 9457 Error) to esquire.kc.response  
&nbsp;- kc.audit rolling log (kcMaster-audit.log) — full JMS message per URQ/URS/URR  
&nbsp;: Feature: keySmith — KC sync decoupled; publishes URQ via JMS instead of direct KC calls  
&nbsp;- KcSyncPublisher replaces syncToKeycloak(); fires after DB transaction commits  
&nbsp;- KcSyncResponseListener receives URS/URR; logs to kc.sync rolling log (keySmith-sync.log)  
&nbsp;- keycloak-admin-client dependency removed from keySmith pom.xml  
&nbsp;: Feature: TestReqID (FIX tag 112) — intra-exchange correlation key; set by requester, echoed in URS/URR  
&nbsp;: Feature: whole-message JMS logging — publishers via LinkedHashMap props; listeners via getPropertyNames()  
&nbsp;: Refactoring: command values C/D/U (create/delete/update); wire header name remains EventType (tag 50005)  
&nbsp;: Fix: kcMaster depends_on keycloak + activemq added to compose (was missing entirely)  
&nbsp;: Fix: keySmith depends_on keycloak removed from compose (no direct KC calls remain)  
&nbsp;: Config: kcMaster logback-spring.xml — kc.audit rolling file appender (kcMaster-audit.log)  
&nbsp;: Config: keySmith logback-spring.xml — kc.sync rolling file appender; kc.audit removed (unused)  
&nbsp;: modified components  
&nbsp;- kcMaster (new service)  
&nbsp;- keySmith  
&nbsp;- compose  
&nbsp;: added doc\Message.Structure.md  

**v1.2.2-2603.2019**  Dictionary affects3 field added  
&nbsp;: Refactoring: EsqEntityField — affects3 field added  

**v1.2.2-2603.2017**  
&nbsp; Messaging Phase 2 — cache update from entity broadcast; properties-only transport; H2 cache loader  
&nbsp;: Feature: bizTree H2 in-memory cache built from DB on startup (BizTreeCacheLoader)  
&nbsp;- loads ORG/USR/ACCT from primary DB via EsqOrgRepository/EsqUsrRepository/EsqAcctRepository  
&nbsp;- builds folder nodes per org/usr kind; computes tree paths and levels via BFS  
&nbsp;: Feature: BizTreeService switched from EsqTreeNodeRepository to IBizTreeCacheRepository  
&nbsp;: Feature: EsqEntityBroadcastConsumer Phase 2 — UPDATE events applied to H2 cache  
&nbsp;- handles "deleted" (usr_deleted_flg, enyMan/USR) and "status" (acc_status, pacMan/ACCT)  
&nbsp;- decodeStatus(): raw string → 0/1/2 (ok/deleted/locked)  
&nbsp;- null status/deleted values not propagated (treated as absent)  
&nbsp;: Feature: single CASE-based SQL UPDATE for cache node (name/desc/status in one query)  
&nbsp;- name: null = skip; desc: CHAR(0) sentinel = skip, null = clear; status: null = skip  
&nbsp;: Feature: enyMan publishes raw "deleted" field (usr_deleted_flg) on USR status change  
&nbsp;: Feature: pacMan publishes raw "status" field (acc_status) on ACCT status change  
&nbsp;: Refactoring: properties-only transport — publishers switched from TextMessage+body to Message+JMS properties  
&nbsp;- Text serialized as JSON string property (entity state snapshot only, no FIX-JSON envelope)  
&nbsp;- enyMan/pacMan EsqEntityBroadcastConsumer updated to read Text via getStringProperty()  
&nbsp;: Refactoring: producer decoupling rule — raw entity field values sent without interpretation  
&nbsp;- consumers own all value interpretation (bizTree decodes status independently)  
&nbsp;: Refactoring: EsqAcctJpa, EsqOrgJpa, EsqUsrJpa — parentId and path fields added  
&nbsp;: Fix: serviceId default corrected in enyMan/pacMan application.yml: was service name, now entity-update-broadcast  
&nbsp;: Fix: EsqMsgConstants.SERVICE_ID_ENTITY_BROADCAST added (for tests; production uses config @Value)  
&nbsp;: Fix: PerformanceAspect — ScopeNotActiveException guard; skip metrics outside request scope (startup loaders)  
&nbsp;: modified components  
&nbsp;- common (EsqMsgConstants, EsqAcctJpa, EsqOrgJpa, EsqUsrJpa, PerformanceAspect)  
&nbsp;- enyMan  
&nbsp;- pacMan  
&nbsp;- bizTree  
&nbsp;: updated doc\Messaging.First.md — Phase 2 delivered; Phase 3 plan; vendor-agnostic broker as item 0  

**v1.2.2-2603.19HH**  
&nbsp; Messaging Phase 1 — ActiveMQ entity broadcast infrastructure  
&nbsp;: Feature: EsqMsgConstants — FIX-JSON protocol constants for esquire.entity.broadcast topic  
&nbsp;: Feature: enyMan publishes entity broadcast on ORG/USR update when name or desc changes  
&nbsp;: Feature: pacMan publishes entity broadcast on ACCT update when name or desc changes  
&nbsp;: Feature: bizTree durable subscriber logs received broadcast messages (Phase 1)  
&nbsp;: Feature: activemq/conf/activemq.xml — broker "esquire" with topic declaration  
&nbsp;: Config: per-service JMS settings (clientId, broker-url, consumer.enabled, broadcast-log-path)  
&nbsp;: Config: bizTree logback-spring.xml — entity.broadcast + JMS/AMQ loggers; rolling log file  
&nbsp;: Fix: AEnyManService.esquireDictionary() — kind normalized to even before dictionary lookup  
&nbsp;: modified components  
&nbsp;- common (EsqMsgConstants)  
&nbsp;- enyMan  
&nbsp;- pacMan  
&nbsp;- bizTree  
&nbsp;- activemq/conf  
&nbsp;: added doc\Messaging.First.md  

**v1.2.2-2603.1620**  
&nbsp; TOTP state machine; reset password handshake; connectFlg lifecycle; KC integration  
&nbsp;: Feature: TOTP pending states (G→g pending enable, N→n pending disable)  
&nbsp;: Feature: TOTP disable via KC: removeTotp parameter added to updateUserAuthState()  
&nbsp;: Feature: au_connect_flg (connectFlg) field added across the stack  
&nbsp;: Feature: syncToKeycloak() — three-branch KC sync on access profile save  
&nbsp;: Feature: TOTP forced to N when connect flag transitions N→Y  
&nbsp;: Async: @EnableAsync — KC calls executed on virtual thread pool (@Async)  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- keySmith  
&nbsp;: doc\keySmithCredentialRoutine.md added  

**v1.2.2-2603.1017** Unit tests were added to all services  
&nbsp;- bizTree  
&nbsp;- common  
&nbsp;- enyMan  
&nbsp;- gateway  
&nbsp;- keySmith  
&nbsp;- pacMan  

**v1.2.2-2603.1015**  
&nbsp; keySmith DTO approach: roles and permissions sourced from in-memory storage  
&nbsp; fillKindFieldLayer() method name corrected — Cyrillic К replaced with ASCII K  
&nbsp;: Refactoring: EsqRolesStorage — two new methods added  
&nbsp;+ roles(): returns all roles as List from in-memory map  
&nbsp;+ fillPermissionsForRole(roleName, list): accumulates permissions for one role; creates list if null  
&nbsp;: Refactoring: EsqAccessProfile.fill() DTO overload added  
&nbsp;- accepts List rolesAll and List — no JPA types required  
&nbsp;- original fill() renamed fillJpa() — still accepts List / List  
&nbsp;: Refactoring: KeySmithService (@Primary) — rolesAll and permissions from EsqRolesStorage  
&nbsp;- eliminates extra JPA round-trips for accessProfileRepository.rolesAll() / .permissions()  
&nbsp;- fillPermissionsForRole() loop over rolesAssigned replaces single JPA permissions query  
&nbsp;- saveAccess(): rolesAll[] param removed; caller sources rolesAll from Storage post-transaction  
&nbsp;: Fix: EsqEntityDictionary.fillКindFieldLayer() renamed to fillKindFieldLayer()  
&nbsp;- Cyrillic К character in method name caused non-ASCII compiler warning  
&nbsp;- all callers updated: AEnyManService, OrgService, UsrService, PacManService, KeySmithService  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- enyMan  
&nbsp;- keySmith  
&nbsp;- pacMan  

**v1.2.2-2603.1013**  
&nbsp; Observability, security, exception handling generalized to common library  
&nbsp;: Refactoring: observability classes moved from all 4 services to common backend.service  
&nbsp;+ MdcFilter — MDC population, correlation/request ID headers, @Order(1)  
&nbsp;+ RequestContextUtils — static getCorrelationId() / getRequestId() utility  
&nbsp;+ RequestPerformance — request-scoped JPA timing tracker (@RequestScope)  
&nbsp;+ PerformanceAspect — @Aspect; pointcut pro.mir0n.esquire..jpa.*.* covers all services  
&nbsp;: Refactoring: security classes moved from all 4 services to common backend.security  
&nbsp;+ JwtAuthenticationFilter — Bearer token validation; 401 if roles missing/empty  
&nbsp;+ JwtService — JWT parsing without signature validation  
&nbsp;+ SecurityConfiguration — stateless filter chain (@EnableWebSecurity)  
&nbsp;: Refactoring: GlobalExceptionHandler moved from all 4 services to common backend.exception  
&nbsp;- one canonical handler; @RestControllerAdvice picked up via scanBasePackages  
&nbsp;- handleMethodArgumentNotValid inline; handleGenericRuntimeException delegates to GenericExceptionHandler  
&nbsp;: Refactoring: all 4 Application classes — scanBasePackages extended  
&nbsp;- backend.service, backend.security, backend.exception added for common bean discovery  
&nbsp;: Refactoring: service impl files — RequestContextUtils import updated to backend.service  
&nbsp;: Cleanup: 28 files removed from enyMan, keySmith, pacMan, bizTree (moved to common)  
&nbsp;: Fix: gateway KeycloakRoleConverter — EsqConstants used for JWT claim keys  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- gateway  
&nbsp;- enyMan  
&nbsp;- keySmith  
&nbsp;- pacMan  
&nbsp;- bizTree  

**v1.2.2-2603.0922**  
&nbsp; Exception handling centralized; Esquire exception hierarchy unified  
&nbsp;: Refactoring: GenericRuntimeException added to common — base class for all Esquire exceptions  
&nbsp;- InvalidValueException, ResourceNotFoundException, PermissionDeniedException all extend it  
&nbsp;: Refactoring: GenericExceptionHandler added to common — static utility; centralizes all handlers  
&nbsp;- handleMethodArgumentNotValid, handleGenericRuntimeException, handleException  
&nbsp;- handleGenericRuntimeException dispatches: PermissionDeniedException→403, others→400  
&nbsp;: Refactoring: all service GlobalExceptionHandlers refactored as thin delegates  
&nbsp;- each handler is a one-liner forwarding to GenericExceptionHandler  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- enyMan  
&nbsp;- keySmith  
&nbsp;- pacMan  
&nbsp;- bizTree  

**v1.2.2-2603.0920**  
&nbsp; Service-side permission validation; exception handling hardened  
&nbsp;: Security: storage.roles package added to common  
&nbsp;+ IRolesService — roles/permissions service interface  
&nbsp;+ JpaRolesRepository — native queries: roles(), permissions(id)  
&nbsp;+ JpaRolesService — JPA-backed impl mapping JPA→DTO  
&nbsp;: Security: EsqRolesStorage added to common — in-memory roles/permissions cache  
&nbsp;- findAdminPermissions(roles): resolves EsqPermission map from JWT role list  
&nbsp;- isAdminCmdPermitted(permission, cmd): checks CREATE/UPDATE/DELETE/AUTH/ACCT flags  
&nbsp;- AdminCmd enum: CREATE(0), UPDATE(1), DELETE(2), AUTH(3), ACCT(4)  
&nbsp;: Security: PermissionDeniedException added to common (HTTP 403 FORBIDDEN)  
&nbsp;: Security: EsqRolesStorage initialized via ApplicationReadyEvent in enyMan, keySmith, pacMan  
&nbsp;: Security: esquireCommandSave() / esquireKeySave() gated by isAdminCmdPermitted()  
&nbsp;- enyMan: UPDATE flag required; self-update bypass for USR (id.equals(uid))  
&nbsp;- keySmith: AUTH flag required; self-update bypass (upk.equals(uid))  
&nbsp;- pacMan: UPDATE flag required  
&nbsp;: Fix: GlobalExceptionHandler — PermissionDeniedException handler added (HTTP 403) in all services  
&nbsp;: Fix: GlobalExceptionHandler — ResourceNotFoundException unified to HTTP 400 BAD_REQUEST  
&nbsp;: Fix: GlobalExceptionHandler — @Slf4j + logging added to keySmith, pacMan, bizTree handlers  
&nbsp;: Fix: InvalidValueException and PermissionDeniedException handlers added to keySmith, pacMan  
&nbsp;: Security: JwtAuthenticationFilter — realm_access.roles validated; 401 if missing/empty  
&nbsp;: Security: controllers (enyMan, keySmith, pacMan) — realm_access.roles extracted from JWT and forwarded to service layer  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- enyMan  
&nbsp;- keySmith  
&nbsp;- pacMan  
&nbsp;- bizTree  

**v1.2.2-2603.0821**  
&nbsp; Personal-field enforcement: users can only update their own personal-marked fields  
&nbsp;: Security: boolean personal param added to IValidator / GenericValidator / ValidatorFactory  
&nbsp;- GenericValidator: throws InvalidValueException if personal=true and field.personal != "Y"  
&nbsp;- keySmith BizValidatorFactory: throws if personal=true (cannot change own permissions)  
&nbsp;: Security: self-update context propagated in enyMan (UsrService) and keySmith (KeySmithService)  
&nbsp;- personal = id.equals(uid) / upk.equals(uid) determined at save entry point  
&nbsp;: Refactoring: EsqEntityField.isSubentity() and isTabField() helper methods added  
&nbsp;: Refactoring: EsqEntityKindFieldLayer.layerTitle field + getLabel() for context-aware labels  
&nbsp;: Fix: esq-entity-dictionaries.xml: personal=N corrected for non-personally-editable fields  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- enyMan  
&nbsp;- keySmith  
&nbsp;- pacMan  

**v1.2.2-2603.0619**  
&nbsp; Field validation framework; business rule validators; null-safe fixes  
&nbsp;: Validation: ValidatorFactory + GenericValidator added to common library  
&nbsp;- null/blank, nullable, pattern, minmax, type validation for all writable fields  
&nbsp;- BizValidatorFactory added to keySmith and pacMan for domain-specific rules  
&nbsp;- keySmith: max 1 admin role per user  
&nbsp;- pacMan: cannot close account with positive balance  
&nbsp;: Validation: applyFields() in enyMan (AEnyManService / OrgService / UsrService)  
&nbsp;     refactored to use dict-driven ValidatorFactory instead of WRITABLE set whitelists  
&nbsp;: Fix: EsqNameValueSerializer — writeNullField() when custom field value is null  
&nbsp;: Fix: EsqOrg.customFields — null-safe LinkedHashMap replaces Collectors.toMap()  
&nbsp;: Fix: InvalidValueException added; GlobalExceptionHandler returns HTTP 400 with field errors  
&nbsp;: Fix: EsqAccessProfileJpa now extends EsqEntityJpa  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- enyMan  
&nbsp;- keySmith  
&nbsp;- pacMan  

**v1.2.2-2603.0413**  
&nbsp;  Dictionary formalized  
&nbsp;- minmax, format, readwrite, nullable, validation, tooltip  

**v1.2.2-2603.0313**  
&nbsp; Roles list save added, Few fixes, new field type: "text"  
&nbsp; Gateway uses default port  
&nbsp;: Role/permission JPA and DTO generalized; access profile rolesAll; dict/kinds extended  
&nbsp;: keySmith access profile update  
&nbsp;: correct saving of address(es)  
&nbsp;: save address.url  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- enyMan  
&nbsp;- keySmith  

**v1.2.2-2603.0115**  
&nbsp; DOB (date of birth) field activated in person entity  
&nbsp;: pe_dob enabled in SELECT and UPDATE queries (oracle + postgres)  
&nbsp;: UsrService.saveUsr() now passes dob to updatePerson()  
&nbsp;: EsqPerson.dob @Schema format updated to ISO-8601 (YYYY-MM-DD)  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- enyMan  

**v1.2.2-2602.2818**  
&nbsp; Person and address subentity support for user detail and save  
&nbsp;: person  
&nbsp;: residential address and biz address  
&nbsp;: EnyManService split into OrgService / UsrService / AEnyManService (abstract base)  
&nbsp;- applyFields() and esquireDictionary() extracted to AEnyManService  
&nbsp;- org and usr logic separated into dedicated service classes  
&nbsp;: getName() NPE when middleName is null — was root cause of HTTP 500 on GET /esq-cmd  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- enyMan  

**v1.2.2-2602.1920**  
&nbsp; Implemented common/audit JPA entity field updates.  
&nbsp;: DB: audit columns (*_CRL_ID, *_REQ_ID, *_UID) added to all major tables  
&nbsp;: Save: save (update) REST API added to enyMan, pacMan, keySmith  
&nbsp;- POST /esq-cmd-save  -> enyMan  (org, usr)  
&nbsp;- POST /esq-cmd-asave -> pacMan  (account)  
&nbsp;- POST /esq-key-save  -> keySmith (access profile)  
&nbsp;: Save: Hibernate spurious auto-flush issue resolved  
&nbsp;- FlushModeType.COMMIT + @Modifying(clearAutomatically=true, flushAutomatically=false)  
&nbsp;- native-query-only write strategy enforced across all services  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- enyMan  
&nbsp;- pacMan  
&nbsp;- keySmith  
&nbsp;- gateway (save routes added)  

**v1.2.2-2602.1322**  
&nbsp; refactoring/cleanup in progress  
&nbsp;- removed treeFlags from EsqTreeNode*  
&nbsp;- children in EsqEntityJpa are EsqEntityJpa  

**v1.2.2-2602.1217**  
&nbsp; let set of Object Kinds configured in server side, and not hardcoded  
&nbsp;: refactoring: EsqObjectKind instead of EsqEntityKind  
&nbsp;: let set of EsqEntityKind-s configured in xml, not hardcoded  
&nbsp;: added "/esq-kinds" REST API entry point  

**v1.2.2-2602.0418**  
&nbsp;  normalization of entity objects structure: EsqThing added  
&nbsp;  some cleanup @Around("execution ...  

**v1.2.2-2602.0216**  
&nbsp; "SysAdmin" and "Sys Admin-s" added  
&nbsp; Gaps in Entity Kind enumeration: system objects  orgs  users  accounts  
&nbsp;: modified components  
&nbsp;- common  
&nbsp;- compose (keyCloak dump refreshed)  
&nbsp;- gateway  
&nbsp;- keycloak (dump refreshed)  
&nbsp;- keySmith (bug in oracle queries)  

---

## Code Changes

### bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt


**04/16/2026** mir0n  procedural style sweep: ret pattern, null-guard  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- insertAcctNode, moveOrgNode, moveUsrNode, moveAcctNode: null-guard replaces early returns  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- kindBits: ternary expression expanded to explicit if-assignments  
**service.impl.BizTreeService**  
&nbsp;- rootId extracted as named local variable  

**04/07/2026** mir0n  kind normalization removed from dispatch and handlers; param rename  
**cache.IBizTreeCacheRepository**  
&nbsp;- moveUsrNode(): param renamed kind (raw kind; storage handles via getOrDefault)  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- moveUsrNode(): param renamed kind; EsqObjectKindStorage.get() receives raw kind  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- kind normalization removed; EsqObjectKindStorage.get() receives raw entityKind  
&nbsp;+ added messaging.handler.MoveUsrHandler header  
**messaging.handler.MoveUsrHandler**  
&nbsp;- normalization removed; raw entityKind passed to moveUsrNode()  

**04/06/2026** mir0n  esq-move path semantics fix  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- moveUsrNode(): admin-aware orgPk extraction — admin ep_path last segment is org pk  

**04/02/2026** mir0n move event processing  
**cache.IBizTreeCacheRepository**  
&nbsp;- added moveOrgNode()  
&nbsp;- added moveUsrNode();  
&nbsp;- added moveAcctNode();  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- added moveOrgNode()  
&nbsp;- added moveUsrNode();  
&nbsp;- added moveAcctNode();  
**cache.BizTreeCacheSql**  
&nbsp;- added 3 Repo queries:  moveNode, moveAcctLink, findFolderPks  
**h2.BizTreeH2Config**  
&nbsp;- added 3 Repo queries:  moveNode, moveAcctLink, findFolderPks  
&nbsp;+ added messaging.handler.MoveAcctHandler  
&nbsp;+ added messaging.handler.MoveOrgHandler  
&nbsp;+ added messaging.handler.MoveUsrHandler  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- added 3 move handlers for each kind-kind  

**03/31/2026** mir0n  folderKindForUsr fix; log cleanup  
BizTreeConstants  
&nbsp;- folderKindForUsr(): skip FOLDER_SYS_ADMIN — admin users no longer mis-routed into root-org sys-admin folder  
**cache.BizTreeCacheLoader**  
&nbsp;- devLog debug line added in user-building loop  

**03/28/2026** mir0n  ESQ_ENTITY_PATH — JOIN esq_entity_path in findAllForTree queries  
META-INF/oracle-entity.xml, postgres-entity.xml  
&nbsp;- EsqOrgJpa.findAllForTree: JOIN esq_entity_path; ep_path AS org_path alias  
&nbsp;- EsqUsrJpa.findAllForTree: JOIN esq_entity_path; ep_path AS usr_path alias  
&nbsp;- EsqAcctJpa.findAllForTree: JOIN esq_entity_path; ep_path AS acc_path alias  

**03/28/2026** mir0n  DELETE workflow — deleteNodes refactored to string entityId; multi-column WHERE covers ORG folders  
**cache.IBizTreeCacheRepository**  
&nbsp;- deleteNodes(String entityId): WHERE ? IN (tree_pk, tree_tree_pk_link, tree_tree_pk_parent)  
&nbsp;   covers USR (tree_pk), ACCT main+shortcut (tree_pk+tree_tree_pk_link), ORG folder nodes (tree_tree_pk_parent)  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- deleteNodes() impl updated for new String entityId param  
**messaging.handler.DeleteEntityHandler**  
&nbsp;- passes entityId string directly to deleteNodes(); no Long.parseLong  

**03/26/2026** mir0n  Handler dispatch refactoring; CREATE handler support; DELETE skeleton; constants  
BizTreeConstants  (new)  
&nbsp;- centralized constants: status codes, folder kinds/names, entity kind ranges, column names, key separator  
&nbsp;- decodeStatus(raw): public static — "Y"/"C"→1, "L"→2, other/null→0  
&nbsp;- folderKindForUsr(etPk): data-driven folder routing — finds folder kind (id < KIND_ORG_MAX)  
&nbsp;   whose childKinds contains etPk via EsqObjectKindStorage; defaults to FOLDER_ADMIN  
&nbsp;- KIND_USR_CLIENT/KIND_USR_MERCHANT removed  
messaging.IBizTreeEventHandler  (new)  
&nbsp;- handler interface for (eventType, kindBits) dispatch map  
messaging.handler.UpdateEntityHandler  (new)  
&nbsp;- handles UPDATE events for ORG/USR/ACCT; decodeStatus() moved from consumer  
messaging.handler.CreateOrgHandler  (new)  
&nbsp;- handles CREATE events for ORG kinds; calls insertOrgNodes()  
messaging.handler.CreateUsrHandler  (new)  
&nbsp;- handles CREATE events for USR kinds; calls insertUsrNode()  
messaging.handler.CreateAcctHandler  (new)  
&nbsp;- handles CREATE events for ACCT kinds; calls insertAcctNode() (main + shortcut nodes)  
messaging.handler.DeleteEntityHandler  (new — skeleton)  
&nbsp;- handles DELETE events for ORG/USR/ACCT; removes nodes by tree_entity_pk; no cascade  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- handler map dispatch: HandlerKey(eventType,kindBits) → IBizTreeEventHandler  
&nbsp;- DeleteEntityHandler registered for (DELETE, ORG/USR/ACCT)  
**cache.IBizTreeCacheRepository**  
&nbsp;- insertOrgNodes/insertUsrNode/insertAcctNode added; deleteNodes(entityPk) added  
**cache.impl.BizTreeCacheRepository**  
&nbsp;- insertOrgNodes: inserts org node + folder nodes (4 for non-root, 1 sys-admin for root)  
&nbsp;- insertUsrNode: inserts user node; folder routing via BizTreeConstants.folderKindForUsr()  
&nbsp;- insertAcctNode: inserts main node under user + shortcut node under org FOLDER_ACCOUNT  
&nbsp;- deleteNodes: DELETE FROM ESQ_TREE WHERE tree_entity_pk = ?  
**cache.BizTreeCacheSql**  
&nbsp;- deleteNode field added to Repo record  
**h2.BizTreeH2Config**  
&nbsp;- delete-node SQL property wired into BizTreeCacheSql.Repo  
**cache.BizTreeCacheLoader**  
&nbsp;- magic constants replaced with BizTreeConstants.*  
&nbsp;- folder routing: folderKindForUsr(etPk) replaces hardcoded KIND_USR_* comparisons  
**messaging.BizTreeJmsConfig**  
&nbsp;- @Qualifier("jmsConnectionFactory") added to ConnectionFactory parameter  
BizTreeApplication  
&nbsp;- BizTreeApplicationStartingListener: EsqObjectKindStorage loaded on ApplicationStartingEvent  

**03/21/2026** mir0n  three-tier logging normalization  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- broadcastLog→msgLog/devLog; MDC set/clear; requestId/correlationId reads  
&nbsp;- dual-mode ENTITY msg audit; console echo log.info; dual error pattern; unused imports removed  
cache.BizTreeCacheLoader, cache.impl.BizTreeCacheRepository  
&nbsp;- devLog added; log.debug→devLog.debug  
**service.IBizTreeService**  
&nbsp;- unused imports removed  
service.impl.BizTreeService, controller.BizTreeController  
&nbsp;- devLog added; log.debug→devLog.debug  

**03/20/2026** mir0n  Messaging Phase 2 — cache update from entity broadcast events  
cache.BizTreeCacheLoader  (new)  
&nbsp;- loads ORG/USR/ACCT entities from DB into H2 in-memory cache on ApplicationReadyEvent  
&nbsp;- builds folder nodes per org/usr kind; computes tree paths and levels via BFS  
cache.IBizTreeCacheRepository  (new)  
&nbsp;- vendor-agnostic interface for the in-memory tree cache  
&nbsp;- updateNode(entityPk, name, desc, statusCode): SKIP sentinel for desc; null name/statusCode = skip  
cache.impl.BizTreeCacheRepository  (new)  
&nbsp;- H2 implementation of IBizTreeCacheRepository  
&nbsp;- updateNode(): single CASE-based SQL UPDATE; WHERE tree_entity_pk only (entity_pk is globally unique)  
cache.BizTreeCacheSql  (new)  
&nbsp;- vendor-agnostic SQL holder records: Ddl (create-table, indexes), Repo (7 selects + update-node), Loader (insert, update-path, select-paths)  
h2.BizTreeH2Config  (new)  
&nbsp;- H2 cache configuration; BizTreeCacheSql and cacheJdbcTemplate beans  
&nbsp;- explicit HikariCP pool config: pool-name, max-size, min-idle, timeouts  
resources/META-INF/h2-cache-sql.properties  (new)  
&nbsp;- H2 DDL and all cache SQL; update-node: single CASE UPDATE (name IS NULL skip, desc CHAR(0) skip, status IS NULL skip)  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- Phase 2: UPDATE events applied to H2 cache via cacheRepository.updateNode()  
&nbsp;- handles "deleted" key (usr_deleted_flg, enyMan/USR) and "status" key (acc_status, pacMan/ACCT)  
&nbsp;- decodeStatus(): "Y"/"C" → 1 (deleted), "L" → 2 (locked), other/null → 0 (ok)  
&nbsp;- null status/deleted values not propagated (treated as absent)  
**service.impl.BizTreeService**  
&nbsp;- switched from EsqTreeNodeRepository to IBizTreeCacheRepository (H2 in-memory cache)  
jpa.EsqAcctRepository  (new)  
&nbsp;- JPA repository for EsqAcctJpa; findAllForTree() native query  
jpa.EsqOrgRepository  (new)  
&nbsp;- JPA repository for EsqOrgJpa; findAllForTree() native query  
jpa.EsqUsrRepository  (new)  
&nbsp;- JPA repository for EsqUsrJpa; findAllForTree() native query  

**03/19/2026** mir0n  Messaging Phase 1 : entity broadcast durable consumer  
messaging.BizTreeJmsConfig  (new)  
&nbsp;- JMS/ActiveMQ config for durable topic consumer  
&nbsp;- clientId set explicitly on CachingConnectionFactory (spring.jms.client-id not reliably applied)  
messaging.EsqEntityBroadcastConsumer  (new)  
&nbsp;- durable subscriber on esquire.entity.broadcast; selector: BusID='esquire.entity' AND MsgType='UE'  
&nbsp;- subscription name: esquire.entity.broadcast.biztree.primary (stable, never change)  
&nbsp;- Phase 1: logs received messages to entity.broadcast logger > entity-broadcast.log  
**resources/application.yml**  
&nbsp;- spring.activemq: broker-url, user, password; spring.jms.client-id  
&nbsp;- biztree.messaging: client-id, consumer.enabled, broadcast-log-path  
&nbsp;- LOG_LEVEL_JMS, LOG_LEVEL_AMQ, LOG_LEVEL_BROADCAST log levels  
&nbsp;- entity.broadcast logger  
resources/logback-spring.xml  (new)  
&nbsp;- entity.broadcast logger  
&nbsp;- org.springframework.jms and org.apache.activemq loggers  

**03/10/2026** mir0n  observability, security, exception handling generalized to common  
x removed exception.GlobalExceptionHandler (moved to common backend.exception)  
x removed security.JwtAuthenticationFilter, JwtService, SecurityConfiguration (moved to common)  
x removed service.MdcFilter, RequestContextUtils, RequestPerformance, PerformanceAspect (moved to common)  
BizTreeApplication  
&nbsp;- scanBasePackages: backend.service, backend.security, backend.exception added  
**service.impl.BizTreeService**  
&nbsp;- import: RequestContextUtils updated to backend.service package  

**03/09/2026** mir0n  exception handling centralized  
**exception.GlobalExceptionHandler**  
&nbsp;- refactored: thin delegate; all handlers forward to GenericExceptionHandler (common)  
&nbsp;- GenericRuntimeException handler covers ResourceNotFoundException and all Esquire exceptions  

**03/09/2026** mir0n  
**exception.GlobalExceptionHandler**  
&nbsp;- @Slf4j added; logging added to all handlers  
&nbsp;- ResourceNotFoundException: NOT_FOUND → BAD_REQUEST  

### common/src/main/java/pro/mir0n/esquire/backend/changes.txt


**04/22/2026** mir0n  MdcFilter: String.valueOf() fix; esq-entity-dictionaries: rate field added to Transfer dictionary  
**security.SecurityConfiguration**  
&nbsp;- k8s issues: addded corsAllowAll()  

**04/20/2026** mir0n  MdcFilter: String.valueOf() fix; esq-entity-dictionaries: rate field added to Transfer dictionary  
**service.MdcFilter**  
&nbsp;- String.valueOf() applied to getTotalJpaTime() in log statement  
**esq-entity-dictionaries.xml**  
&nbsp;- kind=1004 (Transfer): rate field added (nullable=N, default=1.00, format=#,##0.000000)  
&nbsp;- kind=1004 (Transfer): id2 label corrected ("Two" -> "To")  

**04/16/2026** mir0n  procedural style sweep: ret pattern, null-guard  
**dto.access.EsqAccessProfile**  
&nbsp;- fill(): null-guard replaces early return  
**error.ProblemDetailMill**  
&nbsp;- extractCorrelationId(): ret pattern replaces early returns  
**service.MdcFilter**  
&nbsp;- jpaTime local variable inlined in log call  

**04/14/2026** mir0n  esq-entity-dictionaries: kind=1004 Transfer dictionary added; esq-object-kinds: kind 51 acct flag corrected  
**esq-entity-dictionaries.xml**  
&nbsp;- kind=1004 (Transfer) dictionary added with fields: id, id2, typeId, amount, refCode, refCode2, memo, desc  
**esq-object-kinds.xml**  
&nbsp;- kind 51 (cacctlnk): true (was false)  

**04/12/2026** mir0n  kind=1000 account transaction dictionary; EntityFieldUtils kind-based validation; GenericValidator pattern trim  
**service.EntityFieldUtils**  
&nbsp;- applyFields(kind, fields): kind-based validation overload; validates writable fields with listvalues constraint check  
**validator.GenericValidator**  
&nbsp;- regex match: pattern.trim() prevents XML leading/trailing whitespace from breaking validation pattern  

**04/09/2026** mir0n  
esq-entity-dictionaries.xml:  
&nbsp;- kind=980 typeId field:  opening tag corrected to  (caused XML parse failure in all services on startup)  

**04/09/2026** mir0n  funded account fields; IValidator.validateDelete(); enforceDefaults moved to EntityFieldUtils  
&nbsp;+ added service.EntityFieldUtils  (new)  
&nbsp;- applyFields(jpa, fields, personal, subLayer, writables): dict-driven field apply with BeanWrapper; moved from AEnyManService  
&nbsp;- applyFields(jpa, fields): simple overload — delegates with personal=false, subLayer=0, writables=null  
&nbsp;- enforceDefaults(layer, jpa): post-create safety net for required fields; moved from EsqEntityLayer  
**dto.EsqEntityLayer**  
&nbsp;- enforceDefaults() removed — moved to EntityFieldUtils; BeanWrapper/ArrayList imports removed  
**dto.entity.EsqAcct**  
&nbsp;- fundedDate, negativeAllowed fields added; wired in fillDetails()  
**dto.entity.EsqUsr**  
&nbsp;- deleted @Schema example corrected: "false" → "N"  
**jpa.entity.EsqAcctJpa**  
&nbsp;- fundedDate, negativeAllowed fields added  
**validator.IValidator**  
&nbsp;- validate() changed to default method (no-op return value); validateDelete() default no-op added  
**validator.ValidatorFactory**  
&nbsp;- validateDelete(): routes to biz validator chain by entity kind; null-safe (null bizValidators/origin/kind guards)  

**04/07/2026** mir0n  kind validation: storage List → Map; normalization removed; EsqUsr cleanup  
**storage.EsqObjectKindStorage**  
&nbsp;- storage changed from List to Map for O(1) lookup  
&nbsp;- get(): getOrDefault(id, UNKNOWN); getAll(): returns defensive copy  
**storage.EsqEntityDictionaryStorage**  
&nbsp;- storage changed from List to Map for O(1) lookup  
&nbsp;- get(int kind): direct map lookup; init(fileName): forEach put  
**dto.entity.EsqUsr**  
&nbsp;- removed dead findKind() helper; removed debug println  

**04/06/2026** mir0n  entity path semantics: isPathParentOnly()  
**dto.EsqObjectKind**  
&nbsp;- isPathParentOnly(): true for SYS_ADMIN (30) and ADMIN (32) — ep_path equals parent org path, own PK not appended  

**03/31/2026** mir0n  Jackson OffsetDateTime fix  
**security.JwtAuthenticationFilter**  
&nbsp;- JavaTimeModule registered on ObjectMapper: fixes OffsetDateTime serialization in ProblemDetail error responses  
**pom.xml**  
&nbsp;- jackson-datatype-jsr310 dependency added  

**03/28/2026** mir0n  default field — parameter metadata default value support  
EsqCustomEntityFieldJpa  
&nbsp;- defaultValue field added (par_default from ESQ_PARAMETER)  
EsqEntityField  
&nbsp;- defaultValue field added; @JsonProperty("default") for correct JSON serialization  
EsqEntityLayer  
&nbsp;- injectDefaults(Map): populates absent non-nullable fields from dictionary defaults before applyFields  
EsqEntityDictionaryMapper  
&nbsp;- defaultValue mapped from EsqCustomEntityFieldJpa to EsqEntityField  
**esq-entity-dictionaries.xml**  
&nbsp;- added to non-nullable fields: deleted (N), ccy (USD), balance (0), status (O), connectFlg (N), tfaMethod (N)  

**03/28/2026** mir0n  DELETE pre-checks — connectFlg guard (USR), status guard (ACCT); DeleteRestrictedException → 409  
**error.GenericExceptionHandler**  
&nbsp;- DeleteRestrictedException added to 409 handler alongside EmailExistsException  
**jpa.entity.EsqUsrJpa**  
&nbsp;- connectFlg field added — used for pre-delete active-auth check  

**03/26/2026** mir0n  Entity CREATE/DELETE — parentId consolidation; EmailExistsException; CREATE support  
**jpa.EsqEntityJpa**  
&nbsp;- parentId field added — consolidated from EsqAcctJpa/EsqOrgJpa/EsqUsrJpa  
jpa.entity.EsqAcctJpa, EsqOrgJpa, EsqUsrJpa  
&nbsp;- parentId removed — now inherited from EsqEntityJpa  
**dto.EsqEntity**  
&nbsp;- parentId field added  
error.EmailExistsException  (new)  
&nbsp;- thrown when au_email already exists in esq_auth on user creation; @ResponseStatus(CONFLICT)  
**error.GenericExceptionHandler**  
&nbsp;- handleEmailExists(): EmailExistsException → 409 CONFLICT RFC 9457 response  

**03/21/2026** mir0n  three-tier logging normalization; actuator probe filtering; unused imports removed  
**service.MdcFilter**  
&nbsp;- devLog added; log.debug→devLog.debug; dual error pattern  
&nbsp;- /actuator/** short-circuit: bypasses MDC setup and all logging (prevents healthcheck noise)  
**error.GenericExceptionHandler**  
&nbsp;- devLog added; log.warn on exception→dual error (log.error+devLog.error); unused imports removed  
**exception.GlobalExceptionHandler**  
&nbsp;- devLog added; dual error pattern (no stacktrace on console); unused imports removed  
**security.JwtAuthenticationFilter**  
&nbsp;- devLog added; log.debug→devLog.debug; unused imports removed  
**storage.EsqEntityDictionaryStorage**  
&nbsp;- devLog added; dual error pattern; unused imports removed  
**storage.EsqObjectKindStorage**  
&nbsp;- devLog added; dual error pattern; unused imports removed  
**storage.EsqRolesStorage**  
&nbsp;- devLog added; log.debug→devLog.debug; dual error pattern; unused imports removed  
**dto.EsqEntityDictionaryMapper**  
&nbsp;- devLog added; log.debug→devLog.debug; unused imports removed  
dto: EsqColumnHeaderDef, EsqEntity, EsqEntityDictionary, EsqEntityFactory, EsqEntityField,  
&nbsp;    EsqEntityLayer, EsqObjectKind, EsqThing, EsqAccessProfile, EsqAcct, EsqAddress,  
&nbsp;    EsqOrg, EsqPerson, EsqUsr  
&nbsp;- unused imports removed  
validator: IValidator, ValidatorFactory  
&nbsp;- unused imports removed  

**03/20/2026** mir0n  DTO updates  
**dto.EsqEntityField**  
&nbsp;- affects3 field added  

**03/20/2026** mir0n  entity JPA, PerformanceAspect updates  
**jpa.entity.EsqAcctJpa**  
&nbsp;- parentId, path fields added  
**jpa.entity.EsqOrgJpa**  
&nbsp;- parentId, path fields added  
**jpa.entity.EsqUsrJpa**  
&nbsp;- parentId, path fields added  
**service.PerformanceAspect**  
&nbsp;- ScopeNotActiveException guard: skip metrics when no active request scope (startup loaders)  

**03/16/2026** mir0n  connectFlg and path fields added to access profile JPA/DTO  
**jpa.access.EsqAccessProfileJpa**  
&nbsp;- path (usr_path) field added  
&nbsp;- connectFlg (au_connect_flg) field added  
**dto.access.EsqAccessProfile**  
&nbsp;- connectFlg field added with @Schema  
&nbsp;- fill() and fillJpa() updated with setConnectFlg()  
**resources/esq-entity-dictionaries.xml**  
&nbsp;- connectFlg field added to access profile (readwrite=3, type=flag, nullable=N, personal=N)  
&nbsp;- tfaMethod validation updated: g~Enabling..., n~Disabling... added; pattern ^(N|G|g|n)$  

**03/10/2026** mir0n  support keySmith DTO approach: rolesAll and permissions loaded from EsqRolesStorage  
**storage.EsqRolesStorage**  
&nbsp;- roles() added: all roles as List from in-memory map  
&nbsp;- fillPermissionsForRole() added: accumulates permissions for one role into caller-supplied list  
&nbsp;- import java.util.ArrayList added  
**dto.access.EsqAccessProfile**  
&nbsp;- fill() DTO overload added: rolesAll as List, permissions as List  
&nbsp;- original fill() renamed fillJpa() — accepts List rolesAll, List  
**dto.EsqEntityDictionary**  
&nbsp;- fillКindFieldLayer() renamed to fillKindFieldLayer() (Cyrillic К → ASCII K)  

**03/10/2026** mir0n  observability and security generalized; one canonical GlobalExceptionHandler  
&nbsp;+ added backend.service package: MdcFilter, RequestContextUtils, RequestPerformance, PerformanceAspect  
&nbsp;- generalized from per-service implementations; shared by all services via scanBasePackages  
&nbsp;- PerformanceAspect: pointcut pro.mir0n.esquire..jpa.*.* covers all service JPA packages  
&nbsp;+ added backend.security package: JwtAuthenticationFilter, JwtService, SecurityConfiguration  
&nbsp;- generalized from per-service implementations; stateless JWT filter chain  
&nbsp;- JwtAuthenticationFilter: realm_access.roles validated; 401 if missing/empty  
&nbsp;+ added backend.exception package: GlobalExceptionHandler (canonical; one for all services)  
&nbsp;- handleMethodArgumentNotValid inline; delegates handleGenericRuntimeException to GenericExceptionHandler  
**error.GenericExceptionHandler**  
&nbsp;- handleMethodArgumentNotValid, handleException moved to GlobalExceptionHandler (common)  
**error.GenericRuntimeException**  
&nbsp;- unused imports removed (HttpStatus, ResponseStatus, ArrayList, List, Map)  
**pom.xml**  
&nbsp;- spring-webmvc, spring-aspects, spring-security-web/config, jjwt-api added (provided scope)  

**03/09/2026** mir0n  permission validation framework added; exception handling centralized  
&nbsp;+ added error.GenericRuntimeException (base RuntimeException for all Esquire exceptions)  
&nbsp;+ added error.GenericExceptionHandler (static utility; centralizes all exception handlers;  
&nbsp;   handleMethodArgumentNotValid, handleGenericRuntimeException, handleException)  
error.InvalidValueException, ResourceNotFoundException, PermissionDeniedException  
&nbsp;- extends GenericRuntimeException (was RuntimeException)  

**03/09/2026** mir0n  permission validation framework added  
&nbsp;+ added error.PermissionDeniedException (HTTP 403 FORBIDDEN; message format fixed)  
&nbsp;+ added storage.EsqRolesStorage (in-memory roles/permissions; findAdminPermissions(); isAdminCmdPermitted())  
&nbsp;+ added storage.roles package:  
&nbsp;+ IRolesService — roles/permissions service interface (roles(); permissions(id))  
&nbsp;+ JpaRolesRepository — JPA repository with native queries: roles(), permissions(id)  
&nbsp;+ JpaRolesService — JPA-backed impl; maps EsqRoleJpa→EsqRole, EsqPermissionJpa→EsqPermission  

**03/08/2026** mir0n  personal-flag enforcement: users can only update fields marked personal=Y on own records  
**dto.EsqEntityField**  
&nbsp;- isSubentity() method added  
&nbsp;- isTabField() method added (tabstring, tab-ikn-list, tab-iknf-table types)  
**dto.EsqEntityKindFieldLayer**  
&nbsp;- layerTitle field added  
&nbsp;- getLabel() method added: returns layerTitle for tab fields, field.getLabel() otherwise  
**dto.EsqEntityDictionary**  
&nbsp;- fillКindFieldLayer(): setLayerTitle() called to populate layer title context  
**validator.IValidator**  
&nbsp;- validate() signature: boolean personal param added  
**validator.GenericValidator**  
&nbsp;- validate(): personal param added; throws InvalidValueException if personal=true and field.personal != "Y"  
**validator.ValidatorFactory**  
&nbsp;- validate(): personal param forwarded through generic + biz chain  
**resources/esq-entity-dictionaries.xml**  
&nbsp;- personal=Y corrected to personal=N on 3 fields; personal=N added to 1 field  

**03/06/2026** mir0n  field validation framework added; null-safe serialization fixes  
&nbsp;+ added dto.EsqEntityKindFieldLayer (entity kind + layer + field context DTO)  
&nbsp;+ added validator package: IValidator, GenericValidator, ValidatorFactory  
&nbsp;- IValidator: validate(origin, kfl, value) interface  
&nbsp;- GenericValidator: null/blank, nullable, pattern, minmax, type validation  
&nbsp;- ValidatorFactory: singleton; init() registers generic + biz validators  
**dto.EsqEntityDictionary**  
&nbsp;- findField() method added  
&nbsp;- fillКindFieldLayer() method added; reuses EsqEntityKindFieldLayer for allocation reduction  
**dto.EsqNameValueSerializer**  
&nbsp;- null-safe: writeNullField() when custom field value is null  
**dto.entity.EsqOrg**  
&nbsp;- customFields: null-safe LinkedHashMap loop replaces Collectors.toMap()  
**error.InvalidValueException**  
&nbsp;- new class: validation error with field-level error list (fieldName, message, tabIndex)  
**error.ProblemDetailMill**  
&nbsp;- InvalidValueException.errors included in problem detail response body  
**jpa.access.EsqAccessProfileJpa**  
&nbsp;- extends EsqEntityJpa (id/kind/name inherited; individual fields removed)  
**resources/esq-entity-dictionaries.xml**  
&nbsp;- nullable constraints added/updated for access profile and entity fields  

**03/03/2026** mir0n  role/permission JPA and DTO generalized; access profile extended  
**jpa.access.EsqRoleJpa**  
&nbsp;- extends EsqEntityJpa; individual fields (id, name, adminFlg) removed — inherited from base  
**jpa.access.EsqPermissionJpa**  
&nbsp;- extends EsqEntityJpa; individual id field removed — inherited from base  
**dto.access.EsqRole**  
&nbsp;- extends EsqThing; adminFlg removed; id/name/kind inherited  
**dto.access.EsqAccessProfile**  
&nbsp;- rolesAll field added  
&nbsp;- fill() extended with rolesAll param  
**dto.entity.EsqAddress**  
&nbsp;- url field added to fill()  
**resources/esq-entity-dictionaries.xml**  
&nbsp;- field type: integer -> number, string -> text  
&nbsp;- minmax:200 added to name/desc fields (org, usr, acct, access profile)  
&nbsp;- roles-list field renamed to rolesAll  
**resources/esq-object-kinds.xml**  
&nbsp;- kind 980 "admin" (Admin permissions) added  
&nbsp;- kind 982 "tools" (Tool permissions) added  

**03/01/2026** mir0n  DOB field format aligned with ISO-8601  
**dto.entity.EsqPerson**  
&nbsp;- dob @Schema updated: "ISO-8601: YYYY-MM-DD", example "2001/12/31"  

**02/28/2026** mir0n  person/address subentity support added  
&nbsp;+ added jpa.entity.EsqPersonJpa (all person fields; getName() null-safe middleName)  
&nbsp;+ added jpa.entity.EsqAddressJpa (address/biz-address fields)  
&nbsp;+ added dto.entity.EsqPerson (person DTO subentity; fill() from EsqPersonJpa)  
&nbsp;+ added dto.entity.EsqAddress (address DTO subentity; fill() from EsqAddressJpa)  
**dto.entity.EsqUsr**  
&nbsp;- person, addr, bizaddr subentity fields added  
&nbsp;- fillPerson/fillAddress/fillBizAddress implemented  
dto.entity.EsqAcct, EsqOrg  
&nbsp;- empty fillPerson/fillAddress/fillBizAddress stubs added  
**dto.EsqEntity**  
&nbsp;- fillPerson/fillAddress/fillBizAddress abstract methods added  
&nbsp;- fill() extended with person, address, address2 subentity params  
**dto.EsqEntityFactory**  
&nbsp;- createUser() added with person/address/address2 subentity params  
&nbsp;- createEntity() passes null for subentity params  
**dto.EsqObjectKind**  
&nbsp;- address boolean field added  
**storage.EsqObjectKindStorage**  
&nbsp;- UNKNOWN kind updated with address=false  

**02/19/2026** mir0n  dictionary and object kinds extended  
**dto.EsqEntityField**  
&nbsp;- nullable field type changed from Boolean to String  
&nbsp;- minmax field added  
**dto.EsqEntityDictionaryMapper**  
&nbsp;- nullable mapping updated (Boolean -> String passthrough)  
resources/esq-entity-dictionaries.xml (for inital testing)  
&nbsp;- nullable, readwrite (bumped to 3), minmax, validation, format attributes added  
&nbsp;   to org/usr/acct/access profile fields  
&nbsp;- deleted field type changed from string to flag  
&nbsp;- roles-list field added  
**resources/esq-object-kinds.xml**  
&nbsp;- usr=true for sysadminlnks, adminlnks, clientlnks, merchantlnks  
&nbsp;- acct=true for macctlnks, pacctlnks  

**02/12/2026** mir0n  
**jpa.EsqTreeNodeJpa**  
&nbsp;- removed treeFlags  
**jpa.EsqEntityJpa**  
&nbsp;- @MappedSuperclass  
**dto.EsqTreeNode**  
&nbsp;- removed treeFlags  
**dto.EsqTreeNodeMapper**  
&nbsp;- removed treeFlags  
**dto.EsqEntity**  
&nbsp;- use EsqEntityJpa for children  
**dto.EsqEntityFactory**  
&nbsp;- use EsqEntityJpa for children  
**dto.entity.EsqUsr**  
&nbsp;- use EsqEntityJpa for children  
dto.entity.EsqOrg  
&nbsp;- use EsqEntityJpa for children  
**dto.entity.EsqAcct**  
&nbsp;- use EsqEntityJpa for children  

**02/12/2026** mir0n  
&nbsp;+ added dto.EsqColumnHeaderDef  
&nbsp;+ addded dto.EsqObjectKind, dto.EsqObjectKinds  
**dto.EsqEntityFactory**  
&nbsp;- remove EsqEntityKind, use EsqObjectKind instead  
**dto.entity.EsqUsr**  
&nbsp;- use EsqObjectKind instead of EsqEntityKind  
&nbsp;+ added storage.EsqObjectKindStorage  

**02/03/2026** mir0n  
&nbsp;+ added dto.EsqThing  
**dto.EsqTreeNode**  
&nbsp;- extends EsqThing  
**dto.EsqEntity**  
&nbsp;- extends EsqThing  
&nbsp;- back to abstract  
**dto.entity.EsqUsr**  
&nbsp;- accounts are things  
**dto.access.EsqAccessProfile**  
&nbsp;- extends EsqThing  
**dto.access.EsqPermission**  
&nbsp;- extends EsqThing  
**dto.access.EsqRole**  
&nbsp;- adminFlg added  
**jpa.access.EsqAccessProfileJpa**  
&nbsp;- name added  
**jpa.access.EsqPermissionJpa**  
&nbsp;- entityKind renamed with [just] kind  
**jpa.access.EsqRoleJpa**  
&nbsp;- adminFlg added  

**02/02/2026** mir0n  
**dto.EsqEntityFactory**  
&nbsp;- SYSADMIN added  
&nbsp;- gaps in Entity Kind enumeration: system objects - orgs - users - accounts  

### common/src/main/java/pro/mir0n/esquire/common/changes.txt


**03/26/2026** mir0n  Entity CREATE/DELETE — Phase 3 protocol constants and utilities  
EsqMsgConstants  
&nbsp;- TEXT_* JSON field name constants: TEXT_ID, TEXT_KIND, TEXT_NAME, TEXT_DESC, TEXT_STATUS,  
&nbsp;   TEXT_DELETED, TEXT_PARENT_ID, TEXT_PATH, TEXT_CCY  
&nbsp;- MSG_ENCODING_JSON (renamed from MESSAGE_ENCODING)  
&nbsp;- FLAG_OPEN="O"; CCY_DEFAULT="USD"  
EsqUtils  (new entry)  
&nbsp;- generateEntityId(): epoch-based long id (ms since esquireEpoch * 1000 + random sub-ms offset)  

**03/21/2026** mir0n  Messaging Phase 3 — KC sync constants consolidated from KcMsgConstants  
EsqMsgConstants  
&nbsp;- QUEUE_KC_REQUEST = "esquire.kc.request"; QUEUE_KC_RESPONSE = "esquire.kc.response"  
&nbsp;- MSG_TYPE_REQUEST = "URQ"; MSG_TYPE_RESPONSE = "URS"; MSG_TYPE_REJECT = "URR"  
&nbsp;- FIELD_TEST_REQ_ID = "TestReqID" (FIX 112); FIELD_ERROR = "Error" (FIX 50010)  
&nbsp;- ENTITY_KIND_ACCESS_PROFILE removed — duplicate of EsqConstants.KIND_ACCESS_PROFILE  
&nbsp;+ added messaging.jms.Utils  
&nbsp;- formatProps(Map): formats publisher props map as key=value | key=value  
&nbsp;- formatProps(Message): formats all JMS properties sorted alphabetically as key=value | key=value  
&nbsp;- setProps(Message, Map): sets all props map entries as JMS message properties (int or string)  

**03/20/2026** mir0n  Messaging Phase 1/2 — service id constant  
EsqMsgConstants  
&nbsp;- SERVICE_ID_ENTITY_BROADCAST = "entity-update-broadcast" added  

**03/19/2026** mir0n  Messaging Phase 1 — entity broadcast protocol constants  
EsqMsgConstants  (new)  
&nbsp;- FIX-JSON protocol constants for esquire.entity.broadcast JMS topic  
&nbsp;- TOPIC_ENTITY_BROADCAST = "esquire.entity.broadcast"  
&nbsp;- BUS_ID_ENTITY = "esquire.entity"; MSG_TYPE_ENTITY_BROADCASTS = "UE"  
&nbsp;- 14 canonical envelope fields: MsgType, ApplMsgID, BusID, ServiceID, CtrlID, etc.  

**03/09/2026** mir0n  
EsqConstants  
&nbsp;- JWT_CLAIM_REALM_ACCESS and JWT_CLAIM_REALM_ACCESS_ROLES added  

**03/06/2026** mir0n  
EsqConstants  
&nbsp;- KIND_ADMIN_ROLE = 980 added  

**02/28/2026** mir0n  
EsqConstants  
&nbsp;- KIND_ADDRESS_POSTAL (988), KIND_ADDRESS_BIZ (990) added  
&nbsp;- KIND_PERSON_PRIMARY (992), SECONDARY (994), JOINT (996) added  
&nbsp;- SUBENTITY_PERSON, SUBENTITY_ADDRESS, SUBENTITY_ADDRESS2 string constants added  

**02/12/2026** mir0n  
EsqConstants  
&nbsp;- removed CMD_DETAILS  
&nbsp;- DICT_ACCESS_PROFILE renamed with KIND_ACCESS_PROFILE  

### common/src/main/java/pro/mir0n/esquire/messaging/changes.txt

Esquire messaging helper classes  

**04/06/2026** mir0n  JMS Utils: sorted key output  
**messaging.jms.Utils**  
&nbsp;- formatProps(Map): keys sorted alphabetically for consistent log output  

### enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt


**04/16/2026** mir0n  procedural style sweep: ret pattern, null-guard  
**service.impl.AEnyManService**  
&nbsp;- ret declaration moved to top of method  
**service.impl.EnyManService**  
&nbsp;- ret declarations moved to top in detailEntity/saveEntity/newEntity  
**service.impl.OrgService**  
&nbsp;- ret declarations moved to top; moveOrg(): null-guard replaces early return  
**service.impl.UsrService**  
&nbsp;- ret declarations moved to top; moveUsr(): null-guard replaces early return  

**04/09/2026** mir0n  applyFields/enforceDefaults delegated to EntityFieldUtils; validateDelete on all entities  
**service.impl.AEnyManService**  
&nbsp;- applyFields() removed — moved to EntityFieldUtils; BeanWrapper/PropertyDescriptor/ValidatorFactory imports removed  
**service.impl.OrgService**  
&nbsp;- applyFields() calls delegated to EntityFieldUtils.applyFields()  
&nbsp;- enforceDefaults() call delegated to EntityFieldUtils.enforceDefaults()  
&nbsp;- deleteOrg(): ValidatorFactory.getInstance().validateDelete(org) added before delete  
**service.impl.UsrService**  
&nbsp;- all applyFields() calls delegated to EntityFieldUtils.applyFields()  
&nbsp;- hardcoded deleted="N" default replaced with EntityFieldUtils.enforceDefaults(usrLayer, usr)  
&nbsp;- deleteUsr(): ValidatorFactory.getInstance().validateDelete(usr) added before delete  

**04/07/2026** mir0n  unified facade API: /esq-new→/esq-cmd-new, /esq-del→/esq-cmd-del  
**controller.EnyManController**  
&nbsp;- @PostMapping paths: /esq-new→/esq-cmd-new, /esq-del→/esq-cmd-del  

**04/07/2026** mir0n  kind validation: normalization removed; Integer → int service params; upfront applicability checks  
**service.IEnyManService**  
&nbsp;- all kind params Integer → int (primitive)  
**service.impl.AEnyManService**  
&nbsp;- esquireDictionary(): kind param Integer → int; normalization removed  
**service.impl.EnyManService**  
&nbsp;- all kind params Integer → int; normalization removed  
&nbsp;- upfront applicability check (!isOrg && !isUsr → ResourceNotFoundException) at all entry points before permission gate  
**service.impl.OrgService**  
&nbsp;- all kind params Integer → int (including private createOrg)  
**service.impl.UsrService**  
&nbsp;- all kind params Integer → int; get kind directly without normalization in moveUsr/createUsr  

**04/06/2026** mir0n  esq-move Phase III: KC path sync; entity path semantics fix  
&nbsp;+ added messaging.KcRequestPublisher  
&nbsp;- publishes EVENT_UPDATE_PATH URQ to kcMaster KC sync queue on USR entity move  
&nbsp;+ added messaging.KcResponseListener  
&nbsp;- KC response listener — logs URS/URR outcomes for move reconciliation  
**messaging.EnyManJmsConfig**  
&nbsp;- jmsQueueTemplate and jmsQueueListenerFactory added for KC request/response queue  
**messaging.EsqEntityBroadcastPublisher**  
&nbsp;- @Qualifier("jmsTopicTemplate") added to constructor injection  
**service.impl.EnyManService**  
&nbsp;- KcRequestPublisher injected; publishKcMoveRequest() sends EVENT_UPDATE_PATH URQ per USR move record  
**service.impl.UsrService**  
&nbsp;- moveUsr(): admin/regular branch split — admin uses pk-based moveAdminPath (no ACCT cascade);  
&nbsp;   regular uses equality moveUsrPaths (covers user row + all ACCT rows)  
&nbsp;- createUsr(): admin ep_path = parent org path only (no own PK appended)  
**jpa.EsqUsrRepository**  
&nbsp;- moveAdminPath, listAdminMovedPath: pk-based path update/query for admin users  
META-INF/postgres-entity.xml, oracle-entity.xml  
&nbsp;- EsqUsrJpa.moveAdminPath, EsqUsrJpa.listAdminMovedPath queries added  

**04/02/2026** mir0n  
&nbsp;+ added jpa.EsqMoveRecord  
**jpa.EsqOrgRepository**  
&nbsp;- lockEntityPathRoot, listMovedPaths added for move broadcast  
**jpa.EsqUsrRepository**  
&nbsp;- lockEntityPathRoot, listMovedPaths added for move broadcast  
**service.impl.EnyManService**  
&nbsp;- esquireCommandMove(): collects List, publishMoveEvent()  
**service.impl.EsqOgRepository**  
&nbsp;- move: collects updated records  
**service.impl.EsqUsrRepository**  
&nbsp;- move: collects updated records  
**service.IEnyManService**  
&nbsp;- added scan for pro.mir0n.esquire.enyMan.jpa.*  

**03/31/2026** mir0n  esq-move command; ep_et_pk propagation  
**controller.EnyManController**  
&nbsp;- POST /esq-move: move ORG or USR to destination ORG  
**service.IEnyManService**  
&nbsp;- esquireCommandMove() added  
**service.impl.EnyManService**  
&nbsp;- esquireCommandMove(): dual UPDATE permission check; self-move guard for USR; dest org validation  
**service.impl.OrgService**  
&nbsp;- esquireCommandMove() + moveOrg(): subtree path update, descendant guard, skip-if-same-parent  
&nbsp;- insertOrgPath: kind param added (ep_et_pk)  
**service.impl.UsrService**  
&nbsp;- esquireCommandMove() + moveUsr(): mass path update for user+accounts, skip-if-same-parent  
&nbsp;- insertUsrPath: kind param added (ep_et_pk)  
**jpa.EsqOrgRepository**  
&nbsp;- insertOrgPath: kind param added; moveOrgPaths, moveOrgParent queries added  
**jpa.EsqUsrRepository**  
&nbsp;- insertUsrPath: kind param added; moveUsrPaths, moveUsrParent queries added  
**META-INF/postgres-entity.xml**  
&nbsp;- insertOrgPath/insertUsrPath: ep_et_pk added; moveOrgPaths, moveOrgParent, moveUsrPaths, moveUsrParent added  
**META-INF/oracle-entity.xml**  
&nbsp;- same as postgres-entity.xml (Oracle syntax)  

**03/28/2026** mir0n  ESQ_ENTITY_PATH — path column extracted to satellite table; JOIN-based path resolution  
**jpa.EsqOrgRepository**  
&nbsp;- insertOrgPath native query added (INSERT INTO esq_entity_path)  
&nbsp;- insertOrg: path param removed (path row inserted separately before entity row)  
&nbsp;- deleteEntityPath native query added (DELETE FROM esq_entity_path)  
**jpa.EsqUsrRepository**  
&nbsp;- insertUsrPath native query added (INSERT INTO esq_entity_path)  
&nbsp;- insertUsr: path param removed (path row inserted separately before entity row)  
&nbsp;- deleteEntityPath native query added (DELETE FROM esq_entity_path)  
**service.impl.OrgService**  
&nbsp;- createOrg(): insertOrgPath called before insertOrg (trigger atomicity)  
&nbsp;- deleteOrg(): deleteEntityPath called after deleteOrg (FK satisfied)  
**service.impl.UsrService**  
&nbsp;- createUsr(): insertUsrPath called before insertUsr (trigger atomicity)  
&nbsp;- deleteUsr(): deleteEntityPath called after deleteUsr (FK satisfied)  
META-INF/oracle-entity.xml, postgres-entity.xml  
&nbsp;- detailOrg, detailOrgForUpdate, detailUsr, detailUsrForUpdate, userAccts: JOIN esq_entity_path; filter on ep_path  
&nbsp;- orgPath, usrPath: query esq_entity_path directly (was esq_org/esq_user)  
&nbsp;- insertOrgPath, insertUsrPath named queries added  
&nbsp;- insertOrg, insertUsr: path column/value removed  
&nbsp;- deleteEntityPath named queries added (EsqOrgJpa, EsqUsrJpa)  

**03/28/2026** mir0n  default field — inject defaults at entity creation  
**service.impl.OrgService**  
&nbsp;- createOrg(): injectDefaults(fields) before applyFields; custom field loop restricted to request fields  
**service.impl.UsrService**  
&nbsp;- createUsr(): injectDefaults before each applyFields (person/usr/address/address2); removed hardcoded deleted="N" default; custom field loop restricted to request fields  
META-INF/oracle-custom-field.xml, postgres-custom-field.xml  
&nbsp;- insertCustomOrg/insertCustomUsr: NULL replaced with par_default as initial *_PAR value  
META-INF/oracle-dictionary.xml, postgres-dictionary.xml  
&nbsp;- EsqCustomEntityFieldJpa.findCustom: par_default added to SELECT and result mapping  

**03/28/2026** mir0n  USR delete pre-check (connectFlg); cascade-safe delete sequence  
**jpa.EsqUsrRepository**  
&nbsp;- deletePersonAddresses, deletePersonBankInfo replace deleteAuth; explicit pre-delete cleanup  
**service.impl.UsrService**  
&nbsp;- deleteUsr(): connectFlg="Y" → DeleteRestrictedException before delete  
&nbsp;- delete sequence: deletePersonAddresses → deletePersonBankInfo → deleteUsr (CASCADE)  

**03/26/2026** mir0n  Entity CREATE/DELETE — esq-new/esq-del endpoints; ORG and USR create/delete  
**controller.EnyManController**  
&nbsp;- POST /esq-new → esquireCommandNew(); POST /esq-del → esquireCommandDelete()  
**service.IEnyManService**  
&nbsp;- esquireCommandNew(), esquireCommandDelete() added  
**service.impl.EnyManService**  
&nbsp;- esquireCommandNew/Delete(): delegates to OrgService/UsrService  
&nbsp;- TEXT_* constants replace raw JSON string literals; parentId added to broadcast payload  
**service.impl.OrgService**  
&nbsp;- createOrg(), deleteOrg(), esquireCommandNew(), esquireCommandDelete() added  
**service.impl.UsrService**  
&nbsp;- createUsr() with EmailExistsException on duplicate email  
&nbsp;- deleteUsr() + deleteAuth() (auth deleted before usr, FK constraint)  
&nbsp;- esquireCommandNew(), esquireCommandDelete() added  
**jpa.EsqOrgRepository**  
&nbsp;- insertCustomOrg, orgPath, insertOrg, deleteOrg native queries added  
**jpa.EsqUsrRepository**  
&nbsp;- insertUsr, usrPath, deleteUsr native queries added  

**03/21/2026** mir0n  three-tier logging normalization  
**messaging.EsqEntityBroadcastPublisher**  
&nbsp;- msgLog/devLog added; props map migrated to LinkedHashMap+Utils.setProps/formatProps  
&nbsp;- dual-mode ENTITY msg audit (isDebugEnabled: full props / compact fields); console echo log.info  
&nbsp;- final variable copies (finalRid, finalCid, finalText) removed  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- devLog added; log.debug→devLog.debug; log.warn→devLog.debug  
&nbsp;- MDC set/clear; requestId/correlationId reads; dual error pattern; unused imports removed  
EnyManApplication, controller.EnyManController  
&nbsp;- devLog added; log.debug→devLog.debug  
**service.IEnyManService**  
&nbsp;- unused imports removed  
service.impl.AEnyManService, OrgService, UsrService  
&nbsp;- devLog added; log.debug→devLog.debug  
**service.impl.EnyManService**  
&nbsp;- devLog added; dual error pattern (publishEntityEvent catch: log.warn→log.error+devLog.error)  

**03/20/2026** mir0n  Messaging Phase 2 — status broadcast; publisher decoupling; service-id fix; consumer transport  
**messaging.EsqEntityBroadcastPublisher**  
&nbsp;- switched to properties-only transport: Message (no body) replaces TextMessage; FIX-JSON body removed  
&nbsp;- Text serialized as JSON string JMS property (entity state snapshot only, no envelope)  
&nbsp;- @Value: removed inline fallback; service-id is config-only (no default in code)  
**service.impl.EnyManService**  
&nbsp;- isBroadcastableUpdate(): "deleted" (usr_deleted_flg) added as broadcast trigger  
&nbsp;- publishEntityEvent(): emits "deleted" raw field value in Text JSON  
**resources/application.yml**  
&nbsp;- enyman.messaging.service-id default corrected: enyMan → entity-update-broadcast  
**messaging.EsqEntityBroadcastConsumer**  
&nbsp;- switched to properties-only transport: Message replaces TextMessage; Text via getStringProperty()  
&nbsp;- Text added to required properties validation  

**03/19/2026** mir0n  Messaging Phase 1 — entity broadcast producer and consumer template  
messaging.EnyManJmsConfig  (new)  
&nbsp;- JMS/ActiveMQ configuration for entity broadcast producer (JmsTemplate)  
messaging.EsqEntityBroadcastPublisher  (new)  
&nbsp;- publishes FIX-JSON envelope to esquire.entity.broadcast on ORG/USR update  
&nbsp;- fires when name or desc is in the update fields  
messaging.EsqEntityBroadcastConsumer  (new)  
&nbsp;- consumer template; disabled by default (consumer.enabled=false); reserved for Phase 2  
**service.impl.EnyManService**  
&nbsp;- broadcastPublisher injected; publishEntityEvent() added  
&nbsp;- isBroadcastableUpdate(): fires when fields contains "name" or "desc"  
&nbsp;- requestId/correlationId captured before delegate call; text body: id + kind + name/desc  
**service.impl.AEnyManService**  
&nbsp;- esquireDictionary(): kind normalized to even number before dictionary lookup  
&nbsp;- fixes "kind '51' not found" for odd sub-variant kind values  
**service.impl.UsrService**  
&nbsp;- name injection into fields: comment updated to document broadcastableUpdate context  
**resources/application.yml**  
&nbsp;- spring.activemq: broker-url, user, password; spring.jms.client-id  
&nbsp;- enyman.messaging: service-id, ctrl-id, client-id, consumer.enabled  
&nbsp;- LOG_LEVEL_JMS, LOG_LEVEL_AMQ log levels added  

**03/10/2026** mir0n  fillKindFieldLayer() calls updated — Cyrillic К → ASCII K  
**service.impl.AEnyManService**  
&nbsp;- fillКindFieldLayer() call updated to fillKindFieldLayer()  
**service.impl.OrgService**  
&nbsp;- fillКindFieldLayer() call updated to fillKindFieldLayer()  
**service.impl.UsrService**  
&nbsp;- fillКindFieldLayer() call updated to fillKindFieldLayer()  

**03/10/2026** mir0n  observability, security, exception handling generalized to common  
x removed exception.GlobalExceptionHandler (moved to common backend.exception)  
x removed security.JwtAuthenticationFilter, JwtService, SecurityConfiguration (moved to common)  
x removed service.MdcFilter, RequestContextUtils, RequestPerformance, PerformanceAspect (moved to common)  
EnyManApplication  
&nbsp;- scanBasePackages: backend.service, backend.security, backend.exception added  
service.impl.EnyManService, OrgService, UsrService  
&nbsp;- import: RequestContextUtils updated to backend.service package  

**03/09/2026** mir0n  
&nbsp;- refactored: thin delegate; all handlers forward to GenericExceptionHandler (common)  
&nbsp;- GenericRuntimeException handler dispatches InvalidValueException, ResourceNotFoundException,  
&nbsp;   PermissionDeniedException; handleMethodArgumentNotValid and handleException also delegated  

**03/09/2026** mir0n  service-side permission validation added; JWT roles claim validated  
**security.JwtAuthenticationFilter**  
&nbsp;- realm_access.roles extracted and validated; request rejected (401) if missing/empty  
EnyManApplication  
&nbsp;- EsqRolesStorage.init() via ApplicationReadyEvent listener  
&nbsp;- @EnableJpaRepositories extended with backend.storage.roles  
**service.IEnyManService**  
&nbsp;- esquireCommandSave(): roles param added  
**service.impl.EnyManService**  
&nbsp;- roles param added; isAdminCmdPermitted(UPDATE) permission check  
&nbsp;- self-update bypass for USR (id.equals(uid)); PermissionDeniedException thrown  
**service.impl.OrgService**  
&nbsp;- esquireCommandSave(): roles param added  
**service.impl.UsrService**  
&nbsp;- esquireCommandSave(): roles param added  
**controller.EnyManController**  
&nbsp;- realm_access.roles extracted from JWT claims; roles passed to esquireCommandSave()  
**exception.GlobalExceptionHandler**  
&nbsp;- PermissionDeniedException handler added (HTTP 403 FORBIDDEN)  
&nbsp;- ResourceNotFoundException: NOT_FOUND → BAD_REQUEST  
**resources/application.yml**  
&nbsp;- META-INF/oracle-roles.xml and META-INF/postgres-roles.xml added to JPA named queries  

**03/08/2026** mir0n  personal-flag: self-update context propagated through validation chain  
**service.impl.AEnyManService**  
&nbsp;- applyFields(): boolean personal param added; forwarded to ValidatorFactory.validate()  
**service.impl.OrgService**  
&nbsp;- unused imports removed  
&nbsp;- applyFields() and validate() calls pass personal=false  
**service.impl.UsrService**  
&nbsp;- personal = id.equals(uid) computed at updateUsr() entry  
&nbsp;- personal passed to all applyFields() and validate() calls  

**03/06/2026** mir0n  field validation via ValidatorFactory; BizValidatorFactory pattern  
EnyManApplication  
&nbsp;- ValidatorFactory.getInstance().init() called on startup  
**exception.GlobalExceptionHandler**  
&nbsp;- InvalidValueException handler added (HTTP 400)  
&nbsp;- logging added to all exception handlers  
**service.impl.AEnyManService**  
&nbsp;- applyFields() refactored: dict-driven; writable/read-only via field.readwrite  
&nbsp;- ValidatorFactory.validate() called for each writable field  
&nbsp;- subLayer param added (0 = root entity, >0 = sub-entity layer)  
&nbsp;- writables set is now override-only for read-only exceptions  
**service.impl.OrgService**  
&nbsp;- ORG_WRITABLE set removed (dict-driven)  
&nbsp;- applyFields(org, fields, 0, null) uses ValidatorFactory  
&nbsp;- saveOrg(): custom field validation via dictionary readwrite flag  
**service.impl.UsrService**  
&nbsp;- USR_WRITABLE reduced to {"name"} (override for generated value only)  
&nbsp;- dict-driven subLayer lookup for person and address sub-entities  
&nbsp;- saveUsr(): custom field validation via ValidatorFactory  

**03/03/2026** mir0n  sub-entity update id fix  
**service.impl.UsrService**  
&nbsp;- updatePerson/updateAddress/updateAddress2: use user id instead of sub-entity id  

**03/01/2026** mir0n  DOB (date of birth) field activated in person entity  
**service.impl.UsrService**  
&nbsp;- prsn.getDob() uncommented — DOB now passed to updatePerson()  
**META-INF/oracle-entity.xml**  
&nbsp;- pe_dob added to person SELECT (TO_CHAR(TRUNC(pe_dob),'YYYY-MM-DD'))  
&nbsp;- pe_dob added to person UPDATE (TRUNC(TO_DATE(:dob,'YYYY-MM-DD')))  
&nbsp;- field-result "dob" -> "pe_dob" added to EsqPersonJpaMapping  
**META-INF/postgres-entity.xml**  
&nbsp;- pe_dob activated in person SELECT (TO_CHAR(pe_dob,'YYYY-MM-DD'))  
&nbsp;- pe_dob activated in person UPDATE (CAST(:dob AS DATE))  
&nbsp;- field-result "dob" -> "pe_dob" activated in EsqPersonJpaMapping  

**02/28/2026** mir0n  service layer refactored: OrgService/UsrService split from EnyManService  
&nbsp;+ added service.impl.AEnyManService (abstract base: esquireDictionary(), applyFields())  
&nbsp;+ added service.impl.OrgService (org command/save; saveOrg() moved from EnyManService)  
&nbsp;+ added service.impl.UsrService (usr command/save; saveUsr() with person/address support)  
**service.impl.EnyManService**  
&nbsp;- extends AEnyManService; delegates to OrgService/UsrService  
&nbsp;- saveOrg/saveUsr/applyFields moved to OrgService/UsrService  
META-INF/oracle-entity.xml, postgres-entity.xml  
&nbsp;- added EsqUsrJpa.person, address, address2 read queries (EsqPersonJpaMapping/EsqAddressJpaMapping)  
&nbsp;- added updatePerson, updateAddress, updateAddress2 (UPDATE with audit columns)  

**02/19/2026** mir0n  save (update) functionality added  
x jpa.EsqEntityRepository replaced by EsqOrgRepository and EsqUsrRepository  
x jpa.EsqCustomFieldRepository replaced by queries in EsqOrgRepository / EsqUsrRepository  
&nbsp;+ added jpa.EsqOrgRepository  
&nbsp;+ added jpa.EsqUsrRepository  
**jpa.EsqUsrRepository**  
&nbsp;- userAccts() return type corrected from EsqEntityJpa to EsqAcctJpa  
&nbsp;   (was causing IllegalArgumentException: TypedQuery incompatible with EsqAcctJpa)  
**controller.EnyManController**  
&nbsp;- added esquireCommandSave() POST /esq-cmd-save  
**service.IEnyManService**  
&nbsp;- added esquireCommandSave()  
**service.impl.EnyManService**  
&nbsp;- EntityManager and TransactionTemplate injected  
&nbsp;- FlushModeType.COMMIT prevents Hibernate auto-flush before native queries  
&nbsp;- esquireCommandSave() with saveOrg() / saveUsr() helpers  
&nbsp;- applyFields() via BeanWrapper; ORG_WRITABLE / USR_WRITABLE field sets  
META-INF/oracle-entity.xml, postgres-entity.xml  
&nbsp;- query names renamed EsqEntityJpa.* -> EsqOrgJpa.* / EsqUsrJpa.*  
&nbsp;- added detailOrgForUpdate, detailUsrForUpdate (SELECT FOR UPDATE OF primary table)  
&nbsp;- added updateOrg, updateUsr (UPDATE with audit columns)  
META-INF/oracle-custom-field.xml, postgres-custom-field.xml  
&nbsp;- query names renamed EsqNameValueJpa.* -> EsqOrgJpa.* / EsqUsrJpa.*  
&nbsp;- added updateCustomOrg, updateCustomUsr (UPDATE with audit columns)  

**02/13/2026** mir0n  
**jpa.EsqEntityRepository**  
&nbsp;- userAccts() instead of acctsAsNodes  
**service.impl.EnyManService**  
&nbsp;- userAccts() instead of acctsAsNodes  
**service.impl.PacManService**  
&nbsp;- removed unused variables  

**02/12/2026** mir0n  
**controller.EnyManController**  
&nbsp;- added "/esq-kinds" access point  
**service.impl.EnyManService.java**  
&nbsp;- EsqObjectKind instead if EsqEntityKind  
&nbsp;- removed "profile" command  
EnyManApplication  
&nbsp;- initiate EsqObjectKindStorage  

### gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt


**04/07/2026** mir0n  EntityKindRoutePredicateFactory; esq-cmd-new/del routes; EsqObjectKindStorage init  
&nbsp;+ config.EntityKindRoutePredicateFactory  (new)  
&nbsp;- custom RoutePredicateFactory: tests kind query param via EsqObjectKindStorage (isAcct/isOrg/isUsr)  
&nbsp;- YAML usage: - EntityKind=isAcct; add entries to CHECKS map to extend  
GatewayApplication  
&nbsp;- SpringApplication builder; GatewayApplicationStartingListener loads EsqObjectKindStorage on startup  
**application.yml**  
&nbsp;- Query=kind predicate replaced with EntityKind=isAcct on pacman-route/save/new/del routes  
&nbsp;- pacman-new/del-route, enyman-new/del-route: Path /esq-new→/esq-cmd-new, /esq-del→/esq-cmd-del  

**04/07/2026** mir0n  unified facade API routes: kind-discriminated routing for save/new/del  
**application.yml**  
&nbsp;- pacman-save/new/del-route: kind predicate ^(50|51|52|53|54|55)$ added; paths unified (/esq-cmd-save, /esq-new, /esq-del)  
&nbsp;- enyman-save/new/del-route: paths unified (a-prefix variants /esq-cmd-asave, /esq-anew, /esq-adel removed)  

**03/31/2026** mir0n  esq-move route  
**application.yml**  
&nbsp;- enyman-move-route added for POST /esq-move  

**03/21/2026** mir0n  three-tier logging normalization  
**filters.ResponseTraceFilter**  
&nbsp;- devLog added; raw response headers dump moved to devLog.debug; unused imports removed  

**03/10/2026** mir0n  JWT claim key constants; cleanup  
**config.KeycloakRoleConverter**  
&nbsp;- EsqConstants.JWT_CLAIM_REALM_ACCESS, JWT_CLAIM_REALM_ACCESS_ROLES used instead of string literals  

**02/19/2026** mir0n  save routes added  
**config/application.yml**  
&nbsp;- added enyman-save-route:   POST /esq-cmd-save  -> enyMan  
&nbsp;- added pacman-save-route:   POST /esq-cmd-asave -> pacMan  
&nbsp;- added keysmith-save-route: POST /esq-key-save  -> keySmith  

**02/12/2026** mir0n  
**config.SecurityConfig**  
&nbsp;- let "/esq-kinds" pass thru without validation  

**02/01/2026** mir0n  

### kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt

Esquire kcMaster Microservice  

**04/16/2026** mir0n  procedural style sweep: ret pattern, null-guard, for-loops  
**service.impl.KcIdentityService**  
&nbsp;- updateEntityPath(), syncRoles(): null-guard replaces early returns  
&nbsp;- for-loops replace stream operations; explicit types replace var  

**04/06/2026** mir0n  esq-move Phase III: KC path sync  
KcMasterApplication  
&nbsp;- EsqObjectKindStorage loaded on ApplicationStartingEvent  
**messaging.KcMasterJmsConfig**  
&nbsp;- clientId set directly on CachingConnectionFactory (kcmaster.messaging.client-id);  
&nbsp;   fixes "setClientID not supported on shared connection proxy" error  
**messaging.KcRequestConsumer**  
&nbsp;- entityKind read from FIELD_ENTITY_KIND; forwarded to publishSuccess/publishFailure  
**messaging.KcRequestHandler**  
&nbsp;- EVENT_UPDATE_PATH dispatched to updateEntityPath()  
x removed messaging.KcEntityBroadcastConsumer — path sync routed through KC request queue  
**messaging.KcResponsePublisher**  
&nbsp;- publishSuccess/publishFailure: entityKind param added — echoes actual entity kind  
**service.IKcIdentityService**  
&nbsp;- updateEntityPath() added  
**service.impl.KcIdentityService**  
&nbsp;- updateEntityPath(): updates esq_rootpath KC attribute on USR move  
&nbsp;- updateUser(): removed changed-flag guard — attributes always merged and applied  

**03/26/2026** mir0n  Unit test coverage added; constant rename  
**messaging.KcResponsePublisher**  
&nbsp;- MSG_ENCODING_JSON (renamed from MESSAGE_ENCODING)  
&nbsp;+ added test: messaging.KcRequestHandlerTest (12 tests)  
&nbsp;- CREATE/UPDATE/DELETE dispatch; tfaMethod→requireTotp/removeTotp; pwdChangeForced;  
&nbsp;   null roles→empty list; unknown command→IllegalArgumentException  
&nbsp;+ added test: messaging.KcResponsePublisherTest (13 tests)  
&nbsp;- URS/URR routing; MsgType values; EntityKind=998; field forwarding;  
&nbsp;   RFC 9457 Error JSON; Text+MessageEncoding absent when requestText=null  

**03/21/2026** mir0n  three-tier logging normalization across all kcMaster messaging and service classes  
**messaging.KcRequestConsumer**  
&nbsp;- kcAudit→msgLog/devLog; applMsgId read; dual-mode URQ audit; MDC set/clear; dual error pattern  
**messaging.KcResponsePublisher**  
&nbsp;- kcAudit→msgLog/devLog; mid extracted before props map; dual-mode URS and URR audit; dual error  
**messaging.KcEntityBroadcastConsumer**  
&nbsp;- raw string literals replaced with EsqMsgConstants constants  
&nbsp;- requestId/correlationId reads; MDC set/clear; devLog; log.debug→devLog.debug; dual error  
**service.impl.KcIdentityService**  
&nbsp;- kcAudit→devLog; KC state events (STARTED/SUCCESS) promoted to log.info (console observability)  
&nbsp;- all log.debug→devLog.debug; unused imports removed  

**03/21/2026** mir0n  KC sync service implementation — URQ consumer, Keycloak operations, URS/URR publisher  
KcMasterApplication  
&nbsp;- initial scaffold; JMS (ActiveMQ) + Keycloak admin client; no DB, no REST endpoints  
**config.KeycloakConfig**  
&nbsp;- ported from keySmith; Keycloak admin client @ConfigurationProperties  
&nbsp;+ added messaging.KcMasterJmsConfig  
&nbsp;- jmsQueueListenerFactory for esquire.kc.request/response; jmsDurableTopicListenerFactory for entity broadcast  
&nbsp;- @Qualifier on ConnectionFactory resolves Spring Boot 3.5 dual-bean ambiguity  
&nbsp;- clientId set explicitly on durable factory (required for durable topic subscriptions)  
&nbsp;+ added messaging.KcRequestConsumer  
&nbsp;- @JmsListener on esquire.kc.request; reads all headers + Text; dispatches to KcRequestHandler  
&nbsp;- whole message logged via common messaging.jms.Utils.formatProps(Message) to kc.audit (kcMaster-audit.log)  
&nbsp;- publishes URS on success; publishes URR (with echoed Text) on failure  
&nbsp;+ added messaging.KcRequestHandler  
&nbsp;- dispatches URQ command C/D/U to IKcIdentityService operations  
&nbsp;+ added messaging.KcResponsePublisher  
&nbsp;- publishSuccess(): URS — silent ACK, no Text body; whole message logged via common messaging.jms.Utils  
&nbsp;- publishFailure(): URR — RFC 9457 Error header; echoes URQ Text when available; same logging pattern  
&nbsp;- EntityKind: EsqConstants.KIND_ACCESS_PROFILE (EsqMsgConstants.ENTITY_KIND_ACCESS_PROFILE removed as dup)  
&nbsp;- setProps/formatProps delegated to common messaging.jms.Utils  
&nbsp;+ added messaging.KcSyncRequest  
&nbsp;- URQ Text payload POJO; JSON-deserialized from JMS Text property  
&nbsp;+ added messaging.KcEntityBroadcastConsumer  
&nbsp;- durable subscriber on esquire.entity.broadcast topic (skeleton; processing logic TBD)  
&nbsp;+ added service.IKcIdentityService  
&nbsp;- ported from keySmith IKeycloakIdentityService  
&nbsp;+ added service.impl.KcIdentityService  
&nbsp;- ported from keySmith KeycloakIdentityService  
&nbsp;- @Async removed — KC calls synchronous so URS/URR published only after KC operation completes  

### keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt


**04/16/2026** mir0n  procedural style sweep: ret pattern  
**service.impl.KeySmithService**  
&nbsp;- ret declarations moved to top in esquireKeyDetail() and esquireKeySave()  

**03/28/2026** mir0n  ESQ_ENTITY_PATH — JOIN esq_entity_path for path resolution in access queries  
META-INF/oracle-access-profile.xml, postgres-access-profile.xml  
&nbsp;- access, accessForUpdate: JOIN esq_entity_path; ep_path AS usr_path alias; filter on ep_path  

**03/26/2026** mir0n  Cleanup — removed dead code; constant rename  
x removed service.impl.KeySmithServiceJpa — alternative JPA implementation, superseded by KeySmithService (@Primary)  
**messaging.KcSyncPublisher**  
&nbsp;- MSG_ENCODING_JSON (renamed from MESSAGE_ENCODING)  

**03/21/2026** mir0n  KC sync CtrlID — stable instance identifier; response listener selector  
**messaging.KcSyncPublisher**  
&nbsp;- ctrlId injected from keysmith.messaging.ctrl-id config (@Value) instead of derived from correlationId  
**messaging.KcSyncResponseListener**  
&nbsp;- JMS selector: CtrlID = '${keysmith.messaging.ctrl-id}' — routes responses to this instance only  
&nbsp;- full field reads (applMsgId, command, entityId, ctrlId, requestId, correlationId, testReqId)  
&nbsp;- MDC set from requestId/correlationId; cleared in finally  
&nbsp;- three-tier logging (msgLog/devLog); dual-mode msg audit; dual error pattern  
KeySmithApplication, controller.KeySmithController  
&nbsp;- devLog added; log.debug→devLog.debug  
service.BizValidatorFactory, service.impl.KeySmithService, service.impl.KeySmithServiceJpa  
&nbsp;- devLog added; log.debug→devLog.debug  

**03/21/2026** mir0n  KC sync decoupled to kcMaster — JMS messaging replaces direct Keycloak calls  
x removed config.KeycloakConfig (moved to kcMaster)  
x removed service.IKeycloakIdentityService (moved to kcMaster)  
x removed service.impl.KeycloakIdentityService (moved to kcMaster)  
KeySmithApplication  
&nbsp;- @EnableAsync removed (KC calls now fire-and-forget via JMS; no async executor needed)  
**service.impl.KeySmithService**  
&nbsp;- IKeycloakIdentityService replaced with KcSyncPublisher  
&nbsp;- syncToKeycloak() removed; kcSyncPublisher.publish() called after transaction commit  
&nbsp;+ added messaging.KeySmithJmsConfig  
&nbsp;- JMS queue listener factory for esquire.kc.request and esquire.kc.response  
&nbsp;+ added messaging.KcSyncPublisher  
&nbsp;- builds and publishes URQ to esquire.kc.request after DB transaction commit  
&nbsp;- command determined by connectFlg transition: C (N→Y) / D (Y→N) / U (else)  
&nbsp;- TestReqID: uses RequestID if present, else generates new UUID; whole message logged via props map  
&nbsp;- all JMS constants from common.EsqMsgConstants (KcMsgConstants removed)  
&nbsp;- setProps/formatProps delegated to common messaging.jms.Utils  
&nbsp;+ added messaging.KcSyncResponseListener  
&nbsp;- consumes URS/URR from esquire.kc.response; logs to kc.sync logger (keySmith-sync.log)  
&nbsp;- whole message logged dynamically via getPropertyNames() sorted  
&nbsp;- formatProps delegated to common messaging.jms.Utils  

**03/16/2026** mir0n  TOTP state machine; reset password handshake; connectFlg lifecycle; KC integration  
**service.impl.KeySmithService**  
&nbsp;- applyFields(): tfaMethod state machine — accepts G/N only; pending stored as lowercase (G→g, N→n)  
&nbsp;- esquireKey(): confirmPendingFlags() on login handshake (id=null)  
&nbsp;   pwdChangeForced Y→N confirmed; tfaMethod g/n→G/N confirmed  
&nbsp;- esquireKeySave(): oldLoginId[], oldConnectFlg[] captured before transaction  
&nbsp;- saveAccess(): connectFlg change captured; TOTP reset to N on N→Y transition  
&nbsp;- syncToKeycloak() added: three-branch — delete(Y→N) / create(N→Y) / update(else)  
&nbsp;   create: KC user with esq_uid + esq_rootpath attributes, temporary password, forcePasswordChange=true  
**service.impl.KeySmithServiceJpa**  
&nbsp;- saveAccess(): connectFlg param added to updateAccess() call  
**service.impl.KeycloakIdentityService**  
&nbsp;- updateUserAuthState(): removeTotp parameter added; OTP credential removal via KC credential API  
&nbsp;- deleteUser() added  
**service.IKeycloakIdentityService**  
&nbsp;- updateUserAuthState(): removeTotp parameter added  
&nbsp;- deleteUser() added  
**jpa.EsqAccessProfileRepository**  
&nbsp;- updateAccess(): connectFlg param added  
&nbsp;- confirmPendingFlags(@Param id, pwdChangeForced, tfaMethod) added  
&nbsp;   replaces clearPwdChangeForced + confirmTfaMethod; COALESCE for atomic partial update  
KeySmithApplication  
&nbsp;- @EnableAsync added (virtual thread async executor for KeycloakIdentityService)  
META-INF/oracle-access-profile.xml, postgres-access-profile.xml  
&nbsp;- usr_path added to access and accessForUpdate SELECT queries; path→usr_path result mapping added  
&nbsp;- au_connect_flg added to access and accessForUpdate SELECT queries; connectFlg result mapping added  
&nbsp;- au_connect_flg = :connectFlg added to updateAccess UPDATE  
&nbsp;- confirmPendingFlags named native query added  

**03/10/2026** mir0n  @Primary added; rolesAll and permissions from EsqRolesStorage; KeySmithServiceJpa added  
&nbsp;+ added service.impl.KeySmithServiceJpa (JPA-based alternative; was KeySmithService before DTO refactoring)  
&nbsp;- no @Primary; superseded by KeySmithService; kept as fallback  
**service.impl.KeySmithService**  
&nbsp;- @Primary added: selected as preferred IKeySmithService implementation  
&nbsp;- rolesAll loaded from EsqRolesStorage.getInstance().roles() (no JPA call)  
&nbsp;- permissions accumulated via fillPermissionsForRole() loop over rolesAssigned  
&nbsp;- esquireKey(): roles/rolesAll/permissions sourced from Storage after JPA access fetch  
&nbsp;- esquireKeySave(): fill() called with Storage-sourced rolesAll + permissions after transaction  
&nbsp;- saveAccess(): rolesAll[] param removed; rolesAll sourced from Storage in caller  
**service.BizValidatorFactory**  
&nbsp;- unused import removed; getBizValidators() final modifier removed; comment corrected  

**03/10/2026** mir0n  fillKindFieldLayer() call updated — Cyrillic К → ASCII K  
**service.impl.KeySmithService**  
&nbsp;- fillКindFieldLayer() call updated to fillKindFieldLayer()  

**03/10/2026** mir0n  observability, security, exception handling generalized to common  
x removed exception.GlobalExceptionHandler (moved to common backend.exception)  
x removed security.JwtAuthenticationFilter, JwtService, SecurityConfiguration (moved to common)  
x removed service.MdcFilter, RequestContextUtils, RequestPerformance, PerformanceAspect (moved to common)  
KeySmithApplication  
&nbsp;- scanBasePackages: backend.service, backend.security, backend.exception added  
**service.impl.KeySmithService**  
&nbsp;- import: RequestContextUtils updated to backend.service package  

**03/09/2026** mir0nexception.GlobalExceptionHandler  
&nbsp;- refactored: thin delegate; all handlers forward to GenericExceptionHandler (common)  

**03/09/2026** mir0n  service-side permission validation added; JWT roles claim validated  
**controller.KeySmithController**  
&nbsp;- realm_access.roles extracted from JWT claims; roles passed to esquireKeySave()  
**security.JwtAuthenticationFilter**  
&nbsp;- realm_access.roles extracted and validated; request rejected (401) if missing/empty  
**resources/application.yml**  
&nbsp;- META-INF/oracle-roles.xml and META-INF/postgres-roles.xml added to JPA named queries  
KeySmithApplication  
&nbsp;- EsqRolesStorage.init() via ApplicationReadyEvent listener  
&nbsp;- @EnableJpaRepositories extended with backend.storage.roles  
**service.IKeySmithService**  
&nbsp;- esquireKeySave(): roles param added  
**service.impl.KeySmithService**  
&nbsp;- roles var renamed to rolesAssigned; roles param added to saveAccess()  
&nbsp;- isAdminCmdPermitted(AUTH) permission check; self-update bypass (upk.equals(uid))  
&nbsp;- PermissionDeniedException thrown on auth failure  
**exception.GlobalExceptionHandler**  
&nbsp;- @Slf4j added; logging added to all handlers  
&nbsp;- PermissionDeniedException (HTTP 403) and InvalidValueException (HTTP 400) handlers added  
&nbsp;- ResourceNotFoundException: NOT_FOUND → BAD_REQUEST  

**03/08/2026** mir0n  personal-flag: users cannot change their own permissions  
**service.BizValidatorFactory**  
&nbsp;- validate(): boolean personal param added  
&nbsp;- personal guard: throws InvalidValueException if personal=true (cannot change own permissions)  
**service.impl.KeySmithService**  
&nbsp;- InvalidValueException import added  
&nbsp;- personal = upk.equals(uid) computed at saveAccess() entry  
&nbsp;- personal passed to saveAccess() and applyFields()  

**03/06/2026** mir0n  field validation via ValidatorFactory; biz validator added  
&nbsp;+ added service.BizValidatorFactory  
&nbsp;- biz validator for KIND_ACCESS_PROFILE: max 1 admin role per user  
**service.IKeySmithService**  
&nbsp;- FIELD_ROLES = "roles" constant added  
**service.impl.KeySmithService**  
&nbsp;- ACCESS_WRITABLE set removed (dict-driven)  
&nbsp;- applyFields() refactored: dict-driven via ValidatorFactory  
&nbsp;- roles field validated via ValidatorFactory (BizValidatorFactory delegate)  

**03/03/2026** mir0n  roles list save added to saveAccess()  
service.impl.KeySmithService  
&nbsp;- saveAccess(): roles list change detection added  
&nbsp;- deleting roles/ adding roles inserted  
**jpa.EsqAccessProfileRepository**  
&nbsp;- added deleteUserRole  
&nbsp;- added insertUserRole  
META-INF/oracle-access-profile.xml, postgres-access-profile.xml  
&nbsp;- added deleteUserRole  
&nbsp;- added insertUserRole  

**02/19/2026** mir0n  save (update) functionality added  
**controller.KeySmithController**  
&nbsp;- added esquireKeySave() POST /esq-key-save  
**service.IKeySmithService**  
&nbsp;- added esquireKeySave()  
**service.impl.KeySmithService**  
&nbsp;- EntityManager and TransactionTemplate injected  
&nbsp;- FlushModeType.COMMIT prevents Hibernate auto-flush before native queries  
&nbsp;- esquireKeySave() with saveAccess() helper  
&nbsp;- ACCESS_WRITABLE = {email, loginId, pwdChangeForced, tfaMethod}  
**jpa.EsqAccessProfileRepository**  
&nbsp;- added accessForUpdate (SELECT FOR UPDATE OF esq_user)  
&nbsp;- added updateAccess @Modifying native query (UPDATE esq_auth with audit columns)  
META-INF/oracle-access-profile.xml, postgres-access-profile.xml  
&nbsp;- added accessForUpdate (SELECT FOR UPDATE OF esq_user)  
&nbsp;- added updateAccess (UPDATE esq_auth with audit columns)  

**02/04/2026** mir0n  
**service.PerformanceAspect**  
&nbsp;* 02/24/2026  mir0n cleanup: @Around("execution(* pro.mir0n.esquire.keySmith.jpa.*.*(..))")  

### pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt


**04/20/2026** mir0n  acct transfer: conversion rate support; transaction PK Long->String  
**acct.IAcctTransactionProcessor**  
&nbsp;- abstract 8-param signature extended: convRate, amtIncoming, ccyIncoming, pkTx, counterpartId  
**acct.dto.AcctTransactionSingle**  
&nbsp;- FIELD_RATE added; response fields: ccy, convRate, amtIncoming, ccyIncoming; id type Long->String  
**acct.jpa.EsqAcctTransactionJpa**  
&nbsp;- fields added: pkTx, amtIncoming, ccyIncoming, convRate; id type Long->String  
**acct.jpa.EsqAcctTransactionRepository**  
&nbsp;- nextId() removed; PK type Long->String; insertAcctTransaction: pkTx, amtIncoming, ccyIncoming, convRate params added  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- generateTransId() replaces nextId(); conversion rate params threaded through  
&nbsp;- ccy populated in result; refCode4 auto-note on both transfer legs  
**acct.service.AcctTransactionProcessorTransfer**  
&nbsp;- FIELD_RATE required and validated (must be > 0)  
&nbsp;- credit amount = abs(debit) * rate; shared pkTx links both legs; sourceCcy forwarded to credit leg  

**04/15/2026** mir0n  acct transaction PK from ESQ_ATR_SEQ sequence  
**acct.jpa.EsqAcctTransactionRepository**  
&nbsp;- nextId(): returns next value from ESQ_ATR_SEQ (vendor-specific SQL in oracle/postgres XML)  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- transaction PK: EsqUtils.generateEntityId() replaced by transactionRepository.nextId()  

**04/14/2026** mir0n  acct transfer fixes — DICT_KIND_TRANSFER corrected; paper account guard; detailAcctForUpdate kind param removed  
**acct.AcctOperation**  
&nbsp;- DICT_KIND_TRANSFER corrected: 1002 → 1004  
&nbsp;- ACCT_KIND_PAPER = 54 added  
**acct.service.AcctTransactionProcessorSingle**  
&nbsp;- detailAcctForUpdate call: kind param dropped  
**acct.service.AcctTransactionProcessorTransfer**  
&nbsp;- instanceof check bug fixed (was testing rawKind2 instead of rawId2)  
&nbsp;- same-account validation added (source == target → InvalidValueException)  
&nbsp;- paper account restriction added (ACCT_KIND_PAPER → InvalidValueException)  
**jpa.EsqAcctRepository**  
&nbsp;- detailAcctForUpdate: kind param removed; query: AND acc_et_pk = :kind dropped  
**service.impl.PacManService**  
&nbsp;- saveAcct(), deleteAcct(): kind param removed (detailAcctForUpdate aligned)  

**04/13/2026** mir0n  account transaction Phase III — single op complete; transfer draft; AcctTransactionService refactored to thin router  
&nbsp;+ added acct.AcctOperation  
&nbsp;- AmountEffect enum (NEGATIVE/ANY/POSITIVE); Code enum (DEPOSIT/WITHDRAWAL/TRANSFER/ADJUSTMENT/COMMISSION/UNKNOWN)  
&nbsp;- each Code carries id, AmountEffect, transfer flag, dict kind  
&nbsp;+ added acct.IAcctTransactionProcessor  
&nbsp;- default 7-param delegates to abstract 8-param with skipValidation (test-only validation bypass)  
&nbsp;+ added acct.dto.AcctTransactionSingle  
&nbsp;- FIELD_* constants for all map keys; fill(Map) sets desc/refCode fields  
&nbsp;- replaces AcctTransactionSimple  
&nbsp;+ added acct.service.AcctTransactionProcessorSingle  
&nbsp;- permission check via EsqRolesStorage.AdminCmd.ACCT  
&nbsp;- amount sign/zero validation per AmountEffect; status and balance checks (skippable)  
&nbsp;- EntityFieldUtils.applyFields(oper.kind, fields) — dictionary-driven field validation  
&nbsp;- inserts transaction row, updates account balance  
&nbsp;+ added acct.service.AcctTransactionProcessorTransfer  
&nbsp;- two-leg transfer: debit source (first leg), credit target with -amount (second leg, skipValidation=true)  
&nbsp;- validates id2/kind2 presence; permissions checked for both legs  
**acct.service.AcctTransactionService**  
&nbsp;- refactored to thin router; processors instantiated explicitly (no Spring management)  
&nbsp;- pre-validates: fields not null, typeId present, operation not UNKNOWN, amount not null  
&nbsp;- routes by AcctOperation.Code.transfer flag  
**controller.PacManController**  
&nbsp;- AcctTransactionSimple → AcctTransactionSingle  

**04/12/2026** mir0n  account transaction Phase II — KIND_ACCTTR=1000; EntityFieldUtils validation; explicit skipValidation param  
**acct.service.AcctTransactionService**  
&nbsp;- KIND_ACCTTR: 980 -> 1000 (aligns with esq-entity-dictionaries.xml kind)  
&nbsp;- skipValidation: explicit boolean parameter (no longer derived from fields map)  
&nbsp;- field validation: EntityFieldUtils.applyFields(KIND_ACCTTR, fields) — dictionary-driven with listvalues check  

**04/09/2026** mir0n  account transaction command Phase I — POST /esq-acct; isolated pacMan.acct package  
&nbsp;+ added acct.dto.AcctTransactionSimple  (new)  
&nbsp;- account transaction result DTO: id, kind, typeId, amount, desc, refCode/2/3/4, memo  
&nbsp;+ added acct.jpa.EsqAcctTransactionJpa  (new)  
&nbsp;- JPA entity for ESQ_ACCT_TRANSACTION; no @Table/@Column — XML field mapping only  
&nbsp;+ added acct.jpa.EsqAcctTransactionRepository  (new)  
&nbsp;- insertAcctTransaction: 14-param native INSERT  
&nbsp;+ added acct.service.AcctTransactionService  (new)  
&nbsp;- esquireCommandAcct(): kind/permission gate (AdminCmd.ACCT); delegates to postAcctTransaction()  
&nbsp;- postAcctTransaction(): null/zero/negative amount guards; open status check (skipValidation bypass); negative balance check; insertAcctTransaction + updateAcctBalance  
&nbsp;- private constants: KIND_ACCTTR=980, FIELD_AMOUNT, FIELD_TYPE_ID, FIELD_SKIP_VALIDATION  
&nbsp;+ added META-INF/postgres-acct-transaction.xml  (new)  
&nbsp;- insertAcctTransaction named query; EsqAcctTransactionJpaMapping sql-result-set-mapping  
&nbsp;+ added META-INF/oracle-acct-transaction.xml  (new)  
&nbsp;- insertAcctTransaction named query (Oracle syntax); EsqAcctTransactionJpaMapping  
PacManApplication  
&nbsp;- @EntityScan + @EnableJpaRepositories: pro.mir0n.esquire.pacMan.acct.jpa added  
**controller.PacManController**  
&nbsp;- POST /esq-acct → esquireCommandAcct(); AcctTransactionService injected directly  
**jpa.EsqAcctRepository**  
&nbsp;- updateAcctBalance native query added  
**META-INF/postgres-acct.xml**  
&nbsp;- EsqAcctJpa.updateAcctBalance named query added  
**META-INF/oracle-acct.xml**  
&nbsp;- EsqAcctJpa.updateAcctBalance named query added (Oracle syntax)  

**04/09/2026** mir0n  funded account fields; biz validation rules; applyFields/enforceDefaults to EntityFieldUtils  
**service.IPacManService**  
&nbsp;- FIELD_CCY constant added  
**service.BizValidatorFactory**  
&nbsp;- StatusBizValidator renamed to AcctBizValidator  
&nbsp;- validate(): ccy rule added — cannot change currency on a funded account (fundedDate != null)  
&nbsp;- validateDelete(): funded account cannot be deleted; account must be closed (status="C") before delete  
**jpa.EsqAcctRepository**  
&nbsp;- insertAcct: negativeAllowed param added  
&nbsp;- updateAcct: ccy + negativeAllowed params added  
**service.impl.PacManService**  
&nbsp;- createAcct(): applyFields + enforceDefaults loop replace direct field extraction; negativeAllowed passed to insertAcct  
&nbsp;- saveAcct(): ccy + negativeAllowed passed to updateAcct; EntityFieldUtils.applyFields replaces private applyFields  
&nbsp;- deleteAcct(): ValidatorFactory.getInstance().validateDelete(acct) replaces explicit status check  
&nbsp;- private applyFields() removed — delegated to EntityFieldUtils  
**META-INF/postgres-acct.xml**  
&nbsp;- SELECT: TO_CHAR(acc_funded_dt, 'YYYY-MM-DD'), acct_neg_allowed_flg added; mapping entries added  
&nbsp;- INSERT: acct_neg_allowed_flg column + :negativeAllowed param added  
&nbsp;- UPDATE: acc_ccy = :ccy, acct_neg_allowed_flg = :negativeAllowed added  
**META-INF/oracle-acct.xml**  
&nbsp;- same changes as postgres-acct.xml (Oracle syntax)  

**04/07/2026** mir0n  unified facade API: /esq-new→/esq-cmd-new, /esq-del→/esq-cmd-del  
**controller.PacManController**  
&nbsp;- @PostMapping paths: /esq-new→/esq-cmd-new, /esq-del→/esq-cmd-del  

**04/07/2026** mir0n  unified facade API: a-prefix POST mappings replaced with unified paths  
**controller.PacManController**  
&nbsp;- @PostMapping paths: /esq-cmd-asave→/esq-cmd-save, /esq-anew→/esq-new, /esq-adel→/esq-del  
&nbsp;- API description updated: "REST API to manage accounts" → "REST API to manage account entities"  

**04/07/2026** mir0n  kind validation: normalization removed; Integer → int service params; upfront applicability checks  
**service.IPacManService**  
&nbsp;- all kind params Integer → int (primitive)  
**service.impl.PacManService**  
&nbsp;- all kind params Integer → int; normalization removed  
&nbsp;- upfront applicability check (!isAcct → ResourceNotFoundException) at all entry points before permission gate  

**04/06/2026** mir0n  log audit line fix  
**messaging.EsqEntityBroadcastPublisher**  
&nbsp;- log.info: requestId and correlationId added to ENTITY | UE audit line  

**03/31/2026** mir0n  insertAcctPath: kind param added (ep_et_pk)  
**jpa.EsqAcctRepository**  
&nbsp;- insertAcctPath: kind param added  
**service.impl.PacManService**  
&nbsp;- insertAcctPath call: kind param added  
**META-INF/postgres-acct.xml**  
&nbsp;- insertAcctPath: ep_et_pk column added  
**META-INF/oracle-acct.xml**  
&nbsp;- insertAcctPath: ep_et_pk column added  

**03/28/2026** mir0n  ESQ_ENTITY_PATH — path column extracted to satellite table; JOIN-based path resolution  
**jpa.EsqAcctRepository**  
&nbsp;- insertAcctPath native query added (INSERT INTO esq_entity_path)  
&nbsp;- insertAcct: path param removed (path row inserted separately before entity row)  
&nbsp;- deleteEntityPath native query added (DELETE FROM esq_entity_path)  
**service.impl.PacManService**  
&nbsp;- createAcct(): insertAcctPath called before insertAcct (trigger atomicity)  
&nbsp;- deleteAcct(): deleteEntityPath called after deleteAcct (FK satisfied)  
META-INF/oracle-acct.xml, postgres-acct.xml  
&nbsp;- detailAcct, detailAcctForUpdate: JOIN esq_entity_path; filter on ep_path  
&nbsp;- acctPath: queries esq_entity_path directly (was esq_user.usr_path)  
&nbsp;- insertAcctPath named query added  
&nbsp;- insertAcct: path column/value removed  
&nbsp;- deleteEntityPath named query added  

**03/28/2026** mir0n  default field — dict-driven defaults at account creation  
**service.impl.PacManService**  
&nbsp;- createAcct(): hardcoded ccy/status ternaries replaced with EsqEntityDictionaryStorage.injectDefaults loop  

**03/28/2026** mir0n  ACCT delete pre-check — status must be "C" (closed) before delete  
**service.impl.PacManService**  
&nbsp;- deleteAcct(): status != "C" → DeleteRestrictedException (HTTP 409)  

**03/26/2026** mir0n  Entity CREATE/DELETE — esq-anew/esq-adel endpoints; ACCT create/delete  
**controller.PacManController**  
&nbsp;- POST /esq-anew → esquireCommandNew(); POST /esq-adel → esquireCommandDelete()  
**service.IPacManService**  
&nbsp;- esquireCommandNew(), esquireCommandDelete() added  
**service.impl.PacManService**  
&nbsp;- createAcct(), deleteAcct(), esquireCommandNew(), esquireCommandDelete() added  
&nbsp;- publishDeleteEvent added; TEXT_* constants replace raw JSON string literals  
**jpa.EsqAcctRepository**  
&nbsp;- acctPath, insertAcct, deleteAcct native queries added  

**03/21/2026** mir0n  three-tier logging normalization  
**messaging.EsqEntityBroadcastPublisher**  
&nbsp;- msgLog/devLog added; props map migrated to LinkedHashMap+Utils.setProps/formatProps  
&nbsp;- dual-mode ENTITY msg audit (isDebugEnabled: full props / compact fields); console echo log.info  
&nbsp;- final variable copies (finalRid, finalCid, finalText) removed  
PacManApplication, controller.PacManController  
&nbsp;- devLog added; log.debug→devLog.debug  
**service.impl.PacManService**  
&nbsp;- devLog added; log.debug→devLog.debug; dual error pattern (publishEntityEvent catch: log.warn→log.error+devLog.error)  

**03/20/2026** mir0n  Messaging Phase 2 — status broadcast; publisher decoupling; service-id fix  
**messaging.EsqEntityBroadcastPublisher**  
&nbsp;- switched to properties-only transport: Message (no body) replaces TextMessage; FIX-JSON body removed  
&nbsp;- Text serialized as JSON string JMS property (entity state snapshot only, no envelope)  
&nbsp;- @Value: removed inline fallback; service-id is config-only (no default in code)  
**service.impl.PacManService**  
&nbsp;- isBroadcastableUpdate(): "status" (acc_status) added as broadcast trigger  
&nbsp;- publishEntityEvent(): emits "status" raw field value in Text JSON  
**resources/application.yml**  
&nbsp;- pacman.messaging.service-id default corrected: pacMan → entity-update-broadcast  

**03/19/2026** mir0n  Messaging Phase 1 — entity broadcast producer  
messaging.PacManJmsConfig  (new)  
&nbsp;- JMS/ActiveMQ configuration for entity broadcast producer (JmsTemplate)  
messaging.EsqEntityBroadcastPublisher  (new)  
&nbsp;- publishes FIX-JSON envelope to esquire.entity.broadcast on ACCT update  
&nbsp;- fires when name or desc is in the update fields  
**service.impl.PacManService**  
&nbsp;- broadcastPublisher injected; publishEntityEvent() added  
&nbsp;- isBroadcastableUpdate(): fires when fields contains "name" or "desc"  
&nbsp;- text body: id + kind + name/desc  
**resources/application.yml**  
&nbsp;- spring.activemq: broker-url, user, password  
&nbsp;- pacman.messaging: service-id, ctrl-id, client-id  
&nbsp;- LOG_LEVEL_JMS, LOG_LEVEL_AMQ log levels added; pro.mir0n default changed ERROR→INFO  

**03/10/2026** mir0n  fillKindFieldLayer() call updated — Cyrillic К → ASCII K  
**service.impl.PacManService**  
&nbsp;- fillКindFieldLayer() call updated to fillKindFieldLayer()  

**03/10/2026** mir0n  observability, security, exception handling generalized to common  
x removed exception.GlobalExceptionHandler (moved to common backend.exception)  
x removed security.JwtAuthenticationFilter, JwtService, SecurityConfiguration (moved to common)  
x removed service.MdcFilter, RequestContextUtils, RequestPerformance, PerformanceAspect (moved to common)  
PacManApplication  
&nbsp;- scanBasePackages: backend.service, backend.security, backend.exception added  
**service.impl.PacManService**  
&nbsp;- import: RequestContextUtils updated to backend.service package  

**03/09/2026** mir0n  
**exception.GlobalExceptionHandler**  
&nbsp;- refactored: thin delegate; all handlers forward to GenericExceptionHandler (common)  

**03/09/2026** mir0n  service-side permission validation added; JWT roles claim validated  
**controller.PacManController**  
&nbsp;- realm_access.roles extracted from JWT claims; roles passed to esquireCommandSave()  
**security.JwtAuthenticationFilter**  
&nbsp;- realm_access.roles extracted and validated; request rejected (401) if missing/empty  
**resources/application.yml**  
&nbsp;- META-INF/oracle-roles.xml and META-INF/postgres-roles.xml added to JPA named queries  
PacManApplication  
&nbsp;- EsqRolesStorage.init() via ApplicationReadyEvent listener  
&nbsp;- @EnableJpaRepositories extended with backend.storage.roles  
**service.IPacManService**  
&nbsp;- esquireCommandSave(): roles param added; unused imports removed (EsqTreeNode, EsqEntityLayer)  
**service.impl.PacManService**  
&nbsp;- roles param added; isAdminCmdPermitted(UPDATE) permission check  
&nbsp;- PermissionDeniedException thrown on auth failure; stray debug comment removed  
**exception.GlobalExceptionHandler**  
&nbsp;- @Slf4j added; logging added to all handlers  
&nbsp;- PermissionDeniedException (HTTP 403) and InvalidValueException (HTTP 400) handlers added  
&nbsp;- ResourceNotFoundException: NOT_FOUND → BAD_REQUEST  

**03/08/2026** mir0n  validate() interface alignment: personal param added (no behavior change)  
**service.BizValidatorFactory**  
&nbsp;- validate(): boolean personal param added (interface alignment, no behavior change)  
**service.impl.PacManService**  
&nbsp;- validate() calls pass personal=false  

**03/06/2026** mir0n  field validation via ValidatorFactory; biz validator added  
&nbsp;+ added service.BizValidatorFactory  
&nbsp;- biz validator for acct kinds (50/52/54): cannot close account with positive balance  
**service.IPacManService**  
&nbsp;- KIND_CL_ACCT (50), KIND_MR_ACCT (52), KIND_P_ACCT (54) constants added  
&nbsp;- FIELD_STATUS constant added  
**service.impl.PacManService**  
&nbsp;- ACCT_WRITABLE set removed (dict-driven)  
&nbsp;- applyFields() refactored: dict-driven via ValidatorFactory  

**02/19/2026** mir0n  save (update) functionality added  
**controller.PacManController**  
&nbsp;- added esquireCommandSave() POST /esq-cmd-asave  
**service.IPacManService**  
&nbsp;- added esquireCommandSave()  
**service.impl.PacManService**  
&nbsp;- EntityManager and TransactionTemplate injected  
&nbsp;- FlushModeType.COMMIT prevents Hibernate auto-flush before native queries  
&nbsp;- esquireCommandSave() with saveAcct() helper  
&nbsp;- ACCT_WRITABLE = {desc, status}  
**jpa.EsqAcctRepository**  
&nbsp;- base type changed from EsqEntityJpa to EsqAcctJpa  
&nbsp;- added detailAcctForUpdate (SELECT FOR UPDATE)  
&nbsp;- added updateAcct @Modifying native query (UPDATE esq_account with audit columns)  
META-INF/oracle-acct.xml, postgres-acct.xml  
&nbsp;- query renamed EsqEntityJpa.detailAcct -> EsqAcctJpa.detailAcct  
&nbsp;- added detailAcctForUpdate (SELECT FOR UPDATE)  
&nbsp;- added updateAcct (UPDATE esq_account with audit columns)  

**02/12/2026** mir0n  
**service.impl.PacManService**  
&nbsp;- EsqObjectKind instead if EsqEntityKind  
&nbsp;- removed "profile" command  
PacManApplication  
&nbsp;- initiate EsqObjectKindStorage  

**02/04/2026** mir0n  
**service.PerformanceAspect**  
&nbsp;- cleanup: @Around("execution(* pro.mir0n.esquire.pacMan.jpa.*.*(..))")  

---

## Commits

```

-- 2026-05-03 | commit: e798f7e | mir0n.the.programmer | k8s, k8s-oci --
M	README.md
M	bizTree/src/main/resources/logback-spring.xml
M	doc/Esquire.Vision.md
M	gateway/src/main/resources/application.yml
M	gateway/src/main/resources/logback-spring.xml
A	k8s-oci/README.md
A	k8s-oci/add-oke-security-rules.bat
A	k8s-oci/cluster/create-nodepool-placement.json
A	k8s-oci/cluster/create-nodepool-source.json
A	k8s-oci/cluster/ingress.yaml
A	k8s-oci/cluster/letsencrypt-prod.yaml
A	k8s-oci/cluster/node-labels.bat
A	k8s-oci/cluster/oke-egress-rules.json
A	k8s-oci/cluster/oke-ingress-rules.json
A	k8s-oci/cluster/pod-network-options.json
A	k8s-oci/cluster/service-lb-subnets.json
A	k8s-oci/create-basic-cluster.bat
A	k8s-oci/create-nodepool.bat
A	k8s-oci/fix.bat
A	k8s-oci/ghcr-push-rest.sh
A	k8s-oci/ghcr-push.bat
A	k8s-oci/ghcr-push.log
A	k8s-oci/ghcr-repush-spring.sh
A	k8s-oci/ghcr-repush.log
A	k8s-oci/oke-bootstrap.bat
A	k8s-oci/oke-down.bat
A	k8s-oci/oke-login.bat
A	k8s-oci/oke-up.bat
A	k8s-oci/policy-statements.json
A	k8s-oci/publish.bat
A	k8s-oci/show.them.all.bat
A	k8s-oci/values/activemq.yaml
A	k8s-oci/values/biztree.yaml
A	k8s-oci/values/enyman.yaml
A	k8s-oci/values/frontend.yaml
A	k8s-oci/values/gateway.yaml
A	k8s-oci/values/kcmaster.yaml
A	k8s-oci/values/keycloak.yaml
A	k8s-oci/values/keysmith.yaml
A	k8s-oci/values/pacman.yaml
A	k8s-oci/values/postgres.yaml
M	k8s/charts/esquire-biztree/templates/configmap.yaml
M	k8s/charts/esquire-enyman/templates/configmap.yaml
M	k8s/charts/esquire-frontend/templates/service.yaml
M	k8s/charts/esquire-gateway/templates/configmap.yaml
M	k8s/charts/esquire-gateway/templates/service.yaml
M	k8s/charts/esquire-kcmaster/templates/configmap.yaml
M	k8s/charts/esquire-keysmith/templates/configmap.yaml
M	k8s/charts/esquire-pacman/templates/configmap.yaml
M	k8s/charts/infra/keycloak/templates/service.yaml
M	k8s/charts/infra/keycloak/templates/statefulset.yaml
M	k8s/charts/infra/postgres/templates/statefulset.yaml
M	kcMaster/src/main/resources/logback-spring.xml
M	keySmith/src/main/resources/logback-spring.xml
M	keycloak/import/esquire.json
M	pacMan/src/main/resources/logback-spring.xml
 56 files changed, 1756 insertions(+), 85 deletions(-)

-- 2026-04-22 | commit: 4c4473d | mir0n.the.programmer | local k8s deployment --
M	README.md
M	bizTree/Dockerfile
A	bizTree/Dockerfile.lx
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/security/SecurityConfiguration.java
M	doc/Esquire.Vision.md
A	doc/OCI.Pricing.md
A	doc/WhereToGo.md
M	doc/release_notes.txt
M	enyMan/Dockerfile
A	enyMan/Dockerfilel.lx
M	gateway/Dockerfile
A	gateway/Dockerfile.lx
A	k8s/1-by-1/1.1) postgres install.bat
A	k8s/1-by-1/1.2)show.all.bat
A	k8s/1-by-1/1.3) postgres exec.bat
A	k8s/1-by-1/1.9) postgres uninstall.bat
A	k8s/1-by-1/10.1) frontend install.bat
A	k8s/1-by-1/10.3) frontend exec.bat
A	k8s/1-by-1/10.9) frontend uninstall.bat
A	k8s/1-by-1/2.1) aMQ install.bat
A	k8s/1-by-1/2.3) aMQ exec.bat
A	k8s/1-by-1/2.9) aMQ uninstall.bat
A	k8s/1-by-1/3.1) keycloak install.bat
A	k8s/1-by-1/3.3) keycloak exec.bat
A	k8s/1-by-1/3.9) keycloak uninstall.bat
A	k8s/1-by-1/4.1) biztree install.bat
A	k8s/1-by-1/4.3) biztree exec.bat
A	k8s/1-by-1/4.9) biztree uninstall.bat
A	k8s/1-by-1/5.1) enyman install.bat
A	k8s/1-by-1/5.3) enyman exec.bat
A	k8s/1-by-1/5.9) enyman uninstall.bat
A	k8s/1-by-1/6.1) pacman install.bat
A	k8s/1-by-1/6.3) pacman exec.bat
A	k8s/1-by-1/6.9) pacman uninstall.bat
A	k8s/1-by-1/7.1) keysmith install.bat
A	k8s/1-by-1/7.3) keysmith exec.bat
A	k8s/1-by-1/7.9) keysmith uninstall.bat
A	k8s/1-by-1/8.1) kcmaster install.bat
A	k8s/1-by-1/8.3) kcmaster exec.bat
A	k8s/1-by-1/8.9) kcmaster uninstall.bat
A	k8s/1-by-1/9.1) gateway install.bat
A	k8s/1-by-1/9.3) gateway exec.bat
A	k8s/1-by-1/9.9) gateway uninstall.bat
A	k8s/charts/esquire-biztree/Chart.yaml
A	k8s/charts/esquire-biztree/templates/configmap.yaml
A	k8s/charts/esquire-biztree/templates/deployment.yaml
A	k8s/charts/esquire-biztree/templates/secret.yaml
A	k8s/charts/esquire-biztree/templates/service.yaml
A	k8s/charts/esquire-biztree/values.yaml
A	k8s/charts/esquire-enyman/Chart.yaml
A	k8s/charts/esquire-enyman/templates/configmap.yaml
A	k8s/charts/esquire-enyman/templates/deployment.yaml
A	k8s/charts/esquire-enyman/templates/secret.yaml
A	k8s/charts/esquire-enyman/templates/service.yaml
A	k8s/charts/esquire-enyman/values.yaml
A	k8s/charts/esquire-frontend/Chart.yaml
A	k8s/charts/esquire-frontend/templates/configmap.yaml
A	k8s/charts/esquire-frontend/templates/deployment.yaml
A	k8s/charts/esquire-frontend/templates/service.yaml
A	k8s/charts/esquire-frontend/values.yaml
A	k8s/charts/esquire-gateway/Chart.yaml
A	k8s/charts/esquire-gateway/templates/configmap.yaml
A	k8s/charts/esquire-gateway/templates/deployment.yaml
A	k8s/charts/esquire-gateway/templates/service.yaml
A	k8s/charts/esquire-gateway/values.yaml
A	k8s/charts/esquire-kcmaster/Chart.yaml
A	k8s/charts/esquire-kcmaster/templates/configmap.yaml
A	k8s/charts/esquire-kcmaster/templates/deployment.yaml
A	k8s/charts/esquire-kcmaster/templates/secret.yaml
A	k8s/charts/esquire-kcmaster/templates/service.yaml
A	k8s/charts/esquire-kcmaster/values.yaml
A	k8s/charts/esquire-keysmith/Chart.yaml
A	k8s/charts/esquire-keysmith/templates/configmap.yaml
A	k8s/charts/esquire-keysmith/templates/deployment.yaml
A	k8s/charts/esquire-keysmith/templates/secret.yaml
A	k8s/charts/esquire-keysmith/templates/service.yaml
A	k8s/charts/esquire-keysmith/values.yaml
A	k8s/charts/esquire-pacman/Chart.yaml
A	k8s/charts/esquire-pacman/templates/configmap.yaml
A	k8s/charts/esquire-pacman/templates/deployment.yaml
A	k8s/charts/esquire-pacman/templates/secret.yaml
A	k8s/charts/esquire-pacman/templates/service.yaml
A	k8s/charts/esquire-pacman/values.yaml
A	k8s/charts/infra/activemq/Chart.yaml
A	k8s/charts/infra/activemq/templates/service.yaml
A	k8s/charts/infra/activemq/templates/statefulset.yaml
A	k8s/charts/infra/activemq/values.yaml
A	k8s/charts/infra/keycloak/Chart.yaml
A	k8s/charts/infra/keycloak/templates/secret.yaml
A	k8s/charts/infra/keycloak/templates/service.yaml
A	k8s/charts/infra/keycloak/templates/statefulset.yaml
A	k8s/charts/infra/keycloak/values.yaml
A	k8s/charts/infra/postgres/Chart.yaml
A	k8s/charts/infra/postgres/templates/secret.yaml
A	k8s/charts/infra/postgres/templates/service.yaml
A	k8s/charts/infra/postgres/templates/statefulset.yaml
A	k8s/charts/infra/postgres/values.yaml
A	k8s/k8s-down.bat
A	k8s/k8s-up.bat
A	k8s/show.them.all.bat
M	keySmith/Dockerfile
A	keySmith/Dockerfile.lx
M	pacMan/Dockerfile
A	pacMan/Dockerfile.lx
 105 files changed, 1908 insertions(+), 85 deletions(-)

-- 2026-04-21 | commit: 71ab875 | mir0n.the.programmer | v1.2.2 finalization --
M	README.md
A	doc/DatabaseDictionary.md
A	doc/Esquire.Vision.md
A	doc/H2BizTree.md
A	doc/Messaging.md
M	doc/Object.Kind.enum.md
A	doc/keyCloak-gateway.JWE.md
A	doc/media/ComponentModel.svg
A	doc/model/ESQ.2026.ERD.png
A	doc/reports/report_v1.2.2.md
 10 files changed, 19432 insertions(+), 104 deletions(-)

-- 2026-04-20 | commit: ae96cab | mir0n.the.programmer | acct transfer: conversion rate; KC realm theme fix --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/resources/esq-entity-dictionaries.xml
M	doc/release_notes.txt
M	keycloak/import/esquire.json
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/IAcctTransactionProcessor.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/dto/AcctTransactionSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionJpa.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/META-INF/oracle-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/postgres-acct-transaction.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
 16 files changed, 196 insertions(+), 52 deletions(-)

-- 2026-04-16 | commit: 8f71ef2 | mir0n.the.programmer | procedural style sweep: ret pattern, null-guard across services --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/ProblemDetailMill.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
 18 files changed, 224 insertions(+), 150 deletions(-)

-- 2026-04-16 | commit: 504b497 | mir0n.the.programmer | keycloak esquire-explorer schema : more templates added --
M	doc/release_notes.txt
M	keycloak/themes/README-CUSTOMIZATION.md
M	keycloak/themes/README.md
A	keycloak/themes/esquire-explorer/login/info.ftl
A	keycloak/themes/esquire-explorer/login/login-page-expired.ftl
A	keycloak/themes/esquire-explorer/login/login-reset-password.ftl
M	keycloak/themes/esquire-explorer/login/login.ftl
A	keycloak/themes/esquire-explorer/login/logout-confirm.ftl
 8 files changed, 162 insertions(+), 22 deletions(-)

-- 2026-04-15 | commit: ff06a0c | mir0n.the.programmer | postgres container added; local docker compose cleanup --
D	.dockerignore
A	activemq/Dockerfile
M	activemq/compose.yaml
D	compose/Dockerfile.keycloak
M	compose/compose.yaml
D	compose/conf/activemq.xml
A	compose/data/activemq/-placeholder-
A	compose/data/keycloak/-placeholder-
A	compose/data/postgres/-placeholder-
D	compose/import/esquire.json
D	compose/themes/README-CUSTOMIZATION.md
D	compose/themes/README.md
D	compose/themes/esquire-explorer/login/error.ftl
D	compose/themes/esquire-explorer/login/login-config-totp.ftl
D	compose/themes/esquire-explorer/login/login-otp.ftl
D	compose/themes/esquire-explorer/login/login-update-password.ftl
D	compose/themes/esquire-explorer/login/login.ftl
D	compose/themes/esquire-explorer/login/messages/messages_en.properties
D	compose/themes/esquire-explorer/login/resources/css/styles.css
D	compose/themes/esquire-explorer/login/resources/img/main.ico
D	compose/themes/esquire-explorer/login/resources/img/unknown.ico
D	compose/themes/esquire-explorer/login/template.ftl
D	compose/themes/esquire-explorer/login/theme.properties
M	doc/release_notes.txt
D	keySmith/.dockerignore
M	keycloak/Dockerfile.keycloak
D	pacMan/.dockerignore
A	postgres/Dockerfile
A	postgres/compose.yaml
A	postgres/initdb/init.sh
 30 files changed, 153 insertions(+), 3669 deletions(-)

-- 2026-04-15 | commit: 7b132da | mir0n.the.programmer | acct transaction PK : classic : from ESQ_ATR_SEQ --
M	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/resources/META-INF/oracle-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/postgres-acct-transaction.xml
 7 files changed, 25 insertions(+), 2 deletions(-)

-- 2026-04-14 | commit: 3c217c9 | mir0n.the.programmer | Acct transaction Phase IV, acct transfer fixes; Transfer dictionary; --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqPerson.java
M	common/src/main/resources/esq-entity-dictionaries.xml
M	common/src/main/resources/esq-object-kinds.xml
M	doc/release_notes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/AcctOperation.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 17 files changed, 364 insertions(+), 39 deletions(-)

-- 2026-04-13 | commit: a71c684 | mir0n.the.programmer |  account transaction Phase III — single op complete; transfer draft --
M	common/src/main/resources/esq-entity-dictionaries.xml
M	doc/release_notes.txt
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/AcctOperation.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/IAcctTransactionProcessor.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/dto/AcctTransactionSimple.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/dto/AcctTransactionSingle.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
 12 files changed, 661 insertions(+), 218 deletions(-)

-- 2026-04-12 | commit: 6398cc2 | mir0n.the.programmer | account transaction Phase II — kind=1000 dictionary; EntityFieldUtils validation; GenericValidator fix --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/service/EntityFieldUtils.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/GenericValidator.java
M	common/src/main/resources/esq-entity-dictionaries.xml
M	doc/release_notes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
 7 files changed, 93 insertions(+), 19 deletions(-)

-- 2026-04-10 | commit: d54e7af | mir0n.the.programmer | Object Kind enumeration specified --
M	common/src/main/resources/esq-entity-dictionaries.xml
A	doc/Object.Kind.enum.md
M	doc/release_notes.txt
 3 files changed, 178 insertions(+), 94 deletions(-)

-- 2026-04-09 | commit: 44fabca | mir0n.the.programmer | account transaction command Phase I; --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/resources/esq-entity-dictionaries.xml
M	doc/release_notes.txt
M	gateway/src/main/resources/application.yml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/dto/AcctTransactionSimple.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionJpa.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionRepository.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
A	pacMan/src/main/resources/META-INF/oracle-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
A	pacMan/src/main/resources/META-INF/postgres-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
M	pacMan/src/main/resources/application.yml
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
 19 files changed, 874 insertions(+), 2 deletions(-)

-- 2026-04-09 | commit: 361edcb | mir0n.the.programmer | funded account fields; generalization of biz validation rules; EntityFieldUtils utility --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityLayer.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqAcct.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
A	common/src/main/java/pro/mir0n/esquire/backend/service/EntityFieldUtils.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/IValidator.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/resources/esq-entity-dictionaries.xml
A	common/src/test/java/pro/mir0n/esquire/backend/service/EntityFieldUtilsTest.java
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 23 files changed, 464 insertions(+), 120 deletions(-)

-- 2026-04-07 | commit: 87b11ad | mir0n.the.programmer | unified facade REST API: esq-cmd-* namespace; kind-aware gateway predicate --
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
A	gateway/src/main/java/pro/mir0n/esquire/gateway/config/EntityKindRoutePredicateFactory.java
M	gateway/src/main/resources/application.yml
A	gateway/src/test/java/pro/mir0n/esquire/gateway/config/EntityKindRoutePredicateFactoryTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
 10 files changed, 249 insertions(+), 29 deletions(-)

-- 2026-04-06 | commit: b87bb3e | mir0n.the.programmer |  entity kind validation: Map storage, Integer→int, upfront checks; KC theme fixes; gateway config --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveUsrHandler.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqEntityDictionaryStorage.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqObjectKindStorage.java
M	compose/compose.yaml
A	compose/themes/esquire-explorer/login/error.ftl
A	compose/themes/esquire-explorer/login/login-config-totp.ftl
A	compose/themes/esquire-explorer/login/login-otp.ftl
A	compose/themes/esquire-explorer/login/login-update-password.ftl
M	compose/themes/esquire-explorer/login/theme.properties
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateway/src/main/resources/application.yml
A	keycloak/themes/esquire-explorer/login/error.ftl
A	keycloak/themes/esquire-explorer/login/login-config-totp.ftl
A	keycloak/themes/esquire-explorer/login/login-otp.ftl
A	keycloak/themes/esquire-explorer/login/login-update-password.ftl
M	keycloak/themes/esquire-explorer/login/theme.properties
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 34 files changed, 529 insertions(+), 165 deletions(-)

-- 2026-04-06 | commit: 21340b4 | mir0n.the.programmer | esq-move command Phase III: KC path sync; entity path semantics fix --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKind.java
M	common/src/main/java/pro/mir0n/esquire/messaging/changes.txt
M	common/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java
A	doc/entity.path.semantics.md
A	doc/model/ComponentModel.vsdx
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EnyManJmsConfig.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisher.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisher.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListener.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisherTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListenerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	enyMan/src/test/resources/logback-test.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
D	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcMasterJmsConfig.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestConsumer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisher.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/IKcIdentityService.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
M	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisherTest.java
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
 37 files changed, 1309 insertions(+), 158 deletions(-)

-- 2026-04-02 | commit: 4f118d4 | mir0n.the.programmer | esq-move command: move ORG/USR to a different parent org, Phase II - bizTree cache --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveAcctHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveOrgHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveUsrHandler.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqMoveRecord.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqOrgRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
 26 files changed, 506 insertions(+), 47 deletions(-)

-- 2026-03-31 | commit: 125ae34 | mir0n.the.programmer | esq-move command: move ORG/USR to a different parent org, Phase I - basic functionality --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeConstants.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/BizTreeConstantsTest.java
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilter.java
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqOrgRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/resources/application.yml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 28 files changed, 657 insertions(+), 15 deletions(-)

-- 2026-03-31 | commit: 92cdbc2 | mir0n.the.programmer | keyCloak esquire theme --
A	compose/Dockerfile.keycloak
M	compose/compose.yaml
M	compose/import/esquire.json
A	compose/themes/README-CUSTOMIZATION.md
A	compose/themes/README.md
A	compose/themes/esquire-explorer/login/login.ftl
A	compose/themes/esquire-explorer/login/messages/messages_en.properties
A	compose/themes/esquire-explorer/login/resources/css/styles.css
A	compose/themes/esquire-explorer/login/resources/img/main.ico
A	compose/themes/esquire-explorer/login/resources/img/unknown.ico
A	compose/themes/esquire-explorer/login/template.ftl
A	compose/themes/esquire-explorer/login/theme.properties
M	doc/release_notes.txt
A	keycloak/Dockerfile.keycloak
M	keycloak/compose.yaml
M	keycloak/import/esquire.json
A	keycloak/themes/README-CUSTOMIZATION.md
A	keycloak/themes/README.md
A	keycloak/themes/esquire-explorer/login/login.ftl
A	keycloak/themes/esquire-explorer/login/messages/messages_en.properties
A	keycloak/themes/esquire-explorer/login/resources/css/styles.css
A	keycloak/themes/esquire-explorer/login/resources/img/main.ico
A	keycloak/themes/esquire-explorer/login/resources/img/unknown.ico
A	keycloak/themes/esquire-explorer/login/template.ftl
A	keycloak/themes/esquire-explorer/login/theme.properties
 25 files changed, 1616 insertions(+), 14 deletions(-)

-- 2026-03-28 | commit: 7639a35 | mir0n.the.programmer | Preparing for "move" command --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/resources/META-INF/oracle-entity.xml
M	bizTree/src/main/resources/META-INF/postgres-entity.xml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqOrgRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keySmith/src/main/resources/META-INF/postgres-access-profile.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 21 files changed, 363 insertions(+), 63 deletions(-)

-- 2026-03-28 | commit: e0c6ba3 | mir0n.the.programmer | default field — dictionary-driven entity creation defaults --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityField.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityLayer.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqCustomEntityFieldJpa.java
M	common/src/main/resources/esq-entity-dictionaries.xml
A	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityLayerTest.java
A	doc/DefaultRule.md
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-custom-field.xml
M	enyMan/src/main/resources/META-INF/oracle-dictionary.xml
M	enyMan/src/main/resources/META-INF/postgres-custom-field.xml
M	enyMan/src/main/resources/META-INF/postgres-dictionary.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 19 files changed, 360 insertions(+), 19 deletions(-)

-- 2026-03-28 | commit: a7bf0fe | mir0n.the.programmer | DELETE workflow — pre-checks and cascade-safe delete sequence --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/DeleteEntityHandler.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/error/DeleteRestrictedException.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
M	common/src/main/resources/esq-object-kinds.xml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisherTest.java
A	kcMaster/src/test/resources/logback-test.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncPublisher.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 25 files changed, 706 insertions(+), 24 deletions(-)

-- 2026-03-26 | commit: 9c9c045 | mir0n.the.programmer | Entity CREATE workflow complete; DELETE- drafted; bizTree handler dispatch; cleanup --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeConstants.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeJmsConfig.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/IBizTreeEventHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateAcctHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateOrgHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateUsrHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/DeleteEntityHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/UpdateEntityHandler.java
M	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntity.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/EmailExistsException.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqOrgJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/main/resources/esq-entity-dictionaries.xml
M	common/src/main/resources/esq-object-kinds.xml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqOrgRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastConsumer.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisher.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-custom-field.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-custom-field.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisherTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	gateway/src/main/resources/application.yml
A	gateway/src/main/resources/logback-spring.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisher.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncPublisher.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithServiceJpa.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisherTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 68 files changed, 2480 insertions(+), 346 deletions(-)

-- 2026-03-21 | commit: 07f0b44 | mir0n.the.programmer | Three-tier logging normalization across all services; JMS msg audit dual-mode pattern --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/IBizTreeService.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	bizTree/src/main/resources/application.yml
M	bizTree/src/main/resources/logback-spring.xml
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
A	bizTree/src/test/resources/logback-test.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqColumnHeaderDef.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntity.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityFactory.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityField.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityLayer.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKind.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqThing.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqAcct.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqAddress.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqOrg.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqPerson.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/exception/GlobalExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqEntityDictionaryStorage.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqObjectKindStorage.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/IValidator.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/java/pro/mir0n/esquire/messaging/changes.txt
A	common/src/test/resources/logback-test.xml
M	compose/compose.yaml
A	doc/Logging.md
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastConsumer.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisher.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
A	enyMan/src/main/resources/logback-spring.xml
A	enyMan/src/test/resources/logback-test.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/GatewayErrorWebExceptionHandler.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/ResponseTraceFilter.java
M	gateway/src/main/resources/application.yml
A	gateway/src/test/resources/logback-test.xml
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestConsumer.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisher.java
M	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
M	kcMaster/src/main/resources/application.yml
M	kcMaster/src/main/resources/logback-spring.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncPublisher.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncResponseListener.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/BizValidatorFactory.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithServiceJpa.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/main/resources/logback-spring.xml
A	keySmith/src/test/resources/logback-test.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
A	pacMan/src/main/resources/logback-spring.xml
A	pacMan/src/test/resources/logback-test.xml
 87 files changed, 1266 insertions(+), 365 deletions(-)

-- 2026-03-21 | commit: 5146628 | mir0n.the.programmer | Messaging Phase 3 — kcMaster service; KC sync decoupled from keySmith via JMS URQ/URS/URR --
M	activemq/conf/activemq.xml
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/main/java/pro/mir0n/esquire/messaging/changes.txt
A	common/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java
M	compose/compose.yaml
A	compose/conf/activemq.xml
A	doc/Message.Structure.md
M	doc/release_notes.txt
A	kcMaster/Dockerfile
A	kcMaster/Dockerfile.lx
A	kcMaster/Dockerfile.win
A	kcMaster/compose.yaml
A	kcMaster/pom.xml
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
R092	keySmith/src/main/java/pro/mir0n/esquire/keySmith/config/KeycloakConfig.java	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/config/KeycloakConfig.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcEntityBroadcastConsumer.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcMasterJmsConfig.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestConsumer.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisher.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcSyncRequest.java
R078	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeycloakIdentityService.java	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/IKcIdentityService.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
A	kcMaster/src/main/resources/application.yml
A	kcMaster/src/main/resources/logback-spring.xml
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncPublisher.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncResponseListener.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KeySmithJmsConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeycloakIdentityService.java
M	keySmith/src/main/resources/application.yml
M	keySmith/src/main/resources/logback-spring.xml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
M	pom.xml
 40 files changed, 1578 insertions(+), 449 deletions(-)

-- 2026-03-21 | commit: 2b7600c | mir0n.the.programmer | Dictionary affects3 field added --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityField.java
M	common/src/main/resources/esq-entity-dictionaries.xml
M	doc/release_notes.txt
 4 files changed, 39 insertions(+), 1 deletion(-)

-- 2026-03-20 | commit: 488b9f9 | mir0n.the.programmer | Messaging Phase 2 — bizTree cache update from entity broadcast --
M	bizTree/pom.xml
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/jpa/EsqAcctRepository.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/jpa/EsqOrgRepository.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/jpa/EsqTreeNodeRepository.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/jpa/EsqUsrRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
A	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
A	bizTree/src/main/resources/META-INF/oracle-entity.xml
D	bizTree/src/main/resources/META-INF/oracle-tree-node.xml
A	bizTree/src/main/resources/META-INF/postgres-entity.xml
D	bizTree/src/main/resources/META-INF/postgres-tree-node.xml
M	bizTree/src/main/resources/application.yml
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
M	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqOrgJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	compose/compose.yaml
M	doc/Messaging.First.md
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastConsumer.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisher.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/application.yml
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisherTest.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisherTest.java
 43 files changed, 1842 insertions(+), 749 deletions(-)

-- 2026-03-19 | commit: 2443f76 | mir0n.the.programmer | ActiveMQ entity broadcast infrastructure --
A	activemq/compose.yaml
A	activemq/conf/activemq.xml
A	activemq/docker-compose-down.bat
A	activemq/docker-compose-up.bat
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeJmsConfig.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
M	bizTree/src/main/resources/application.yml
A	bizTree/src/main/resources/logback-spring.xml
A	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	compose/compose.yaml
A	doc/Messaging.First.md
M	doc/release_notes.txt
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EnyManJmsConfig.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastConsumer.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisher.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisherTest.java
M	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/PacManJmsConfig.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisherTest.java
M	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 34 files changed, 2294 insertions(+), 11 deletions(-)

-- 2026-03-16 | commit: 7167791 | mir0n.the.programmer | keycloak dump is renewed --
M	compose/import/esquire.json
A	compose/logs/-placeholder-
M	keycloak/import/esquire.json
 3 files changed, 170 insertions(+), 28 deletions(-)

-- 2026-03-16 | commit: 6bbc1f6 | mir0n.the.programmer | TOTP state machine; reset password handshake; connectFlg lifecycle; KC integration --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqAccessProfileJpa.java
M	common/src/main/resources/esq-entity-dictionaries.xml
M	compose/compose.yaml
A	doc/keySmithCredentialRoutine.md
M	doc/release_notes.txt
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/config/KeycloakConfig.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/jpa/EsqAccessProfileRepository.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeycloakIdentityService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithServiceJpa.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeycloakIdentityService.java
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keySmith/src/main/resources/META-INF/postgres-access-profile.xml
M	keySmith/src/main/resources/application.yml
A	keySmith/src/main/resources/logback-spring.xml
M	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
 21 files changed, 1184 insertions(+), 27 deletions(-)

-- 2026-03-10 | commit: 8477aa4 | mir0n.the.programmer | Unit tests were added to all services --
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityFieldTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityKindFieldLayerTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfileTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/error/GenericExceptionHandlerTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/error/InvalidValueExceptionTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/storage/EsqRolesStorageTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/validator/GenericValidatorTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/validator/ValidatorFactoryTest.java
M	doc/release_notes.txt
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverterTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/error/ProblemDetailMillTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilterTest.java
A	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
A	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
 21 files changed, 2253 insertions(+)

-- 2026-03-10 | commit: 81bf673 | mir0n.the.programmer | keySmith DTO approach: roles and permissions sourced from in-memory storage, Cyrillic К character fix --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/BizValidatorFactory.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithServiceJpa.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
 15 files changed, 374 insertions(+), 29 deletions(-)

-- 2026-03-10 | commit: 7050391 | mir0n.the.programmer | Observability, security, exception handling generalized to common library --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/exception/GlobalExceptionHandler.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/security/JwtAuthenticationFilter.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/security/JwtService.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/security/SecurityConfiguration.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/MdcFilter.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/RequestContextUtils.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/GenericRuntimeException.java
A	common/src/main/java/pro/mir0n/esquire/backend/exception/GlobalExceptionHandler.java
R097	pacMan/src/main/java/pro/mir0n/esquire/pacMan/security/JwtAuthenticationFilter.java	common/src/main/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilter.java
R093	pacMan/src/main/java/pro/mir0n/esquire/pacMan/security/JwtService.java	common/src/main/java/pro/mir0n/esquire/backend/security/JwtService.java
R090	pacMan/src/main/java/pro/mir0n/esquire/pacMan/security/SecurityConfiguration.java	common/src/main/java/pro/mir0n/esquire/backend/security/SecurityConfiguration.java
R094	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/MdcFilter.java	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
R083	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/PerformanceAspect.java	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
R087	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/RequestContextUtils.java	common/src/main/java/pro/mir0n/esquire/backend/service/RequestContextUtils.java
R086	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/RequestPerformance.java	common/src/main/java/pro/mir0n/esquire/backend/service/RequestPerformance.java
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/exception/GlobalExceptionHandler.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/security/JwtAuthenticationFilter.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/security/JwtService.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/security/SecurityConfiguration.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/MdcFilter.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/PerformanceAspect.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/RequestContextUtils.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/RequestPerformance.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/ProblemDetailMill.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/exception/GlobalExceptionHandler.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/security/JwtAuthenticationFilter.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/security/JwtService.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/security/SecurityConfiguration.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/MdcFilter.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/PerformanceAspect.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/RequestContextUtils.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/RequestPerformance.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/exception/GlobalExceptionHandler.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/PerformanceAspect.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/RequestPerformance.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
 56 files changed, 272 insertions(+), 1842 deletions(-)

-- 2026-03-09 | commit: 94bec32 | mir0n.the.programmer | Exception handling centralized; Esquire exception hierarchy unified --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/exception/GlobalExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/GenericRuntimeException.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/InvalidValueException.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/PermissionDeniedException.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/ResourceNotFoundException.java
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/exception/GlobalExceptionHandler.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/exception/GlobalExceptionHandler.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/exception/GlobalExceptionHandler.java
 15 files changed, 198 insertions(+), 269 deletions(-)

-- 2026-03-09 | commit: 8544eac | mir0n.the.programmer | Service-side permission validation; exception handling hardened --
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/exception/GlobalExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/error/PermissionDeniedException.java
A	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
A	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/IRolesService.java
A	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/JpaRolesRepository.java
A	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/JpaRolesService.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/main/resources/META-INF/oracle-roles.xml
A	common/src/main/resources/META-INF/postgres-roles.xml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/exception/GlobalExceptionHandler.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqEntityDictionaryRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/security/JwtAuthenticationFilter.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/exception/GlobalExceptionHandler.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/security/JwtAuthenticationFilter.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/exception/GlobalExceptionHandler.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/security/JwtAuthenticationFilter.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/application.yml
 40 files changed, 753 insertions(+), 50 deletions(-)

-- 2026-03-08 | commit: f23a320 | mir0n.the.programmer | Personal-field enforcement: users can only update their own personal-marked fields --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityField.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityKindFieldLayer.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/GenericValidator.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/IValidator.java
M	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/resources/esq-entity-dictionaries.xml
M	compose/compose.yaml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/BizValidatorFactory.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/application.yml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
 21 files changed, 155 insertions(+), 59 deletions(-)

-- 2026-03-06 | commit: 6c11478 | mir0n.the.programmer | Field validation framework; business rule validators; null-safe fixes --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityKindFieldLayer.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqNameValueSerializer.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqOrg.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/InvalidValueException.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/ProblemDetailMill.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqAccessProfileJpa.java
A	common/src/main/java/pro/mir0n/esquire/backend/validator/GenericValidator.java
A	common/src/main/java/pro/mir0n/esquire/backend/validator/IValidator.java
A	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/main/resources/esq-entity-dictionaries.xml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/exception/GlobalExceptionHandler.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/BizValidatorFactory.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
 31 files changed, 712 insertions(+), 71 deletions(-)

-- 2026-03-04 | commit: 8701026 | mir0n.the.programmer | Dictionary formalized --
M	common/src/main/resources/esq-entity-dictionaries.xml
M	doc/release_notes.txt
 2 files changed, 201 insertions(+), 75 deletions(-)

-- 2026-03-03 | commit: 92a60ee | mir0n.the.programmer | Gateway uses default port: 7070 --
M	compose/compose.yaml
M	compose/import/esquire.json
 2 files changed, 3 insertions(+), 3 deletions(-)

-- 2026-03-03 | commit: eeba46a | mir0n.the.programmer | DOB, Roles list save added, Few fixes, new field type: "text" --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqRole.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqAddress.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqPerson.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqPermissionJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqRoleJpa.java
M	common/src/main/resources/esq-entity-dictionaries.xml
M	common/src/main/resources/esq-object-kinds.xml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/jpa/EsqAccessProfileRepository.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keySmith/src/main/resources/META-INF/postgres-access-profile.xml
 21 files changed, 265 insertions(+), 57 deletions(-)

-- 2026-03-03 | commit: 8160186 | mir0n.the.programmer | Dockerfiles for Linux, CORS issue (by A.O.) --
A	.dockerignore
M	bizTree/Dockerfile
A	bizTree/Dockerfile.win
M	enyMan/Dockerfile
A	enyMan/Dockerfile.win
M	gateway/Dockerfile
A	gateway/Dockerfile.win
M	gateway/src/main/resources/application.yml
M	keySmith/Dockerfile
A	keySmith/Dockerfile.win
M	pacMan/Dockerfile
A	pacMan/Dockerfile.win
 12 files changed, 169 insertions(+), 53 deletions(-)

-- 2026-02-28 | commit: a225b04 | mir0n.the.programmer | Person and address subentity support for user detail and save --
M	README.md
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntity.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityFactory.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKind.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqAcct.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqAddress.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqOrg.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqPerson.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
A	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAddressJpa.java
A	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqPersonJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqObjectKindStorage.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
M	common/src/main/resources/esq-entity-dictionaries.xml
M	common/src/main/resources/esq-object-kinds.xml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-custom-field.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
 29 files changed, 2102 insertions(+), 194 deletions(-)

-- 2026-02-19 | commit: 73fca34 | mir0n.the.programmer | Implemented common/audit JPA entity field updates. --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityField.java
M	common/src/main/resources/esq-entity-dictionaries.xml
M	common/src/main/resources/esq-object-kinds.xml
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqCustomFieldRepository.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqEntityRepository.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqOrgRepository.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/META-INF/oracle-custom-field.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-custom-field.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/resources/application.yml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/jpa/EsqAccessProfileRepository.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeySmithService.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keySmith/src/main/resources/META-INF/postgres-access-profile.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
 34 files changed, 1034 insertions(+), 142 deletions(-)

-- 2026-02-13 | commit: 7f17c34 | mir0n.the.programmer | Refactoring/cleanup in progress --
M	bizTree/src/main/resources/META-INF/oracle-tree-node.xml
M	bizTree/src/main/resources/META-INF/postgres-tree-node.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntity.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityFactory.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqTreeNode.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqTreeNodeMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqAcct.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqOrg.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqTreeNodeJpa.java
M	doc/release_notes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqEntityRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
 20 files changed, 75 insertions(+), 184 deletions(-)

-- 2026-02-12 | commit: 34de730 | mir0n.the.programmer | Let set of Object Kinds configured in server side, and not hardcoded --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqColumnHeaderDef.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityFactory.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKind.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKinds.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
A	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqObjectKindStorage.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/main/resources/esq-object-kinds.xml
M	doc/release_notes.txt
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
M	gateway/src/main/resources/application.yml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
 22 files changed, 976 insertions(+), 85 deletions(-)

-- 2026-02-04 | commit: e8e623a | mir0n.the.programmer | normalization of entity objects structure: EsqThing added --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntity.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqThing.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqTreeNode.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqPermission.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqRole.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqAccessProfileJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqPermissionJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqRoleJpa.java
M	doc/release_notes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/PerformanceAspect.java
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keySmith/src/main/resources/META-INF/postgres-access-profile.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/PerformanceAspect.java
 18 files changed, 160 insertions(+), 88 deletions(-)

-- 2026-02-02 | commit: d401fec | mir0n.the.programmer | "SysAdmin" and "Sys Admin-s" added --
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityFactory.java
M	common/src/main/resources/esq-entity-dictionaries.xml
M	compose/import/esquire.json
M	doc/release_notes.txt
M	gateway/src/main/resources/application.yml
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keycloak/import/esquire.json
M	pom.xml
 9 files changed, 237 insertions(+), 92 deletions(-)
```

---

## Files Modified

```
M	README.md
A	activemq/Dockerfile
A	activemq/compose.yaml
A	activemq/conf/activemq.xml
A	activemq/docker-compose-down.bat
A	activemq/docker-compose-up.bat
A	bizTree/Dockerfile.lx
A	bizTree/Dockerfile.win
M	bizTree/pom.xml
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeApplication.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/BizTreeConstants.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoader.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheSql.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/IBizTreeCacheRepository.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/cache/impl/BizTreeCacheRepository.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/changes.txt
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/controller/BizTreeController.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/exception/GlobalExceptionHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/h2/BizTreeH2Config.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/jpa/EsqAcctRepository.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/jpa/EsqOrgRepository.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/jpa/EsqTreeNodeRepository.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/jpa/EsqUsrRepository.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/BizTreeJmsConfig.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumer.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/IBizTreeEventHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateAcctHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateOrgHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/CreateUsrHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/DeleteEntityHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveAcctHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveOrgHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/MoveUsrHandler.java
A	bizTree/src/main/java/pro/mir0n/esquire/bizTree/messaging/handler/UpdateEntityHandler.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/security/JwtAuthenticationFilter.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/security/JwtService.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/security/SecurityConfiguration.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/IBizTreeService.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/MdcFilter.java
D	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/RequestContextUtils.java
M	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/impl/BizTreeService.java
A	bizTree/src/main/resources/META-INF/h2-cache-sql.properties
A	bizTree/src/main/resources/META-INF/oracle-entity.xml
D	bizTree/src/main/resources/META-INF/oracle-tree-node.xml
A	bizTree/src/main/resources/META-INF/postgres-entity.xml
D	bizTree/src/main/resources/META-INF/postgres-tree-node.xml
M	bizTree/src/main/resources/application.yml
A	bizTree/src/main/resources/logback-spring.xml
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/BizTreeConstantsTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/cache/BizTreeCacheLoaderTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/controller/BizTreeControllerTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/messaging/EsqEntityBroadcastConsumerTest.java
A	bizTree/src/test/java/pro/mir0n/esquire/bizTree/service/BizTreeServiceTest.java
A	bizTree/src/test/resources/logback-test.xml
M	common/pom.xml
M	common/src/main/java/pro/mir0n/esquire/backend/changes.txt
A	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqColumnHeaderDef.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntity.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionary.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityFactory.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityField.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityKindFieldLayer.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqEntityLayer.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqNameValueSerializer.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKind.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqObjectKinds.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqThing.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqTreeNode.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/EsqTreeNodeMapper.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfile.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqPermission.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/access/EsqRole.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqAcct.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqAddress.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqOrg.java
A	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqPerson.java
M	common/src/main/java/pro/mir0n/esquire/backend/dto/entity/EsqUsr.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/DeleteRestrictedException.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/EmailExistsException.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/GenericExceptionHandler.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/GenericRuntimeException.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/InvalidValueException.java
A	common/src/main/java/pro/mir0n/esquire/backend/error/PermissionDeniedException.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/ProblemDetailMill.java
M	common/src/main/java/pro/mir0n/esquire/backend/error/ResourceNotFoundException.java
A	common/src/main/java/pro/mir0n/esquire/backend/exception/GlobalExceptionHandler.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqCustomEntityFieldJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqEntityJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/EsqTreeNodeJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqAccessProfileJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqPermissionJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/access/EsqRoleJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAcctJpa.java
A	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqAddressJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqOrgJpa.java
A	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqPersonJpa.java
M	common/src/main/java/pro/mir0n/esquire/backend/jpa/entity/EsqUsrJpa.java
R072	enyMan/src/main/java/pro/mir0n/esquire/enyMan/security/JwtAuthenticationFilter.java	common/src/main/java/pro/mir0n/esquire/backend/security/JwtAuthenticationFilter.java
R093	pacMan/src/main/java/pro/mir0n/esquire/pacMan/security/JwtService.java	common/src/main/java/pro/mir0n/esquire/backend/security/JwtService.java
R058	pacMan/src/main/java/pro/mir0n/esquire/pacMan/security/SecurityConfiguration.java	common/src/main/java/pro/mir0n/esquire/backend/security/SecurityConfiguration.java
A	common/src/main/java/pro/mir0n/esquire/backend/service/EntityFieldUtils.java
R074	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/MdcFilter.java	common/src/main/java/pro/mir0n/esquire/backend/service/MdcFilter.java
R063	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/PerformanceAspect.java	common/src/main/java/pro/mir0n/esquire/backend/service/PerformanceAspect.java
R087	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/RequestContextUtils.java	common/src/main/java/pro/mir0n/esquire/backend/service/RequestContextUtils.java
R086	bizTree/src/main/java/pro/mir0n/esquire/bizTree/service/RequestPerformance.java	common/src/main/java/pro/mir0n/esquire/backend/service/RequestPerformance.java
M	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqEntityDictionaryStorage.java
A	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqObjectKindStorage.java
A	common/src/main/java/pro/mir0n/esquire/backend/storage/EsqRolesStorage.java
A	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/IRolesService.java
A	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/JpaRolesRepository.java
A	common/src/main/java/pro/mir0n/esquire/backend/storage/roles/JpaRolesService.java
A	common/src/main/java/pro/mir0n/esquire/backend/validator/GenericValidator.java
A	common/src/main/java/pro/mir0n/esquire/backend/validator/IValidator.java
A	common/src/main/java/pro/mir0n/esquire/backend/validator/ValidatorFactory.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqConstants.java
A	common/src/main/java/pro/mir0n/esquire/common/EsqMsgConstants.java
M	common/src/main/java/pro/mir0n/esquire/common/EsqUtils.java
M	common/src/main/java/pro/mir0n/esquire/common/changes.txt
A	common/src/main/java/pro/mir0n/esquire/messaging/changes.txt
A	common/src/main/java/pro/mir0n/esquire/messaging/jms/Utils.java
A	common/src/main/resources/META-INF/oracle-roles.xml
A	common/src/main/resources/META-INF/postgres-roles.xml
M	common/src/main/resources/esq-entity-dictionaries.xml
A	common/src/main/resources/esq-object-kinds.xml
A	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityDictionaryTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityFieldTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityKindFieldLayerTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/dto/EsqEntityLayerTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/dto/access/EsqAccessProfileTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/error/GenericExceptionHandlerTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/error/InvalidValueExceptionTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/service/EntityFieldUtilsTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/storage/EsqRolesStorageTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/validator/GenericValidatorTest.java
A	common/src/test/java/pro/mir0n/esquire/backend/validator/ValidatorFactoryTest.java
A	common/src/test/resources/logback-test.xml
M	compose/compose.yaml
A	compose/data/activemq/-placeholder-
A	compose/data/keycloak/-placeholder-
A	compose/data/postgres/-placeholder-
D	compose/import/esquire.json
A	compose/logs/-placeholder-
A	doc/DatabaseDictionary.md
A	doc/DefaultRule.md
A	doc/Esquire.Vision.md
A	doc/H2BizTree.md
A	doc/Logging.md
A	doc/Message.Structure.md
A	doc/Messaging.First.md
A	doc/Messaging.md
A	doc/OCI.Pricing.md
A	doc/Object.Kind.enum.md
A	doc/WhereToGo.md
A	doc/entity.path.semantics.md
A	doc/keyCloak-gateway.JWE.md
A	doc/keySmithCredentialRoutine.md
A	doc/media/ComponentModel.svg
A	doc/model/ComponentModel.vsdx
A	doc/model/ESQ.2026.ERD.png
M	doc/release_notes.txt
A	doc/reports/report_v1.2.2.md
A	enyMan/Dockerfile.win
A	enyMan/Dockerfilel.lx
M	enyMan/pom.xml
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/EnyManApplication.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/changes.txt
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/controller/EnyManController.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/exception/GlobalExceptionHandler.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqCustomFieldRepository.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqEntityDictionaryRepository.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqEntityRepository.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqMoveRecord.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqOrgRepository.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/jpa/EsqUsrRepository.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EnyManJmsConfig.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastConsumer.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisher.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisher.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListener.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/security/JwtService.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/security/SecurityConfiguration.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/IEnyManService.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/MdcFilter.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/PerformanceAspect.java
D	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/RequestPerformance.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/AEnyManService.java
M	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/EnyManService.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/OrgService.java
A	enyMan/src/main/java/pro/mir0n/esquire/enyMan/service/impl/UsrService.java
M	enyMan/src/main/resources/META-INF/oracle-custom-field.xml
M	enyMan/src/main/resources/META-INF/oracle-dictionary.xml
M	enyMan/src/main/resources/META-INF/oracle-entity.xml
M	enyMan/src/main/resources/META-INF/postgres-custom-field.xml
M	enyMan/src/main/resources/META-INF/postgres-dictionary.xml
M	enyMan/src/main/resources/META-INF/postgres-entity.xml
M	enyMan/src/main/resources/application.yml
A	enyMan/src/main/resources/logback-spring.xml
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/controller/EnyManControllerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/EsqEntityBroadcastPublisherTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcRequestPublisherTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/messaging/KcResponseListenerTest.java
A	enyMan/src/test/java/pro/mir0n/esquire/enyMan/service/EnyManServiceTest.java
A	enyMan/src/test/resources/logback-test.xml
A	gateway/Dockerfile.lx
A	gateway/Dockerfile.win
M	gateway/src/main/java/pro/mir0n/esquire/gateway/GatewayApplication.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/changes.txt
A	gateway/src/main/java/pro/mir0n/esquire/gateway/config/EntityKindRoutePredicateFactory.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/config/SecurityConfig.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/GatewayErrorWebExceptionHandler.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/error/ProblemDetailMill.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilter.java
M	gateway/src/main/java/pro/mir0n/esquire/gateway/filters/ResponseTraceFilter.java
M	gateway/src/main/resources/application.yml
A	gateway/src/main/resources/logback-spring.xml
A	gateway/src/test/java/pro/mir0n/esquire/gateway/config/EntityKindRoutePredicateFactoryTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/config/KeycloakRoleConverterTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/error/ProblemDetailMillTest.java
A	gateway/src/test/java/pro/mir0n/esquire/gateway/filters/RequestTraceFilterTest.java
A	gateway/src/test/resources/logback-test.xml
A	k8s-oci/README.md
A	k8s-oci/add-oke-security-rules.bat
A	k8s-oci/cluster/create-nodepool-placement.json
A	k8s-oci/cluster/create-nodepool-source.json
A	k8s-oci/cluster/ingress.yaml
A	k8s-oci/cluster/letsencrypt-prod.yaml
A	k8s-oci/cluster/node-labels.bat
A	k8s-oci/cluster/oke-egress-rules.json
A	k8s-oci/cluster/oke-ingress-rules.json
A	k8s-oci/cluster/pod-network-options.json
A	k8s-oci/cluster/service-lb-subnets.json
A	k8s-oci/create-basic-cluster.bat
A	k8s-oci/create-nodepool.bat
A	k8s-oci/fix.bat
A	k8s-oci/ghcr-push-rest.sh
A	k8s-oci/ghcr-push.bat
A	k8s-oci/ghcr-push.log
A	k8s-oci/ghcr-repush-spring.sh
A	k8s-oci/ghcr-repush.log
A	k8s-oci/oke-bootstrap.bat
A	k8s-oci/oke-down.bat
A	k8s-oci/oke-login.bat
A	k8s-oci/oke-up.bat
A	k8s-oci/policy-statements.json
A	k8s-oci/publish.bat
A	k8s-oci/show.them.all.bat
A	k8s-oci/values/activemq.yaml
A	k8s-oci/values/biztree.yaml
A	k8s-oci/values/enyman.yaml
A	k8s-oci/values/frontend.yaml
A	k8s-oci/values/gateway.yaml
A	k8s-oci/values/kcmaster.yaml
A	k8s-oci/values/keycloak.yaml
A	k8s-oci/values/keysmith.yaml
A	k8s-oci/values/pacman.yaml
A	k8s-oci/values/postgres.yaml
A	k8s/1-by-1/1.1) postgres install.bat
A	k8s/1-by-1/1.2)show.all.bat
A	k8s/1-by-1/1.3) postgres exec.bat
A	k8s/1-by-1/1.9) postgres uninstall.bat
A	k8s/1-by-1/10.1) frontend install.bat
A	k8s/1-by-1/10.3) frontend exec.bat
A	k8s/1-by-1/10.9) frontend uninstall.bat
A	k8s/1-by-1/2.1) aMQ install.bat
A	k8s/1-by-1/2.3) aMQ exec.bat
A	k8s/1-by-1/2.9) aMQ uninstall.bat
A	k8s/1-by-1/3.1) keycloak install.bat
A	k8s/1-by-1/3.3) keycloak exec.bat
A	k8s/1-by-1/3.9) keycloak uninstall.bat
A	k8s/1-by-1/4.1) biztree install.bat
A	k8s/1-by-1/4.3) biztree exec.bat
A	k8s/1-by-1/4.9) biztree uninstall.bat
A	k8s/1-by-1/5.1) enyman install.bat
A	k8s/1-by-1/5.3) enyman exec.bat
A	k8s/1-by-1/5.9) enyman uninstall.bat
A	k8s/1-by-1/6.1) pacman install.bat
A	k8s/1-by-1/6.3) pacman exec.bat
A	k8s/1-by-1/6.9) pacman uninstall.bat
A	k8s/1-by-1/7.1) keysmith install.bat
A	k8s/1-by-1/7.3) keysmith exec.bat
A	k8s/1-by-1/7.9) keysmith uninstall.bat
A	k8s/1-by-1/8.1) kcmaster install.bat
A	k8s/1-by-1/8.3) kcmaster exec.bat
A	k8s/1-by-1/8.9) kcmaster uninstall.bat
A	k8s/1-by-1/9.1) gateway install.bat
A	k8s/1-by-1/9.3) gateway exec.bat
A	k8s/1-by-1/9.9) gateway uninstall.bat
A	k8s/charts/esquire-biztree/Chart.yaml
A	k8s/charts/esquire-biztree/templates/configmap.yaml
A	k8s/charts/esquire-biztree/templates/deployment.yaml
A	k8s/charts/esquire-biztree/templates/secret.yaml
A	k8s/charts/esquire-biztree/templates/service.yaml
A	k8s/charts/esquire-biztree/values.yaml
A	k8s/charts/esquire-enyman/Chart.yaml
A	k8s/charts/esquire-enyman/templates/configmap.yaml
A	k8s/charts/esquire-enyman/templates/deployment.yaml
A	k8s/charts/esquire-enyman/templates/secret.yaml
A	k8s/charts/esquire-enyman/templates/service.yaml
A	k8s/charts/esquire-enyman/values.yaml
A	k8s/charts/esquire-frontend/Chart.yaml
A	k8s/charts/esquire-frontend/templates/configmap.yaml
A	k8s/charts/esquire-frontend/templates/deployment.yaml
A	k8s/charts/esquire-frontend/templates/service.yaml
A	k8s/charts/esquire-frontend/values.yaml
A	k8s/charts/esquire-gateway/Chart.yaml
A	k8s/charts/esquire-gateway/templates/configmap.yaml
A	k8s/charts/esquire-gateway/templates/deployment.yaml
A	k8s/charts/esquire-gateway/templates/service.yaml
A	k8s/charts/esquire-gateway/values.yaml
A	k8s/charts/esquire-kcmaster/Chart.yaml
A	k8s/charts/esquire-kcmaster/templates/configmap.yaml
A	k8s/charts/esquire-kcmaster/templates/deployment.yaml
A	k8s/charts/esquire-kcmaster/templates/secret.yaml
A	k8s/charts/esquire-kcmaster/templates/service.yaml
A	k8s/charts/esquire-kcmaster/values.yaml
A	k8s/charts/esquire-keysmith/Chart.yaml
A	k8s/charts/esquire-keysmith/templates/configmap.yaml
A	k8s/charts/esquire-keysmith/templates/deployment.yaml
A	k8s/charts/esquire-keysmith/templates/secret.yaml
A	k8s/charts/esquire-keysmith/templates/service.yaml
A	k8s/charts/esquire-keysmith/values.yaml
A	k8s/charts/esquire-pacman/Chart.yaml
A	k8s/charts/esquire-pacman/templates/configmap.yaml
A	k8s/charts/esquire-pacman/templates/deployment.yaml
A	k8s/charts/esquire-pacman/templates/secret.yaml
A	k8s/charts/esquire-pacman/templates/service.yaml
A	k8s/charts/esquire-pacman/values.yaml
A	k8s/charts/infra/activemq/Chart.yaml
A	k8s/charts/infra/activemq/templates/service.yaml
A	k8s/charts/infra/activemq/templates/statefulset.yaml
A	k8s/charts/infra/activemq/values.yaml
A	k8s/charts/infra/keycloak/Chart.yaml
A	k8s/charts/infra/keycloak/templates/secret.yaml
A	k8s/charts/infra/keycloak/templates/service.yaml
A	k8s/charts/infra/keycloak/templates/statefulset.yaml
A	k8s/charts/infra/keycloak/values.yaml
A	k8s/charts/infra/postgres/Chart.yaml
A	k8s/charts/infra/postgres/templates/secret.yaml
A	k8s/charts/infra/postgres/templates/service.yaml
A	k8s/charts/infra/postgres/templates/statefulset.yaml
A	k8s/charts/infra/postgres/values.yaml
A	k8s/k8s-down.bat
A	k8s/k8s-up.bat
A	k8s/show.them.all.bat
A	kcMaster/Dockerfile
A	kcMaster/Dockerfile.lx
A	kcMaster/Dockerfile.win
A	kcMaster/compose.yaml
A	kcMaster/pom.xml
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/KcMasterApplication.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/changes.txt
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/config/KeycloakConfig.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcMasterJmsConfig.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestConsumer.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandler.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisher.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/messaging/KcSyncRequest.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/IKcIdentityService.java
A	kcMaster/src/main/java/pro/mir0n/esquire/kcMaster/service/impl/KcIdentityService.java
A	kcMaster/src/main/resources/application.yml
A	kcMaster/src/main/resources/logback-spring.xml
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcRequestHandlerTest.java
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/messaging/KcResponsePublisherTest.java
A	kcMaster/src/test/java/pro/mir0n/esquire/kcMaster/service/KcIdentityServiceTest.java
A	kcMaster/src/test/resources/logback-test.xml
D	keySmith/.dockerignore
M	keySmith/Dockerfile
A	keySmith/Dockerfile.lx
A	keySmith/Dockerfile.win
M	keySmith/pom.xml
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/KeySmithApplication.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/changes.txt
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/controller/KeySmithController.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/exception/GlobalExceptionHandler.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/jpa/EsqAccessProfileRepository.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncPublisher.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KcSyncResponseListener.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/messaging/KeySmithJmsConfig.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/security/JwtAuthenticationFilter.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/security/JwtService.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/security/SecurityConfiguration.java
A	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/BizValidatorFactory.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/IKeySmithService.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/MdcFilter.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/PerformanceAspect.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/RequestContextUtils.java
D	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/RequestPerformance.java
M	keySmith/src/main/java/pro/mir0n/esquire/keySmith/service/impl/KeySmithService.java
M	keySmith/src/main/resources/META-INF/oracle-access-profile.xml
M	keySmith/src/main/resources/META-INF/postgres-access-profile.xml
M	keySmith/src/main/resources/application.yml
A	keySmith/src/main/resources/logback-spring.xml
A	keySmith/src/test/java/pro/mir0n/esquire/keySmith/controller/KeySmithControllerTest.java
A	keySmith/src/test/java/pro/mir0n/esquire/keySmith/service/KeySmithServiceTest.java
A	keySmith/src/test/resources/logback-test.xml
A	keycloak/Dockerfile.keycloak
M	keycloak/compose.yaml
M	keycloak/import/esquire.json
A	keycloak/themes/README-CUSTOMIZATION.md
A	keycloak/themes/README.md
A	keycloak/themes/esquire-explorer/login/error.ftl
A	keycloak/themes/esquire-explorer/login/info.ftl
A	keycloak/themes/esquire-explorer/login/login-config-totp.ftl
A	keycloak/themes/esquire-explorer/login/login-otp.ftl
A	keycloak/themes/esquire-explorer/login/login-page-expired.ftl
A	keycloak/themes/esquire-explorer/login/login-reset-password.ftl
A	keycloak/themes/esquire-explorer/login/login-update-password.ftl
A	keycloak/themes/esquire-explorer/login/login.ftl
A	keycloak/themes/esquire-explorer/login/logout-confirm.ftl
A	keycloak/themes/esquire-explorer/login/messages/messages_en.properties
A	keycloak/themes/esquire-explorer/login/resources/css/styles.css
A	keycloak/themes/esquire-explorer/login/resources/img/main.ico
A	keycloak/themes/esquire-explorer/login/resources/img/unknown.ico
A	keycloak/themes/esquire-explorer/login/template.ftl
A	keycloak/themes/esquire-explorer/login/theme.properties
D	pacMan/.dockerignore
A	pacMan/Dockerfile.lx
A	pacMan/Dockerfile.win
M	pacMan/pom.xml
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/PacManApplication.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/AcctOperation.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/IAcctTransactionProcessor.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/dto/AcctTransactionSingle.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionJpa.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/jpa/EsqAcctTransactionRepository.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorSingle.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransfer.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionService.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/changes.txt
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/controller/PacManController.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/exception/GlobalExceptionHandler.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/jpa/EsqAcctRepository.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisher.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/messaging/PacManJmsConfig.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/security/JwtAuthenticationFilter.java
A	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/BizValidatorFactory.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/IPacManService.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/PerformanceAspect.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/RequestContextUtils.java
D	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/RequestPerformance.java
M	pacMan/src/main/java/pro/mir0n/esquire/pacMan/service/impl/PacManService.java
A	pacMan/src/main/resources/META-INF/oracle-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/oracle-acct.xml
A	pacMan/src/main/resources/META-INF/postgres-acct-transaction.xml
M	pacMan/src/main/resources/META-INF/postgres-acct.xml
M	pacMan/src/main/resources/application.yml
A	pacMan/src/main/resources/logback-spring.xml
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionProcessorTransferTest.java
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/acct/service/AcctTransactionServiceTest.java
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/controller/PacManControllerTest.java
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/messaging/EsqEntityBroadcastPublisherTest.java
A	pacMan/src/test/java/pro/mir0n/esquire/pacMan/service/PacManServiceTest.java
A	pacMan/src/test/resources/logback-test.xml
M	pom.xml
A	postgres/Dockerfile
A	postgres/compose.yaml
A	postgres/initdb/init.sh
 459 files changed, 48844 insertions(+), 5731 deletions(-)
```

---

*From `v1.2.1` till `v1.2.2`*
