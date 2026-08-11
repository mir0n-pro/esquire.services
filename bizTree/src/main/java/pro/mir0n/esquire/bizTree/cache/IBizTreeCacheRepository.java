/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  created: vendor-agnostic interface for in-memory tree cache
 *                   updateNode(entityPk, name, desc, statusCode); SKIP sentinel for desc; entity_pk unique
 * 03/26/2026 mir0n  insertOrgNodes/insertUsrNode/insertAcctNode added for CREATE event support;
 *                   deleteNodes(entityPk) added for DELETE event support
 * 03/28/2026 mir0n  deleteNodes(String entityId): WHERE ? IN (tree_pk, tree_tree_pk_link, tree_tree_pk_parent);
 *                   covers USR (tree_pk), ACCT main+shortcut (tree_pk+tree_tree_pk_link), ORG folder nodes (tree_tree_pk_parent)
 * 04/02/2026 mir0n  added moveOrgNode()
 *                   added moveUsrNode();
 *                   added moveAcctNode();
 * 04/07/2026 mir0n  moveUsrNode(): param renamed kind (raw kind; storage handles via getOrDefault)
 * 05/14/2026 mir0n  findSubtree(seedId, rootLevel, rootPath) added: recursive subtree by tree_path
 *                   prefix for the new /esq-tree endpoint (counterpart to enyMan /esq-cmd-tree)
 * 05/23/2026 mir0n  clear() (TRUNCATE the cache table) + prepareCancelable(command) returning a
 *                   CancelableStatement (statement + connection) for the night-watch cancelable CHECKSUM.
 * 08/11/2026 mir0n  v1.2.12 -- findChangeNumbers() returns the node's [entityChangeNo, pathChangeNo] (null
 *                   when the entity is not cached); stampEntityChangeNo() and stampPathChangeNo() write an
 *                   applied number onto every row of the entity
 */
package pro.mir0n.esquire.bizTree.cache;

import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;

import java.util.List;

/**
 * Vendor-agnostic interface for the in-memory tree cache.
 *
 * Mirrors the EsqTreeNodeRepository contract so BizTreeService can switch
 * between any embedded cache implementation without modification.
 *
 * Implementations: BizTreeH2Repository (H2 in-memory database).
 */
public interface IBizTreeCacheRepository {

    List<EsqTreeNodeJpa> findRoot(String rootId, int rootLevel, String rootPath);

    List<EsqTreeNodeJpa> findNodes(String id, int rootLevel, String rootPath);

    String findPath(String id);

    EsqTreeNodeJpa findByEntityId(String id, int rootLevel, String rootPath);

    EsqTreeNodeJpa findByNameKind(String name, Integer kind, int rootLevel, String rootPath);

    /**
     * Sentinel: pass as desc to mean "field absent — do not update this column".
     * Null desc means "present but cleared (set to SQL NULL)".
     * Not used for name (null name = skip) or statusCode (null = skip).
     */
    String SKIP = "\u0000";

    /**
     * Applies a name/desc/status update from an entity broadcast event to all matching cache nodes.
     *   name:       null = absent/skip (name is required, never cleared)
     *   desc:       SKIP = absent/skip; null = explicitly cleared
     *   statusCode: null = absent/skip; 0=ok, 1=deleted/closed, 2=locked
     */
    void updateNode(long entityPk, String name, String desc, Integer statusCode);

    /**
     * The node's two change numbers, for the freshness guard: {@code [entityChangeNo, pathChangeNo]},
     * either element null when unknown. Null return = the entity is not in the cache at all (a CREATE
     * that has not landed yet, or a node outside this cache) -- the caller then applies unguarded.
     *
     * <p>TWO numbers because a node is fed by two independent counters: the entity row (C/U/D events)
     * and the path row (X events). They are separate per-entity counters and are never comparable with
     * each other -- guarding a path event against the entity number would drop legitimate move updates
     * and leave a moved subtree half-repathed.
     */
    Long[] findChangeNumbers(long entityPk);

    /** Stamp an applied ENTITY event's number onto every row of the entity. */
    void stampEntityChangeNo(long entityPk, Long changeNo);

    /** Stamp an applied PATH event's number onto every row of the entity. */
    void stampPathChangeNo(long entityPk, Long changeNo);

