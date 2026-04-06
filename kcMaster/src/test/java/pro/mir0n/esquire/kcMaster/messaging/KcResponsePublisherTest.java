package pro.mir0n.esquire.kcMaster.messaging;

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
import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.common.EsqMsgConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KcResponsePublisherTest {

    @Mock
    private JmsTemplate jmsQueueTemplate;

    @Captor
    private ArgumentCaptor<String> destinationCaptor;

    @Captor
    private ArgumentCaptor<MessageCreator> messageCreatorCaptor;

    private KcResponsePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KcResponsePublisher(jmsQueueTemplate, new ObjectMapper());
    }

    // --- helper: capture the MessageCreator, invoke it with mock session/message ---

    private Message captureAndCreateMessage() throws JMSException {
        verify(jmsQueueTemplate).send(destinationCaptor.capture(), messageCreatorCaptor.capture());
        Session mockSession = mock(Session.class);
        Message mockMsg = mock(Message.class);
        when(mockSession.createMessage()).thenReturn(mockMsg);
        messageCreatorCaptor.getValue().createMessage(mockSession);
        return mockMsg;
    }

    // --- publishSuccess ---

    @Test
    @DisplayName("publishSuccess: sends to esquire.kc.response")
    void publishSuccess_sendsToCorrectQueue() {
        publisher.publishSuccess("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE, "ctrl1", "rid1", "cid1", "treq1");
        verify(jmsQueueTemplate).send(eq(EsqMsgConstants.QUEUE_KC_RESPONSE), any(MessageCreator.class));
    }

    @Test
    @DisplayName("publishSuccess: MsgType is URS")
    void publishSuccess_msgTypeIsUrs() throws Exception {
        publisher.publishSuccess("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE, "ctrl1", "rid1", "cid1", "treq1");
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_MSG_TYPE, EsqMsgConstants.MSG_TYPE_RESPONSE);
    }

    @Test
    @DisplayName("publishSuccess: EventType echoed from command")
    void publishSuccess_eventTypeEchoed() throws Exception {
        publisher.publishSuccess("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_UPDATE, "ctrl1", "rid1", "cid1", "treq1");
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_EVENT_TYPE, EsqMsgConstants.EVENT_UPDATE);
    }

    @Test
    @DisplayName("publishSuccess: EntityKind echoed from parameter")
    void publishSuccess_entityKindEchoed() throws Exception {
        publisher.publishSuccess("eid1", 34, EsqMsgConstants.EVENT_CREATE, "ctrl1", "rid1", "cid1", "treq1");
        Message msg = captureAndCreateMessage();
        verify(msg).setIntProperty(EsqMsgConstants.FIELD_ENTITY_KIND, 34);
    }

    @Test
    @DisplayName("publishSuccess: EntityID forwarded")
    void publishSuccess_entityIdForwarded() throws Exception {
        publisher.publishSuccess("entity-42", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE, "ctrl1", "rid1", "cid1", "treq1");
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_ENTITY_ID, "entity-42");
    }

    @Test
    @DisplayName("publishSuccess: CtrlID forwarded")
    void publishSuccess_ctrlIdForwarded() throws Exception {
        publisher.publishSuccess("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE, "my-ctrl", "rid1", "cid1", "treq1");
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_CTRL_ID, "my-ctrl");
    }

    @Test
    @DisplayName("publishSuccess: TestReqID forwarded")
    void publishSuccess_testReqIdForwarded() throws Exception {
        publisher.publishSuccess("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE, "ctrl1", "rid1", "cid1", "my-treq");
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_TEST_REQ_ID, "my-treq");
    }

    @Test
    @DisplayName("publishSuccess: ApplMsgID is always set (non-null)")
    void publishSuccess_applMsgIdIsSet() throws Exception {
        publisher.publishSuccess("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE, "ctrl1", "rid1", "cid1", "treq1");
        Message msg = captureAndCreateMessage();
        ArgumentCaptor<String> midCap = ArgumentCaptor.forClass(String.class);
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_APPL_MSG_ID), midCap.capture());
        assertThat(midCap.getValue()).isNotNull().isNotEmpty();
    }

    // --- publishFailure ---

    @Test
    @DisplayName("publishFailure: sends to esquire.kc.response")
    void publishFailure_sendsToCorrectQueue() {
        publisher.publishFailure("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE, null,
                "KC_SYNC_ERROR", "something went wrong",
                "ctrl1", "rid1", "cid1", "treq1", null);
        verify(jmsQueueTemplate).send(eq(EsqMsgConstants.QUEUE_KC_RESPONSE), any(MessageCreator.class));
    }

    @Test
    @DisplayName("publishFailure: MsgType is URR")
    void publishFailure_msgTypeIsUrr() throws Exception {
        publisher.publishFailure("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE, null,
                "KC_SYNC_ERROR", "something went wrong",
                "ctrl1", "rid1", "cid1", "treq1", null);
        Message msg = captureAndCreateMessage();
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_MSG_TYPE, EsqMsgConstants.MSG_TYPE_REJECT);
    }

    @Test
    @DisplayName("publishFailure: Error field contains RFC 9457 JSON with errorCode and detail")
    void publishFailure_errorFieldIsRfc9457Json() throws Exception {
        publisher.publishFailure("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE, null,
                "KC_SYNC_ERROR", "user not found",
                "ctrl1", "rid1", "cid1", "treq1", null);
        Message msg = captureAndCreateMessage();

        ArgumentCaptor<String> errorCap = ArgumentCaptor.forClass(String.class);
        verify(msg).setStringProperty(eq(EsqMsgConstants.FIELD_ERROR), errorCap.capture());

        ObjectMapper om = new ObjectMapper();
        var errorNode = om.readTree(errorCap.getValue());
        assertThat(errorNode.get("title").asText()).isEqualTo("KC_SYNC_ERROR");
        assertThat(errorNode.get("detail").asText()).isEqualTo("user not found");
        assertThat(errorNode.get("status").asInt()).isEqualTo(500);
        assertThat(errorNode.get("type").asText()).isEqualTo("about:blank");
    }

    @Test
    @DisplayName("publishFailure: Text and MessageEncoding set when requestText is provided")
    void publishFailure_textAndEncodingSetWhenRequestTextProvided() throws Exception {
        String reqText = "{\"id\":\"uid-001\",\"kind\":34}";
        publisher.publishFailure("eid1", 34, EsqMsgConstants.EVENT_CREATE, null,
                "KC_SYNC_ERROR", "error",
                "ctrl1", "rid1", "cid1", "treq1", reqText);
        Message msg = captureAndCreateMessage();

        verify(msg).setStringProperty(EsqMsgConstants.FIELD_TEXT, reqText);
        verify(msg).setStringProperty(EsqMsgConstants.FIELD_MESSAGE_ENCODING, EsqMsgConstants.MSG_ENCODING_JSON);
    }

    @Test
    @DisplayName("publishFailure: Text and MessageEncoding absent when requestText is null")
    void publishFailure_textAndEncodingAbsentWhenRequestTextNull() throws Exception {
        publisher.publishFailure("eid1", EsqConstants.KIND_ACCESS_PROFILE, EsqMsgConstants.EVENT_CREATE, null,
                "KC_SYNC_ERROR", "error",
                "ctrl1", "rid1", "cid1", "treq1", null);
        Message msg = captureAndCreateMessage();

        verify(msg, org.mockito.Mockito.never()).setStringProperty(eq(EsqMsgConstants.FIELD_TEXT), anyString());
        verify(msg, org.mockito.Mockito.never()).setStringProperty(eq(EsqMsgConstants.FIELD_MESSAGE_ENCODING), anyString());
    }
}
