/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  created: vendor-agnostic interface for in-memory tree cache
 *                   updateNode(entityPk, name, desc, statusCode); SKIP sentinel for desc; entity_pk unique
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
}
