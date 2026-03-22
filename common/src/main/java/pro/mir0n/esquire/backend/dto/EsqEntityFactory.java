/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/25/2026 mir0n  Paper Client Account added
 * 02/02/2026 mir0n  SYSADMIN added
 *                   gaps in Entity Kind enumeration: system objects - orgs - users - accounts
 * 02/12/2026 mir0n  remove EsqEntityKind, use EsqObjectKind instead
 * 02/13/2026 mir0n  use EsqEntityJpa for children
 * 02/28/2026 mir0n  createUser() added with person/address/address2 subentity params
 *                   createEntity() passes null for subentity params
 */

package pro.mir0n.esquire.backend.dto;

import pro.mir0n.esquire.backend.dto.entity.EsqAcct;
import pro.mir0n.esquire.backend.dto.entity.EsqOrg;
import pro.mir0n.esquire.backend.dto.entity.EsqUsr;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.jpa.EsqNameValueJpa;
import org.jetbrains.annotations.NotNull;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

import java.util.List;

public class EsqEntityFactory {

    private static final EsqEntityFactory itSelf = new EsqEntityFactory();

    public static EsqEntityFactory getInstance() {
         return itSelf;
     }

    public EsqEntity createEntity (int kind ) {
        EsqObjectKind eek = EsqObjectKindStorage.getInstance().get(kind);
        EsqEntity ret = null;
        //todo
        if (eek.isOrg()) {
            ret = new EsqOrg();
        } else if (eek.isUsr()) {
            ret = new EsqUsr();
        } else if (eek.isAcct()) {
            ret = new EsqAcct();
        }
        return ret;
     }

    public EsqEntity createEntity (@NotNull EsqEntityJpa jpa,
            List<EsqNameValueJpa> custom,
            List<EsqEntityJpa> children
    ) {
        EsqEntity ret = createEntity (jpa.getKind());
        if (ret != null) {
            ret.fill(jpa, custom, children, null, null, null);
        }
        return ret;
    }

    public EsqEntity createUser (@NotNull EsqEntityJpa jpa,
                   List<EsqNameValueJpa> custom,
                   List<EsqEntityJpa> children,
                   EsqEntityJpa person,
                   EsqEntityJpa address,
                   EsqEntityJpa address2
    ) {
        EsqEntity ret = createEntity (jpa.getKind());
        if (ret != null) {
            ret.fill(jpa, custom, children, person, address, address2);
        }
        return ret;
    }

}

