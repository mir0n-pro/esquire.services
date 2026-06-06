/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created (enyMan): read projection for parameter rows (name, et_pk, value) used by the
 *                   x-Rod param audit (listOrgPar/listUsrPar re-SELECT -> *_PAR events).
 * 06/05/2026 mir0n  moved to common; implements IMappable -- fillMap() emits etPk + value (name is the
 *                   sub_id, carried in the x-Rod header). Shared across enyMan org_par / usr_par (+ future).
 */
package pro.mir0n.esquire.backend.jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import pro.mir0n.esquire.backend.jpa.IMappable;

import java.util.Map;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class EsqParRow implements IMappable {
    @Id
    private String name;
    private Integer etPk;
    private String value;

    @Override
    public void fillMap(Map<String, Object> body) {
        body.put("etPk", etPk);       // name is the param name -> sub_id (header), not body
        body.put("value", value);
    }
}
