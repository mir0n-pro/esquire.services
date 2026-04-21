## bizTree H2-backed in-memory tree cache
### Goal
Implement the first step of persistence and synchronization: `bizTree`
- store `esq_tree` records in an **H2 database**
- keep H2 **memory-only**
- load all tree entities at application startup
- transform loaded entities into tree nodes
- keep the tree consistent via the event bus
- for this first step, handle **updates only** from received entity messages

### Important implementation details
#### 1) H2 table is outside `esquireDB`
- The tree table is **not part of `esquireDB`**.
- No foreign key relationships to entity tables are required.
- The tree persistence model should stay isolated from business entity persistence.

#### 2) Minimal tree ownership column
- Do **not** add these columns for this step:
    - `tree_acc_pk`
    - `tree_usr_pk`
    - `tree_org_pk`

- The only ownership/reference column needed here is:
    - `tree_entity_pk BIGINT`

#### 3) Initial data load routine
- Use the seed script located at:``` text
C:\MyProjects\esquire\db.seed\postgres\tree\breate_tree.sql
