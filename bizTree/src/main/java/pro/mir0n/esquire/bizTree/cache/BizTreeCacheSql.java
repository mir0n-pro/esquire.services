/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  created: SQL holder records (Ddl, Repo, Loader) for vendor-agnostic cache
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
            String updateNode
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
