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

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @ToString @AllArgsConstructor @NoArgsConstructor
public class EsqCustomEntityFieldJpa {
    @Id
    private String name;
    private Integer kind;
    private Integer layer;
    private Integer sort;
    private String label;
    private String type;
    private String tooltip;
    private String listvalues;
    private String nullable;
    private String nullmeaning;
    private String validation;
    private Integer readwrite;
    private String format;
    private String personal;
};



