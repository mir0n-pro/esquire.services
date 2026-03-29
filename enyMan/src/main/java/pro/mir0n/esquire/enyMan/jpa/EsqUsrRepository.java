/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/19/2026 mir0n  created: native queries for usr read/update
 *                   flushAutomatically=false on @Modifying prevents spurious
 *                   Hibernate auto-flush before native query execution
 * 02/28/2026 mir0n  person/address/address2 read queries added
 *                   updatePerson/updateAddress/updateAddress2 write queries added
 * 03/26/2026 mir0n  insertUsr, usrPath, deleteUsr native queries added
 * 03/28/2026 mir0n  deletePersonAddresses, deletePersonBankInfo replace deleteAuth; cascade-safe delete sequence
 * 03/28/2026 mir0n  insertUsrPath, deleteEntityPath added; insertUsr: path param removed
 */

package pro.mir0n.esquire.enyMan.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAcctJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqAddressJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqPersonJpa;
import pro.mir0n.esquire.backend.jpa.entity.EsqUsrJpa;

import java.util.List;

@Repository
public interface EsqUsrRepository extends JpaRepository<EsqUsrJpa, String> {

    @NativeQuery
    EsqUsrJpa detailUsr(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    EsqUsrJpa detailUsrForUpdate(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    List<EsqAcctJpa> userAccts(@Param("id") String id, @Param("rootPath") String rootPath);
    @NativeQuery
    List<EsqNameValueJpa> customUsr(@Param("id") String id);

    @NativeQuery
    EsqPersonJpa person(@Param("id") String id, @Param("kind") int kind); // xxx: user id, person Kind
    @NativeQuery
    EsqAddressJpa address(@Param("id") String id, @Param("kind") int kind); // xxx: user id, person Kind
    @NativeQuery
    EsqAddressJpa address2(@Param("id") String id, @Param("kind") int kind); // xxx: user id, person Kind

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateUsr(@Param("id") String id,
        @Param("name") String name,
        @Param("registration") String registration,
        @Param("deleted") String deleted,
        @Param("desc") String desc,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId

    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateCustomUsr(@Param("id") String id,
        @Param("name") String name,
        @Param("value") String value,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );


    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateAddress(@Param("id") String id,
         @Param("kind") int kind,
         @Param("desc") String desc,
         @Param("addr") String addr,
         @Param("addr2") String addr2,
         @Param("city") String city,
         @Param("company") String company,
         @Param("country") String country,
         @Param("department") String department,
         @Param("fax") String fax,
         @Param("postalCode") String postalCode,
         @Param("province") String province,
         @Param("title") String title,
         @Param("url") String url,
         @Param("uid") String uid,
         @Param("correlationId") String correlationId,
         @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updateAddress2(@Param("id") String id,
                      @Param("kind") int kind,
                      @Param("desc") String desc,
                      @Param("addr") String addr,
                      @Param("addr2") String addr2,
                      @Param("city") String city,
                      @Param("company") String company,
                      @Param("country") String country,
                      @Param("department") String department,
                      @Param("fax") String fax,
                      @Param("postalCode") String postalCode,
                      @Param("province") String province,
                      @Param("title") String title,
                      @Param("url") String url,
                      @Param("uid") String uid,
                      @Param("correlationId") String correlationId,
                      @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int updatePerson(@Param("id") String id,
         @Param("kind") int kind,
         @Param("firstName") String firstName,
         @Param("middleName") String middleName,
         @Param("lastName") String lastName,
         @Param("title") String title,
         @Param("dob") String dob,
         @Param("birthPlace") String birthPlace,
         @Param("sex") String sex,
         @Param("taxId") String taxId,
         @Param("citizenship") String citizenship,
         @Param("marStatus") String marStatus,
         @Param("personIdType") String personIdType,
         @Param("personIdNumber") String personIdNumber,
         @Param("email") String email,
         @Param("phone") String phone,
         @Param("phone2") String phone2,
         @Param("uid") String uid,
         @Param("correlationId") String correlationId,
         @Param("requestId") String requestId
    );

    @NativeQuery
    String usrPath(@Param("parentId") String parentId, @Param("rootPath") String rootPath);

    @NativeQuery
    int countByEmail(@Param("email") String email);

    @NativeQuery
    Long nextAddrPk();

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertCustomUsr(@Param("id") long id,
        @Param("kind") int kind,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertAddress(@Param("id") long id,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertPerson(@Param("id") long id,
        @Param("kind") int kind,
        @Param("adPk") long adPk,
        @Param("bizAdPk") long bizAdPk,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertUsrPath(@Param("pk") long pk, @Param("path") String path);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertUsr(@Param("id") long id,
        @Param("kind") int kind,
        @Param("name") String name,
        @Param("desc") String desc,
        @Param("parentId") String parentId,
        @Param("registration") String registration,
        @Param("deleted") String deleted,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertAuth(@Param("id") long id,
        @Param("loginId") String loginId,
        @Param("email") String email,
        @Param("uid") String uid,
        @Param("correlationId") String correlationId,
        @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int deletePersonAddresses(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int deletePersonBankInfo(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int deleteUsr(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int deleteEntityPath(@Param("pk") String pk);
}
