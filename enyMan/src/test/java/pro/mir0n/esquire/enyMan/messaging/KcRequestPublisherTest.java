package pro.mir0n.esquire.enyMan.messaging;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KcRequestPublisherTest {

    @Mock
    private JmsTemplate jmsQueueTemplate;

    @Captor
    private ArgumentCaptor<String> destinationCaptor;

    @Captor
    private ArgumentCaptor<MessageCreator> messageCreatorCaptor;

    private KcRequestPublisher publisher;

    @BeforeEach
    void setUp() throws Exception {
        publisher = new KcRequestPublisher(jmsQueueTemplate, new ObjectMapper());
        var f = KcRequestPublisher.class.getDeclaredField("ctrlId");
        f.setAccessible(true);
        f.set(publisher, "enyman.test");
    }

    private Message captureAndCreateMessage() throws JMSException {
        verify(jmsQueueTemplate).send(destinationCaptor.capture(), messageCreatorCaptor.capture());
        Session mockSession = mock(Session.class);
        Message mockMsg = mock(Message.class);
        when(mockSession.createMessage()).thenReturn(mockMsg);
        messageCreatorCaptor.getValue().createMessage(mockSession);
        return mockMsg;
    }

    @Test
    @DisplayName("publishPathUpdate: sends to esquire.kc.request")
    void publishPathUpdate_sendsToKcRequestQueue() {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "rid1", "cid1");
        verify(jmsQueueTemplate).send(eq(EsqMsgConstants.QUEUE_KC_REQUEST), any(MessageCreator.class));
    }

    @Test
    @DisplayName("publishPathUpdate: all required JMS properties are set")
    void publishPathUpdate_allRequiredPropertiesSet() throws Exception {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "rid1", "cid1");
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_APPL_MSG_ID),      anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_MSG_TYPE),         anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_EVENT_TYPE),       anyString());
        verify(msg).setIntProperty   (eq(EsqMsgConstants.FIELD_ENTITY_KIND),      anyInt());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_ENTITY_ID),        anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_CTRL_ID),          anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_REQUEST_ID),       anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_CORRELATION_ID),   anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_TEST_REQ_ID),      anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_MESSAGE_ENCODING), anyString());
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_TEXT),             anyString());
    }

    @Test
    @DisplayName("publishPathUpdate: MsgType=URQ, EventType=X, Encoding=JSON")
    void publishPathUpdate_fixedFieldValues() throws Exception {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "rid1", "cid1");
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_MSG_TYPE,         EsqMsgConstants.MSG_TYPE_REQUEST);
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE,       EsqMsgConstants.EVENT_UPDATE_PATH);
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MSG_ENCODING_JSON);
    }

    @Test
    @DisplayName("publishPathUpdate: entityId and entityKind set correctly")
    void publishPathUpdate_entityFieldsCorrect() throws Exception {
        publisher.publishPathUpdate("uid-99", 20, "1.10.uid-99.", "rid1", "cid1");
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_ENTITY_ID,   "uid-99");
        verify(msg).setIntProperty   (EsqMsgConstants.FIELD_ENTITY_KIND, 20);
    }

    @Test
    @DisplayName("publishPathUpdate: requestId and correlationId propagated")
    void publishPathUpdate_traceContextPropagated() throws Exception {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "my-rid", "my-cid");
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_REQUEST_ID,     "my-rid");
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_CORRELATION_ID, "my-cid");
    }

    @Test
    @DisplayName("publishPathUpdate: testReqId equals requestId when requestId provided")
    void publishPathUpdate_testReqIdEqualsRequestId() throws Exception {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "my-rid", "cid1");
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_TEST_REQ_ID, "my-rid");
    }

    @Test
    @DisplayName("publishPathUpdate: testReqId generated when requestId is null")
    void publishPathUpdate_testReqIdGeneratedWhenRequestIdNull() throws Exception {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", null, "cid1");
        Message msg = captureAndCreateMessage();
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_TEST_REQ_ID), cap.capture());
        assertThat(cap.getValue()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("publishPathUpdate: Text is valid JSON containing id, kind and path")
    void publishPathUpdate_textIsJsonWithIdKindPath() throws Exception {
        publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "rid1", "cid1");
        Message msg = captureAndCreateMessage();
        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_TEXT), textCap.capture());
        String json = textCap.getValue();
        new ObjectMapper().readTree(json);
        assertThat(json).contains("\"id\"").contains("\"kind\"").contains("\"path\"");
        assertThat(json).contains("uid-1").contains("1.10.uid-1.");
    }

    @Test
    @DisplayName("publishPathUpdate: JmsTemplate exception does not propagate")
    void publishPathUpdate_jmsExceptionAbsorbed() {
        doThrow(new RuntimeException("broker down"))
                .when(jmsQueueTemplate).send(anyString(), any(MessageCreator.class));
        assertThatNoException().isThrownBy(() ->
                publisher.publishPathUpdate("uid-1", 20, "1.10.uid-1.", "rid1", "cid1"));
    }
}
