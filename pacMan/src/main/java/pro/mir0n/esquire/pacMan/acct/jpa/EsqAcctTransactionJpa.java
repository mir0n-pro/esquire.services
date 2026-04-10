/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *
 *  History:
 * 04/09/2026 mir0n  created: JPA entity for ESQ_ACCT_TRANSACTION;
 */

package pro.mir0n.esquire.pacMan.acct.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter @Setter @ToString
public class EsqAcctTransactionJpa {

    @Id
    private Long id;

    private Long accPk;
    private int atPk;
    private Double amt;
    private Double prevBalance;
    private String desc;
    private String refCode;
    private String refCode2;
    private String refCode3;
    private String refCode4;
    private String memo;
    private String crlId;
    private String reqId;
    private String uid;
}
