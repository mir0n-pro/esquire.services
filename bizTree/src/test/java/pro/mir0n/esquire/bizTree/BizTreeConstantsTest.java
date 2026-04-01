package pro.mir0n.esquire.bizTree;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BizTreeConstantsTest {

    @BeforeAll
    static void seedStorage() {
        // FOLDER_SYS_ADMIN first — so iteration encounters it before FOLDER_ADMIN.
        // Both claim childKinds=[32], reproducing the mis-routing scenario that was fixed.
        EsqObjectKind sysAdminFolder = new EsqObjectKind();
        sysAdminFolder.setId(BizTreeConstants.FOLDER_SYS_ADMIN);   // 2
        sysAdminFolder.setChildKinds(List.of(32));
        EsqObjectKindStorage.getInstance().init(sysAdminFolder);

        EsqObjectKind adminFolder = new EsqObjectKind();
        adminFolder.setId(BizTreeConstants.FOLDER_ADMIN);           // 4
        adminFolder.setChildKinds(List.of(32));
        EsqObjectKindStorage.getInstance().init(adminFolder);
    }

    // ---- folderKindForUsr ----

    @Test
    @DisplayName("folderKindForUsr: kind matching FOLDER_SYS_ADMIN childKinds → FOLDER_ADMIN, not FOLDER_SYS_ADMIN")
    void folderKindForUsr_kindMatchesSysAdminChildKinds_returnsFolderAdmin() {
        int result = BizTreeConstants.folderKindForUsr(32);
        assertThat(result).isEqualTo(BizTreeConstants.FOLDER_ADMIN);
        assertThat(result).isNotEqualTo(BizTreeConstants.FOLDER_SYS_ADMIN);
    }

    @Test
    @DisplayName("folderKindForUsr: unknown kind → defaults to FOLDER_ADMIN")
    void folderKindForUsr_unknownKind_defaultsToFolderAdmin() {
        int result = BizTreeConstants.folderKindForUsr(999);
        assertThat(result).isEqualTo(BizTreeConstants.FOLDER_ADMIN);
    }

    // ---- decodeStatus ----

    @Test
    @DisplayName("decodeStatus: Y → STATUS_DELETED")
    void decodeStatus_Y_returnsStatusDeleted() {
        assertThat(BizTreeConstants.decodeStatus("Y")).isEqualTo(BizTreeConstants.STATUS_DELETED);
    }

    @Test
    @DisplayName("decodeStatus: C → STATUS_DELETED")
    void decodeStatus_C_returnsStatusDeleted() {
        assertThat(BizTreeConstants.decodeStatus("C")).isEqualTo(BizTreeConstants.STATUS_DELETED);
    }

    @Test
    @DisplayName("decodeStatus: L → STATUS_LOCKED")
    void decodeStatus_L_returnsStatusLocked() {
        assertThat(BizTreeConstants.decodeStatus("L")).isEqualTo(BizTreeConstants.STATUS_LOCKED);
    }

    @Test
    @DisplayName("decodeStatus: null → STATUS_OK")
    void decodeStatus_null_returnsStatusOk() {
        assertThat(BizTreeConstants.decodeStatus(null)).isEqualTo(BizTreeConstants.STATUS_OK);
    }

    @Test
    @DisplayName("decodeStatus: O → STATUS_OK")
    void decodeStatus_O_returnsStatusOk() {
        assertThat(BizTreeConstants.decodeStatus("O")).isEqualTo(BizTreeConstants.STATUS_OK);
    }
}
