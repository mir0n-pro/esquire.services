/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  created: loads ORG/USR/ACCT entities from DB into H2 in-memory cache on ApplicationReadyEvent;
 *                   builds folder nodes; computes tree paths and levels via BFS
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 */
package pro.mir0n.esquire.bizTree.cache;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqOrgJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;
import pro.mir0n.esquire.bizTree.jpa.EsqAcctRepository;
import pro.mir0n.esquire.bizTree.jpa.EsqOrgRepository;
import pro.mir0n.esquire.bizTree.jpa.EsqUsrRepository;

import java.util.*;

@Slf4j
@Component
public class BizTreeCacheLoader implements ApplicationListener<ApplicationReadyEvent> {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + BizTreeCacheLoader.class.getName());

    private static final int STATUS_OK      = 0;
    private static final int STATUS_DELETED = 1;
    private static final int STATUS_LOCKED  = 2;

    private final EsqOrgRepository  orgRepo;
    private final EsqUsrRepository  usrRepo;
    private final EsqAcctRepository acctRepo;
    private final JdbcTemplate       cacheDb;
    private final BizTreeCacheSql    sql;

    public BizTreeCacheLoader(EsqOrgRepository orgRepo,
                              EsqUsrRepository usrRepo,
                              EsqAcctRepository acctRepo,
                              @Qualifier("cacheJdbcTemplate") JdbcTemplate cacheDb,
                              BizTreeCacheSql sql) {
        this.orgRepo  = orgRepo;
        this.usrRepo  = usrRepo;
        this.acctRepo = acctRepo;
        this.cacheDb  = cacheDb;
        this.sql      = sql;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("BizTreeCacheLoader: building in-memory tree cache from entity tables");

        List<Object[]> rows = new ArrayList<>();
        buildOrgRows(rows);
        Map<String, Long> usrOrgMap = buildUserRows(rows);
        buildAccountRows(rows, usrOrgMap);

        int[] counts = cacheDb.batchUpdate(sql.loader.insertNode(), rows);
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

            rows.add(node(pk, etPk, name, desc, parentPk, null, entityPk, entityPath, STATUS_OK));

            if (etPk > 1) {
                rows.add(node(pk + "~4",  4,  "All admin-s",   "Admin-s folder",   pk, null, null, entityPath, STATUS_OK));
                rows.add(node(pk + "~6",  6,  "All accounts",  "Accounts folder",  pk, null, null, entityPath, STATUS_OK));
                rows.add(node(pk + "~8",  8,  "All clients",   "Clients folder",   pk, null, null, entityPath, STATUS_OK));
                rows.add(node(pk + "~10", 10, "All merchants", "Merchants folder", pk, null, null, entityPath, STATUS_OK));
            } else {
                rows.add(node(pk + "~2", 2, "Sys admin-s", "Sys admin-s folder", pk, null, null, entityPath, STATUS_OK));
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

            int folderType;
            if (orgPk == 1)       folderType = 2;
            else if (etPk == 34)  folderType = 8;
            else if (etPk == 36)  folderType = 10;
            else                  folderType = 4;

            int    status   = "Y".equals(deletedFlg) ? STATUS_DELETED : STATUS_OK;
            String parentPk = orgPk + "~" + folderType;
            rows.add(node(pk, etPk, name, desc, parentPk, null, usrPk, entityPath, status));
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
            if ("Y".equals(accStatus) || "C".equals(accStatus)) status = STATUS_DELETED;
            else if ("L".equals(accStatus))                      status = STATUS_LOCKED;
            else                                                 status = STATUS_OK;

            rows.add(node(pk, etPk, name, desc, usrPk, null, accPk, entityPath, status));

            String shortcutPk     = orgPk + "~" + pk;
            String shortcutParent = orgPk + "~6";
            rows.add(node(shortcutPk, etPk + 1, name, desc, shortcutParent, pk, accPk, entityPath, status));
        }
    }

    private void computePathsAndLevels() {
        Map<String, String>       parents  = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();

        cacheDb.query(sql.loader.selectPaths(), rs -> {
            String pk       = rs.getString("tree_pk");
            String parentPk = rs.getString("tree_tree_pk_parent");
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
        cacheDb.batchUpdate(sql.loader.updatePath(), updates);
        devLog.debug("BizTreeCacheLoader: path/level updated for {} nodes", updates.size());
    }

    private static Object[] node(String pk, int etPk, String name, String desc,
                                  String parentPk, String linkPk, Long entityPk,
                                  String entityPath, int status) {
        return new Object[]{ pk, etPk, name, desc, parentPk, linkPk, entityPk, 0, null, entityPath, status };
    }
}
