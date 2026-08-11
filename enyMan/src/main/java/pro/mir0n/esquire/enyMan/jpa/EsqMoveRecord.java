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
    /** The PATH change number ({@code EP_CHANGE_NO}), read back after the move raised it. Its own counter,
     *  separate from the entity row's: a move rewrites every descendant path row while leaving those
     *  descendants' entity rows untouched, so this is the only number a descendant's move can carry. */
    private Long changeNo;
}
