/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  parentId, path fields added
 * 03/26/2026 mir0n  parentId removed — consolidated to EsqEntityJpa
 * 04/09/2026 mir0n  fundedDate, negativeAllowed fields added
 * 06/05/2026 mir0n  fillMap() override (IMappable): emits ccy/balance/status/fundedDate/negativeAllowed
 *                   on top of super (name/desc/parentId) for the x-Rod account audit body
 */

package pro.mir0n.esquire.backend.jpa.entity;

import jakarta.persistence.Entity;
import lombok.*;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

import java.util.Map;

@Entity
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqAcctJpa extends EsqEntityJpa {
    private String ccy;
    private Double balance;
    private String status;
    private String path;
    private String fundedDate;
    private String negativeAllowed;

    @Override
    public void fillMap(Map<String, Object> body) {
        super.fillMap(body);          // name (-> accl_id), desc, parentId (-> acc_usr_pk)
        body.put("ccy", ccy);
        body.put("balance", balance);
        body.put("status", status);
        body.put("fundedDate", fundedDate);   // pacMan stamps acc_funded_dt on first balance change
        body.put("negativeAllowed", negativeAllowed);
    }
}

