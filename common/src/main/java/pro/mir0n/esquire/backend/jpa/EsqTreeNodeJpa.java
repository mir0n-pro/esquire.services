/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.backend.jpa;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @ToString @AllArgsConstructor @NoArgsConstructor
public class EsqTreeNodeJpa {
    @Id
    private String id;
    private String parentId;
    private String linkId;
    private String name;
    private Integer kind;
    private Long entityId;
    private Integer statusCode;
    private String treeFlags;
    private Integer level;
    private String desc;
    private String path;
}
