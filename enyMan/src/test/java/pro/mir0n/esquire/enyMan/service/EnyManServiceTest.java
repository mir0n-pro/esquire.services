package pro.mir0n.esquire.enyMan.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import pro.mir0n.esquire.backend.dto.EsqEntityDictionary;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.error.PermissionDeniedException;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqOrgRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqUsrRepository;
import pro.mir0n.esquire.enyMan.messaging.EsqEntityBroadcastPublisher;
import pro.mir0n.esquire.enyMan.service.impl.EnyManService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class EnyManServiceTest {

    @Mock
    private EsqEntityDictionaryRepository dictRepo;

    @Mock
    private EsqOrgRepository orgRepo;

    @Mock
    private EsqUsrRepository usrRepo;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private EntityManager em;

    @Mock
    private EsqEntityBroadcastPublisher broadcastPublisher;

    private EnyManService service;

    @BeforeAll
    static void initStorage() {
        EsqObjectKindStorage oks = EsqObjectKindStorage.getInstance();
        oks.init(new EsqObjectKind(10, "org", "Org", "orgs", "Test org",
            true, false, false, "", false, false, "", null, null, null, false));
        oks.init(new EsqObjectKind(20, "usr", "Usr", "usrs", "Test usr",
            false, true, false, "", false, false, "", null, null, null, false));

        // Dictionary entry for kind 50 — used by esquireDictionary() tests
        EsqEntityLayer layer = new EsqEntityLayer();
        layer.setLayer(1);
        layer.setTitle("Test Layer");
        layer.setFields(List.of());
        EsqEntityDictionary dict = new EsqEntityDictionary();
        dict.setKind(50);
        dict.getLayers().add(layer);
        dict.setCompleted(true);
        EsqEntityDictionaryStorage.getInstance().init(dict);
    }

    @BeforeEach
    void setUp() {
        service = new EnyManService(dictRepo, orgRepo, usrRepo, transactionTemplate, em, broadcastPublisher);
    }

    // ---- esquireCommandSave: org kind, null roles → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandSave: org kind, null roles → PermissionDeniedException")
    void esquireCommandSave_orgKind_nullRoles_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandSave(10, "100", "save", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandSave: usr kind, null roles, not self → PermissionDeniedException ----

    @Test
    @DisplayName("esquireCommandSave: usr kind, null roles, id != uid → PermissionDeniedException")
    void esquireCommandSave_usrKind_nullRoles_notSelf_throwsPermissionDeniedException() {
        assertThatThrownBy(() ->
            service.esquireCommandSave(20, "50", "save", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(PermissionDeniedException.class);
    }

    // ---- esquireCommandSave: unknown kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommandSave: unknown kind → ResourceNotFoundException")
    void esquireCommandSave_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommandSave(99, "1", "save", Map.of(), "1.2.3", "99", null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireCommand: unknown kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireCommand: unknown kind → ResourceNotFoundException")
    void esquireCommand_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireCommand(99, "1", "details", "1.2.3", "99")
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireDictionary: even kind → returns layers ----

    @Test
    @DisplayName("esquireDictionary: even kind 50 → returns layers")
    void esquireDictionary_evenKind_returnsLayers() {
        List<?> ret = service.esquireDictionary(50);
        assertThat(ret).isNotNull().isNotEmpty();
    }

    // ---- esquireDictionary: odd kind normalized to even → same result ----

    @Test
    @DisplayName("esquireDictionary: odd kind 51 normalized to 50 → returns same layers")
    void esquireDictionary_oddKind_normalizedToEven_returnsLayers() {
        List<?> ret = service.esquireDictionary(51);
        assertThat(ret).isNotNull().isNotEmpty();
    }

    // ---- esquireDictionary: null kind → ResourceNotFoundException (no NPE) ----

    @Test
    @DisplayName("esquireDictionary: null kind → ResourceNotFoundException, not NPE")
    void esquireDictionary_nullKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireDictionary(null)
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- esquireDictionary: unknown kind → ResourceNotFoundException ----

    @Test
    @DisplayName("esquireDictionary: unknown kind 9999 → ResourceNotFoundException")
    void esquireDictionary_unknownKind_throwsResourceNotFoundException() {
        assertThatThrownBy(() ->
            service.esquireDictionary(9999)
        ).isInstanceOf(ResourceNotFoundException.class);
    }
}
