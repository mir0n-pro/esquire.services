/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/11/2026 mir0n  v1.2.12 -- changeNo component added: the PATH row's number, the only number a
 *                   descendant's move can carry
 * 08/26/2026 mir0n  changeNo split in two: pathChangeNo (EP_CHANGE_NO) and entityChangeNo, read back from the
 *                   same row after the move raised them
 */
package pro.mir0n.esquire.enyMan.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class EsqMoveRecord {
    @Id
    private String id;
    private Integer kind;
    private String path;
    private Long pathChangeNo;
    private Long entityChangeNo;
}
