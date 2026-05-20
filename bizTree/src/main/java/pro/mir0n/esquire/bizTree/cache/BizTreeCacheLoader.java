/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  created: loads ORG/USR/ACCT entities from DB into H2 in-memory cache on ApplicationReadyEvent;
 *                   builds folder nodes; computes tree paths and levels via BFS
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 * 03/26/2026 mir0n  magic constants replaced with BizTreeConstants.*;
 *                   folder routing: folderKindForUsr(etPk) replaces hardcoded KIND_USR_* comparisons
 * 03/31/2026 mir0n  devLog debug line added in user-building loop
 * 05/20/2026 mir0n  Taijitu refactor (v1.2.5): no longer an ApplicationReadyEvent
 *                   listener -- exposes load() invoked by the active director's
 *                   bootstrap; consumes precomposed CacheSqlSet (was BizTreeCacheSql)
 */
package pro.mir0n.esquire.bizTree.cache;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.bizTree.BizTreeConstants;
import pro.mir0n.esquire.bizTree.jpa.EsqAcctRepository;
import pro.mir0n.esquire.bizTree.jpa.EsqOrgRepository;
import pro.mir0n.esquire.bizTree.jpa.EsqUsrRepository;

import java.util.*;

/**
 * Bulk-loads ORG/USR/ACCT entities from esq2025 into the in-memory H2 tree
 * cache. Invoked explicitly via {@link #load()} -- no longer an
 * ApplicationReadyEvent listener. The active director's bootstrap() decides
 * when to call it (legacy: directly; Yang/Taijitu: from the INIT command).
 * This is the {@code CacheLoad} seam the taijitu monads delegate to.
 */
