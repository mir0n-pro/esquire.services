# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Object Kind Enumeration

Every entity, folder, sub-entity, and operation in Esquire is identified by an integer kind code.
The kind code drives routing, permissions, UI rendering, tree placement, and command availability.

---

## Even / Odd Rule

- **Even kind** — the real entity (canonical node)
- **Odd kind** — a shortcut link to a real entity elsewhere in the tree; `kind = real_kind + 1`

Example: kind `34` = Client (real); kind `35` = Client link (shortcut to a Client node).

---

## Tree Flags

Each kind carries a `treeFlags` string controlling its tree behavior:

| Flag | Meaning |
|---|---|
| `B` | Node appears in bizTree |
| `T` | Node has children expanded in the tree |
| `b` | Leaf node — no children expanded |

---

## Available Commands

Commands listed in the kind definition are offered in the UI context menu:

| Command | Action |
|---|---|
| `move` | Move entity to another parent |
| `delete` | Delete entity |
| `key` | View / edit access profile (keySmith) |
| `acct` | Accounting operations (deposit, withdrawal, transfer) |

---

## Kind Table

### System (0)

| Kind | Name | Title | Flags | Child Kinds | Notes |
|---|---|---|---|---|---|
| 0 | system | System | BT | 20 | Root node; `org=true` |

### System Folder Nodes (2–10)

Virtual folders — not real entities, used for tree navigation only.

| Kind | Name | Title | Flags | Child Kinds |
|---|---|---|---|---|
| 2 | sysadmins | Sys Admin-s | BTb | 30, 32 |
| 4 | alladmins | All Admin-s | BTb | 32 |
| 6 | allaccts | All Accounts | BTb | — |
| 8 | allclients | All Clients | BTb | 34 |
| 10 | allmerchants | All Merchants | BTb | 36 |

### Organization (20)

| Kind | Name | Title | Flags | Child Kinds | Commands |
|---|---|---|---|---|---|
| 20 | org | Organization | BTb | 20 | move, delete |

Organizations are self-referential — an org can contain other orgs. `org=true`.

### User Entities (30–37)

`usr=true`. Path semantics: SysAdmin (30) and Admin (32) use parent org path only (`isPathParentOnly=true`).
Client (34) and Merchant (36) carry their own pk in path.

| Kind | Name | Title | Flags | Address | Child Kinds | Commands |
|---|---|---|---|---|---|---|
| 30 | sysadmin | SysAdmin | b | — | — | move, key, delete |
| 31 | sysadminlnk | SysAdmin *(link)* | b | — | — | key |
| 32 | admin | Admin | b | — | — | move, key, delete |
| 33 | adminlnk | Admin *(link)* | b | — | — | key |
| 34 | client | Client | BTb | yes | 50, 54 | move, key, delete |
| 35 | clientlnk | Client *(link)* | b | yes | — | key |
| 36 | merchant | Merchant | BTb | yes | 52 | move, key, delete |
| 37 | merchantlnk | Merchant *(link)* | b | yes | — | key |

### Account Entities (50–55)

`acct=true`. Accounts are children of user entities.

| Kind | Name | Title | Flags | Commands |
|---|---|---|---|---|
| 50 | cacct | Client Account | b | acct, delete |
| 51 | cacctlnk | Client Account *(link)* | b | acct |
| 52 | macct | Merchant Account | b | acct, delete |
| 53 | macctlnk | Merchant Account *(link)* | b | acct |
| 54 | pacct | Paper Client Account | b | acct, delete |
| 55 | pacctlnk | Paper Client Account *(link)* | b | acct |

### Parameter Audit Routing Kinds (970–978)

Not tree nodes, not in the XML — synthetic kinds defined in `EsqConstants`, used only to **route a
custom-parameter change to its `*_par_log` table** in the x-rod audit feed. They are not real entity
kinds: the event's `entity_id` is the owning entity (`usr_pk` / `org_pk`), `sub_id` is the parameter
name, and the parameter row's own `par_et_pk` rides in the event body.

| Kind | Constant | Sub-asset | x-rod target |
|---|---|---|---|
| 970 | `KIND_USR_PAR` | User custom parameter | `esq_usr_par_log` |
| 972 | `KIND_ORG_PAR` | Org custom parameter | `esq_org_par_log` |
| 974–978 | — | — | Reserved |

### Role / Permission Kinds (980–986)

Not tree nodes. Used as keys for permission dictionaries served by keySmith.

| Kind | Name | Title | Status |
|---|---|---|---|
| 980 | admin | Admin permissions | Active (`KIND_ADMIN_ROLE`) |
| 982 | tools | Tool permissions | Active |
| 984 | — | Application permissions | Reserved |
| 986 | — | Report permissions | Reserved |

### Sub-entity Kinds (988–998)

Not tree nodes. Used as dictionary keys for sub-entity forms rendered in detail dialogs.

| Kind | Name | Title | Constant |
|---|---|---|---|
| 988 | addr | Postal Address | `KIND_ADDRESS_POSTAL` |
| 990 | bizaddr | Business Address | `KIND_ADDRESS_BIZ` |
| 992 | person | Primary Contact | `KIND_PERSON_PRIMARY` |
| 994 | sperson | Secondary Contact | `KIND_PERSON_SECONDARY` |
| 996 | jperson | Joint Contact | `KIND_PERSON_JOINT` |
| 998 | aprofile | Access Profile | `KIND_ACCESS_PROFILE` |

### Special / Sentinel Values

| Kind | Meaning |
|---|---|
| -1 | Unknown / not a kind (`EsqObjectKindStorage.UNKNOWN`) |
| 999 | "More…" UI hint *(defined but not active)* |

### Accounting Operation Kinds (1000–1004)

Used as dictionary keys for accounting command dialogs. Not in the XML — defined in the frontend
and referenced by `AcctOperation` constants. Served as dictionary entries by pacMan.

| Kind | Name | Title |
|---|---|---|
| 1000 | deposit | Deposit |
| 1002 | withdrawal | Withdrawal |
| 1004 | transfer | Transfer |

---

## Kind Classification Summary

| Range | Category | In Tree | Real Entity |
|---|---|---|---|
| 0 | System root | Yes | — |
| 2–10 | System folders | Yes | No |
| 20 | Organization | Yes | Yes |
| 30–37 | User entities + links | Yes | Even only |
| 50–55 | Account entities + links | Yes | Even only |
| 970–978 | Parameter audit routing keys | No | No |
| 980–986 | Role / permission keys | No | No |
| 988–998 | Sub-entity keys | No | No |
| 1000–1004 | Accounting operation keys | No | No |
| -1 | Unknown sentinel | — | — |

---

## Source

Kind definitions: `common/src/main/resources/esq-object-kinds.xml`  
Kind constants: `common/.../EsqConstants.java`  
Kind storage: `common/.../storage/EsqObjectKindStorage.java`

---

*Esquire Frameworks(tm) 2.0 — mir0n&co — mir0n.the.programmer@gmail.com*
