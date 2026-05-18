/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 04/09/2026 mir0n  created: insertAcctTransaction 14-param native INSERT
 * 04/15/2026 mir0n  nextId(): sequence-based PK via ESQ_ATR_SEQ (vendor SQL in XML)
 * 04/20/2026 mir0n  nextId() removed; PK type Long->String; insertAcctTransaction: pkTx, amtIncoming, ccyIncoming, convRate params added
 */

package pro.mir0n.esquire.pacMan.acct.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface EsqAcctTransactionRepository extends JpaRepository<EsqAcctTransactionJpa, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Transactional
    @NativeQuery
    int insertAcctTransaction(
        @Param("pk")          String pk,
        @Param("pkTx")        String pkTx,
        @Param("accPk")       long   accPk,
        @Param("atPk")        int    atPk,
        @Param("amt")         double amt,
        @Param("prevBalance") double prevBalance,
        @Param("desc")        String desc,
        @Param("refCode")     String refCode,
        @Param("refCode2")    String refCode2,
        @Param("refCode3")    String refCode3,
        @Param("refCode4")    String refCode4,
        @Param("memo")        String memo,
        @Param("crlId")       String crlId,
        @Param("reqId")       String reqId,
        @Param("uid")         String uid,
        @Param("amtIncoming") Double amtIncoming,
        @Param("ccyIncoming") String ccyIncoming,
        @Param("convRate")    Double convRate
    );

    // Bulk delete of all transactions for an account. Called from
    // PacManService.deleteAcct only when the account's ep_path sits inside
    // the seeded Test House subtree ("1.14."); never reached for production
    // accounts.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @NativeQuery
    int deleteAcctTransactionsByAccPk(@Param("accPk") long accPk);
}