@Slf4j
@Component
public class BizTreeCacheLoader {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + BizTreeCacheLoader.class.getName());

    private final EsqOrgRepository  orgRepo;
    private final EsqUsrRepository  usrRepo;
    private final EsqAcctRepository acctRepo;
    private final JdbcTemplate    cacheDb;
    private final CacheSqlSet     sql;

    public BizTreeCacheLoader(EsqOrgRepository orgRepo,
                              EsqUsrRepository usrRepo,
                              EsqAcctRepository acctRepo,
                              @Qualifier("cacheJdbcTemplate") JdbcTemplate cacheDb,
                              CacheSqlSet sql) {
        this.orgRepo  = orgRepo;
        this.usrRepo  = usrRepo;
        this.acctRepo = acctRepo;
        this.cacheDb  = cacheDb;
        this.sql      = sql;
    }

    /** Build the in-memory tree cache from the entity tables. Throws on failure
     *  so the caller (a monad INIT) can transition to FAILED. */
    public void load() {
        log.info("BizTreeCacheLoader: building in-memory tree cache from entity tables");

        List<Object[]> rows = new ArrayList<>();
        buildOrgRows(rows);
        Map<String, Long> usrOrgMap = buildUserRows(rows);
        buildAccountRows(rows, usrOrgMap);

        int[] counts = cacheDb.batchUpdate(sql.insertNode(), rows);
        log.info("BizTreeCacheLoader: inserted {} nodes; computing paths", counts.length);

        computePathsAndLevels();
        log.info("BizTreeCacheLoader: in-memory tree cache ready");
    }

    private void buildOrgRows(List<Object[]> rows) {
        List<EsqOrgJpa> orgs = orgRepo.findAllForTree();
        for (EsqOrgJpa o : orgs) {
            String pk         = o.getId();
            int    etPk       = o.getKind();
            String name       = o.getName();
            String desc       = o.getDesc();
            String parentPk   = o.getParentId();
            String entityPath = o.getPath();
            long   entityPk   = Long.parseLong(pk);

            rows.add(node(pk, etPk, name, desc, parentPk, null, entityPk, entityPath, BizTreeConstants.STATUS_OK));

            if (etPk > 1) {
                rows.add(node(pk + "~" + BizTreeConstants.FOLDER_ADMIN,    BizTreeConstants.FOLDER_ADMIN,    BizTreeConstants.FOLDER_ADMIN_NAME,    BizTreeConstants.FOLDER_ADMIN_DESC,    pk, null, null, entityPath, BizTreeConstants.STATUS_OK));
                rows.add(node(pk + "~" + BizTreeConstants.FOLDER_ACCOUNT,  BizTreeConstants.FOLDER_ACCOUNT,  BizTreeConstants.FOLDER_ACCOUNT_NAME,  BizTreeConstants.FOLDER_ACCOUNT_DESC,  pk, null, null, entityPath, BizTreeConstants.STATUS_OK));
                rows.add(node(pk + "~" + BizTreeConstants.FOLDER_CLIENT,   BizTreeConstants.FOLDER_CLIENT,   BizTreeConstants.FOLDER_CLIENT_NAME,   BizTreeConstants.FOLDER_CLIENT_DESC,   pk, null, null, entityPath, BizTreeConstants.STATUS_OK));
                rows.add(node(pk + "~" + BizTreeConstants.FOLDER_MERCHANT, BizTreeConstants.FOLDER_MERCHANT, BizTreeConstants.FOLDER_MERCHANT_NAME, BizTreeConstants.FOLDER_MERCHANT_DESC, pk, null, null, entityPath, BizTreeConstants.STATUS_OK));
            } else {
                rows.add(node(pk + "~" + BizTreeConstants.FOLDER_SYS_ADMIN, BizTreeConstants.FOLDER_SYS_ADMIN, BizTreeConstants.FOLDER_SYS_ADMIN_NAME, BizTreeConstants.FOLDER_SYS_ADMIN_DESC, pk, null, null, entityPath, BizTreeConstants.STATUS_OK));
            }
        }
    }

    private Map<String, Long> buildUserRows(List<Object[]> rows) {
        Map<String, Long> ret    = new HashMap<>();
        List<EsqUsrJpa>   users  = usrRepo.findAllForTree();
        for (EsqUsrJpa u : users) {
            String pk         = u.getId();
            int    etPk       = u.getKind();
            String name       = u.getName();
            String desc       = u.getDesc();
            long   orgPk      = Long.parseLong(u.getParentId());
            String entityPath = u.getPath();
            String deletedFlg = u.getDeleted();
            long   usrPk      = Long.parseLong(pk);

            ret.put(pk, orgPk);

            int folderType = (orgPk == BizTreeConstants.ORG_ROOT_PK)
                    ? BizTreeConstants.FOLDER_SYS_ADMIN
                    : BizTreeConstants.folderKindForUsr(etPk);

            int    status   = BizTreeConstants.FLAG_DELETED.equals(deletedFlg) ? BizTreeConstants.STATUS_DELETED : BizTreeConstants.STATUS_OK;
            String parentPk = orgPk + "~" + folderType;
            rows.add(node(pk, etPk, name, desc, parentPk, null, usrPk, entityPath, status));
            devLog.debug("added user: pk={}, etPk={}, name={}, desc={}, parentPk={}, usrPk={}, entityPath={}, status={}", pk, etPk, name, desc, parentPk, usrPk, entityPath, status);
        }
        return ret;
    }

    private void buildAccountRows(List<Object[]> rows, Map<String, Long> usrOrgMap) {
        List<EsqAcctJpa> accounts = acctRepo.findAllForTree();
        for (EsqAcctJpa a : accounts) {
            String pk         = a.getId();
            int    etPk       = a.getKind();
            String name       = a.getName();
            String desc       = a.getDesc();
            String usrPk      = a.getParentId();
            long   orgPk      = usrOrgMap.getOrDefault(usrPk, 0L);
            String entityPath = a.getPath();
            String accStatus  = a.getStatus();
            long   accPk      = Long.parseLong(pk);

            int status;
            if (BizTreeConstants.FLAG_DELETED.equals(accStatus) || BizTreeConstants.FLAG_CLOSED.equals(accStatus)) status = BizTreeConstants.STATUS_DELETED;
            else if (BizTreeConstants.FLAG_LOCKED.equals(accStatus))                                                status = BizTreeConstants.STATUS_LOCKED;
            else                                                                                                     status = BizTreeConstants.STATUS_OK;

            rows.add(node(pk, etPk, name, desc, usrPk, null, accPk, entityPath, status));

            String shortcutPk     = orgPk + "~" + pk;
            String shortcutParent = orgPk + "~" + BizTreeConstants.FOLDER_ACCOUNT;
            rows.add(node(shortcutPk, etPk + 1, name, desc, shortcutParent, pk, accPk, entityPath, status));
        }
    }

    private void computePathsAndLevels() {
        Map<String, String>       parents  = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();

        cacheDb.query(sql.selectPaths(), rs -> {
            String pk       = rs.getString(BizTreeConstants.COL_PK);
            String parentPk = rs.getString(BizTreeConstants.COL_PARENT_PK);
            parents.put(pk, parentPk);
            if (parentPk != null) {
                children.computeIfAbsent(parentPk, k -> new ArrayList<>()).add(pk);
            }
        });

        Map<String, Integer> levels = new HashMap<>();
        Map<String, String>  paths  = new HashMap<>();

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, String> e : parents.entrySet()) {
            if (e.getValue() == null) {
                String pk = e.getKey();
                levels.put(pk, 0);
                paths.put(pk, pk + ".");
                queue.add(pk);
            }
        }

        while (!queue.isEmpty()) {
            String pk    = queue.poll();
            int    level = levels.get(pk);
            String path  = paths.get(pk);
            for (String child : children.getOrDefault(pk, Collections.emptyList())) {
                levels.put(child, level + 1);
                paths.put(child, path + child + ".");
                queue.add(child);
            }
        }

        List<Object[]> updates = new ArrayList<>(parents.size());
        for (String pk : parents.keySet()) {
            updates.add(new Object[]{ levels.getOrDefault(pk, 0), paths.get(pk), pk });
        }
        cacheDb.batchUpdate(sql.updatePath(), updates);
        devLog.debug("BizTreeCacheLoader: path/level updated for {} nodes", updates.size());
    }

    private static Object[] node(String pk, int etPk, String name, String desc,
                                  String parentPk, String linkPk, Long entityPk,
                                  String entityPath, int status) {
        return new Object[]{ pk, etPk, name, desc, parentPk, linkPk, entityPk, 0, null, entityPath, status };
    }
}
