# Default Field Rule

## What `default` is

A value pre-filled for required (non-nullable) fields when absent from the request at entity creation time.

## Where it lives

- **Static fields** — `<default>` element inside `<field>` in `esq-entity-dictionaries.xml` (common library), loaded into `EsqEntityDictionaryStorage` at startup.
- **Custom fields** — `PAR_DEFAULT` column in `ESQ_PARAMETER` table, returned by the `EsqCustomEntityFieldJpa.findCustom` named native query.

## When it applies

CREATE operations only. Injection mechanism differs by field type:

### Static fields (`esq-entity-dictionaries.xml`)

`EsqEntityLayer.injectDefaults(Map<String, Object> fields)` is called before each `applyFields()` invocation.

`injectDefaults` puts a default value into the fields map only when:
1. The field's `nullable` is `"N"` (required), and
2. The field has a non-null `defaultValue`, and
3. The field name is not already present in the fields map (request value wins).

Validation (`ValidatorFactory`) still runs on the injected default value — defaults are not exempt.

Each sub-entity has its own fields map. `injectDefaults` is called per-layer, matched to the layer of each sub-entity, so person defaults go into the person map, address defaults into the address map, etc.

For account creation (`PacManService`), all layers of the dictionary are iterated and defaults are injected into the single flat fields map.

### Custom fields (`ESQ_PARAMETER` / `*_PAR` tables)

Default injection happens at the SQL level. The `insertCustomOrg` / `insertCustomUsr` queries read directly from `ESQ_PARAMETER`:

```sql
INSERT INTO esq_org_par (..., opr_value, ...)
SELECT ..., par_default, ...
FROM esq_parameter
WHERE par_et_pk = :kind
```

`par_default` is used as the initial value for every custom parameter row on creation. If the request contains a value for a custom field, the Java loop validates it and issues a subsequent `UPDATE` to override the default. Fields absent from the request keep the `par_default` value as inserted — no Java-level fallback is needed.

## Fields with defaults

| Field       | Kind(s)                   | Default | Reason                                    |
|-------------|---------------------------|---------|-------------------------------------------|
| `deleted`   | all entity kinds          | `N`     | Entities are active on creation           |
| `ccy`       | account (50, 52, 54)      | `USD`   | Most common currency                      |
| `balance`   | account (50, 52)          | `0`     | Zero balance on new account               |
| `status`    | account (50, 52, 54)      | `O`     | Accounts open on creation                 |
| `connectFlg`| auth                      | `N`     | Not connected to external IdP by default  |
| `tfaMethod` | auth                      | `N`     | Two-factor auth off by default            |

## Fields without defaults — explicitly required

The following fields have no `<default>` defined and must be supplied in the request. The validator throws `InvalidValueException` if they are absent:

| Field         | Sub-entity |
|---------------|------------|
| `firstName`   | person     |
| `lastName`    | person     |
| `email`       | person     |
| `loginId`     | auth       |
| `email`       | auth       |

## Rule for new non-nullable fields

- If a sensible system default exists → define `<default>` in the dictionary.
- If the value must come from the user (no safe fallback) → leave `<default>` absent; the validator will reject creation if it is missing.
