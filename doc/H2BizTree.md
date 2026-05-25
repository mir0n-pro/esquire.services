| ![Alt text](../favicon.ico) | Esquire Frameworks(tm) 2.0 |
|----------------------------|---------------------------|


# bizTree -- H2-backed In-Memory Tree Cache

The storage layer behind the bizTree service: a single hierarchical tree of every business
entity, held in an embedded **H2 database, memory-only**, and queried with plain JDBC. This doc
covers the **data model and storage mechanics**; the cache's control architecture (the two-monad
Taijitu night-watch) lives in [Esquire.BizTree.md](Esquire.BizTree.md).


## Where it sits

- **Embedded + in-memory.** H2 runs inside the bizTree JVM (`jdbc:h2:mem:biztree`); nothing is
  persisted to disk. A restart rebuilds the tree from scratch.
- **Outside `esquireDB`.** The tree table is independent of the business-entity tables -- no
  foreign keys, no shared datasource. `esq2025` (Oracle / Postgres) remains the source of truth;
  the H2 tree is a derived, traversal-shaped projection of it.
- **One table per monad.** The table name is a `{table}` token. The Taijitu builds one table per
  monad (`ESQ_TREE_MONAD`, `ESQ_TREE_DANOM`) inside the single H2 instance, so the serving and
  shadow caches never collide; `ESQ_TREE` is the configurable base name (`biztree.cache.table`).


## The tree table

One row per tree node. Real entities, virtual folders, and account shortcuts all share the same
table, distinguished by which columns are set.

| Column | Type | Meaning |
|---|---|---|
| `TREE_PK` | VARCHAR(33), PK | Node id. Real entity = the entity id; virtual folder = `<parentPk>~<kind>`; account shortcut = its own pk. |
| `TREE_ET_PK` | INTEGER | Entity-type / kind code (e.g. org=20, client user=34, account=50; folder kinds 4 / 6 / 8 / 10). |
| `TREE_NAME` | VARCHAR(50) | Display name. |
| `TREE_DESC` | VARCHAR(1024) | Description. |
| `TREE_TREE_PK_PARENT` | VARCHAR(33) | Parent node's `TREE_PK` -- the tree edge. |
| `TREE_TREE_PK_LINK` | VARCHAR(33) | Set on an **account shortcut** node; links it back to the real account row. |
| `TREE_ENTITY_PK` | BIGINT | The business entity id. **NULL for virtual folder nodes.** |
| `TREE_LEVEL` | INTEGER | Depth from the root. |
| `TREE_PATH` | VARCHAR(2000) | Materialized path of `TREE_PK`s -- fast subtree queries via prefix `LIKE`. |
| `TREE_ENTITY_PATH` | VARCHAR(2000) | Materialized path of entity ids -- the rootPath-scoping axis for JWT-scoped reads. |
| `TREE_STATUS` | INTEGER | Status code (0 default; 1 / 2 derived from entity status). |

Indexes: `{table}_PARENT_I` on `TREE_TREE_PK_PARENT`, `{table}_ENTITY_PK_I` on `TREE_ENTITY_PK`;
primary key `{table}_PK` on `TREE_PK`. Index, constraint, and table names are all parameterized
by the `{table}` token so multiple monad tables coexist in one H2 instance without name clashes.

### Node kinds

- **Real-entity node** -- `TREE_ENTITY_PK` set; an org, user, or account.
- **Virtual folder node** -- `TREE_ENTITY_PK` NULL; the grouping folders under an entity
  ("All clients", "All accounts", "All admin-s", ...), with `TREE_PK = <parentPk>~<kind>`.
- **Account shortcut node** -- `TREE_TREE_PK_LINK` set; a second placement of an account under
  the owning org's accounts folder, linked back to the real account row.


## Loading and staying current

- **Startup load.** `BizTreeCacheLoader` bulk-reads the canonical entity tables (org / user /
  account repositories), inserts one node per entity plus the virtual folders and account
  shortcuts, then computes `TREE_LEVEL` / `TREE_PATH` in a second pass (`update-path`). There is
  no tree seed script -- the tree is derived from the live entity data on every load.
- **Live updates.** The cache stays current by consuming the entity-broadcast bus:
  CREATE / UPDATE / DELETE / MOVE events from enyMan and pacMan are applied directly to the table
  (insert / CASE-based update / delete / re-path). The original "first step: updates only" has
  long since grown to the full event set.
- **Reconciliation.** Under the Taijitu director a periodic night-watch reloads a shadow table
  from `esq2025`, checksums both legs, and self-heals any drift -- so an event missed while the
  service was down is recovered automatically. See [Esquire.BizTree.md](Esquire.BizTree.md).


## SQL

All cache SQL lives in `bizTree/src/main/resources/META-INF/h2-cache-sql.properties` -- one
template per query, carrying the `{table}` token, grouped as **DDL / Repository / Loader**
(a different embedded vendor would supply its own `*-cache-sql.properties`). At startup
`CacheSqlSet.forTable(templates, table)` substitutes the table name and pre-joins the read
fragments **once per monad**, so the hot path executes ready statements with no per-call string
assembly.
