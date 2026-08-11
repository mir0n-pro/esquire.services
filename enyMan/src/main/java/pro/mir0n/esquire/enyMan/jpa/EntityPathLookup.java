/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/01/2026 mir0n  created: tiny native-query repository for the move-queue worker
 *                   to read and update ep_path on esq_entity_path during reconciliation.
 *                   Two queries: pathFor(id) returns the current ep_path; updatePath(id, path)
 *                   sets it. Kept separate from EsqOrgRepository / EsqUsrRepository / EsqAcctRepository
 *                   so the worker reads via one entity-kind-agnostic seam.
 * 08/11/2026 mir0n  v1.2.12 -- updatePath raises ep_change_no inline (the path table is written under one
 *                   global lock, not read for update per row); pathChangeNoFor() reads the raised number
 *                   back
 */

package pro.mir0n.esquire.enyMan.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;

@Repository
public interface EntityPathLookup extends JpaRepository<EsqAcctJpa, String> {

    @NativeQuery
    String pathFor(@Param("id") String id);

    /** Rewrites the path and raises its change number INLINE ({@code ep_change_no + 1}). The path table is
     *  not read for update per row -- a move takes ONE global lock on the root row -- so the number cannot
     *  be raised in Java before the write. Read it back with {@link #pathChangeNoFor}. */
    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updatePath(@Param("id") String id, @Param("path") String path);

    /** The path change number as it stands now -- read after {@link #updatePath} raised it. */
    @NativeQuery
    Long pathChangeNoFor(@Param("id") String id);
}
