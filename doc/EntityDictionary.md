# <img src="../favicon.ico" alt="Esquire logo" valign="middle" width="64" height="64"> Esquire Application Frameworks(tm) 2.0

# Esquire Entity Dictionary

The entity dictionary is where an entity says what it is: the fields it has, what each field means,
what a valid value looks like, who may change it, and how it should be shown. It is stated once, on
the server, and every tool that touches the entity reads it from there.

This is the third pillar of the framework -- **entity self-description**. There is no field layout
written into the browser code, no validation rule kept in two places, and no business meaning
interpreted on the client side.

---

## Contents

- [Entity dictionary vs database dictionary](#entity-dictionary-vs-database-dictionary) -- the two dictionaries, the four layers (DB / JPA / DTO / application), and where the entity dictionary sits
- [Where it lives and how it reaches the browser](#where-it-lives-and-how-it-reaches-the-browser)
- [Structure](#structure) -- dictionary, layers, fields
- [What a field can say](#what-a-field-can-say) -- every field element, and the field types
- [Standard fields and custom fields](#standard-fields-and-custom-fields)
- [Kinds -- the key of the dictionary](#kinds----the-key-of-the-dictionary)
- [Defaults -- filling a value that was not given](#defaults----filling-a-value-that-was-not-given)
- [What the browser does with it](#what-the-browser-does-with-it)
- [What ships is the demonstration, not the domain](#what-ships-is-the-demonstration-not-the-domain)
- [The dictionaries that ship](#the-dictionaries-that-ship)
- [Appendix -- the kind enumeration](#appendix----the-kind-enumeration) -- every kind code and what a definition carries

---

## Entity dictionary vs database dictionary

The two are easy to confuse by name, so state the difference plainly:

- **The database dictionary describes the database structure in database terms** -- tables, columns,
  data types, primary and foreign keys, indexes, constraints.
- **The entity dictionary describes the application's representation of the entity part of that
  structure** -- the same information as the application and the browser see it, under application
  names, grouped as entities rather than as tables.

They answer different questions:

| | **Entity dictionary** (this document) | **Database dictionary** ([DatabaseDictionary.md](DatabaseDictionary.md)) |
|---|---|---|
| Question it answers | What is this entity, as the business sees it? | Where is it stored, physically? |
| Unit | A **kind** -- organization, client, account, deposit | A **table** -- ESQ_ORG, ESQ_USER, ESQ_ACCOUNT |
| Item | A **field** -- `name`, `balance`, `status` | A **column** -- ORG_NAME, ACC_BALANCE, ACC_STATUS |
| Also carries | Label, tooltip, tab, order, allowed values, validation rule, editable or not, default | Data type, length, key, index, foreign key |
| Lives in | `esq-entity-dictionaries.xml` in the common library, plus custom fields in ESQ_PARAMETER | The schema scripts in `esquire.db.seed` |
| Read by | The services and the browser, at run time | The database, at deployment time |
| Changes when | The business meaning changes | The storage changes |

### Four layers, not two

A value travels through four shapes on its way from the disk to the screen, and each one is stated
in a different place:

| | Layer | What it is | Where it is stated |
|---|---|---|---|
| 1 | **Database** | Tables, columns, keys, indexes, constraints -- in database terms | The schema scripts in `esquire.db.seed` -- [DatabaseDictionary.md](DatabaseDictionary.md) |
| 2 | **JPA** | The rows as Java objects (`EsqOrgJpa`, `EsqUsrJpa`, `EsqAcctJpa`, `EsqPersonJpa`, `EsqAddressJpa`), filled by the named native queries whose `field-result` entries say which column feeds which field -- for example `<field-result name="connectFlg" column="au_connect_flg"/>` | `META-INF/<vendor>-entity.xml` in each service |
| 3 | **DTO** | What actually travels to the browser (`EsqOrg`, `EsqUsr`, `EsqAcct`, and the dictionary's own `EsqEntityLayer` / `EsqEntityField`), produced from the JPA objects by the mappers | `pro.mir0n.esquire.backend.dto` in the common library |
| 4 | **Application** | The screens -- the tree, the details dialog, the create dialog -- built at run time | The explorer and the shared UI library |

Layer 2 is where the database naming ends: from there on a field is `connectFlg`, not `AU_CONNECT_FLG`.
Layer 3 is where the shape stops following the tables and starts following the entity.

**The entity dictionary works at layer 3 -- the DTO level.** It is not a step in the chain: the chain
carries *values*, the dictionary describes them. What it describes is the DTO -- which fields a DTO
of this kind holds, and what is to be done with each of them on the screen:

| | The dictionary says | Stated by |
|---|---|---|
| **Represent** | Which tab the field sits on, in what order, under what label, with what hint | `layer`, `title`, `sort`, `label`, `tooltip` |
| **Interpolate** | What the user sees instead of the raw value: a stored code shown as its label (`O` as "Open") and sent back as the code, a number shown under its pattern, an empty value shown as words | `listvalues` (`code~Label`), `format` (`#,##0.00`), `nullmeaning` |
| **Validate** | What a value must satisfy before the change is accepted -- a pattern, a size, whether it may be empty at all | `validation`, `minmax`, `nullable` |
| **Edit** | Whether the field can be changed at all, and by whom -- read-only for others, editable on one's own record | `readwrite`, `personal` |
| **Fill in** | What value to use at creation when the request does not carry one | `default` -- see [Defaults](#defaults----filling-a-value-that-was-not-given) |
| **React** | Whether a change to this field must refresh the tree | `affects3` |

Layer 4 decides none of that. The dialog is a renderer: it draws the DTO the way the dictionary for
that kind says to draw it, checks it the way the dictionary says to check it, and sends back what the
dictionary says is sendable. Change the dictionary and the screen changes, with no change in the
browser.

The validation rules are not the browser's to keep: the server applies the same ones from the same
dictionary when the change arrives. The copy in the dialog only saves a round trip -- it is not the
guard.

The one place the dictionary and the chain meet is the custom-field merge: rows read through layer 2
are mapped into dictionary fields and joined to the ones declared in the XML.

### The dictionary also extends the database definition

The entity dictionary is not only a translation. It adds everything an entity needs that has no
place in a database structure:

- what the field is called on the screen, its hint text, which tab it belongs to and in what order;
- the expression a value must match, and the length limit;
- whether it can be edited, and whether a user may edit it on their own record;
- what to show when the value is empty;
- the list of allowed values together with the labels for them;
- the value to use at creation when the request does not carry one;
- whether changing it must refresh the tree.

None of that is storage, so none of it belongs in the schema -- and all of it is needed before an
entity can be shown or accepted.

For the same reason the two are not a one-to-one mapping. A single entity kind draws on several
tables (a client has a person record, two addresses and a set of custom parameters), and some
dictionary fields are not stored columns at all -- the account list on a client, or the permission
table on an access profile, are assembled for display.

---

## Where it lives and how it reaches the browser

1. **The file.** `common/src/main/resources/esq-entity-dictionaries.xml` -- one XML file shipped
   inside the common library, so every service carries the same copy.
2. **Loaded at start-up.** `EsqEntityDictionaryStorage` reads the file once and keeps the
   dictionaries in memory, keyed by kind.
3. **Completed on first use.** Custom fields are not in the XML -- they live in the ESQ_PARAMETER
   table. The first time a kind is asked for, enyMan reads that kind's custom fields and merges them
   into the in-memory dictionary, once, and marks it complete. Later calls use the merged copy.
4. **Served.** `GET /esq-dict?kind=<kind>` (enyMan) returns the kind's layers with all their fields.
5. **Used by the browser.** The explorer builds the dialog from the answer: tabs, fields, labels,
   read-only state, allowed values, validation.

The same completed dictionary is used by the write path -- create and save resolve a field's
editability through it -- so the browser and the server always work from one description.

---

## Structure

Three levels: **dictionary -> layers -> fields**.

```xml
<dictionary>
    <kind>50</kind>                     <!-- which entity kind this describes -->
    <layers>
        <layer>
            <layer>1</layer>            <!-- tab number -->
            <title>Generic</title>      <!-- tab title -->
            <fields>
                <field>
                    <name>ccy</name>            <!-- field key, as used in the request -->
                    <sort>2</sort>              <!-- position within the tab -->
                    <label>Currency</label>     <!-- what the user sees -->
                    <type>string</type>
                    <tooltip>Account denomination, 3 characters currency code</tooltip>
                    <listvalues>
                        <value>USD</value>
                        <value>EUR</value>
                    </listvalues>
                    <validation>^(USD|EUR)$</validation>
                    <nullable>N</nullable>
                    <default>USD</default>
                    <readwrite>3</readwrite>
                </field>
            </fields>
        </layer>
    </layers>
</dictionary>
```

- A **dictionary** describes exactly one kind.
- A **layer** is a tab in the dialog: a number, a title, and its fields. By convention layer `1` is
  the main tab and layer `99` is the free-text Description tab shown last.
- A **field** is one item on that tab.

---

## What a field can say

| Element | Meaning |
|---|---|
| `name` | The field key -- what the request and the response use |
| `label` | The text shown next to the field |
| `sort` | Position within the tab |
| `type` | How the value is handled and drawn (see below) |
| `tooltip` | Hover text |
| `readwrite` | `0` hidden, `1` read-only, `3` editable. The write path treats a field as writable only when this value has the "write" bit set |
| `nullable` | `N` = a value is required |
| `default` | Value used at creation when the request does not carry one -- see [Defaults](#defaults----filling-a-value-that-was-not-given) |
| `validation` | Regular expression the value must match; the same rule is applied on the server |
| `minmax` | Length or size limit |
| `listvalues` | The allowed values. Either bare (`USD`) or code and label together (`O~Open`) |
| `format` | For numbers, the display pattern (`#,##0.00`). For a `subentity` field, the kind of the nested dictionary |
| `nullmeaning` | What to show when the value is empty (`(n/a)`, `[inherited]`) |
| `personal` | `Y` = the field belongs to a person's own profile, so a user may edit it on their own record |
| `affects3` | `Y` = changing it changes how the entity appears in the tree, so the tree is refreshed after saving |

### Field types

| Type | What it is |
|---|---|
| `string`, `number`, `date`, `datetime`, `flag` | Plain single values |
| `text`, `tabstring` | Longer free text; `tabstring` fills its own tab |
| `listvalues` | A choice from a fixed list |
| `href`, `image` | A link and a picture |
| `subentity` | A nested entity drawn inside this one -- `format` names the nested kind (person 992, postal address 988, business address 990) |
| `tablist`, `tab-ikn-list` | A list filling a tab, such as a client's accounts |
| `tab-iknf-table` | A table filling a tab, such as the permission grid on an access profile |

---

## Standard fields and custom fields

An entity's fields come from two places, and the dictionary presents them as one list.

| | **Standard fields** | **Custom fields** |
|---|---|---|
| Defined in | `esq-entity-dictionaries.xml` | The ESQ_PARAMETER table |
| Changed by | A release of the common library | A row in the database, no release |
| Value stored in | The entity's own column | ESQ_USR_PAR / ESQ_ORG_PAR |
| Default | `<default>` element | The PAR_DEFAULT column |

ESQ_PARAMETER carries the same description a standard field carries -- label, type, tab, order,
tooltip, editability, default, whether it may be empty -- which is why the two kinds of field can be
merged into one dictionary and the browser cannot tell them apart.

---

## Kinds -- the key of the dictionary

Every entity, folder, sub-entity and operation in Esquire carries an integer **kind** code, and the
dictionary is keyed by it. Three points matter here:

- **One dictionary per real kind.** Link kinds (odd) have none of their own -- a link is a shortcut
  to a real entity, and the browser rounds the kind down to the even one before asking.
- **Sub-entities have their own dictionaries.** Person (992), postal address (988) and business
  address (990) are described exactly like top-level entities and are pulled in by a `subentity`
  field.
- **Operations have dictionaries too.** Deposit (1000), withdrawal (1002) and transfer (1004) are
  not stored entities -- their dictionaries describe the operation dialog: which inputs it takes,
  what is valid, what is pre-filled.

The kind decides much more than the dictionary -- routing, permissions, tree placement, path
semantics, and which commands are offered. The full code list, and everything a kind definition
carries, are in [Appendix -- the kind enumeration](#appendix----the-kind-enumeration) at the end of
this document.

---

## Defaults -- filling a value that was not given

A **default** is a value pre-filled for a required (non-nullable) field when the request does not
carry one at creation time. It applies to CREATE only; an update never fills anything in.

### Three places a default can live

- **Standard fields** -- the `<default>` element inside `<field>` in `esq-entity-dictionaries.xml`,
  loaded with the rest of the dictionary at start-up.
- **Custom fields** -- the `PAR_DEFAULT` column in ESQ_PARAMETER, read by the `findCustom` query.
- **The column itself** -- a `DEFAULT` on the database column, applied by the database when a row is
  inserted without a value. These are invisible to the dictionary and to the validator: for example
  `AU_CONNECT_FLG` and `AU_TFA_METHOD` default to `N`, and the `*_CREATED_TS` / `ATR_TS` timestamps
  are stamped by the database. See [DatabaseDictionary.md](DatabaseDictionary.md).

### How a standard-field default is injected

`EsqEntityLayer.injectDefaults(Map<String, Object> fields)` runs before each `applyFields()` call. It
puts a value into the fields map only when all three hold:

1. the field is required (`nullable` is `N`), and
2. the field has a default value, and
3. the field is not already in the map -- a value from the request always wins.

Validation still runs on the injected value: a default is not exempt from the field's own rules.

Each sub-entity has its own fields map, and `injectDefaults` is called per layer, so person defaults
land in the person map and address defaults in the address map. Account creation is the exception:
all layers are iterated into one flat map.

### How a custom-field default is injected

At the SQL level, not in Java. The `insertCustomOrg` / `insertCustomUsr` queries read the default
straight out of ESQ_PARAMETER:

```sql
INSERT INTO esq_org_par (..., opr_value, ...)
SELECT ..., par_default, ...
FROM esq_parameter
WHERE par_et_pk = :kind
```

Every custom parameter row is created carrying `par_default`. If the request did contain a value, the
Java loop validates it and issues an `UPDATE` over the top. Fields absent from the request simply
keep what was inserted -- no fallback in Java is needed.

### The defaults that ship

Declared in the dictionary (`<default>`):

| Field | Kind(s) | Default | Reason |
|---|---|---|---|
| `deleted` | client (34), merchant (36) | `N` | Entities are active on creation |
| `ccy` | account (50, 52, 54) | `USD` | Most common currency |
| `balance` | account (50, 52) | `0` | Zero balance on a new account |
| `status` | account (50, 52, 54) | `O` | Accounts open on creation |
| `negativeAllowed` | account (50, 52, 54) | `N` | Overdraft is off unless granted |

Operation dialogs carry defaults too -- these pre-fill a form rather than an entity row:

| Field | Kind(s) | Default | Reason |
|---|---|---|---|
| `typeId` | deposit (1000) / withdrawal (1002) / transfer (1004) | `1` / `2` / `3` | The activity type of that operation |
| `amount` | deposit, withdrawal, transfer | `0` | An empty amount to start from |
| `refCode` | deposit, withdrawal / transfer | `cc` / `other` | Most common reference code |
| `rate` | transfer (1004) | `1.00` | A same-currency transfer needs no conversion |

Applied by the database, not by the dictionary (`DEFAULT` on the column):

| Column | Default | Effect |
|---|---|---|
| `AU_CONNECT_FLG` | `N` | A new user cannot sign in until it is set |
| `AU_TFA_METHOD` | `N` | Two-factor authentication off |
| `ORG_` / `USR_` / `ACC_CREATED_TS` | now (UTC) | Entity creation time stamped by the database |
| `ATR_TS` | now (UTC) | Ledger row time stamped by the database |

### Fields with no default -- required from the caller

These have no `<default>` and must be supplied in the request; the validator rejects the creation
with `InvalidValueException` if they are missing:

| Field | Sub-entity |
|---|---|
| `firstName` | person |
| `lastName` | person |
| `email` | person |
| `loginId` | auth |
| `email` | auth |

### The rule for a new non-nullable field

- If a sensible system value exists -> give it a `<default>` in the dictionary.
- If the value must come from the caller -> leave the default out, and the validator will refuse a
  creation that omits it.

---

## What the browser does with it

The explorer holds no knowledge of any entity. Everything on the screen comes from the answer to
`/esq-dict`:

- **Tabs** -- one per layer, in layer order, titled by the layer title.
- **Field order** -- by `sort` within the tab.
- **Labels and hints** -- `label` and `tooltip`.
- **Editable or not** -- from `readwrite`, with `personal` allowing a user to edit their own profile
  fields even where the same field is read-only on somebody else's record.
- **Choices** -- `listvalues` becomes a drop-down; the `code~Label` form shows the label and sends
  the code.
- **Empty values** -- `nullmeaning` is shown instead of a blank.
- **Checking before sending** -- `validation`, `minmax` and `nullable` are applied in the dialog.
  The server applies the same rules again; the browser copy only saves a round trip.
- **Tree refresh** -- if a changed field is marked `affects3`, the tree is reloaded after the save,
  because the change is visible in the tree itself.

Adding a field to an entity is therefore a server-side change only. Nothing in the browser has to
know it happened.

---

## What ships is the demonstration, not the domain

The kinds below -- organization, client, merchant, account, deposit, withdrawal, transfer -- and the
fields inside them exist for one reason: to make the accounting demonstration work from end to end,
on a domain everybody already understands. They are **not** the framework.

The framework is the mechanism: kinds, dictionaries, layers, fields, allowed values, validation,
editability, defaults, and the tree they hang on. The content is replaceable.

On a real domain you define your own kinds and write their dictionaries -- cases and clients, assets
and portfolios, contracts and milestones, devices and sites -- and **that is where the domain's
business rules are stated**: which fields an entity has, which of them are required, what values are
legal, who may change what, what is shown where, and what must be filled in when nothing is given.
The kind numbering is laid out with gaps for exactly this (see the range table in
the range summary in the appendix) -- the ranges in use leave room between them for kinds
that are not ours to invent.

Nothing in the services or in the browser changes when the domain does. New kinds, new dictionaries;
the same tree, the same authorization, the same screens built the same way.

---

## The dictionaries that ship

| Kind | Entity | Tabs |
|---|---|---|
| 0 | System | Generic, Description |
| 20 | Organization | Generic, Description |
| 30 | SysAdmin | Generic, Profile, Description |
| 32 | Admin | Generic, Profile, Description |
| 34 | Client | Generic, Profile, Address, Biz Address, Accounts, Description |
| 36 | Merchant | Generic, Profile, Address, Biz Address, Accounts, Description |
| 50 | Client Account | Generic, Description |
| 52 | Merchant Account | Generic, Description |
| 54 | Paper Client Account | Generic, Description |
| 988 | Postal Address *(sub-entity)* | Postal Address |
| 990 | Business Address *(sub-entity)* | Biz Address |
| 992 | Primary Contact *(sub-entity)* | Details |
| 998 | Access Profile | Identity, Roles, Admin, Tools |
| 1000 | Deposit *(operation)* | Deposit |
| 1002 | Withdrawal *(operation)* | Withdrawal |
| 1004 | Transfer *(operation)* | Transfer |

---

## Appendix -- the kind enumeration

The complete kind codes, and what each definition states. Kinds are loaded at start-up and
used by every service and by the browser.

### What a kind definition carries

A kind is more than a number and a name. Each definition in `esq-object-kinds.xml` states:

| Element | What it decides |
|---|---|
| `id` | The code itself -- the key everything else is looked up by |
| `name`, `title`, `plural`, `desc` | The internal name and the wording shown to the user |
| `icon` | The picture used for the node in the tree |
| `org`, `usr`, `acct` | Which family the kind belongs to -- organization, user, account |
| `address` | Whether entities of this kind carry postal and business addresses |
| `detailed` | Whether the node has a details dialog |
| `childrenDetailed` | Whether its children have one |
| `treeFlags` | Behavior in the tree (see below) |
| `childKinds` | Which kinds may be created under it -- the tree's shape rules |
| `commands` | Which operations the context menu offers |
| `listHeaders` | The columns of the list view: which field feeds each column and what its heading reads |

Two of these are worth stopping at, because they carry business rules rather than decoration:
**`childKinds`** is what makes a tree legal or illegal -- an account cannot be created under an
organization because kind 20 does not list it -- and **`listHeaders`** is a second, smaller
description of the UI, alongside the entity dictionary: the dictionary describes the *form*, the
kind describes the *list*.

### Even / odd rule

The lowest bit of the kind code carries the real-versus-link distinction -- it is not a property a
kind happens to have, it is what the bit means:

- **Bit clear (even code)** -- the real entity, the canonical node.
- **Bit set (odd code)** -- a shortcut link to that real entity elsewhere in the tree;
  `link = real + 1`.

Every real entity is therefore even, and every link is the even code plus one. Example: kind `34` =
Client (real); kind `35` = Client link (a shortcut to a Client node).

**A link is how the same entity appears in more than one place.** The real entity is stored once and
lives at one home position in the tree. A link is a second tree node -- with the odd kind -- that
stands for that same entity somewhere else. A link node is a light node: it carries only the node's
own display properties (its name and description) and a **reference to the origin node**, together
with the id of the same underlying entity. It holds no entity data of its own. Act on it -- open it,
edit it -- and the kind is rounded down to the even one and the one real entity is worked on.

This is what lets the tree carry more than one relationship over the same entities. The home
position expresses one dimension -- ownership, say -- and a link expresses another, over the same
entity, with no second copy of the entity to keep in step.

You can see it in bizTree's tree cache. When it loads accounts, each account produces two nodes: the
home node under its owning user, and a link node -- kind `+1`, keyed by `org~account`, its parent the
org's **All Accounts** folder, its link column pointing back at the real account. That is how the
All Accounts folder is filled: one link node per account, each referring to an account that lives, as
its home, under a user.

Example -- an omnibus account. A client account's home is under its owner, the client. The same
account is also part of an omnibus account run by a managing broker for a hedge fund; a link node
under the omnibus structure places it there too. One stored account, two positions -- seen under its
client as the client's account, and under the broker as one holding inside the omnibus -- and both
refer to the same entity, so there is nothing to reconcile between them.

### Tree flags

Each kind carries a `treeFlags` string controlling its behavior in the tree:

| Flag | Meaning |
|---|---|
| `B` | Node appears in bizTree |
| `T` | Node has children expanded in the tree |
| `b` | Leaf node -- no children expanded |

### Available commands

Commands listed in the kind definition are offered in the tree context menu:

| Command | Action |
|---|---|
| `move` | Move entity to another parent |
| `delete` | Delete entity |
| `key` | View / edit access profile (keySmith) |
| `acct` | Accounting operations (deposit, withdrawal, transfer) |

### The kind codes

**System (0)**

| Kind | Name | Title | Flags | Child kinds | Notes |
|---|---|---|---|---|---|
| 0 | system | System | BT | 20 | Root node; `org=true` |

**System folder nodes (2-10).** Virtual folders -- not real entities, used for tree navigation only.

| Kind | Name | Title | Flags | Child kinds |
|---|---|---|---|---|
| 2 | sysadmins | Sys Admin-s | BTb | 30, 32 |
| 4 | alladmins | All Admin-s | BTb | 32 |
| 6 | allaccts | All accounts | BTb | -- |
| 8 | allclients | All clients | BTb | 34 |
| 10 | allmerchants | All merchants | BTb | 36 |

**Organization (20).** Self-referential -- an org can contain other orgs. `org=true`.

| Kind | Name | Title | Flags | Child kinds | Commands |
|---|---|---|---|---|---|
| 20 | org | Organization | BTb | 20 | move, delete |

**User entities (30-37).** `usr=true`. Path semantics: SysAdmin (30) and Admin (32) use the parent
org path only (`isPathParentOnly=true`); Client (34) and Merchant (36) carry their own pk in the path.

| Kind | Name | Title | Flags | Address | Child kinds | Commands |
|---|---|---|---|---|---|---|
| 30 | sysadmin | SysAdmin | b | -- | -- | move, key, delete |
| 31 | sysadminlnk | SysAdmin *(link)* | b | -- | -- | key |
| 32 | admin | Admin | b | -- | -- | move, key, delete |
| 33 | adminlnk | Admin *(link)* | b | -- | -- | key |
| 34 | client | Client | BTb | yes | 50, 54 | move, key, delete |
| 35 | clientlnk | Client *(link)* | b | yes | -- | key |
| 36 | merchant | Merchant | BTb | yes | 52 | move, key, delete |
| 37 | merchantlnk | Merchant *(link)* | b | yes | -- | key |

**Account entities (50-55).** `acct=true`. Accounts are children of user entities. A **paper
account** (54) is a demonstration account -- it holds no real money.

| Kind | Name | Title | Flags | Commands |
|---|---|---|---|---|
| 50 | cacct | Client Account | b | acct, delete |
| 51 | cacctlnk | Client Account *(link)* | b | acct |
| 52 | macct | Merchant Account | b | acct, delete |
| 53 | macctlnk | Merchant Account *(link)* | b | acct |
| 54 | pacct | Paper Client Account | b | acct, delete |
| 55 | pacctlnk | Paper Client Account *(link)* | b | acct |

**Parameter audit routing kinds (970-978).** Not tree nodes, not in the XML -- synthetic kinds
defined in `EsqConstants`, used only to route a custom-parameter change to its `*_par_log` table in
the x-rod audit feed. The event's `entity_id` is the owning entity (`usr_pk` / `org_pk`), `sub_id` is
the parameter name, and the parameter row's own `par_et_pk` rides in the event body.

| Kind | Constant | Sub-asset | x-rod target |
|---|---|---|---|
| 970 | `KIND_USR_PAR` | User custom parameter | `esq_usr_par_log` |
| 972 | `KIND_ORG_PAR` | Org custom parameter | `esq_org_par_log` |
| 974-978 | -- | -- | Reserved |

**Role / permission kinds (980-986).** Not tree nodes. Keys for the permission dictionaries served
by keySmith.

| Kind | Name | Title | Status |
|---|---|---|---|
| 980 | admin | Admin permissions | Active (`KIND_ADMIN_ROLE`) |
| 982 | tools | Tool permissions | Active |
| 984 | -- | Application permissions | Reserved |
| 986 | -- | Report permissions | Reserved |

**Sub-entity kinds (988-998).** Not tree nodes. Dictionary keys for the sub-entity forms drawn
inside a detail dialog.

| Kind | Name | Title | Constant |
|---|---|---|---|
| 988 | addr | Postal address | `KIND_ADDRESS_POSTAL` |
| 990 | bizaddr | Biz address | `KIND_ADDRESS_BIZ` |
| 992 | person | Primary contact | `KIND_PERSON_PRIMARY` |
| 994 | sperson | Secondary contact | `KIND_PERSON_SECONDARY` |
| 996 | jperson | Joint contact | `KIND_PERSON_JOINT` |
| 998 | aprofile | Access Profile | `KIND_ACCESS_PROFILE` |

**Accounting operation kinds (1000-1004).** Dictionary keys for the accounting command dialogs. Not
in the XML -- defined in the frontend, referenced by `AcctOperation` constants, served by pacMan.

| Kind | Name | Title |
|---|---|---|
| 1000 | deposit | Deposit |
| 1002 | withdrawal | Withdrawal |
| 1004 | transfer | Transfer |

**Special / sentinel values**

| Kind | Meaning |
|---|---|
| -1 | Unknown / not a kind (`EsqObjectKindStorage.UNKNOWN`) |
| 999 | "More..." UI hint *(defined but not active)* |

### Range summary

Real entity or not follows the bit rule above; the ranges that hold entities also hold their links
on the odd codes.

| Range | Holds | In tree |
|---|---|---|
| 0 | The system root -- a real entity (`esq_org` row `org_pk=1`) | Yes |
| 2-10 | System folders -- navigation only, not entities | Yes |
| 20 | Organization -- a real entity | Yes |
| 30-37 | User entities (even) and their links (odd) | Yes |
| 50-55 | Account entities (even) and their links (odd) | Yes |
| 970-978 | Parameter audit routing keys -- not entities | No |
| 980-986 | Role / permission keys -- not entities | No |
| 988-998 | Sub-entity keys -- not tree nodes | No |
| 1000-1004 | Accounting operation keys -- not entities | No |
| -1 | Unknown sentinel | -- |

The gaps between the ranges are deliberate: they leave room for the kinds a real domain will need.

**Where the kinds are stated:** definitions in `common/src/main/resources/esq-object-kinds.xml`,
constants in `common/.../EsqConstants.java`, loaded by `common/.../storage/EsqObjectKindStorage.java`.


---

## Related documents

- [DatabaseDictionary.md](DatabaseDictionary.md) -- where the values are stored.
- [Esquire.Auth.md](Esquire.Auth.md) -- how an entity is placed in the tree (its `ep_path` / visibility root).
