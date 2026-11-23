/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/18/2026 mir0n  created (v2.0.2): the claims of one user -- loginId, uid, rootPath, roles, changeNo;
 *                   compareTo orders two answers of the same uid by changeNo, and another uid always compares
 *                   greater
 */

package pro.mir0n.esquire.backend.dto.access;

import java.util.List;
import java.util.Objects;

public record EsqUserClaims(String loginId, String uid, String rootPath, List<String> roles, long changeNo)
        implements Comparable<EsqUserClaims> {

    //xxx: the change number counts the saves of ONE user, so it orders two answers of the same user only;
    // another user under the same login id always compares greater, and the cache takes the fresh answer
    @Override
    public int compareTo(EsqUserClaims other) {
        int ret = 1;
        if (Objects.equals(uid, other.uid)) {
            ret = Long.compare(changeNo, other.changeNo);
        }
        return ret;
    }
}
