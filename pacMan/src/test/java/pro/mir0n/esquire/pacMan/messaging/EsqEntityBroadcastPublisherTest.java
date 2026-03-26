package pro.mir0n.esquire.pacMan.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
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
        sf.set(publisher, EsqMsgConstants.SERVICE_ID_ENTITY_BROADCAST);
        var field = EsqEntityBroadcastPublisher.class.getDeclaredField("ctrlId");
        field.setAccessible(true);
        field.set(publisher, "pacman.test");
    }

    // --- helper: capture and invoke the message creator ---

    private Message captureAndCreateMessage() throws JMSException {
        verify(jmsTopicTemplate).send(destinationCaptor.capture(), messageCreatorCaptor.capture());
        Session mockSession = mock(Session.class);
        Message mockMsg = mock(Message.class);
        when(mockSession.createMessage()).thenReturn(mockMsg);
        messageCreatorCaptor.getValue().createMessage(mockSession);
        return mockMsg;
    }

    // ---- topic name ----

    @Test
    @DisplayName("publish: sends to esquire.entity.broadcast")
    void publish_sendsToCorrectTopic() {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of());
        verify(jmsTopicTemplate).send(eq(EsqMsgConstants.TOPIC_ENTITY_BROADCAST), any(MessageCreator.class));
    }

    // ---- all 14 canonical fields set as JMS properties ----

    @Test
    @DisplayName("publish: all 14 canonical fields are set as JMS properties")
    void publish_allCanonicalFieldsSetAsProperties() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of("status", "ACTIVE"));
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_APPL_MSG_ID),      anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_SENDING_TIME),     anyString());
        verify(msg).setIntProperty   (eq(EsqMsgConstants.FIELD_SCHEMA_VERSION),   anyInt());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_BUS_ID),           anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_SERVICE_ID),       anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_CTRL_ID),          anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_MSG_TYPE),         anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_EVENT_TYPE),       anyString());
        verify(msg).setIntProperty   (eq(EsqMsgConstants.FIELD_ENTITY_KIND),      anyInt());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_ENTITY_ID),        anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_REQUEST_ID),       anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_CORRELATION_ID),   anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_MESSAGE_ENCODING), anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_TEXT),             anyString());
    }

    // ---- fixed phase-1 property values ----

    @Test
    @DisplayName("publish: fixed phase-1 property values are correct")
    void publish_fixedPropertyValues() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of());
        Message msg = captureAndCreateMessage();
        verify(msg).setIntProperty   (EsqMsgConstants.FIELD_SCHEMA_VERSION,   EsqMsgConstants.SCHEMA_VERSION);
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_BUS_ID,           EsqMsgConstants.BUS_ID_ENTITY);
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_SERVICE_ID,       EsqMsgConstants.SERVICE_ID_ENTITY_BROADCAST);
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_MSG_TYPE,         EsqMsgConstants.MSG_TYPE_ENTITY_BROADCASTS);
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MSG_ENCODING_JSON);
    }

    // ---- RequestID propagation ----

    @Test
    @DisplayName("publish: RequestID from caller is used when provided")
    void publish_requestIdPropagated() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "my-request-id", "cid1", Map.of());
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_REQUEST_ID, "my-request-id");
    }

    // ---- CorrelationID propagation ----

    @Test
    @DisplayName("publish: CorrelationID from caller is used when provided")
    void publish_correlationIdPropagated() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "my-correlation-id", Map.of());
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID, "my-correlation-id");
    }

    // ---- fallback generation when null ----

    @Test
    @DisplayName("publish: RequestID generated (non-null) when caller passes null")
    void publish_requestIdGeneratedWhenNull() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, null, null, Map.of());
        Message msg = captureAndCreateMessage();
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_REQUEST_ID), cap.capture());
        assertThat(cap.getValue()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("publish: CorrelationID generated (non-null) when caller passes null")
    void publish_correlationIdGeneratedWhenNull() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, null, null, Map.of());
        Message msg = captureAndCreateMessage();
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_CORRELATION_ID), cap.capture());
        assertThat(cap.getValue()).isNotNull().isNotEmpty();
    }

    // ---- Text is a JMS property containing valid JSON ----

    @Test
    @DisplayName("publish: Text is a JMS string property with valid JSON entity state")
    void publish_textIsJsonProperty() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1",
                Map.of("id", "200", "kind", 34, "name", "ACC-1"));
        Message msg = captureAndCreateMessage();
        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_TEXT), textCap.capture());
        // must be valid JSON
        new ObjectMapper().readTree(textCap.getValue());
        assertThat(textCap.getValue()).contains("\"name\"");
    }

    // ---- ApplMsgID is unique per call ----

    @Test
    @DisplayName("publish: ApplMsgID is unique per message")
    void publish_applMsgIdIsUnique() throws Exception {
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of());
        publisher.publish(34, "200", EsqMsgConstants.EVENT_UPDATE, "rid1", "cid1", Map.of());

        ArgumentCaptor<MessageCreator> mcCap = ArgumentCaptor.forClass(MessageCreator.class);
        verify(jmsTopicTemplate, times(2)).send(anyString(), mcCap.capture());

        Session s1 = mock(Session.class); Message m1 = mock(Message.class);
        Session s2 = mock(Session.class); Message m2 = mock(Message.class);
        when(s1.createMessage()).thenReturn(m1);
        when(s2.createMessage()).thenReturn(m2);
        mcCap.getAllValues().get(0).createMessage(s1);
        mcCap.getAllValues().get(1).createMessage(s2);

        ArgumentCaptor<String> id1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> id2 = ArgumentCaptor.forClass(String.class);
        verify(m1).setStringProperty(eq(EsqMsgConstants.FIELD_APPL_MSG_ID), id1.capture());
        verify(m2).setStringProperty(eq(EsqMsgConstants.FIELD_APPL_MSG_ID), id2.capture());
        assertThat(id1.getValue()).isNotEqualTo(id2.getValue());
    }
}
