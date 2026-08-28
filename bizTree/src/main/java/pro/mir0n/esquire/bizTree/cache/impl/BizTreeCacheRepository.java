/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  created: H2 implementation of IBizTreeCacheRepository
 *                   updateNode(): single CASE-based SQL UPDATE; WHERE tree_entity_pk only (entity_pk unique)
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 * 03/26/2026 mir0n  insertOrgNodes/insertUsrNode/insertAcctNode: insert cache nodes for ORG/USR/ACCT CREATE events;
 *                   insertAcctNode inserts main node (under user) + shortcut node (under org FOLDER_ACCOUNT);
 *                   deleteNodes(entityPk): DELETE FROM ESQ_TREE WHERE tree_entity_pk = ?;
 *                   insertUsrNode: folder routing via BizTreeConstants.folderKindForUsr() — data-driven from kind metadata
 * 03/28/2026 mir0n  deleteNodes(String entityId): WHERE ? IN (tree_pk, tree_tree_pk_link, tree_tree_pk_parent)
 * 04/02/2026 mir0n  added moveOrgNode()
 *                   added moveUsrNode();
 *                   added moveAcctNode();
 * 04/06/2026 mir0n  moveUsrNode(): admin-aware orgPk extraction using isPathParentOnly()
 * 04/07/2026 mir0n  moveUsrNode(): param renamed kind; EsqObjectKindStorage.get() receives raw kind
 * 04/16/2026 mir0n  insertAcctNode, moveOrgNode, moveUsrNode, moveAcctNode: null-guard replaces early returns
 * 05/14/2026 mir0n  findSubtree(seedId, rootLevel, rootPath) implementation: SELECT_SUBTREE_SQL
 *                   recursive walk by tree_path LIKE seed.tree_path || '%' (rootPath-scoped)
 * 05/20/2026 mir0n  Taijitu refactor (v1.2.5): consume precomposed CacheSqlSet -- reads
 *                   run ready statements (no per-call selectCols()+where concatenation)
 * 05/23/2026 mir0n  clear() = TRUNCATE via sql.clearAll(); prepareCancelable(CHECKSUM) opens a
 *                   connection + prepares sql.checksum(), returned as a CancelableStatement
 *                   (closeQuietly the connection if prepare throws -- no leak).
 * 08/11/2026 mir0n  v1.2.12 -- findChangeNumbers, stampEntityChangeNo and stampPathChangeNo implemented;
 *                   the node mapper reads the two new columns
 * 08/26/2026 mir0n  findPathScoped implemented: the path is returned only when the row sits under rootPath
 */
