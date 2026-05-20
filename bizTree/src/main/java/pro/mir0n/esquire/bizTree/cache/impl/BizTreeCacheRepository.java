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
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;

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

        List<Object[]> rows = new ArrayList<>();
        rows.add(row(acctPkStr, etPk, name, desc, usrPkStr, null, acctPk, mainLevel, mainPath, entityPath, statusCode));
        if (shortcutParentPath != null) {
            String shortcutPk    = orgPkStr + "~" + acctPkStr;
            int    shortcutLevel = countDots(shortcutParentPath);
            String shortcutPath  = shortcutParentPath + shortcutPk + ".";
            rows.add(row(shortcutPk, etPk + 1, name, desc, shortcutParent, acctPkStr, acctPk, shortcutLevel, shortcutPath, entityPath, statusCode));
        } else {
            log.error("BizTreeCacheRepository: insertAcctNode: FOLDER_ACCOUNT not found in cache, shortcutParent={}", shortcutParent);
            devLog.error("BizTreeCacheRepository: insertAcctNode: FOLDER_ACCOUNT not found in cache, shortcutParent={}", shortcutParent);
        }
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
    public void moveOrgNode(long orgPk, String newEntityPath) {
        String   orgPkStr = String.valueOf(orgPk);
        String[] segs     = newEntityPath.substring(0, newEntityPath.length() - 1).split("\\.");
        long     entityPk = orgPk;
        if (segs.length < 2) return;
        String parentPk   = segs[segs.length - 2];
        String parentPath = findPath(parentPk);
        if (parentPath != null) {
            String newOrgPath = parentPath + orgPkStr + ".";
            int    orgLevel   = countDots(parentPath);
            cache.update(sql.moveNode(), newOrgPath, newEntityPath, orgLevel, parentPk, orgPkStr);

            List<String> folderPks   = cache.queryForList(sql.findFolderPks(), String.class, orgPkStr);
            int          folderLevel = orgLevel + 1;
            List<Object[]> folderRows = new ArrayList<>();
            for (String folderPk : folderPks) {
                folderRows.add(new Object[]{ newOrgPath + folderPk + ".", newEntityPath, folderLevel, orgPkStr, folderPk });
            }
            int[] folderUpdates = cache.batchUpdate(sql.moveNode(), folderRows);
            devLog.debug("BizTreeCacheRepository: moveOrgNode pk={} newEntityPath={} folderCount={}",
                    entityPk, newEntityPath, folderUpdates.length);
        } else {
            log.error("BizTreeCacheRepository: moveOrgNode: parent not in cache, pk={}, parentPk={}", entityPk, parentPk);
            devLog.error("BizTreeCacheRepository: moveOrgNode: parent not in cache, pk={}, parentPk={}", entityPk, parentPk);
        }
    }

    @Override
    public void moveUsrNode(long usrPk, int kind, String newEntityPath) {
        String   usrPkStr = String.valueOf(usrPk);
        String[] segs     = newEntityPath.substring(0, newEntityPath.length() - 1).split("\\.");
        long     entityPk = usrPk;
        if (segs.length < 2) return;
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        // Admin ep_path = orgPath: last segment IS the org pk  (e.g. "1.9.200." → 200)
        // Regular ep_path includes own pk: second-to-last is org pk  (e.g. "1.9.200.100." → 200)
        long orgPk;
        if (eek.isPathParentOnly()) {
            orgPk = Long.parseLong(segs[segs.length - 1]);
        } else {
            orgPk = Long.parseLong(segs[segs.length - 2]);
        }
        int    folderKind = (orgPk == BizTreeConstants.ORG_ROOT_PK)
                            ? BizTreeConstants.FOLDER_SYS_ADMIN
                            : BizTreeConstants.folderKindForUsr(eek.getId());
        String folderPk   = orgPk + "~" + folderKind;
        String folderPath = findPath(folderPk);
        if (folderPath != null) {
            String newUsrPath = folderPath + usrPkStr + ".";
            int    usrLevel   = countDots(folderPath);
            int updated = cache.update(sql.moveNode(), newUsrPath, newEntityPath, usrLevel, folderPk, usrPkStr);
            devLog.debug("BizTreeCacheRepository: moveUsrNode pk={} newEntityPath={} updated={}", entityPk, newEntityPath, updated);
        } else {
            log.error("BizTreeCacheRepository: moveUsrNode: folder not in cache, pk={}, folderPk={}", entityPk, folderPk);
            devLog.error("BizTreeCacheRepository: moveUsrNode: folder not in cache, pk={}, folderPk={}", entityPk, folderPk);
        }
    }

    @Override
    public void moveAcctNode(long acctPk, String newEntityPath) {
        String   acctPkStr = String.valueOf(acctPk);
        String[] segs      = newEntityPath.substring(0, newEntityPath.length() - 1).split("\\.");
        long     entityPk  = acctPk;
        if (segs.length < 3) return;
        String orgPkStr = segs[segs.length - 2];
        String usrPkStr = segs[segs.length - 1];

        String userPath = findPath(usrPkStr);
        if (userPath != null) {
            String mainPath  = userPath + acctPkStr + ".";
            int    mainLevel = countDots(userPath);
            cache.update(sql.moveNode(), mainPath, newEntityPath, mainLevel, usrPkStr, acctPkStr);

            String shortcutParent     = orgPkStr + "~" + BizTreeConstants.FOLDER_ACCOUNT;
            String shortcutParentPath = findPath(shortcutParent);
            if (shortcutParentPath != null) {
                String newShortcutPk = orgPkStr + "~" + acctPkStr;
                String shortcutPath  = shortcutParentPath + newShortcutPk + ".";
                int    shortcutLvl   = countDots(shortcutParentPath);
                int updated = cache.update(sql.moveAcctLink(), newShortcutPk, shortcutPath, newEntityPath, shortcutLvl, shortcutParent, acctPkStr);
                devLog.debug("BizTreeCacheRepository: moveAcctNode pk={} newEntityPath={} shortcutUpdated={}", entityPk, newEntityPath, updated);
            } else {
                log.error("BizTreeCacheRepository: moveAcctNode: FOLDER_ACCOUNT not in cache, pk={}, parent={}", entityPk, shortcutParent);
                devLog.error("BizTreeCacheRepository: moveAcctNode: FOLDER_ACCOUNT not in cache, pk={}, parent={}", entityPk, shortcutParent);
            }
        } else {
            log.error("BizTreeCacheRepository: moveAcctNode: user not in cache, pk={}, usrPk={}", entityPk, usrPkStr);
            devLog.error("BizTreeCacheRepository: moveAcctNode: user not in cache, pk={}, usrPk={}", entityPk, usrPkStr);
        }
    }

    private static int countDots(String s) {
        int ret = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '.') ret++;
        }
        return ret;
    }

    private static Object[] row(String pk, int etPk, String name, String desc,
                                 String parentPk, String linkPk, Long entityPk,
                                 int level, String path, String entityPath, int status) {
        return new Object[]{ pk, etPk, name, desc, parentPk, linkPk, entityPk, level, path, entityPath, status };
    }
}
