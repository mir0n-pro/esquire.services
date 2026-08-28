package pro.mir0n.esquire.bizTree.cache;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.bizTree.BizTreeConstants;
import pro.mir0n.esquire.bizTree.cache.impl.BizTreeCacheRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * A subtree move arrives as one EVENT_UPDATE_PATH per node, and the receive pool applies them on
 * several threads -- so a child can be applied before its parent. These check that the placement a
 * move writes comes from the EVENT and not from another node's cached state, which is what made the
 * order matter.
 */
@ExtendWith(MockitoExtension.class)
class MoveNodeOrderTest {

    private static final long   HOME_B  = 41L;
    private static final long   TOP_ORG = 42L;
    private static final long   MID_ORG = 43L;
    private static final long   USR     = 44L;
    private static final String NEW_MID_PATH = "1.14." + HOME_B + "." + TOP_ORG + "." + MID_ORG + ".";
    private static final String NEW_USR_PATH = NEW_MID_PATH + USR + ".";

    @Mock private JdbcTemplate cache;

    private BizTreeCacheRepository repository;

    @BeforeAll
    static void seedKindStorage() {
        EsqObjectKind clientFolder = new EsqObjectKind();
        clientFolder.setId(BizTreeConstants.FOLDER_CLIENT);
        clientFolder.setChildKinds(List.of(34));
        EsqObjectKindStorage.getInstance().init(clientFolder);

        EsqObjectKind client = new EsqObjectKind();
        client.setId(34);
        EsqObjectKindStorage.getInstance().init(client);
    }

    @BeforeEach
    void setUp() {
        BizTreeCacheSql templates = new BizTreeCacheSql(
                new BizTreeCacheSql.Ddl("", "", ""),
                new BizTreeCacheSql.Repo("", "", "", "", "FIND_PATH", "", "", "", "", "", "MOVE_NODE",
                        "MOVE_ACCT_LINK", "FIND_FOLDER_PKS", "", "", "", "", "", ""),
                new BizTreeCacheSql.Loader("", "", "")
        );
        repository = new BizTreeCacheRepository(cache, CacheSqlSet.forTable(templates, "ESQ_TREE"));
    }

    @Test
    @DisplayName("a user applied before its org lands under the NEW home")
    void userMoveIsIndependentOfTheOrgMove() {
        repository.moveUsrNode(USR, 34, NEW_USR_PATH);

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(cache).update(anyString(), args.capture(), args.capture(), args.capture(),
                args.capture(), args.capture());

        String folderPk = MID_ORG + "~" + BizTreeConstants.folderKindForUsr(34);
        assertThat(args.getAllValues().get(0)).isEqualTo(NEW_MID_PATH + folderPk + "." + USR + ".");
        assertThat(args.getAllValues().get(1)).isEqualTo(NEW_USR_PATH);
        assertThat(args.getAllValues().get(3)).isEqualTo(folderPk);
    }

    @Test
    @DisplayName("an org takes its path from the event, not from its parent's cached row")
    void orgMoveIsIndependentOfTheParentMove() {
        lenient().when(cache.queryForList(anyString(), eq(String.class), any(Object[].class)))
                 .thenReturn(List.of());
        lenient().when(cache.batchUpdate(anyString(), anyList())).thenReturn(new int[0]);

        repository.moveOrgNode(MID_ORG, NEW_MID_PATH);

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(cache).update(anyString(), args.capture(), args.capture(), args.capture(),
                args.capture(), args.capture());

        assertThat(args.getAllValues().get(0)).isEqualTo(NEW_MID_PATH);
        assertThat(args.getAllValues().get(2)).isEqualTo(4);
        assertThat(args.getAllValues().get(3)).isEqualTo(String.valueOf(TOP_ORG));
    }

    // =====================================================================
    // A created node must describe itself the way a LOADED node does
    // =====================================================================

    @Test
    @DisplayName("a CREATE row carries change numbers 1/1, so the night-watch digest matches a loaded row")
    void createRow_carriesChangeNumberOne() {
        // The digest includes both change-number columns. A loader seeds them from the DB, where a new path
        // row is ep_change_no DEFAULT 1 -- so a created row must say 1 as well, or every sweep after a create
        // reports drift that is not there.
        when(cache.queryForList(anyString(), eq(String.class), any())).thenReturn(List.of("1.5."));

        repository.insertOrgNodes(77L, 20, "ACME", "d", "5", "1.5.77.");

        ArgumentCaptor<List<Object[]>> capt = ArgumentCaptor.forClass(List.class);
        verify(cache).batchUpdate(anyString(), capt.capture());
        for (Object[] row : capt.getValue()) {
            assertThat(row[row.length - 2])
                    .as("entity change number on a created row")
                    .isEqualTo(1L);
            assertThat(row[row.length - 1])
                    .as("path change number on a created row -- the one nothing stamps afterwards")
                    .isEqualTo(1L);
        }
    }
}
