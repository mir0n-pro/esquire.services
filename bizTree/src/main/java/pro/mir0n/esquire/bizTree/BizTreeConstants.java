/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/25/2026 mir0n  created: centralized constants for status codes, folder kinds, entity kind
 *                   ranges, folder names, H2 column names, JSON field names, key separator
 * 03/26/2026 mir0n  folderKindForUsr(): data-driven folder routing via EsqObjectKindStorage childKinds;
 *                   KIND_USR_CLIENT/KIND_USR_MERCHANT removed
 */
package pro.mir0n.esquire.bizTree;

import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

/**
 * Centralized constants for the BizTree service.
 *
 * Covers: cache status codes, raw status flag strings, folder entity kinds,
 * entity kind ranges, folder display names, H2 column names, and the folder composite-key separator.
 */
public class BizTreeConstants {
    private BizTreeConstants() {}

    // --- Cache status codes (stored in tree_status) ---
    public static final int STATUS_OK      = 0;
    public static final int STATUS_DELETED = 1;
    public static final int STATUS_LOCKED  = 2;

    // --- Raw DB/JMS status flag values ---
    public static final String FLAG_DELETED = "Y";   // usr_deleted_flg = Y  → STATUS_DELETED
    public static final String FLAG_CLOSED  = "C";   // acc_status = C       → STATUS_DELETED
    public static final String FLAG_LOCKED  = "L";   // acc_status = L       → STATUS_LOCKED

    // --- Folder entity kinds (tree_et_pk for virtual folder nodes) ---
    public static final int FOLDER_SYS_ADMIN = 2;    // root-org sys-admin folder
    public static final int FOLDER_ADMIN     = 4;    // all-admins folder
    public static final int FOLDER_ACCOUNT   = 6;    // all-accounts folder
    public static final int FOLDER_CLIENT    = 8;    // all-clients folder
    public static final int FOLDER_MERCHANT  = 10;   // all-merchants folder

    // --- Root org primary key ---
    public static final long ORG_ROOT_PK = 1L;

    // --- Entity kind ranges (for broadcast consumer dispatch) ---
    public static final int KIND_ORG_MIN = 0;
    public static final int KIND_ORG_MAX = 20;
    public static final int KIND_USR_MIN = 30;
    public static final int KIND_USR_MAX = 50;

    // --- Folder display names and descriptions ---
    public static final String FOLDER_ADMIN_NAME      = "All admin-s";
    public static final String FOLDER_ADMIN_DESC      = "Admin-s folder";
    public static final String FOLDER_ACCOUNT_NAME    = "All accounts";
    public static final String FOLDER_ACCOUNT_DESC    = "Accounts folder";
    public static final String FOLDER_CLIENT_NAME     = "All clients";
    public static final String FOLDER_CLIENT_DESC     = "Clients folder";
    public static final String FOLDER_MERCHANT_NAME   = "All merchants";
    public static final String FOLDER_MERCHANT_DESC   = "Merchants folder";
    public static final String FOLDER_SYS_ADMIN_NAME  = "Sys admin-s";
    public static final String FOLDER_SYS_ADMIN_DESC  = "Sys admin-s folder";

    // --- H2 cache table column names ---
    public static final String COL_PK        = "tree_pk";
    public static final String COL_PARENT_PK = "tree_tree_pk_parent";
    public static final String COL_LINK_PK   = "tree_tree_pk_link";
    public static final String COL_NAME      = "tree_name";
    public static final String COL_ET_PK     = "tree_et_pk";
    public static final String COL_ENTITY_PK = "tree_entity_pk";
    public static final String COL_STATUS    = "tree_status";
    public static final String COL_LEVEL     = "tree_level_adj";
    public static final String COL_DESC      = "tree_desc";
    public static final String COL_PATH      = "tree_path";


    // --- Folder routing helper ---
    // Returns the folder kind for a non-root-org user by searching EsqObjectKindStorage:
    // finds the folder kind (id < KIND_ORG_MAX) whose childKinds contains etPk.
    // Defaults to FOLDER_ADMIN if no match is found.
    public static int folderKindForUsr(int etPk) {
        int ret = FOLDER_ADMIN;
        for (EsqObjectKind k : EsqObjectKindStorage.getInstance().getAll()) {
            if (k.getId() < KIND_ORG_MAX && k.getChildKinds() != null && k.getChildKinds().contains(etPk)) {
                ret = k.getId();
                break;
            }
        }
        return ret;
    }

    // --- Status decoding helper ---
    // usr_deleted_flg: Y/C → deleted(1), L → locked(2), null/other → ok(0)
    // acc_status:      C   → deleted(1), L → locked(2), O/null/other → ok(0)
    public static int decodeStatus(String raw) {
        int ret = STATUS_OK;
        if (FLAG_DELETED.equals(raw) || FLAG_CLOSED.equals(raw)) {
            ret = STATUS_DELETED;
        } else if (FLAG_LOCKED.equals(raw)) {
            ret = STATUS_LOCKED;
        }
        return ret;
    }
}
