/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  created: H2 implementation of IBizTreeCacheRepository
 *                   updateNode(): single CASE-based SQL UPDATE; WHERE tree_entity_pk only (entity_pk unique)
 */
package pro.mir0n.esquire.bizTree.cache.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pro.mir0n.esquire.backend.jpa.EsqTreeNodeJpa;
import pro.mir0n.esquire.bizTree.cache.BizTreeCacheSql;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;

import java.util.List;

@Slf4j
@Repository
public class BizTreeCacheRepository implements IBizTreeCacheRepository {

    private final JdbcTemplate    cache;
    private final BizTreeCacheSql sql;

    private static final RowMapper<EsqTreeNodeJpa> NODE_MAPPER = (rs, rowNum) -> {
        EsqTreeNodeJpa n = new EsqTreeNodeJpa();
        n.setId(rs.getString("tree_pk"));
        n.setParentId(rs.getString("tree_tree_pk_parent"));
        n.setLinkId(rs.getString("tree_tree_pk_link"));
        n.setName(rs.getString("tree_name"));
        n.setKind(rs.getInt("tree_et_pk"));
        long epkRaw = rs.getLong("tree_entity_pk");
        n.setEntityId(rs.wasNull() ? null : epkRaw);
        n.setStatusCode(rs.getInt("tree_status"));
        n.setLevel(rs.getInt("tree_level_adj"));
        n.setDesc(rs.getString("tree_desc"));
        n.setPath(rs.getString("tree_path"));
        return n;
    };

    public BizTreeCacheRepository(@Qualifier("cacheJdbcTemplate") JdbcTemplate cache,
                                   BizTreeCacheSql sql) {
        this.cache = cache;
        this.sql   = sql;
    }

    @Override
    public List<EsqTreeNodeJpa> findRoot(String rootId, int rootLevel, String rootPath) {
        String q = sql.repo.selectCols() + sql.repo.findRoot();
        return cache.query(q, NODE_MAPPER, rootLevel, rootId, rootPath + "%");
    }

    @Override
    public List<EsqTreeNodeJpa> findNodes(String id, int rootLevel, String rootPath) {
        String q = sql.repo.selectCols() + sql.repo.findNodes();
        return cache.query(q, NODE_MAPPER, rootLevel, id, rootPath + "%");
    }

    @Override
    public String findPath(String id) {
        String q = sql.repo.findPath() + sql.repo.selectOne();
        List<String> ret = cache.queryForList(q, String.class, id);
        return ret.isEmpty() ? null : ret.get(0);
    }

    @Override
    public EsqTreeNodeJpa findByEntityId(String id, int rootLevel, String rootPath) {
        String q = sql.repo.selectCols() + sql.repo.findByEntityId() + sql.repo.selectOne();
        List<EsqTreeNodeJpa> ret = cache.query(q, NODE_MAPPER, rootLevel, Long.parseLong(id), rootPath + "%");
        return ret.isEmpty() ? null : ret.get(0);
    }

    @Override
    public EsqTreeNodeJpa findByNameKind(String name, Integer kind, int rootLevel, String rootPath) {
        String q = sql.repo.selectCols() + sql.repo.findByNameKind() + sql.repo.selectOne();
        List<EsqTreeNodeJpa> ret = cache.query(q, NODE_MAPPER, rootLevel, name, kind, rootPath + "%");
        return ret.isEmpty() ? null : ret.get(0);
    }

    @Override
    public void updateNode(long entityPk, String name, String desc, Integer statusCode) {
        int ret = cache.update(sql.repo.updateNode(),
                name, name, desc, desc, statusCode, statusCode, entityPk);
        log.debug("BizTreeCacheRepository: updateNode id={} name={} desc={} status={} rows={}",
                entityPk, name, desc, statusCode, ret);
    }
}
