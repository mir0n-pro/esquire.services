/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: precomposed, table-bound SQL set for one cache monad
 *                   (v1.2.5 Taijitu refactor). forTable() substitutes the {table} token
 *                   and joins the read fragments ONCE, so the repository executes
 *                   ready statements with no per-call concatenation. Each monad owns its
 *                   own CacheSqlSet bound to its own table (ESQ_TREE for Yang; the full
 *                   Taijitu builds one per table, e.g. ESQ_TREE_YANG / ESQ_TREE_YIN).
 */
package pro.mir0n.esquire.bizTree.cache;

/**
 * The fully-assembled, executable SQL for one cache table. Built once by
 * {@link #forTable} from the vendor templates ({@link BizTreeCacheSql}) and a
 * table name: the {@code {table}} token is substituted and the read fragments
 * (cols + where [+ limit]) are joined a single time. The repository then runs
 * {@code set.findRoot()} directly -- no concatenation per call.
 *
 * Per-monad by construction: one set = one table. Yang holds one (ESQ_TREE);
 * the full Taijitu holds one per monad, each bound to its own table.
 */
public record CacheSqlSet(
        // ddl
        String createTable,
        String createIndexParent,
        String createIndexEntityPk,
        // reads -- fully assembled, executable
        String findRoot,
        String findNodes,
        String findPath,
        String findByEntityId,
        String findByNameKind,
        String findSubtree,
        // writes
        String updateNode,
        String deleteNode,
        String moveNode,
        String moveAcctLink,
        String findFolderPks,
        // loader
        String insertNode,
        String updatePath,
        String selectPaths
) {

    /**
     * Compose the executable set for {@code table} from the vendor templates.
     * Substitutes every {@code {table}} token and pre-joins the read fragments
     * (the selectCols prefix and the selectOne single-row limiter) so nothing
     * is concatenated at query time.
     */
    public static CacheSqlSet forTable(BizTreeCacheSql t, String table) {
        String cols = sub(t.repo.selectCols(), table);
        String one  = sub(t.repo.selectOne(),  table);
        return new CacheSqlSet(
                sub(t.ddl.createTable(),       table),
                sub(t.ddl.createIndexParent(), table),
                sub(t.ddl.createIndexEntityPk(), table),
                cols + sub(t.repo.findRoot(),       table),
                cols + sub(t.repo.findNodes(),      table),
                sub(t.repo.findPath(),  table) + one,
                cols + sub(t.repo.findByEntityId(), table) + one,
                cols + sub(t.repo.findByNameKind(), table) + one,
                cols + sub(t.repo.findSubtree(),    table),
                sub(t.repo.updateNode(),     table),
                sub(t.repo.deleteNode(),     table),
                sub(t.repo.moveNode(),       table),
                sub(t.repo.moveAcctLink(),   table),
                sub(t.repo.findFolderPks(),  table),
                sub(t.loader.insertNode(),   table),
                sub(t.loader.updatePath(),   table),
                sub(t.loader.selectPaths(),  table)
        );
    }

    private static String sub(String template, String table) {
        return template.replace("{table}", table);
    }
}
