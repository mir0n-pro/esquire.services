/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  created: SQL holder records (Ddl, Repo, Loader) for vendor-agnostic cache
 * 03/26/2026 mir0n  deleteNode field added to Repo record
 * 04/02/2026 mir0n  added 3 Repo queries:  moveNode, moveAcctLink, findFolderPks
 * 05/14/2026 mir0n  selectSubtree field added to Repo record (for fetchSubtree / /esq-tree endpoint)
 * 05/23/2026 mir0n  Repo record: added clearAll + checksum fields (night-watch TRUNCATE + MD5 digest).
 */
package pro.mir0n.esquire.bizTree.cache;

/**
 * All SQL for the in-memory tree cache, grouped by concern.
 * Populated from the active vendor's properties file (e.g. h2-cache-sql.properties).
 */
public class BizTreeCacheSql {

    public record Ddl(
            String createTable,
            String createIndexParent,
            String createIndexEntityPk
    ) {}

    public record Repo(
            String selectCols,
            String selectOne,
            String findRoot,
            String findNodes,
            String findPath,
            String findByEntityId,
            String findByNameKind,
            String updateNode,
            String deleteNode,
            String moveNode,
            String moveAcctLink,
            String findFolderPks,
            String findSubtree,
            String clearAll,
            String checksum
    ) {}

    public record Loader(
            String insertNode,
            String updatePath,
            String selectPaths
    ) {}

    public final Ddl    ddl;
    public final Repo   repo;
    public final Loader loader;

    public BizTreeCacheSql(Ddl ddl, Repo repo, Loader loader) {
        this.ddl    = ddl;
        this.repo   = repo;
        this.loader = loader;
    }
}
