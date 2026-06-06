/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/05/2026 mir0n  created: esq_auth audit body. Carries the keySmith-managed, non-secret auth fields
 *                   (loginId / email / connectFlg / tfaMethod / forceChangeFlg) and fills them by property
 *                   name for the x-Rod AUTH log. Security question / answer are deliberately excluded --
 *                   they are secrets and must not reach esq_auth_log. Not @Entity: keySmith builds it by
 *                   hand from the access profile; it is never persisted or loaded.
 */

package pro.mir0n.esquire.backend.jpa.access;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

import java.util.Map;

@Getter @Setter @ToString
public class EsqAuthJpa extends EsqEntityJpa {
    private String loginId;
    private String email;
    private String connectFlg;
    private String tfaMethod;
    private String forceChangeFlg;

    @Override
    public void fillMap(Map<String, Object> body) {
        // no super: esq_auth_log has no name/desc/parentId; au_usr_pk is the x-Rod header (entityId).
        body.put("loginId", loginId);
        body.put("email", email);
        body.put("connectFlg", connectFlg);
        body.put("tfaMethod", tfaMethod);
        body.put("forceChangeFlg", forceChangeFlg);
    }
}
