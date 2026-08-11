/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/11/2026 mir0n  v1.2.12 -- changeNo field and bumpChangeNo() added
 */

package pro.mir0n.esquire.backend.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter @Setter @ToString //@AllArgsConstructor @NoArgsConstructor
public class EsqNameValueJpa {
    @Id
    private String name;
    private String value;
    // Per-parameter-row change number, read with the row inside the OWNER's FOR UPDATE lock so the raise
    // needs no guard of its own. Same routine as everywhere else: every write to the row raises it.
    private Long changeNo;

    /** @see pro.mir0n.esquire.backend.jpa.EsqEntityJpa#bumpChangeNo() -- identical contract. */
    public Long bumpChangeNo() {
        changeNo = (changeNo == null ? 0L : changeNo) + 1L;
        return changeNo;
    }
};



