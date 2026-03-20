package pro.mir0n.esquire.bizTree.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    // ---- helpers ----

    private void stubEnvelope(String entityId, int kind, String eventType, String textJson) throws JMSException {
        when(message.getStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID)).thenReturn("msg-1");
        when(message.getStringProperty(EsqMsgConstants.FIELD_SERVICE_ID)).thenReturn(EsqMsgConstants.SERVICE_ID_ENTITY_BROADCAST);
        when(message.getStringProperty(EsqMsgConstants.FIELD_ENTITY_ID)).thenReturn(entityId);
        when(message.getIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND)).thenReturn(kind);
        when(message.getStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE)).thenReturn(eventType);
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
}