    /**
     * Inserts a new org entity node and its folder nodes into the cache.
     * Mirrors the pattern used by BizTreeCacheLoader.buildOrgRows().
     * Looks up the parent's entity_path and tree_path from the cache to compute
     * the new node's entityPath, tree path, and level.
     *
     * @param orgPk      the new org's primary key
     * @param etPk       entity type (0 = root, 20 = org)
     * @param name       display name
     * @param desc       description (may be null)
     * @param parentPk   the parent org's pk string
     * @param entityPath the new org's entity path (org_path from the DB, e.g. "1.12345.67890.")
     */
    void insertOrgNodes(long orgPk, int etPk, String name, String desc, String parentPk, String entityPath);

    /**
     * Inserts a new user entity node into the cache under the correct folder.
     * Folder selection mirrors BizTreeCacheLoader.buildUserRows():
     *   orgPk == 1  → folder type 2 (sys admins)
     *   etPk  == 34 → folder type 8 (clients)
     *   etPk  == 36 → folder type 10 (merchants)
     *   else        → folder type 4 (admins)
     *
     * @param usrPk      the new user's primary key
     * @param etPk       entity type (30/32/34/36)
     * @param name       display name
     * @param desc       description (may be null)
     * @param orgPk      the parent org's primary key
     * @param entityPath the new user's entity path (usr_path from the DB)
     * @param statusCode the node status (0=ok, 1=deleted, 2=locked)
     */
    void insertUsrNode(long usrPk, int etPk, String name, String desc, long orgPk, String entityPath, int statusCode);

    /**
     * Inserts two cache nodes for a new account: main node under user and shortcut under org's FOLDER_ACCOUNT.
     * Mirrors BizTreeCacheLoader.buildAccountRows().
     *   Main node:     tree_pk = acctPk,         parent = usrPk,             etPk = etPk,     linkPk = null
     *   Shortcut node: tree_pk = orgPk+"~"+acctPk, parent = orgPk+"~"+FOLDER_ACCOUNT, etPk = etPk+1, linkPk = acctPk
     * orgPk is derived from the user's tree path (second-to-last dot-segment contains orgPk~folderType).
     *
     * @param acctPk     new account primary key
     * @param etPk       entity kind
     * @param name       display name (acc_id)
     * @param desc       description (may be null)
     * @param usrPk      parent user's primary key
     * @param entityPath user/account entityPath (same value — acc_path = usr_path)
     * @param statusCode 0=ok, 1=deleted/closed, 2=locked
     */
    void insertAcctNode(long acctPk, int etPk, String name, String desc, long usrPk, String entityPath, int statusCode);

    /**
     * Removes all cache nodes associated with the given entity id string.
     * Uses a single WHERE clause: id IN (tree_pk, tree_tree_pk_link, tree_tree_pk_parent).
     *   USR:  matches the user node by tree_pk.
     *   ACCT: matches the main node by tree_pk and the shortcut node by tree_tree_pk_link.
     *   ORG:  matches the org node by tree_pk and all folder nodes by tree_tree_pk_parent.
     */
    void deleteNodes(String entityId);

    /**
     * Moves an org node and all its folder nodes to the new entity path.
     */
    void moveOrgNode(long orgPk, String newEntityPath);

    /**
     * Moves a user node to the new entity path, re-parenting under the correct folder.
     *
     * @param kind entity kind (used for folder routing)
     */
    void moveUsrNode(long usrPk, int kind, String newEntityPath);

    /**
     * Moves an account main node and its shortcut node to the new entity path.
     */
    void moveAcctNode(long acctPk, String newEntityPath);

    /**
     * Returns every cache row whose tree_path is rooted at the seed: the seed
     * node itself, all real-entity descendants, all virtual folder nodes, and
     * all account shortcut nodes. Used by the CompareTrees diff scenario to
     * see the cache's full picture of what lives under a given subtree.
     */
    List<EsqTreeNodeJpa> findSubtree(String seedId, int rootLevel, String rootPath);

    /**
     * Wipes this cache table entirely (TRUNCATE). Used by the night-watch CLEAR so a shadow reload
     * starts from an empty table (no PK collision with rows from a prior sweep).
     */
    void clear();

    /**
     * Prepare the cancelable JDBC statement for the named command (currently CHECKSUM -- the
     * order-independent MD5 digest over the whole table). The night-watch registers the statement's
     * {@code cancel()} as the command's ICancelable, runs {@code executeQuery()}, and reads the result;
     * a sweep timeout calls {@code cancel()} to abort the in-flight query. Returns a
     * {@link CancelableStatement} (statement + its connection) so the caller closes BOTH via
     * try-with-resources on every path.
     */
    CancelableStatement prepareCancelable(String command);
}
