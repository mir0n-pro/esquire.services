/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n rootPath and uid params added were required
 * 01/23/2026 mir0n use common library
 *                  no more EsqTreeNode methods
 * 02/19/2026 mir0n  added esquireCommandSave()
 * 03/06/2026 mir0n  KIND_CL/MR/P_ACCT and FIELD_STATUS constants added
 * 03/09/2026 mir0n  esquireCommandSave(): roles param added; unused imports removed
 * 03/26/2026 mir0n  esquireCommandNew(), esquireCommandDelete() added
 * 04/07/2026 mir0n  all kind params Integer → int (primitive)
 * 04/09/2026 mir0n  FIELD_CCY constant added
 * 04/14/2026 mir0n  KIND_P_ACCT spacing corrected
 */

package pro.mir0n.esquire.pacMan.service;

import java.util.List;
import java.util.Map;

import pro.mir0n.esquire.backend.dto.EsqEntity;

public interface IPacManService {
    public static final int  KIND_CL_ACCT = 50; // client account
    public static final int  KIND_MR_ACCT = 52; // merchant account
    public static final int  KIND_P_ACCT  = 54; // paper/demo account
    public static final String  FIELD_STATUS = "status";
    public static final String  FIELD_CCY    = "ccy";

    public EsqEntity esquireCommand(int kind, String id, String cmd, String rootPath, String uid );
    public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles);
    public EsqEntity esquireCommandNew(int kind, String parentId, String cmd, Map<String, Object> fields, String rootPath, String uid, List<String> roles);
    public void esquireCommandDelete(int kind, String id, String cmd, String rootPath, String uid, List<String> roles);

    }
