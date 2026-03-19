package pro.mir0n.esquire.pacMan.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import pro.mir0n.esquire.common.EsqMsgConstants;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EsqEntityBroadcastPublisherTest {

    @Mock
    private JmsTemplate jmsTopicTemplate;

    @Captor
    private ArgumentCaptor<String> destinationCaptor;

    @Captor
    private ArgumentCaptor<MessageCreator> messageCreatorCaptor;

    private EsqEntityBroadcastPublisher publisher;

    @BeforeEach
    void setUp() throws Exception {
        publisher = new EsqEntityBroadcastPublisher(jmsTopicTemplate, new ObjectMapper());
        var sf = EsqEntityBroadcastPublisher.class.getDeclaredField("serviceId");
        sf.setAccessible(true);
        sf.set(publisher, "pacMan");
        var field = EsqEntityBroadcastPublisher.class.getDeclaredField("ctrlId");
        field.setAccessible(true);
        field.set(publisher, "pacman.test");
    }

    // --- helper to capture and execute the message creator ---

    private TextMessage captureAndCreateMessage() throws JMSException {
        verify(jmsTopicTemplate).send(destinationCaptor.capture(), messageCreatorCaptor.capture());
        Session mockSession = mock(Session.class);
        TextMessage mockMsg = mock(TextMessage.class);
        when(mockSession.createTextMessage(anyString())).thenReturn(mockMsg);
        messageCreatorCaptor.getValue().createMessage(mockSession);
        return mockMsg;
    }

    private String captureBodyJson() throws JMSException {
        verify(jmsTopicTemplate).send(destinationCaptor.capture(), messageCreatorCaptor.capture());
        Session mockSession = mock(Session.class);
        TextMessage mockMsg = mock(TextMessage.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(mockSession.createTextMessage(bodyCaptor.capture())).thenReturn(mockMsg);
        messageCreatorCaptor.getValue().createMessage(mockSession);
        return bodyCaptor.getValue();
    }

    // ---- topic name ----

    @Test
    @DisplayName("publish: sends to esquire.entity.broadcast")
    void publish_sendsToCorrectTopic() {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of());
        verify(jmsTopicTemplate).send(eq(EsqMsgConstants.TOPIC_ENTITY_BROADCAST), any(MessageCreator.class));
    }

    // ---- all 14 canonical fields in body ----

    @Test
    @DisplayName("publish: body contains all 14 required canonical fields")
    void publish_bodyContainsAllCanonicalFields() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of("status", "ACTIVE"));
        String body = captureBodyJson();
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_APPL_MSG_ID + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_SENDING_TIME + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_SCHEMA_VERSION + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_BUS_ID + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_SERVICE_ID + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_CTRL_ID + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_MSG_TYPE + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_EVENT_TYPE + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_ENTITY_KIND + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_ENTITY_ID + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_REQUEST_ID + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_CORRELATION_ID + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_MESSAGE_ENCODING + "\"");
        assertThat(body).contains("\"" + EsqMsgConstants.FIELD_TEXT + "\"");
    }

    // ---- fixed phase-1 values in body ----

    @Test
    @DisplayName("publish: body carries correct fixed phase-1 values")
    @SuppressWarnings("unchecked")
    void publish_fixedValuesInBody() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of());
        String body = captureBodyJson();
        Map<String, Object> parsed = new ObjectMapper().readValue(body, Map.class);
        assertThat(parsed.get(EsqMsgConstants.FIELD_SCHEMA_VERSION)).isEqualTo(EsqMsgConstants.SCHEMA_VERSION);
        assertThat(parsed.get(EsqMsgConstants.FIELD_BUS_ID)).isEqualTo(EsqMsgConstants.BUS_ID_ENTITY);
        assertThat(parsed.get(EsqMsgConstants.FIELD_SERVICE_ID)).isEqualTo("pacMan");
        assertThat(parsed.get(EsqMsgConstants.FIELD_MSG_TYPE)).isEqualTo(EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS);
        assertThat(parsed.get(EsqMsgConstants.FIELD_MESSAGE_ENCODING)).isEqualTo(EsqMsgConstants.MESSAGE_ENCODING);
    }

    // ---- RequestID propagation ----

    @Test
    @DisplayName("publish: RequestID from caller is used when provided")
    @SuppressWarnings("unchecked")
    void publish_requestIdPropagated() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "my-request-id", "cid1", Map.of());
        String body = captureBodyJson();
        Map<String, Object> parsed = new ObjectMapper().readValue(body, Map.class);
        assertThat(parsed.get(EsqMsgConstants.FIELD_REQUEST_ID)).isEqualTo("my-request-id");
    }

    // ---- CorrelationID propagation ----

    @Test
    @DisplayName("publish: CorrelationID from caller is used when provided")
    @SuppressWarnings("unchecked")
    void publish_correlationIdPropagated() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "my-correlation-id", Map.of());
        String body = captureBodyJson();
        Map<String, Object> parsed = new ObjectMapper().readValue(body, Map.class);
        assertThat(parsed.get(EsqMsgConstants.FIELD_CORRELATION_ID)).isEqualTo("my-correlation-id");
    }

    // ---- fallback generation when null ----

    @Test
    @DisplayName("publish: RequestID generated (non-null) when caller passes null")
    @SuppressWarnings("unchecked")
    void publish_requestIdGeneratedWhenNull() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, null, null, Map.of());
        String body = captureBodyJson();
        Map<String, Object> parsed = new ObjectMapper().readValue(body, Map.class);
        assertThat(parsed.get(EsqMsgConstants.FIELD_REQUEST_ID)).isNotNull().isInstanceOf(String.class);
        assertThat((String) parsed.get(EsqMsgConstants.FIELD_REQUEST_ID)).isNotEmpty();
    }

    @Test
    @DisplayName("publish: CorrelationID generated (non-null) when caller passes null")
    @SuppressWarnings("unchecked")
    void publish_correlationIdGeneratedWhenNull() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, null, null, Map.of());
        String body = captureBodyJson();
        Map<String, Object> parsed = new ObjectMapper().readValue(body, Map.class);
        assertThat(parsed.get(EsqMsgConstants.FIELD_CORRELATION_ID)).isNotNull().isInstanceOf(String.class);
        assertThat((String) parsed.get(EsqMsgConstants.FIELD_CORRELATION_ID)).isNotEmpty();
    }

    // ---- JMS properties match body fields (authority rule) ----

    @Test
    @DisplayName("publish: JMS properties carry same values as body fields")
    @SuppressWarnings("unchecked")
    void publish_jmsPropertiesMatchBodyFields() throws Exception {
        publisher.publish(42, "acct-99", EsqMsgConstants.EVENT_CREATE, "rid-x", "cid-x", Map.of());

        verify(jmsTopicTemplate).send(destinationCaptor.capture(), messageCreatorCaptor.capture());
        Session mockSession = mock(Session.class);
        TextMessage mockMsg = mock(TextMessage.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(mockSession.createTextMessage(bodyCaptor.capture())).thenReturn(mockMsg);
        messageCreatorCaptor.getValue().createMessage(mockSession);

        String body = bodyCaptor.getValue();
        Map<String, Object> parsed = new ObjectMapper().readValue(body, Map.class);

        String bodyApplMsgId = (String) parsed.get(EsqMsgConstants.FIELD_APPL_MSG_ID);
        String bodyBusId     = (String) parsed.get(EsqMsgConstants.FIELD_BUS_ID);
        String bodyMsgType   = (String) parsed.get(EsqMsgConstants.FIELD_MSG_TYPE);
        String bodyEventType = (String) parsed.get(EsqMsgConstants.FIELD_EVENT_TYPE);
        String bodyEntityId  = (String) parsed.get(EsqMsgConstants.FIELD_ENTITY_ID);
        String bodyRid       = (String) parsed.get(EsqMsgConstants.FIELD_REQUEST_ID);
        String bodyCid       = (String) parsed.get(EsqMsgConstants.FIELD_CORRELATION_ID);
        int    bodyKind      = (int)    parsed.get(EsqMsgConstants.FIELD_ENTITY_KIND);

        verify(mockMsg).setStringProperty(EsqMsgConstants.FIELD_APPL_MSG_ID,      bodyApplMsgId);
        verify(mockMsg).setStringProperty(EsqMsgConstants.FIELD_BUS_ID,           bodyBusId);
        verify(mockMsg).setStringProperty(EsqMsgConstants.FIELD_MSG_TYPE,         bodyMsgType);
        verify(mockMsg).setStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE,       bodyEventType);
        verify(mockMsg).setStringProperty(EsqMsgConstants.FIELD_ENTITY_ID,        bodyEntityId);
        verify(mockMsg).setStringProperty(EsqMsgConstants.FIELD_REQUEST_ID,       bodyRid);
        verify(mockMsg).setStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID,   bodyCid);
        verify(mockMsg).setIntProperty   (EsqMsgConstants.FIELD_ENTITY_KIND,      bodyKind);
        verify(mockMsg).setIntProperty   (EsqMsgConstants.FIELD_SCHEMA_VERSION,   EsqMsgConstants.SCHEMA_VERSION);
        verify(mockMsg).setStringProperty(EsqMsgConstants.FIELD_SERVICE_ID,       "pacMan");
        verify(mockMsg).setStringProperty(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MESSAGE_ENCODING);
    }

    // ---- Text is body-only ----

    @Test
    @DisplayName("publish: Text is set in body, not as JMS property")
    void publish_textIsBodyOnlyNotJmsProperty() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of("status", "ACTIVE"));
        TextMessage mockMsg = captureAndCreateMessage();
        verify(mockMsg, never()).setStringProperty(eq(EsqMsgConstants.FIELD_TEXT), anyString());
        verify(mockMsg, never()).setObjectProperty(eq(EsqMsgConstants.FIELD_TEXT), any());
    }

    // ---- ApplMsgID is unique per call ----

    @Test
    @DisplayName("publish: ApplMsgID is unique per message")
    @SuppressWarnings("unchecked")
    void publish_applMsgIdIsUnique() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of());
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of());

        ArgumentCaptor<MessageCreator> mcCap = ArgumentCaptor.forClass(MessageCreator.class);
        verify(jmsTopicTemplate, times(2)).send(anyString(), mcCap.capture());

        Session s1 = mock(Session.class);
        Session s2 = mock(Session.class);
        ArgumentCaptor<String> b1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> b2 = ArgumentCaptor.forClass(String.class);
        TextMessage tm1 = mock(TextMessage.class);
        TextMessage tm2 = mock(TextMessage.class);
        when(s1.createTextMessage(b1.capture())).thenReturn(tm1);
        when(s2.createTextMessage(b2.capture())).thenReturn(tm2);
        mcCap.getAllValues().get(0).createMessage(s1);
        mcCap.getAllValues().get(1).createMessage(s2);

        Map<String, Object> p1 = new ObjectMapper().readValue(b1.getValue(), Map.class);
        Map<String, Object> p2 = new ObjectMapper().readValue(b2.getValue(), Map.class);
        assertThat(p1.get(EsqMsgConstants.FIELD_APPL_MSG_ID))
                .isNotEqualTo(p2.get(EsqMsgConstants.FIELD_APPL_MSG_ID));
    }
}
