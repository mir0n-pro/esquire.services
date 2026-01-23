/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.enyMan.dto;

import lombok.Getter;
import pro.mir0n.esquire.enyMan.dto.entity.EsqAcct;
import pro.mir0n.esquire.enyMan.dto.entity.EsqOrg;
import pro.mir0n.esquire.enyMan.dto.entity.EsqUsr;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityJpa;
import pro.mir0n.esquire.enyMan.jpa.EsqNameValueJpa;
import pro.mir0n.esquire.enyMan.jpa.EsqTreeNodeJpa;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EsqEntityFactory {

    public enum EsqEntityKind {
        UNKNOWN("unknown", -1, false, false, false, false),
        SYSTEM("system",0, true, false, false, false),
        ORG("organization", 10, true, false, false, false),
        CLIENT("client", 12, false, true, false, true),
        MERCHANT("merchant", 14, false, true, false, true),
        ADMIN("admin", 16, false, true, false, false),
        ACCT_CLIENT("account", 18, false, false, true, false),
        ACCT_MERCHANT("maccount", 20, false, false, true, false),
        ;

        private final int kind;
        @Getter
        private final String name;
        @Getter
        private final String plural;
        @Getter
        private final boolean org;
        @Getter
        private final  boolean usr;
        @Getter
        private final boolean acct;
        @Getter
        private final boolean childrenDetailed;

        private EsqEntityKind(String name, int kind, boolean org, boolean usr, boolean acct, boolean childrenDetailed) {
            this.name = name;
            this.plural = name + "s";
            this.kind = kind;
            this.org = org;
            this.usr = usr;
            this.acct = acct;
            this.childrenDetailed = childrenDetailed;
        }

        public static EsqEntityKind getKind(int kind) {
            for (EsqEntityKind e : EsqEntityKind.values()) {
                if (e.kind == kind) {
                    return e;
                }
            }
            return EsqEntityKind.UNKNOWN;
        }


    }
    private static final EsqEntityFactory itSelf = new EsqEntityFactory();

    public static EsqEntityFactory getInstance() {
         return itSelf;
     }

    public EsqEntity createEntity (int kind ) {
        EsqEntityKind eek = EsqEntityKind.getKind(kind);
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

    public EsqEntity createEntity (@NotNull EsqEntityJpa jpa, List<EsqNameValueJpa> custom, List<EsqTreeNodeJpa> children ) {
        EsqEntity ret = createEntity (jpa.getKind());
        if (ret != null) {
            ret.fill(jpa, custom, children);
        }
        return ret;
    }

}

