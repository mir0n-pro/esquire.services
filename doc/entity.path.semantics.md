# Entity Path Semantics (`esq_entity_path.ep_path`)

## Overview

`esq_entity_path` stores one row per entity. The `ep_path` column holds a dot-separated
hierarchical path string. Its meaning depends on the entity type and kind.

The path is the **visibility root** for the entity: it determines the subtree of the
org→user→account hierarchy that the entity can see or belongs to.

---

## Path Rules by Entity Type

| Entity type | `ep_path` value | Example |
|-------------|-----------------|---------|
| **ORG** | Org's own full path — `parentOrgPath + orgPk + "."` | `1.9.200.` |
| **USR — admin (kind 30, 32)** | Parent org's path only — no user PK appended | `1.9.200.` |
| **USR — regular (kind 34, 36)** | User's own full path — `parentOrgPath + usrPk + "."` | `1.9.200.100.` |
| **ACCT** | Owner user's `ep_path` — no account PK appended | `1.9.200.100.` |

### Rationale

- **ORG**: navigated hierarchically; path includes own PK so children can be scoped via `LIKE '1.9.200.%'`.
- **Admin users (SYS_ADMIN/ADMIN, kind 30/32)**: visibility root = their org. They see everything
  under their org but do not form a subtree of their own. Path = org path (no user PK).
- **Regular users (CLIENT/MERCHANT, kind 34/36)**: visibility root = their own node. They own
  accounts underneath them. Path = org path + own PK.
- **ACCT**: accounts belong to a user; their path equals the owner user's `ep_path`. No ACCT PK
  is appended; the ACCT is always scoped via its owner.

---

## `EsqObjectKind.isPathParentOnly()`

A helper method on `EsqObjectKind` encodes this rule:

```java
public boolean isPathParentOnly() {
    return id == 30 || id == 32;   // SYS_ADMIN and ADMIN
}
```

Returns `true` when the entity's `ep_path` = its parent's `ep_path` (no own PK appended).

Usage when **forming** a path:
```java
String path = eek.isPathParentOnly() ? parentPath : parentPath + newId + ".";
```

Usage when **reading** orgPk from a user's path (e.g. in bizTree cache move):
```java
// Admin   ep_path "1.9.200."     → last segment    = 200 (org pk)
// Regular ep_path "1.9.200.100." → second-to-last  = 200 (org pk)
long orgPk = eek.isPathParentOnly()
        ? Long.parseLong(segs[segs.length - 1])
        : Long.parseLong(segs[segs.length - 2]);
```

---

## ACCT Path Formation

ACCT path is never computed from kind metadata — it is always fetched from the DB:

```sql
-- EsqAcctJpa.acctPath
SELECT ep_path FROM esq_entity_path WHERE ep_pk = CAST(:parentId AS bigint)
```

Where `:parentId` = owner user's PK. The returned value (owner user's `ep_path`) is stored
directly as the new ACCT's `ep_path`.

Because only CLIENT (34) and MERCHANT (36) users own accounts — and these are regular users
whose `ep_path` includes their own PK — the ACCT path always ends with `usrPk + "."`.

---

## Move Semantics

### Admin user move

An admin's `ep_path` = parent org path. Multiple admins under the same org share the same
`ep_path` value. Updating by `ep_path` equality would affect all of them.

**Admin move must update by `ep_pk` (not by ep_path equality):**

```sql
UPDATE esq_entity_path SET ep_path = :newPath WHERE ep_pk = CAST(:id AS bigint)
```

`newPath` = destination org's `ep_path` (org's own full path, no user PK appended).

Admins have no child accounts, so no cascade update is needed.

### Regular user move

A regular user's `ep_path` is unique (ends with own PK). Their ACCT rows share the same
`ep_path` value. Updating by `ep_path` equality covers both the user and all their accounts:

```sql
UPDATE esq_entity_path SET ep_path = :newPath WHERE ep_path = :oldPath
```

`newPath` = dest org path + user PK + `"."`.

---

## KC `esq_rootpath` Attribute

The KeyCloak `esq_rootpath` attribute holds the user's visibility root — exactly `ep_path`:

- Admin: `esq_rootpath` = org path (e.g. `1.9.200.`)
- Regular user: `esq_rootpath` = own path (e.g. `1.9.200.100.`)

When a user is moved, enyMan sends a `KC | URQ | X` message to kcMaster with
`"path": record.getPath()`. kcMaster calls `updateEntityPath()` which stores this value
directly as `esq_rootpath`. No transformation needed.

---

## Where These Rules Are Enforced

| Location | What |
|----------|------|
| `enyMan / UsrService.createUsr()` | Path computed via `isPathParentOnly()` |
| `enyMan / UsrService.moveUsr()` | Admin: `moveAdminPath` by pk; regular: `moveUsrPaths` by equality |
| `enyMan / EnyManService.publishKcMoveRequest()` | Sends `record.getPath()` directly to KC (no stripping) |
| `bizTree / BizTreeCacheRepository.moveUsrNode()` | OrgPk extracted via `isPathParentOnly()` |
| `pacMan / PacManService.createAcct()` | Path fetched from parent user's `ep_path` (no kind logic needed) |
