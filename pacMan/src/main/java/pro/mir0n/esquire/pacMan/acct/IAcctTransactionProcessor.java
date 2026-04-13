/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *
 *  History:
 * 04/13/2026 mir0n  created: account transaction processor interface; default 7-param delegates to abstract 8-param with skipValidation (test-only validation bypass)
 */

package pro.mir0n.esquire.pacMan.acct;

import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSingle;

import java.util.List;
import java.util.Map;

public interface  IAcctTransactionProcessor {
    default AcctTransactionSingle esquireCommandAcct(int kind, String id, AcctOperation.Code code, Map<String, Object> fields, String rootPath, String uid, List<String> roles) {
        return esquireCommandAcct( kind, id, code, fields, false, rootPath,uid, roles);
    };

    /** For test use only — allows bypassing status/balance/field validation. */
    AcctTransactionSingle esquireCommandAcct(int kind, String id, AcctOperation.Code code, Map<String, Object> fields, boolean skipValidation, String rootPath, String uid, List<String> roles);
}
