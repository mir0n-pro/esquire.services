/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.enyMan.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EsqCustomFieldRepository extends JpaRepository<EsqNameValueJpa, String> {

    @NativeQuery
    List<EsqNameValueJpa> customUsr (@Param("id") String id);
    @NativeQuery
    List<EsqNameValueJpa> customOrg (@Param("id") String id);

}
