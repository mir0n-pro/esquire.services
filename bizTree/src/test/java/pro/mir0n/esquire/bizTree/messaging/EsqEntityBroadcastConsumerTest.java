package pro.mir0n.esquire.bizTree.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pro.mir0n.esquire.backend.dto.EsqObjectKind;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.bizTree.cache.IBizTreeCacheRepository;
import pro.mir0n.esquire.common.EsqMsgConstants;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EsqEntityBroadcastConsumerTest {

    @Mock private IBizTreeCacheRepository cacheRepository;
    @Mock private ObjectMapper            objectMapper;
    @Mock private Message                 message;

    @InjectMocks private EsqEntityBroadcastConsumer consumer;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String       SKIP   = IBizTreeCacheRepository.SKIP;

    @BeforeAll
    static void initKindStorage() {
        EsqObjectKindStorage s = EsqObjectKindStorage.getInstance();
        // kind 2  — normalized from kind=3 used in UPDATE tests; registered as org type
        s.init(new EsqObjectKind(2,  "testOrg",  "Test Org",  "", "", true,  false, false, "", false, false, "", null, null, null, false));
        s.init(new EsqObjectKind(0,  "system",   "System",    "", "", true,  false, false, "", false, false, "", null, null, null, false));
        s.init(new EsqObjectKind(20, "org20",    "Org 20",    "", "", true,  false, false, "", false, false, "", null, null, null, false));
        s.init(new EsqObjectKind(30, "usr30",    "User 30",   "", "", false, true,  false, "", false, false, "", null, null, null, false));
        s.init(new EsqObjectKind(34, "client",   "Client",    "", "", false, true,  false, "", false, false, "", null, null, null, false));
        s.init(new EsqObjectKind(36, "merchant", "Merchant",  "", "", false, true,  false, "", false, false, "", null, null, null, false));
        s.init(new EsqObjectKind(50, "acct50",   "Acct 50",   "", "", false, false, true,  "", false, false, "", null, null, null, false));
    }

    // ---- helpers ----

    private void stubEnvelope(String entityId, int kind, String eventType, String textJson) throws JMSException {
        when(message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID)).thenReturn("msg-1");
        when(message.getStringProperty(EsqMsgConstants.FIELD_SERVICE_ID)).thenReturn(EsqMsgConstants.SERVICE_ID_ENTITY_BROADCAST);
        when(message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID)).thenReturn(entityId);
        when(message.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND)).thenReturn(kind);
        when(message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE)).thenReturn(eventType);
        when(message.getStringProperty(EsqMsgConstants.FIELD_REQUEST_ID)).thenReturn("req-1");
        when(message.getStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID)).thenReturn("corr-1");
        when(message.getStringProperty(EsqMsgConstants.FIELD_TEXT)).thenReturn(textJson);
    }

    // ---- UPDATE with name and desc ----

    @Test
    @DisplayName("UPDATE event with name and desc → updateNode(id, kind, name, desc, null)")
    void update_nameAndDesc_callsUpdateNode() throws Exception {
        String textJson = "{\"name\":\"ACME\",\"desc\":\"test\"}";
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).updateNode(42L,"ACME", "test", null);
    }

    // ---- UPDATE with name only ----

    @Test
    @DisplayName("UPDATE event with name only → updateNode(id, kind, name, SKIP, null)")
    void update_nameOnly_descSkipped() throws Exception {
        String textJson = "{\"name\":\"ACME\"}";
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).updateNode(42L,"ACME", SKIP, null);
    }

    // ---- UPDATE with desc only ----

    @Test
    @DisplayName("UPDATE event with desc only → updateNode(id, kind, SKIP, desc, null)")
    void update_descOnly_nameSkipped() throws Exception {
        String textJson = "{\"desc\":\"test\"}";
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).updateNode(42L,null, "test", null);
    }

    // ---- UPDATE with desc explicitly null (clear) ----

    @Test
    @DisplayName("UPDATE event with desc:null → updateNode(id, kind, null, null, null)")
    void update_descExplicitNull_clearsDesc() throws Exception {
        String textJson = "{\"desc\":null}";
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).updateNode(42L,null, null, null);
    }

    // ---- UPDATE with deleted:Y (USR entity, enyMan) ----

    @Test
    @DisplayName("UPDATE event with deleted:Y → updateNode(id, kind, SKIP, SKIP, 1)")
    void update_deletedY_callsUpdateNode() throws Exception {
        String textJson = "{\"deleted\":\"Y\"}";
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).updateNode(42L,null, SKIP, 1);
    }

    // ---- UPDATE with deleted:null → no status update (null is not a valid status signal) ----

    @Test
    @DisplayName("UPDATE event with deleted:null → cache not touched")
    void update_deletedNull_noCacheCall() throws Exception {
        String textJson = "{\"deleted\":null}";
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository, never()).updateNode(anyLong(), any(), any(), any());
    }

    // ---- UPDATE with status:C (ACCT entity closed, pacMan) ----

    @Test
    @DisplayName("UPDATE event with status:C → updateNode(id, kind, null, SKIP, 1)")
    void update_statusClosed_callsUpdateNode() throws Exception {
        String textJson = "{\"status\":\"C\"}";
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).updateNode(42L,null, SKIP, 1);
    }

    // ---- UPDATE with status:L (ACCT entity locked, pacMan) ----

    @Test
    @DisplayName("UPDATE event with status:L → updateNode(id, kind, null, SKIP, 2)")
    void update_statusLocked_callsUpdateNode() throws Exception {
        String textJson = "{\"status\":\"L\"}";
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).updateNode(42L,null, SKIP, 2);
    }

    // ---- UPDATE with name + deleted together ----

    @Test
    @DisplayName("UPDATE event with name + deleted:C → single updateNode call with both")
    void update_nameAndDeleted_callsUpdateNode() throws Exception {
        String textJson = "{\"name\":\"ACME\",\"deleted\":\"C\"}";
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).updateNode(42L,"ACME", SKIP, 1);
    }

    // ---- non-UPDATE event → no cache call ----

    @Test
    @DisplayName("non-UPDATE event → cache not touched")
    void nonUpdate_noCacheCall() throws Exception {
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_CREATE, null);

        consumer.onEntityBroadcast(message);

        verify(cacheRepository, never()).updateNode(anyLong(), any(), any(), any());
    }

    // ---- UPDATE with null Text property → no cache call ----

    @Test
    @DisplayName("UPDATE event with null Text property → cache not touched")
    void update_nullText_noCacheCall() throws Exception {
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, null);

        consumer.onEntityBroadcast(message);

        verify(cacheRepository, never()).updateNode(anyLong(), any(), any(), any());
    }

    // ---- UPDATE with neither name nor desc nor status → no cache call ----

    @Test
    @DisplayName("UPDATE event with no name, desc, status or deleted fields → cache not touched")
    void update_noRelevantFields_noCacheCall() throws Exception {
        String textJson = "{\"id\":\"42\",\"kind\":3}";
        stubEnvelope("42", 3, EsqMsgConstants.EVENT_UPDATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository, never()).updateNode(anyLong(), any(), any(), any());
    }

    // ---- JMS exception → no crash, no cache call ----

    @Test
    @DisplayName("JMSException on property read → handled gracefully, no cache call")
    void jmsException_handledGracefully() throws Exception {
        when(message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID)).thenThrow(new JMSException("err"));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository, never()).updateNode(anyLong(), any(), any(), any());
    }

    // ---- CREATE: org kind 0 → insertOrgNodes called ----

    @Test
    @DisplayName("CREATE event, kind=0 → insertOrgNodes(pk, 0, name, desc, parentId, entityPath)")
    void create_orgKind0_callsInsertOrgNodes() throws Exception {
        String textJson = "{\"parentId\":\"1\",\"path\":\"1.42.\",\"name\":\"NewOrg\",\"desc\":\"d\"}";
        stubEnvelope("42", 0, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).insertOrgNodes(42L, 0, "NewOrg", "d", "1", "1.42.");
    }

    // ---- CREATE: org kind 20 → insertOrgNodes called ----

    @Test
    @DisplayName("CREATE event, kind=20 → insertOrgNodes(pk, 20, name, desc, parentId, entityPath)")
    void create_orgKind20_callsInsertOrgNodes() throws Exception {
        String textJson = "{\"parentId\":\"5\",\"path\":\"1.5.42.\",\"name\":\"SubOrg\",\"desc\":null}";
        stubEnvelope("42", 20, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).insertOrgNodes(42L, 20, "SubOrg", null, "5", "1.5.42.");
    }

    // ---- CREATE: org kind 0, missing parentId → insertOrgNodes NOT called ----

    @Test
    @DisplayName("CREATE event, kind=0, no parentId → insertOrgNodes not called")
    void create_orgMissingParentId_noInsertCall() throws Exception {
        String textJson = "{\"path\":\"1.42.\",\"name\":\"NewOrg\"}";
        stubEnvelope("42", 0, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository, never()).insertOrgNodes(anyLong(), anyInt(), any(), any(), any(), any());
    }

    // ---- CREATE: usr kind 30 → insertUsrNode called ----

    @Test
    @DisplayName("CREATE event, kind=30 → insertUsrNode(pk, 30, name, desc, orgPk, entityPath)")
    void create_usrKind30_callsInsertUsrNode() throws Exception {
        String textJson = "{\"parentId\":\"5\",\"path\":\"1.5.42.\",\"name\":\"John Doe\",\"desc\":\"d\"}";
        stubEnvelope("42", 30, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).insertUsrNode(42L, 30, "John Doe", "d", 5L, "1.5.42.", 0);
    }

    // ---- CREATE: usr kind 34 (client) → insertUsrNode called ----

    @Test
    @DisplayName("CREATE event, kind=34 → insertUsrNode(pk, 34, name, desc, orgPk, entityPath)")
    void create_usrKind34_callsInsertUsrNode() throws Exception {
        String textJson = "{\"parentId\":\"5\",\"path\":\"1.5.43.\",\"name\":\"Client\",\"desc\":null}";
        stubEnvelope("43", 34, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).insertUsrNode(43L, 34, "Client", null, 5L, "1.5.43.", 0);
    }

    // ---- CREATE: usr kind 36 (merchant) → insertUsrNode called ----

    @Test
    @DisplayName("CREATE event, kind=36 → insertUsrNode(pk, 36, name, desc, orgPk, entityPath)")
    void create_usrKind36_callsInsertUsrNode() throws Exception {
        String textJson = "{\"parentId\":\"5\",\"path\":\"1.5.44.\",\"name\":\"Merchant\",\"desc\":\"m\"}";
        stubEnvelope("44", 36, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).insertUsrNode(44L, 36, "Merchant", "m", 5L, "1.5.44.", 0);
    }

    // ---- CREATE: usr kind 30, with deleted flag → insertUsrNode called with STATUS_DELETED ----

    @Test
    @DisplayName("CREATE event, kind=30, deleted=Y → insertUsrNode(pk, 30, name, desc, orgPk, entityPath, 1)")
    void create_usrKind30_withDeletedFlag_callsInsertUsrNodeWithStatusDeleted() throws Exception {
        String textJson = "{\"parentId\":\"5\",\"path\":\"1.5.45.\",\"name\":\"Deleted User\",\"deleted\":\"Y\"}";
        stubEnvelope("45", 30, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).insertUsrNode(45L, 30, "Deleted User", null, 5L, "1.5.45.", 1);
    }

    // ---- CREATE: usr kind 30, missing parentId → insertUsrNode NOT called ----

    @Test
    @DisplayName("CREATE event, kind=30, no parentId → insertUsrNode not called")
    void create_usrMissingParentId_noInsertCall() throws Exception {
        String textJson = "{\"path\":\"1.5.42.\",\"name\":\"John\"}";
        stubEnvelope("42", 30, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository, never()).insertUsrNode(anyLong(), anyInt(), any(), any(), anyLong(), any(), anyInt());
    }

    // ---- CREATE: account kind 50 → insertAcctNode called ----

    @Test
    @DisplayName("CREATE event, kind=50 → insertAcctNode(pk, 50, name, desc, usrPk, entityPath, 0)")
    void create_acctKind50_callsInsertAcctNode() throws Exception {
        String textJson = "{\"parentId\":\"10\",\"path\":\"1.5.10.\",\"name\":\"ACC-1\",\"desc\":\"d\"}";
        stubEnvelope("100", 50, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).insertAcctNode(100L, 50, "ACC-1", "d", 10L, "1.5.10.", 0);
    }

    // ---- CREATE: account kind 50, missing parentId → insertAcctNode NOT called ----

    @Test
    @DisplayName("CREATE event, kind=50, no parentId → insertAcctNode not called")
    void create_acctMissingParentId_noInsertCall() throws Exception {
        String textJson = "{\"path\":\"1.5.10.\",\"name\":\"ACC-1\"}";
        stubEnvelope("100", 50, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository, never()).insertAcctNode(anyLong(), anyInt(), any(), any(), anyLong(), any(), anyInt());
    }

    // ---- CREATE: account kind 50, with status:L → insertAcctNode called with statusCode=2 ----

    @Test
    @DisplayName("CREATE event, kind=50, status=L → insertAcctNode(pk, 50, name, desc, usrPk, path, 2)")
    void create_acctKind50_withStatusLocked_callsInsertAcctNodeWithStatusLocked() throws Exception {
        String textJson = "{\"parentId\":\"10\",\"path\":\"1.5.10.\",\"name\":\"ACC-2\",\"status\":\"L\"}";
        stubEnvelope("101", 50, EsqMsgConstants.EVENT_CREATE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).insertAcctNode(101L, 50, "ACC-2", null, 10L, "1.5.10.", 2);
    }

    // ---- DELETE: org ----

    @Test
    @DisplayName("DELETE event, kind=20 (org) → deleteNodes(pk) called")
    void delete_org_callsDeleteNodes() throws Exception {
        String textJson = "{\"id\":\"42\",\"kind\":20}";
        stubEnvelope("42", 20, EsqMsgConstants.EVENT_DELETE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).deleteNodes(42L);
    }

    // ---- DELETE: usr ----

    @Test
    @DisplayName("DELETE event, kind=30 (usr) → deleteNodes(pk) called")
    void delete_usr_callsDeleteNodes() throws Exception {
        String textJson = "{\"id\":\"55\",\"kind\":30}";
        stubEnvelope("55", 30, EsqMsgConstants.EVENT_DELETE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).deleteNodes(55L);
    }

    // ---- DELETE: acct ----

    @Test
    @DisplayName("DELETE event, kind=50 (acct) → deleteNodes(pk) called")
    void delete_acct_callsDeleteNodes() throws Exception {
        String textJson = "{\"id\":\"100\",\"kind\":50}";
        stubEnvelope("100", 50, EsqMsgConstants.EVENT_DELETE, textJson);
        when(objectMapper.readTree(textJson)).thenReturn(MAPPER.readTree(textJson));

        consumer.onEntityBroadcast(message);

        verify(cacheRepository).deleteNodes(100L);
    }
}