package pro.mir0n.esquire.bizTree.cache.impl;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.bizTree.BizTreeConstants;
import pro.mir0n.esquire.bizTree.cache.CacheSqlSet;
import pro.mir0n.esquire.bizTree.cache.CancelableStatement;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.utils.taijitu.MonadCmd;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class BizTreeCacheRepository implements IBizTreeCacheRepository {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + BizTreeCacheRepository.class.getName());

    private final JdbcTemplate cache;
    private final CacheSqlSet  sql;

    private static final RowMapper<EsqTreeNodeJpa> NODE_MAPPER = (rs, rowNum) -> {
        EsqTreeNodeJpa n = new EsqTreeNodeJpa();
        n.setId(rs.getString(BizTreeConstants.COL_PK));
        n.setParentId(rs.getString(BizTreeConstants.COL_PARENT_PK));
        n.setLinkId(rs.getString(BizTreeConstants.COL_LINK_PK));
        n.setName(rs.getString(BizTreeConstants.COL_NAME));
        n.setKind(rs.getInt(BizTreeConstants.COL_ET_PK));
        long epkRaw = rs.getLong(BizTreeConstants.COL_ENTITY_PK);
        n.setEntityId(rs.wasNull() ? null : epkRaw);
        n.setStatusCode(rs.getInt(BizTreeConstants.COL_STATUS));
        n.setLevel(rs.getInt(BizTreeConstants.COL_LEVEL));
        n.setDesc(rs.getString(BizTreeConstants.COL_DESC));
        n.setPath(rs.getString(BizTreeConstants.COL_PATH));
        return n;
    };

    public BizTreeCacheRepository(@Qualifier("cacheJdbcTemplate") JdbcTemplate cache,
                                   CacheSqlSet sql) {
        this.cache = cache;
        this.sql   = sql;
    }

    @Override
    public List<EsqTreeNodeJpa> findRoot(String rootId, int rootLevel, String rootPath) {
        return cache.query(sql.findRoot(), NODE_MAPPER, rootLevel, rootId, rootPath + "%");
    }

    @Override
    public List<EsqTreeNodeJpa> findNodes(String id, int rootLevel, String rootPath) {
        return cache.query(sql.findNodes(), NODE_MAPPER, rootLevel, id, rootPath + "%");
    }

    @Override
    public String findPath(String id) {
        List<String> ret = cache.queryForList(sql.findPath(), String.class, id);
        return ret.isEmpty() ? null : ret.get(0);
    }

    @Override
    public String findPathScoped(String id, String rootPath) {
        List<String> ret = cache.queryForList(sql.findPathScoped(), String.class, id, rootPath + "%");
        return ret.isEmpty() ? null : ret.get(0);
    }

    @Override
    public EsqTreeNodeJpa findByEntityId(String id, int rootLevel, String rootPath) {
        List<EsqTreeNodeJpa> ret = cache.query(sql.findByEntityId(), NODE_MAPPER, rootLevel, Long.parseLong(id), rootPath + "%");
        return ret.isEmpty() ? null : ret.get(0);
    }

    @Override
    public EsqTreeNodeJpa findByNameKind(String name, Integer kind, int rootLevel, String rootPath) {
        List<EsqTreeNodeJpa> ret = cache.query(sql.findByNameKind(), NODE_MAPPER, rootLevel, name, kind, rootPath + "%");
        return ret.isEmpty() ? null : ret.get(0);
    }

    @Override
    public Long[] findChangeNumbers(long entityPk) {
        Long[] ret = null;
        List<Long[]> rows = cache.query(sql.findChangeNumbers(),
                (rs, i) -> new Long[]{ (Long) rs.getObject("tree_entity_change_no"),
                                       (Long) rs.getObject("tree_path_change_no") },
                entityPk);
        if (!rows.isEmpty()) {
            ret = rows.get(0);
        }
        return ret;
    }

    @Override
    public void stampEntityChangeNo(long entityPk, Long changeNo) {
        cache.update(sql.stampEntityChangeNo(), changeNo, entityPk);
    }

    @Override
    public void stampPathChangeNo(long entityPk, Long changeNo) {
        cache.update(sql.stampPathChangeNo(), changeNo, entityPk);
    }

    @Override
    public void updateNode(long entityPk, String name, String desc, Integer statusCode) {
        int ret = cache.update(sql.updateNode(),
                name, name, desc, desc, statusCode, statusCode, entityPk);
        devLog.debug("BizTreeCacheRepository: updateNode id={} name={} desc={} status={} rows={}",
                entityPk, name, desc, statusCode, ret);
    }

    @Override
    public void insertOrgNodes(long orgPk, int etPk, String name, String desc, String parentPk, String entityPath) {

        String parentTreePath = findPath(parentPk);
        if (parentTreePath == null) {
            log.error("BizTreeCacheRepository: insertOrgNodes: parent not found in cache, parentPk={}", parentPk);
            devLog.error("BizTreeCacheRepository: insertOrgNodes: parent not found in cache, parentPk={}", parentPk);
            return;
        }
        String orgTreePath    = (parentTreePath != null ? parentTreePath : "") + orgPk + ".";
        int    orgLevel       = parentTreePath != null ? countDots(parentTreePath) : 0;

        String orgPkStr     = String.valueOf(orgPk);
        int    folderLevel  = orgLevel + 1;

        List<Object[]> rows = new ArrayList<>();
        rows.add(row(orgPkStr, etPk, name, desc, parentPk, null, orgPk, orgLevel, orgTreePath, entityPath, BizTreeConstants.STATUS_OK));

        if (etPk > 1) {
            rows.add(row(orgPkStr + "~" + BizTreeConstants.FOLDER_ADMIN,    BizTreeConstants.FOLDER_ADMIN,    BizTreeConstants.FOLDER_ADMIN_NAME,    BizTreeConstants.FOLDER_ADMIN_DESC,    orgPkStr, null, null, folderLevel, orgTreePath + orgPkStr + "~" + BizTreeConstants.FOLDER_ADMIN    + ".", entityPath, BizTreeConstants.STATUS_OK));
            rows.add(row(orgPkStr + "~" + BizTreeConstants.FOLDER_ACCOUNT,  BizTreeConstants.FOLDER_ACCOUNT,  BizTreeConstants.FOLDER_ACCOUNT_NAME,  BizTreeConstants.FOLDER_ACCOUNT_DESC,  orgPkStr, null, null, folderLevel, orgTreePath + orgPkStr + "~" + BizTreeConstants.FOLDER_ACCOUNT  + ".", entityPath, BizTreeConstants.STATUS_OK));
            rows.add(row(orgPkStr + "~" + BizTreeConstants.FOLDER_CLIENT,   BizTreeConstants.FOLDER_CLIENT,   BizTreeConstants.FOLDER_CLIENT_NAME,   BizTreeConstants.FOLDER_CLIENT_DESC,   orgPkStr, null, null, folderLevel, orgTreePath + orgPkStr + "~" + BizTreeConstants.FOLDER_CLIENT   + ".", entityPath, BizTreeConstants.STATUS_OK));
            rows.add(row(orgPkStr + "~" + BizTreeConstants.FOLDER_MERCHANT, BizTreeConstants.FOLDER_MERCHANT, BizTreeConstants.FOLDER_MERCHANT_NAME, BizTreeConstants.FOLDER_MERCHANT_DESC, orgPkStr, null, null, folderLevel, orgTreePath + orgPkStr + "~" + BizTreeConstants.FOLDER_MERCHANT + ".", entityPath, BizTreeConstants.STATUS_OK));
        } else {
            rows.add(row(orgPkStr + "~" + BizTreeConstants.FOLDER_SYS_ADMIN, BizTreeConstants.FOLDER_SYS_ADMIN, BizTreeConstants.FOLDER_SYS_ADMIN_NAME, BizTreeConstants.FOLDER_SYS_ADMIN_DESC, orgPkStr, null, null, folderLevel, orgTreePath + orgPkStr + "~" + BizTreeConstants.FOLDER_SYS_ADMIN + ".", entityPath, BizTreeConstants.STATUS_OK));
        }

        cache.batchUpdate(sql.insertNode(), rows);
        devLog.debug("BizTreeCacheRepository: insertOrgNodes: inserted {} nodes for orgPk={}", rows.size(), orgPk);
    }

    @Override
    public void insertUsrNode(long usrPk, int etPk, String name, String desc, long orgPk, String entityPath, int statusCode) {
        int folderType = (orgPk == BizTreeConstants.ORG_ROOT_PK)
                ? BizTreeConstants.FOLDER_SYS_ADMIN
                : BizTreeConstants.folderKindForUsr(etPk);

        String folderNodePk   = orgPk + "~" + folderType;
        String parentTreePath = findPath(folderNodePk);
        if (parentTreePath == null) {
            log.error("BizTreeCacheRepository: insertUsrNode: folder not found in cache, folderNodePk={}", folderNodePk);
            devLog.error("BizTreeCacheRepository: insertUsrNode: folder not found in cache, folderNodePk={}", folderNodePk);
            return;
        }
        String usrPkStr  = String.valueOf(usrPk);
        String treePath  = parentTreePath + usrPkStr + ".";
        int    level     = countDots(parentTreePath);

        List<Object[]> usrRow = new ArrayList<>();
        usrRow.add(row(usrPkStr, etPk, name, desc, folderNodePk, null, usrPk, level, treePath, entityPath, statusCode));
        cache.batchUpdate(sql.insertNode(), usrRow);
        devLog.debug("BizTreeCacheRepository: insertUsrNode: inserted node for usrPk={}", usrPk);
    }

    @Override
    public void insertAcctNode(long acctPk, int etPk, String name, String desc, long usrPk, String entityPath, int statusCode) {
        String usrPkStr     = String.valueOf(usrPk);
        String userTreePath = findPath(usrPkStr);
        if (userTreePath == null) {
            log.error("BizTreeCacheRepository: insertAcctNode: user not found in cache, usrPk={}", usrPk);
            devLog.error("BizTreeCacheRepository: insertAcctNode: user not found in cache, usrPk={}", usrPk);
            return;
        }
        // userTreePath e.g. "1.5.5~4.42." → strip trailing dot, split → second-to-last = "5~4" (folder pk)
        String[] parts    = userTreePath.substring(0, userTreePath.length() - 1).split("\\.");
        long     orgPk    = Long.parseLong(parts[parts.length - 2].split("~")[0]);
        String   orgPkStr = String.valueOf(orgPk);

        String acctPkStr = String.valueOf(acctPk);
        int    mainLevel = countDots(userTreePath);
        String mainPath  = userTreePath + acctPkStr + ".";

        String shortcutParent     = orgPkStr + "~" + BizTreeConstants.FOLDER_ACCOUNT;
        String shortcutParentPath = findPath(shortcutParent);

        if (shortcutParentPath == null) {
            log.error("BizTreeCacheRepository: insertAcctNode: FOLDER_ACCOUNT not found in cache, shortcutParent={}", shortcutParent);
            devLog.error("BizTreeCacheRepository: insertAcctNode: FOLDER_ACCOUNT not found in cache, shortcutParent={}", shortcutParent);
            return;
        }

        String shortcutPk    = orgPkStr + "~" + acctPkStr;
        int    shortcutLevel = countDots(shortcutParentPath);
        String shortcutPath  = shortcutParentPath + shortcutPk + ".";

        List<Object[]> rows = new ArrayList<>();
        rows.add(row(acctPkStr, etPk, name, desc, usrPkStr, null, acctPk, mainLevel, mainPath, entityPath, statusCode));
        rows.add(row(shortcutPk, etPk + 1, name, desc, shortcutParent, acctPkStr, acctPk, shortcutLevel, shortcutPath, entityPath, statusCode));
        cache.batchUpdate(sql.insertNode(), rows);
        devLog.debug("BizTreeCacheRepository: insertAcctNode: inserted {} nodes for acctPk={}", rows.size(), acctPk);
    }

    @Override
    public void deleteNodes(String entityId) {
        int ret = cache.update(sql.deleteNode(), entityId);
        devLog.debug("BizTreeCacheRepository: deleteNodes entityId={} rows={}", entityId, ret);
    }

    @Override
    public List<EsqTreeNodeJpa> findSubtree(String seedId, int rootLevel, String rootPath) {
        return cache.query(sql.findSubtree(), NODE_MAPPER, rootLevel, seedId, rootPath + "%");
    }

    @Override
    public void clear() {
        cache.execute(sql.clearAll());
        devLog.debug("BizTreeCacheRepository: clear (truncate)");
    }

    @Override
    public CancelableStatement prepareCancelable(String command) {
        CancelableStatement ret;
        if (!MonadCmd.CHECKSUM.equals(command)) {
            throw new IllegalArgumentException("no cancelable query for command '" + command + "'");
        }
        Connection con = null;
        try {
            con = cache.getDataSource().getConnection();
            ret = new CancelableStatement(con, con.prepareStatement(sql.checksum()));
        } catch (SQLException e) {
            closeQuietly(con);   // prepareStatement failed after getConnection -- don't leak the connection
            throw new IllegalStateException("prepareCancelable failed for '" + command + "'", e);
        }
        return ret;
    }

    private static void closeQuietly(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException ignore) {
                // best-effort
            }
        }
    }

    @Override
    public void moveOrgNode(long orgPk, String newEntityPath) {
        String   orgPkStr = String.valueOf(orgPk);
        String[] segs     = newEntityPath.substring(0, newEntityPath.length() - 1).split("\\.");
        if (segs.length >= 2) {
            String parentPk = segs[segs.length - 2];
            String newOrgPath = newEntityPath;
            int    orgLevel   = countDots(newEntityPath) - 1;
            cache.update(sql.moveNode(), newOrgPath, newEntityPath, orgLevel, parentPk, orgPkStr);

            List<String>   folderPks   = cache.queryForList(sql.findFolderPks(), String.class, orgPkStr);
            int            folderLevel = orgLevel + 1;
            List<Object[]> folderRows  = new ArrayList<>();
            for (String folderPk : folderPks) {
                folderRows.add(new Object[]{ newOrgPath + folderPk + ".", newEntityPath, folderLevel, orgPkStr, folderPk });
            }
            int[] folderUpdates = cache.batchUpdate(sql.moveNode(), folderRows);
            devLog.debug("BizTreeCacheRepository: moveOrgNode pk={} newPath={} folderCount={}",
                    orgPk, newOrgPath, folderUpdates.length);
        }
    }

    @Override
    public void moveUsrNode(long usrPk, int kind, String newEntityPath) {
        String   usrPkStr = String.valueOf(usrPk);
        String[] segs     = newEntityPath.substring(0, newEntityPath.length() - 1).split("\\.");
        if (segs.length >= 2) {
            EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
            long   orgPk;
            String orgPath;
            if (eek.isPathParentOnly()) {
                orgPk   = Long.parseLong(segs[segs.length - 1]);
                orgPath = newEntityPath;
            } else {
                orgPk   = Long.parseLong(segs[segs.length - 2]);
                orgPath = newEntityPath.substring(0, newEntityPath.length() - (usrPkStr.length() + 1));
            }
            int    folderKind = (orgPk == BizTreeConstants.ORG_ROOT_PK)
                                ? BizTreeConstants.FOLDER_SYS_ADMIN
                                : BizTreeConstants.folderKindForUsr(eek.getId());
            String folderPk   = orgPk + "~" + folderKind;
            String newUsrPath = orgPath + folderPk + "." + usrPkStr + ".";
            int    usrLevel   = countDots(orgPath) + 1;
            int updated = cache.update(sql.moveNode(), newUsrPath, newEntityPath, usrLevel, folderPk, usrPkStr);
            devLog.debug("BizTreeCacheRepository: moveUsrNode pk={} newPath={} updated={}",
                    usrPk, newUsrPath, updated);
        } else {
            log.error("BizTreeCacheRepository: moveUsrNode: entity path too short to apply, path={}, pk={}",
                    newEntityPath, usrPk);
            devLog.error("BizTreeCacheRepository: moveUsrNode: entity path too short to apply, path={}, pk={}",
                    newEntityPath, usrPk);
        }
    }

    @Override
    public void moveAcctNode(long acctPk, String newEntityPath) {
        String   acctPkStr = String.valueOf(acctPk);
        String[] segs      = newEntityPath.substring(0, newEntityPath.length() - 1).split("\\.");
        if (segs.length >= 3) {
            String orgPkStr = segs[segs.length - 2];
            String usrPkStr = segs[segs.length - 1];
            String orgPath  = newEntityPath.substring(0, newEntityPath.length() - (usrPkStr.length() + 1));
            String usrCached = findPath(usrPkStr);
            if (usrCached != null) {
                String[] usrSegs   = usrCached.substring(0, usrCached.length() - 1).split("\\.");
                String   folderSeg = usrSegs[usrSegs.length - 2];
                int      tilde     = folderSeg.indexOf('~');
                String   folderPk  = (tilde < 0) ? folderSeg : orgPkStr + folderSeg.substring(tilde);
                String   userPath  = orgPath + folderPk + "." + usrPkStr + ".";
                String   mainPath  = userPath + acctPkStr + ".";
                int      mainLevel = countDots(userPath);
                cache.update(sql.moveNode(), mainPath, newEntityPath, mainLevel, usrPkStr, acctPkStr);

                String shortcutParent = orgPkStr + "~" + BizTreeConstants.FOLDER_ACCOUNT;
                String newShortcutPk  = orgPkStr + "~" + acctPkStr;
                String shortcutPath   = orgPath + shortcutParent + "." + newShortcutPk + ".";
                int    shortcutLvl    = countDots(orgPath) + 1;
                int updated = cache.update(sql.moveAcctLink(), newShortcutPk, shortcutPath, newEntityPath,
                        shortcutLvl, shortcutParent, acctPkStr);
                devLog.debug("BizTreeCacheRepository: moveAcctNode pk={} newPath={} shortcutUpdated={}",
                        acctPk, mainPath, updated);
            } else {
                log.error("BizTreeCacheRepository: moveAcctNode: user not in cache, pk={}, usrPk={}", acctPk, usrPkStr);
                devLog.error("BizTreeCacheRepository: moveAcctNode: user not in cache, pk={}, usrPk={}", acctPk, usrPkStr);
            }
        } else {
            log.error("BizTreeCacheRepository: moveAcctNode: entity path too short to apply, path={}, pk={}",
                    newEntityPath, acctPk);
            devLog.error("BizTreeCacheRepository: moveAcctNode: entity path too short to apply, path={}, pk={}",
                    newEntityPath, acctPk);
        }
    }

    private static int countDots(String s) {
        int ret = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '.') ret++;
        }
        return ret;
    }

    /**
     * A row for the shared {@code insert-node} statement -- the SAME statement the loader uses, so the
     * column list and this array must stay in step (v1.2.12 added the two change-number columns).
     *
     */
    private static Object[] row(String pk, int etPk, String name, String desc,
                                 String parentPk, String linkPk, Long entityPk,
                                 int level, String path, String entityPath, int status) {
        return new Object[]{ pk, etPk, name, desc, parentPk, linkPk, entityPk, level, path, entityPath, status,
                             1L, 1L };
    }
}
